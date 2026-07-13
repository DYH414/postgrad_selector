package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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
    void checksumMismatchIsReportedAsIllegalState() {
        RagEvaluationDatasetLoader mismatchedLoader = new RagEvaluationDatasetLoader(
            "rag-eval/frozen-v1.jsonl", "rag-eval/safety-v1.manifest.json");

        assertThrows(IllegalStateException.class, mismatchedLoader::verifyFrozenV1Checksum);
    }
}
