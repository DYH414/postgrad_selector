package com.ruoyi.postgrad.rag.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.build.RagIndexBuildService;
import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.eval.RagEvaluationRunReport;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RagAdminControllerTraceTest {

    @Test
    void searchTestTraceIncludesFiltersLatencyAndSelectedChunks() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
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
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));
        RagTraceRecorder recorder = new RagTraceRecorder();
        RagAdminController controller = controller(
            mock(RagIndexBuildService.class),
            mock(RagIndexStatusService.class),
            mock(RagIndexRunMapper.class),
            retrievalService,
            recorder);

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
    void startIndexRunReturnsRunId() {
        RagIndexBuildService buildService = mock(RagIndexBuildService.class);
        when(buildService.startBuild(2025, 2025)).thenReturn("test-run-id");
        RagAdminController controller = controller(
            buildService,
            mock(RagIndexStatusService.class),
            mock(RagIndexRunMapper.class),
            mock(RagRetrievalService.class),
            new RagTraceRecorder());

        AjaxResult result = controller.startIndexRun(2025, 2025);

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("test-run-id", data.get("runId"));
    }

    @Test
    void getIndexRunReturnsRun() {
        RagIndexRunMapper runMapper = mock(RagIndexRunMapper.class);
        RagIndexRun run = new RagIndexRun();
        run.setRunId("test-run-id");
        run.setStatus("SUCCEEDED");
        run.setStartYear(2025);
        run.setEndYear(2025);
        run.setIndexedChunkCount(500);
        when(runMapper.selectByRunId("test-run-id")).thenReturn(run);
        RagAdminController controller = controller(
            mock(RagIndexBuildService.class),
            mock(RagIndexStatusService.class),
            runMapper,
            mock(RagRetrievalService.class),
            new RagTraceRecorder());

        AjaxResult result = controller.getIndexRun("test-run-id");

        assertTrue(result.isSuccess());
        RagIndexRun resultRun = (RagIndexRun) result.get("data");
        assertEquals("test-run-id", resultRun.getRunId());
        assertEquals("SUCCEEDED", resultRun.getStatus());
        assertEquals(500, resultRun.getIndexedChunkCount());
    }

    @Test
    void getIndexRunReturnsErrorWhenNotFound() {
        RagIndexRunMapper runMapper = mock(RagIndexRunMapper.class);
        when(runMapper.selectByRunId("nonexistent")).thenReturn(null);
        RagAdminController controller = controller(
            mock(RagIndexBuildService.class),
            mock(RagIndexStatusService.class),
            runMapper,
            mock(RagRetrievalService.class),
            new RagTraceRecorder());

        AjaxResult result = controller.getIndexRun("nonexistent");

        assertTrue(result.isError());
    }

    @Test
    void statusReturnsMergedStatus() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagStatus status = new RagStatus();
        status.setIndexed(true);
        status.setEnabled(true);
        status.setBackend("qdrant");
        status.setDocumentCount(42);
        status.setReachable(true);
        when(statusService.getStatus()).thenReturn(status);
        RagAdminController controller = controller(
            mock(RagIndexBuildService.class),
            statusService,
            mock(RagIndexRunMapper.class),
            mock(RagRetrievalService.class),
            new RagTraceRecorder());

        AjaxResult result = controller.status();

        assertTrue(result.isSuccess());
        RagStatus resultStatus = (RagStatus) result.get("data");
        assertTrue(resultStatus.isIndexed());
        assertTrue(resultStatus.isEnabled());
        assertEquals(42, resultStatus.getDocumentCount());
        assertEquals("qdrant", resultStatus.getBackend());
    }

    @Test
    void oldReindexEndpointsAreRemoved() throws Exception {
        Method[] methods = RagAdminController.class.getMethods();
        boolean hasOldReindex = false;
        for (Method method : methods) {
            String name = method.getName();
            if (name.equals("reindex") || name.equals("reindexDatabase") || name.equals("generateDocuments")) {
                hasOldReindex = true;
                break;
            }
        }
        assertFalse(hasOldReindex,
            "Old reindex/generate-documents/reindex-database endpoints must be removed from RagAdminController");
    }

    @Test
    void frozenEvaluationReturnsAblationReport() {
        RagEvaluationService evalService = mock(RagEvaluationService.class);
        RagEvaluationRunReport report = new RagEvaluationRunReport();
        when(evalService.runFrozenV1Ablation()).thenReturn(report);
        RagAdminController controller = new RagAdminController(
            mock(RagIndexBuildService.class), mock(RagIndexStatusService.class),
            mock(RagIndexRunMapper.class), mock(RagRetrievalService.class),
            new RagTraceRecorder(), evalService);

        AjaxResult result = controller.runFrozenEvaluation();

        assertTrue(result.isSuccess());
        assertEquals(report, result.get("data"));
    }

    @Test
    void expensiveEvaluationEndpointsRequireExecutePermission() throws Exception {
        for (String methodName : List.of("runEval", "runEvalDev", "runEvalTest", "runFrozenEvaluation", "runEvalFull")) {
            Method method = RagAdminController.class.getMethod(methodName);
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertTrue(annotation != null, methodName + " must declare PreAuthorize");
            assertTrue(annotation.value().contains("postgrad:rag:eval"),
                methodName + " must require postgrad:rag:eval instead of view-only permission");
        }
    }

    @Test
    void searchRequestCapsExpensiveRetrievalParameters() {
        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 10_000, 0.5, 10_000);

        assertEquals(20, request.getMaxResults());
        assertEquals(10, request.getRerankTopN());
    }

    private static RagAdminController controller(RagIndexBuildService buildService,
                                                  RagIndexStatusService statusService,
                                                  RagIndexRunMapper runMapper,
                                                  RagRetrievalService retrievalService,
                                                  RagTraceRecorder recorder) {
        RagEvaluationService evalService = mock(RagEvaluationService.class);
        return new RagAdminController(buildService, statusService, runMapper, retrievalService, recorder, evalService);
    }
}
