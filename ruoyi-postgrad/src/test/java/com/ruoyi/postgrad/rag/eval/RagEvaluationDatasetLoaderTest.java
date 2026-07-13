package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    void frozenCasesKeepTheirQueriesAndEvaluationMetadata() {
        List<RagEvaluationCase> cases = loader.loadFrozenV1();

        RagEvaluationCase first = cases.get(0);
        assertEquals("fact-test-01", first.getId());
        assertEquals("深圳大学 人工智能 2025 复试线", first.getQuery());
        assertEquals("15", first.getExpectedMetadata().get("school_id"));
        assertEquals("1768", first.getRelevantConditions().get(0).getProgramId());

        RagEvaluationCase general = cases.stream()
            .filter(testCase -> "general-test-02".equals(testCase.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("408 统考考哪些科目？每门占多少？", general.getQuery());
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
}
