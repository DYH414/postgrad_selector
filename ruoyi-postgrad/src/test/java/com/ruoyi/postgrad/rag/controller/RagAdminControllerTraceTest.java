package com.ruoyi.postgrad.rag.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.eval.RagEvaluationResult;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.knowledge.RagMarkdownDocumentService;
import com.ruoyi.postgrad.rag.service.RagSearchTestService;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagAdminControllerTraceTest {

    @Test
    void searchTestTraceIncludesFiltersLatencyAndSelectedChunks() {
        RagSearchTestService searchService = mock(RagSearchTestService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setRetrievedCount(2);
        response.setSelectedCount(1);
        RagSearchTestResponse.RagSearchLatency latency = new RagSearchTestResponse.RagSearchLatency();
        latency.setEmbeddingMs(10L);
        latency.setRetrievalMs(20L);
        latency.setRerankMs(30L);
        latency.setTotalMs(60L);
        response.setLatency(latency);
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSelectedForPrompt(true);
        chunk.setSourceName("N诺-杭州电子科技大学");
        chunk.setContentPreview("复试线: 260");
        response.setChunks(List.of(chunk));
        when(searchService.searchTest(any())).thenReturn(response);
        RagTraceRecorder recorder = new RagTraceRecorder();
        RagAdminController controller = controller(searchService, recorder, mock(RagEvaluationService.class));

        RagSearchTestRequest request = new RagSearchTestRequest("杭州电子科技大学 复试线", 5, 0.5, 3);
        request.setSchoolId("56");
        request.setProgramId("2854");
        request.setYear("2025");
        controller.searchTest(request);

        RagTrace trace = recorder.listRecent(1).get(0);
        assertEquals("search-test", trace.getOperationKey());
        assertEquals("56", trace.getMetadataFilters().get("school_id"));
        assertEquals(10L, trace.getEmbeddingLatencyMs());
        assertEquals(20L, trace.getRetrievalLatencyMs());
        assertEquals(30L, trace.getRerankLatencyMs());
        assertFalse(trace.getSelectedChunks().isEmpty());
    }

    @Test
    void evaluateRecordsRagEvaluateTraceWithFailureSummary() {
        RagEvaluationService evaluationService = mock(RagEvaluationService.class);
        RagEvaluationResult result = new RagEvaluationResult();
        result.setTotalCases(3);
        result.setPassedCases(2);
        result.setFailedCases(1);
        RagEvaluationResult.RagEvaluationCaseResult caseResult = new RagEvaluationResult.RagEvaluationCaseResult();
        caseResult.setId("admission-result-program-year");
        caseResult.setFailureReason("MISSING_SECTION");
        result.setCases(List.of(caseResult));
        when(evaluationService.runDefault()).thenReturn(result);
        RagTraceRecorder recorder = new RagTraceRecorder();
        RagAdminController controller = controller(mock(RagSearchTestService.class), recorder, evaluationService);

        controller.evaluate();

        RagTrace trace = recorder.listRecent(1).get(0);
        assertEquals("rag-evaluate", trace.getOperationKey());
        assertEquals(RagTrace.Outcome.FAILURE, trace.getOutcome());
        assertEquals("3", trace.getMetadataFilters().get("total_cases"));
        assertEquals("2", trace.getMetadataFilters().get("passed_cases"));
        assertEquals("1", trace.getMetadataFilters().get("failed_cases"));
        assertEquals("admission-result-program-year:MISSING_SECTION", trace.getErrorSummary());
    }

    private RagAdminController controller(RagSearchTestService searchService,
                                          RagTraceRecorder recorder,
                                          RagEvaluationService evaluationService) {
        return new RagAdminController(
            mock(RagDocumentService.class),
            searchService,
            recorder,
            evaluationService,
            mock(RagMarkdownDocumentService.class)
        );
    }
}
