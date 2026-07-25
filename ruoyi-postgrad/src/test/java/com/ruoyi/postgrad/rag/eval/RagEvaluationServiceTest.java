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
    void pairedComparisonCountsImprovedTiedAndRegressedCases() {
        RagEvaluationResult vectorOnly = resultWithRanks(
            Map.of("improved", 4, "tied", 2, "regressed", 1, "new-hit", -1));
        RagEvaluationResult reranked = resultWithRanks(
            Map.of("improved", 1, "tied", 2, "regressed", 3, "new-hit", 5));

        RagEvaluationRunReport.PairedComparison comparison =
            RagEvaluationService.pairedComparison(vectorOnly, reranked);

        assertEquals(2, comparison.getImprovedCases());
        assertEquals(1, comparison.getTiedCases());
        assertEquals(1, comparison.getRegressedCases());
        assertEquals(4, comparison.getComparedCases());
    }

    @Test
    void frozenV3AblationWritesCurrentBenchmarkAndSafetyMetricsIntoJsonReport() throws Exception {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = response();
        response.getLatency().setTotalMs(12L);
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active")).thenReturn("postgrad_rag_20260713");

        RagEvaluationRunReport report = new RagEvaluationService(
            retrievalService, new RagEvaluationDatasetLoader(), collectionManager, tempDir)
            .runFrozenV3Ablation();

        assertEquals(36, report.getResults().get(RagEvaluationMode.VECTOR_ONLY).getTotalCases());
        assertEquals(36, report.getResults().get(RagEvaluationMode.VECTOR_RERANK).getTotalCases());
        assertEquals(0, report.getResults().get(RagEvaluationMode.VECTOR_ONLY).getNoAnswerCases());
        assertEquals(12, report.getSafetyResults().get(RagEvaluationMode.VECTOR_ONLY).getTotalCases());
        assertEquals(12, report.getSafetyResults().get(RagEvaluationMode.VECTOR_RERANK).getTotalCases());
        assertEquals(12, report.getAbstentionMetrics().get(RagEvaluationMode.VECTOR_ONLY).getTotalCases());
        assertEquals(12, report.getAbstentionMetrics().get(RagEvaluationMode.VECTOR_RERANK).getTotalCases());
        assertEquals(12L, report.getLatencyMetrics().get(RagEvaluationMode.VECTOR_ONLY).getP50Ms());
        assertEquals("postgrad_rag_20260713", report.getRunMetadata().get("qdrantCollection"));
        assertTrue(Files.isRegularFile(Path.of(report.getReportPath())));

        JSONObject json = JSON.parseObject(Files.readString(Path.of(report.getReportPath())));
        assertEquals(36, json.getJSONObject("results").getJSONObject("VECTOR_ONLY").getIntValue("totalCases"));
        assertEquals(12, json.getJSONObject("abstentionMetrics").getJSONObject("VECTOR_RERANK").getIntValue("totalCases"));

        verify(retrievalService, times(48)).retrieve(argThat(request -> request.isSkipRerank()));
        verify(retrievalService, times(48)).retrieve(argThat(request -> !request.isSkipRerank()));
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
    void retrievalCasesShouldMatchCurrentUnstructuredCorpusBoundary() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagChunkResult general = new RagChunkResult();
        general.setSourceName("02-考研国家线与复试.md");
        general.setSelectedForPrompt(true);
        RagSearchTestResponse generalResponse = response(general);

        RagChunkResult school = new RagChunkResult();
        school.setSourceName("杭州电子科技大学-招生简章.md");
        school.setSchoolName("杭州电子科技大学");
        school.setDocType("admission_guide");
        school.setSelectedForPrompt(true);
        RagSearchTestResponse schoolResponse = response(school);

        RagSearchTestResponse empty = new RagSearchTestResponse();
        empty.setChunks(List.of());
        when(retrievalService.retrieve(any()))
            .thenReturn(new RagRetrievalResult(generalResponse, List.of()))
            .thenReturn(new RagRetrievalResult(schoolResponse, List.of()))
            .thenReturn(new RagRetrievalResult(empty, List.of()));

        RagEvaluationCase generalCase = new RagEvaluationCase(
            "smoke-general", "没过国家线还能调剂吗", Map.of());
        generalCase.setRelevantSources(List.of("02-考研国家线与复试"));
        RagEvaluationCase schoolCase = new RagEvaluationCase(
            "smoke-school", "杭州电子科技大学研究生学费",
            Map.of("school_name", "杭州电子科技大学", "doc_type", "admission_guide"));
        schoolCase.setRelevantSources(List.of("杭州电子科技大学-招生简章"));
        RagEvaluationCase noAnswerCase = new RagEvaluationCase(
            "smoke-no-answer", "星河大学招生简章",
            Map.of("school_name", "星河大学", "doc_type", "admission_guide"));
        noAnswerCase.setNoAnswer(true);

        RagEvaluationResult result = new RagEvaluationService(retrievalService)
            .run(List.of(generalCase, schoolCase, noAnswerCase));

        assertTrue(result.getCases().stream().anyMatch(c -> c.getQuery().contains("国家线")));
        assertEquals(3, result.getTotalCases());
        assertEquals(3, result.getPassedCases(), "smoke tests should cover general, school and no-answer");
        assertFalse(result.isRouteEvaluated(), "retrieval-only evaluation must not claim route accuracy");
        verify(retrievalService, atLeastOnce()).retrieve(argThat(req ->
            "杭州电子科技大学".equals(req.getSchoolName())
                && "admission_guide".equals(req.getDocType())
                && req.getProgramId() == null
        ));
    }

    private static RagEvaluationResult resultWithRanks(Map<String, Integer> ranks) {
        RagEvaluationResult result = new RagEvaluationResult();
        result.setCases(ranks.entrySet().stream().map(entry -> {
            RagEvaluationResult.RagEvaluationCaseResult caseResult =
                new RagEvaluationResult.RagEvaluationCaseResult();
            caseResult.setId(entry.getKey());
            caseResult.setFirstRelevantRank(entry.getValue());
            return caseResult;
        }).toList());
        return result;
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
    void repeatedChunksFromOneRelevantSourceCountAsOneRelevanceUnit() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagChunkResult first = sourceChunk("03-调剂政策.md", "基本条件");
        RagChunkResult second = sourceChunk("03-调剂政策.md", "志愿规则");
        RagChunkResult third = sourceChunk("03-调剂政策.md", "系统操作");
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
            response(first, second, third), List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase("general-duplicate",
            "考研调剂需要满足什么条件？", Map.of());
        testCase.setRelevantSources(List.of("03-调剂政策"));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(1.0, result.getMrr());
        assertEquals(1.0, result.getRecallAt5());
        assertEquals(1.0, result.getNdcg5());
        assertEquals(1, result.getCases().get(0).getRelevantInTopK());
    }

    @Test
    void contentHashQrelMatchesOnlyTheReviewedEvidenceChunk() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagChunkResult wrongChunkFromSameSource = sourceChunk("杭州电子科技大学-招生简章.md", "招生计划");
        wrongChunkFromSameSource.setContentHash("wrong-hash");
        RagChunkResult reviewedEvidence = sourceChunk("杭州电子科技大学-招生简章.md", "学费与学制");
        reviewedEvidence.setContentHash("reviewed-hash");
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
            response(wrongChunkFromSameSource, reviewedEvidence), List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "hash-qrel", "杭电研究生学费和学制", Map.of("school_name", "杭州电子科技大学"));
        RagEvaluationCase.RelevantCondition condition = new RagEvaluationCase.RelevantCondition();
        condition.setContentHash("reviewed-hash");
        testCase.setRelevantConditions(List.of(condition));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(0.5, result.getMrr());
        assertEquals(1, result.getCases().get(0).getRelevantInTopK());
    }

    @Test
    void relevantCandidateOutsidePromptTopKDoesNotPass() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagChunkResult selectedIrrelevant = sourceChunk("其他文档.md", "无关内容");
        RagChunkResult unselectedRelevant = sourceChunk("03-调剂政策.md", "调剂条件");
        unselectedRelevant.setSelectedForPrompt(false);
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(
            response(selectedIrrelevant, unselectedRelevant), List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "prompt-boundary", "调剂需要什么条件", Map.of());
        testCase.setRelevantSources(List.of("03-调剂政策"));

        RagEvaluationResult result = new RagEvaluationService(retrievalService)
            .runDataset("prompt-boundary", List.of(testCase), RagEvaluationMode.VECTOR_ONLY, 20, 1);

        assertEquals(0, result.getPassedCases());
        assertEquals(0.0, result.getMrr());
        assertEquals(0.0, result.getHitAt1());
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
        assertEquals(1, result.getFilterEvaluatedCases());
        assertEquals(1, result.getFilterEmptyCases());
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

    private static RagChunkResult sourceChunk(String sourceName, String title) {
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSourceName(sourceName);
        chunk.setChunkTitle(title);
        chunk.setSelectedForPrompt(true);
        return chunk;
    }
}
