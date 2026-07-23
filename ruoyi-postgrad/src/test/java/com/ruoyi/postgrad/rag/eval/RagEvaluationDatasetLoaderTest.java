package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class RagEvaluationDatasetLoaderTest {

    private final RagEvaluationDatasetLoader loader = new RagEvaluationDatasetLoader();

    @Test
    void frozenV1LoadsExactlyEighteenCasesAndMatchesManifest() {
        assertEquals(18, loader.loadFrozenV1().size());
        assertDoesNotThrow(loader::verifyFrozenV1Checksum);
        assertEquals(4, loader.loadSafetyV1().size());
        assertDoesNotThrow(loader::verifySafetyV1Checksum);
    }

    @Test
    void frozenV3LoadsReviewedRankingAndSafetyCases() {
        List<RagEvaluationCase> ranking = loader.loadFrozenV3();
        List<RagEvaluationCase> safety = loader.loadSafetyV3();

        assertEquals(36, ranking.size());
        assertEquals(12, safety.size());
        assertDoesNotThrow(loader::verifyFrozenV3Checksum);
        assertDoesNotThrow(loader::verifySafetyV3Checksum);
        assertTrue(ranking.stream().allMatch(testCase ->
            !testCase.getRelevantConditions().isEmpty()
                && testCase.getRelevantConditions().stream()
                .allMatch(condition -> condition.getContentHash() != null)));
        assertTrue(safety.stream().allMatch(RagEvaluationCase::isNoAnswer));
    }

    @Test
    void frozenCasesKeepTheirQueriesAndEvaluationMetadata() {
        List<RagEvaluationCase> cases = loader.loadFrozenV1();

        RagEvaluationCase first = cases.get(0);
        assertEquals("fact-test-01", first.getId());
        assertEquals("深圳大学 人工智能 2025 复试线", first.getQuery());
        assertEquals("15", first.getExpectedMetadata().get("school_id"));
        assertEquals("1768", first.getExpectedMetadata().get("program_id"));
        assertEquals("admission_score", first.getRelevantConditions().get(0).getSection());

        RagEvaluationCase general = cases.stream()
            .filter(testCase -> "general-test-01".equals(testCase.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("考研 A 区和 B 区有什么区别？", general.getQuery());
        assertTrue(general.getExpectedMetadata().isEmpty());
        assertEquals("01-408考试介绍", general.getRelevantSources().get(0));
    }

    @Test
    void frozenAndSafetySetsHaveUniqueIdentityAndExplicitNoAnswerSemantics() {
        List<RagEvaluationCase> frozen = loader.loadFrozenV1();
        List<RagEvaluationCase> safety = loader.loadSafetyV1();

        Set<String> frozenIds = frozen.stream().map(RagEvaluationCase::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> frozenQueries = frozen.stream().map(RagEvaluationCase::getQuery).collect(java.util.stream.Collectors.toSet());
        Set<String> safetyIds = safety.stream().map(RagEvaluationCase::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> safetyQueries = safety.stream().map(RagEvaluationCase::getQuery).collect(java.util.stream.Collectors.toSet());

        assertEquals(18, frozenIds.size());
        assertEquals(4, safety.size());
        assertEquals(4, safetyIds.size());
        assertEquals(4, safetyQueries.size());
        assertTrue(java.util.Collections.disjoint(frozenIds, safetyIds));
        assertTrue(java.util.Collections.disjoint(frozenQueries, safetyQueries));
        assertTrue(safety.stream().allMatch(testCase -> testCase.isExpectedFallback()
            && !testCase.isSourceDataAvailable()));
        assertEquals("99999", safety.get(0).getExpectedMetadata().get("school_id"));
        assertTrue(new HashSet<>(safety.get(0).getTags()).contains("nonexistent-school"));

        RagEvaluationCase ambiguous = safety.stream()
            .filter(testCase -> "safety-004".equals(testCase.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("请查一下那个学校那个专业去年的那个数据，是多少？", ambiguous.getQuery());
        assertTrue(ambiguous.getExpectedMetadata().isEmpty());
        assertTrue(ambiguous.isExpectedFallback());
        assertFalse(ambiguous.isSourceDataAvailable());
        assertTrue(safety.stream().noneMatch(testCase -> "我想了解计算机考研".equals(testCase.getQuery())));
    }

    @Test
    void checksumMismatchIsReportedAsIllegalState() {
        RagEvaluationDatasetLoader mismatchedLoader = new RagEvaluationDatasetLoader(
            "rag-eval/frozen-v1.jsonl", "rag-eval/safety-v1.manifest.json");

        assertThrows(IllegalStateException.class, mismatchedLoader::verifyFrozenV1Checksum);
    }

    @Test
    void countMismatchThrowsForFrozenAndSafetyFixtures() {
        byte[] frozenFixture = jsonLines(17);
        byte[] safetyFixture = jsonLines(3);

        assertThrows(IllegalStateException.class,
            () -> loader.readJsonLines(frozenFixture, "frozen-fixture", 18));
        assertThrows(IllegalStateException.class,
            () -> loader.readJsonLines(safetyFixture, "safety-fixture", 4));
    }

    @Test
    void manifestCaseCountMismatchIsRejectedEvenWhenChecksumIsValid() {
        byte[] fixture = jsonLines(4);
        RagEvaluationDatasetLoader.Manifest manifest = new RagEvaluationDatasetLoader.Manifest();
        manifest.caseCount = 3;
        manifest.sha256 = sha256Hex(fixture);

        assertThrows(IllegalStateException.class,
            () -> loader.verifyChecksum(fixture, manifest, "manifest-fixture", 4));
    }

    @Test
    void checksumTreatsCrLfAndLfAsTheSameFrozenText() {
        byte[] lfFixture = jsonLines(4);
        byte[] crlfFixture = new String(lfFixture, StandardCharsets.UTF_8)
            .replace("\n", "\r\n")
            .getBytes(StandardCharsets.UTF_8);
        RagEvaluationDatasetLoader.Manifest manifest = new RagEvaluationDatasetLoader.Manifest();
        manifest.caseCount = 4;
        manifest.sha256 = sha256Hex(lfFixture);

        assertEquals(manifest.sha256,
            loader.verifyChecksum(crlfFixture, manifest, "crlf-fixture", 4));
    }

    private static byte[] jsonLines(int count) {
        return ("{\"id\":\"fixture\",\"query\":\"fixture\"}\n").repeat(count)
            .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
