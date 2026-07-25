package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class RagEvaluationDatasetLoaderTest {

    private final RagEvaluationDatasetLoader loader = new RagEvaluationDatasetLoader();

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
    void frozenV3CasesKeepTheirQueriesAndEvaluationMetadata() {
        List<RagEvaluationCase> cases = loader.loadFrozenV3();

        RagEvaluationCase first = cases.get(0);
        assertEquals("v3-general-01", first.getId());
        assertEquals("408 统考具体考哪四门，每门多少分？", first.getQuery());
        assertTrue(first.getExpectedMetadata().isEmpty());
        assertEquals("fda7fc39133c3c5a987b5583f67b92f18152e6965f1efd623943a83c3a8c1cdf",
            first.getRelevantConditions().get(0).getContentHash());

        RagEvaluationCase school = cases.stream()
            .filter(testCase -> "v3-school-01".equals(testCase.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("杭电硕士研究生报考需要满足什么条件？", school.getQuery());
        assertEquals("杭州电子科技大学", school.getExpectedMetadata().get("school_name"));
        assertEquals("admission_guide", school.getExpectedMetadata().get("doc_type"));
    }

    @Test
    void frozenV3AndSafetyV3HaveUniqueIdentityAndExplicitNoAnswerSemantics() {
        List<RagEvaluationCase> frozen = loader.loadFrozenV3();
        List<RagEvaluationCase> safety = loader.loadSafetyV3();

        Set<String> frozenIds = frozen.stream().map(RagEvaluationCase::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> frozenQueries = frozen.stream().map(RagEvaluationCase::getQuery).collect(java.util.stream.Collectors.toSet());
        Set<String> safetyIds = safety.stream().map(RagEvaluationCase::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> safetyQueries = safety.stream().map(RagEvaluationCase::getQuery).collect(java.util.stream.Collectors.toSet());

        assertEquals(36, frozenIds.size());
        assertEquals(12, safety.size());
        assertEquals(12, safetyIds.size());
        assertEquals(12, safetyQueries.size());
        assertTrue(java.util.Collections.disjoint(frozenIds, safetyIds));
        assertTrue(java.util.Collections.disjoint(frozenQueries, safetyQueries));
        assertTrue(safety.stream().allMatch(testCase -> testCase.isExpectedFallback()
            && !testCase.isSourceDataAvailable()));
        assertEquals("星河大学", safety.get(0).getExpectedMetadata().get("school_name"));
        assertTrue(safety.get(0).getTags().contains("nonexistent-school"));

        RagEvaluationCase ambiguous = safety.stream()
            .filter(testCase -> "v3-safety-09".equals(testCase.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("那个学校研究生一年多少钱？", ambiguous.getQuery());
        assertTrue(ambiguous.getExpectedMetadata().isEmpty());
        assertTrue(ambiguous.isExpectedFallback());
        assertFalse(ambiguous.isSourceDataAvailable());
        assertTrue(safety.stream().noneMatch(testCase -> "408 统考具体考哪四门，每门多少分？".equals(testCase.getQuery())));
    }

    @Test
    void checksumMismatchIsReportedAsIllegalState() {
        RagEvaluationDatasetLoader mismatchedLoader = new RagEvaluationDatasetLoader(
            "rag-eval/frozen-v3.jsonl", "rag-eval/safety-v3.manifest.json");

        assertThrows(IllegalStateException.class, mismatchedLoader::verifyFrozenV3Checksum);
    }

    @Test
    void countMismatchThrowsForFrozenAndSafetyFixtures() {
        byte[] frozenFixture = jsonLines(35);
        byte[] safetyFixture = jsonLines(11);

        assertThrows(IllegalStateException.class,
            () -> loader.readJsonLines(frozenFixture, "frozen-fixture", 36));
        assertThrows(IllegalStateException.class,
            () -> loader.readJsonLines(safetyFixture, "safety-fixture", 12));
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
