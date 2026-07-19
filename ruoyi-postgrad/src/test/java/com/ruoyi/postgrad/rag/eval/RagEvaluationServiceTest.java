package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagEvaluationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void technicalFailureIsRecordedAndRemainingCasesContinue() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setChunks(List.of());
        when(retrievalService.retrieve(any()))
            .thenThrow(new IllegalStateException("qdrant unavailable"))
            .thenReturn(new RagRetrievalResult(response, List.of()));
        RagEvaluationCase first = new RagEvaluationCase("first", "first", Map.of());
        RagEvaluationCase second = new RagEvaluationCase("second", "second", Map.of());

        RagEvaluationResult result = new RagEvaluationService(retrievalService)
            .runDataset("resilient", List.of(first, second), RagEvaluationMode.VECTOR_ONLY);

        assertEquals(2, result.getTotalCases());
        assertEquals("RETRIEVAL_FAILED", result.getCases().get(0).getTechnicalErrorCode());
        verify(retrievalService, times(2)).retrieve(any());
    }

    @Test
    void frozenAblationSeparatesRankingAndAbstentionIntoJsonReport() throws Exception {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = response();
        response.getLatency().setTotalMs(12L);
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active")).thenReturn("postgrad_rag_20260713");

        RagEvaluationRunReport report = new RagEvaluationService(
            retrievalService, new RagEvaluationDatasetLoader(), collectionManager, tempDir)
            .runFrozenV1Ablation();

        assertEquals(18, report.getResults().get(RagEvaluationMode.VECTOR_ONLY).getTotalCases());
        assertEquals(18, report.getResults().get(RagEvaluationMode.VECTOR_RERANK).getTotalCases());
        assertEquals(0, report.getResults().get(RagEvaluationMode.VECTOR_ONLY).getNoAnswerCases());
        assertEquals(4, report.getAbstentionMetrics().get(RagEvaluationMode.VECTOR_ONLY).getTotalCases());
        assertEquals(4, report.getAbstentionMetrics().get(RagEvaluationMode.VECTOR_RERANK).getTotalCases());
        assertEquals(12L, report.getLatencyMetrics().get(RagEvaluationMode.VECTOR_ONLY).getP50Ms());
        assertEquals("postgrad_rag_20260713", report.getRunMetadata().get("qdrantCollection"));
        assertTrue(Files.isRegularFile(Path.of(report.getReportPath())));

        JSONObject json = JSON.parseObject(Files.readString(Path.of(report.getReportPath())));
        assertEquals(18, json.getJSONObject("results").getJSONObject("VECTOR_ONLY").getIntValue("totalCases"));
        assertEquals(4, json.getJSONObject("abstentionMetrics").getJSONObject("VECTOR_RERANK").getIntValue("totalCases"));

        verify(retrievalService, times(22)).retrieve(argThat(request -> request.isSkipRerank()));
        verify(retrievalService, times(22)).retrieve(argThat(request -> !request.isSkipRerank()));
    }

    @Test
    void shouldPassCaseWhenRetrievedChunkMatchesExpectedMetadata() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolName("南京大学");
        chunk.setSchoolId("1");
        chunk.setProgramName("软件工程");
        chunk.setProgramId("10");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("admission_score");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "case-1",
            "南京大学软件工程 2025 复试线",
            Map.of("school_id","1","program_id","10","year","2025","doc_type","program_year")
        );
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("10","program_year","admission_score","2025")));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(1, result.getTotalCases());
        assertEquals(1, result.getPassedCases());
        assertEquals("PASSED", result.getCases().get(0).getFailureReason());
        verify(retrievalService).retrieve(argThat(req ->
            "1".equals(req.getSchoolId())
                && "10".equals(req.getProgramId())
                && "2025".equals(req.getYear())
        ));
    }

    @Test
    void shouldFailWithWrongScopeWhenSelectedChunkBelongsToAnotherProgram() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolId("56");
        chunk.setProgramId("9999");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("admission_score");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "wrong-scope",
            "杭电网安方向 2025 复试线",
            Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year")
        );
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("2854","program_year","admission_score","2025")));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(0, result.getPassedCases());
        assertEquals("WRONG_SCOPE", result.getCases().get(0).getFailureReason());
    }

    @Test
    void shouldFailWithMissingSectionWhenCorrectProgramHasDifferentSection() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolId("56");
        chunk.setProgramId("2854");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("basic_info");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "missing-section",
            "杭电网安方向 2025 复试线",
            Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year")
        );
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("2854","program_year","admission_score","2025")));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals("MISSING_SECTION", result.getCases().get(0).getFailureReason());
    }

    @Test
    void shouldFailWithSourceDataMissingWhenCaseMarksExpectedSectionUnavailable() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setChunks(List.of());
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "source-missing",
            "杭电网安方向 2025 录取结果",
            Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year")
        );
        testCase.setSourceDataAvailable(false);
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("2854","program_year","admission_result","2025")));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals("SOURCE_DATA_MISSING", result.getCases().get(0).getFailureReason());
        assertFalse(result.getCases().get(0).isPassed());
    }

    @Test
    void defaultCasesShouldUseConcreteKnownProgramMetadata() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        // 3 个 smoke case 各查不同 section，mock 返回 3 个 chunk 各匹配一个
        RagChunkResult c1 = chunk("56","2854","2025","program_year","admission_score");
        RagChunkResult c2 = chunk("56","2854","2025","program_year","admission_plan");
        RagChunkResult c3 = chunk("56","2854","2025","program_year","admission_result");
        response.setChunks(List.of(c1, c2, c3));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationResult result = new RagEvaluationService(retrievalService)
            .run(RagEvaluationDataset.smokeTests());

        assertTrue(result.getCases().stream().anyMatch(c -> c.getQuery().contains("杭电")), "smoke should contain 杭电");
        assertEquals(3, result.getTotalCases());
        assertEquals(3, result.getPassedCases(), "smoke tests should all pass with matching chunk");
        verify(retrievalService, atLeastOnce()).retrieve(argThat(req ->
            "56".equals(req.getSchoolId())
                && "2854".equals(req.getProgramId())
                && "2025".equals(req.getYear())
        ));
    }

    @Test
    void vectorOnlyUsesRecallTwentyAndFinalTopFive() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setChunks(List.of());
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "vector-only",
            "南京大学软件工程 2025 复试线",
            Map.of()
        );

        new RagEvaluationService(retrievalService).run(List.of(testCase));

        verify(retrievalService).retrieve(argThat(request ->
            request.getMaxResults() == 20
                && request.getRerankTopN() == 5
                && request.isSkipRerank()
        ));
    }

    @Test
    void noAnswerCorrectAbstentionDoesNotReducePositiveMetrics() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse relevantResponse = response(chunk("1", "10", "2025", "program_year", "admission_score"));
        RagSearchTestResponse abstentionResponse = response();
        when(retrievalService.retrieve(any())).thenReturn(
            new RagRetrievalResult(relevantResponse, List.of()),
            new RagRetrievalResult(abstentionResponse, List.of()));

        RagEvaluationCase relevantCase = relevantCase("positive");
        RagEvaluationCase noAnswerCase = new RagEvaluationCase("safety", "不存在的学校", Map.of());
        noAnswerCase.setNoAnswer(true);

        RagEvaluationResult result = new RagEvaluationService(retrievalService)
            .run(List.of(relevantCase, noAnswerCase));

        assertEquals(1, result.getPositiveCases());
        assertEquals(1, result.getNoAnswerCases());
        assertEquals(1.0, result.getMrr());
        assertEquals(1.0, result.getNoAnswerAccuracy());
        assertEquals(0.0, result.getFalsePositiveRate());
    }

    @Test
    void noAnswerSelectedEvidenceCountsAsFalsePositive() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
            response(chunk("1", "10", "2025", "program_year", "admission_score")), List.of()));

        RagEvaluationCase noAnswerCase = new RagEvaluationCase("safety", "不存在的学校", Map.of());
        noAnswerCase.setNoAnswer(true);

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(noAnswerCase));

        assertEquals(0, result.getPositiveCases());
        assertEquals(0.0, result.getNoAnswerAccuracy());
        assertEquals(1.0, result.getFalsePositiveRate());
        assertFalse(result.getCases().get(0).isPassed());
    }

    @Test
    void ndcgAtFiveUsesPerPositionRelevanceAndNeverExceedsOne() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagChunkResult first = chunk("1", "10", "2025", "program_year", "admission_score");
        RagChunkResult second = chunk("1", "10", "2025", "program_year", "admission_plan");
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response(first, second), List.of()));

        RagEvaluationCase testCase = relevantCase("two-relevant");
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("10", "program_year", "admission_score", "2025"),
            RagEvaluationCase.RelevantCondition.of("10", "program_year", "admission_plan", "2025")));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(1.0, result.getNdcg5());
        assertTrue(result.getNdcg5() >= 0.0 && result.getNdcg5() <= 1.0);
    }

    @Test
    void filterAccuracyIncludesOnlyPositiveCasesWithReturnedChunks() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse matchingPositive = response(chunk("1", "10", "2025", "program_year", "admission_score"));
        RagSearchTestResponse emptyPositive = response();
        RagSearchTestResponse noAnswerEvidence = response(chunk("999", "10", "2025", "program_year", "admission_score"));
        when(retrievalService.retrieve(any())).thenReturn(
            new RagRetrievalResult(matchingPositive, List.of()),
            new RagRetrievalResult(emptyPositive, List.of()),
            new RagRetrievalResult(noAnswerEvidence, List.of()));

        RagEvaluationCase noAnswerCase = new RagEvaluationCase(
            "safety", "不存在的学校", Map.of("school_id", "99999"));
        noAnswerCase.setNoAnswer(true);

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(
            relevantCase("matching"), relevantCase("empty"), noAnswerCase));

        assertEquals(1.0, result.getFilterAccuracy());
    }

    private static RagEvaluationCase relevantCase(String id) {
        RagEvaluationCase testCase = new RagEvaluationCase(
            id, "南京大学软件工程 2025 复试线",
            Map.of("school_id", "1", "program_id", "10", "year", "2025", "doc_type", "program_year"));
        testCase.setRelevantConditions(List.of(
            RagEvaluationCase.RelevantCondition.of("10", "program_year", "admission_score", "2025")));
        return testCase;
    }

    private static RagSearchTestResponse response(RagChunkResult... chunks) {
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setChunks(List.of(chunks));
        return response;
    }

    private static RagChunkResult chunk(String sid, String pid, String yr, String dt, String sec) {
        RagChunkResult c = new RagChunkResult();
        c.setSchoolId(sid);
        c.setProgramId(pid);
        c.setYear(yr);
        c.setDocType(dt);
        c.setSection(sec);
        c.setSelectedForPrompt(true);
        return c;
    }
}
