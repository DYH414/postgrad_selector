package com.ruoyi.postgrad.rag.eval.workbench;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationDispatchRequest;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationWorkerClient;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.AgentEvalRunService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentEvalRunServiceTest {

    private RagEvaluationWorkbenchMapper mapper;
    private EvaluationWorkerClient workerClient;
    private AgentEvalRunService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RagEvaluationWorkbenchMapper.class);
        workerClient = mock(EvaluationWorkerClient.class);
        service = new AgentEvalRunService(mapper, workerClient);
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private static RagEvalRun runningRun(String runId) {
        RagEvalRun run = new RagEvalRun();
        run.setRunId(runId);
        run.setStatus("RUNNING");
        run.setTotalCases(3);
        run.setCompletedCases(0);
        return run;
    }

    private static AgentEvalCaseResult result(String checksum) {
        AgentEvalCaseResult r = new AgentEvalCaseResult();
        r.setPassed(true);
        r.setAgentSuccess(true);
        r.setTraceComplete(true);
        r.setRuleResultJson("{}");
        r.setFailureReasonsJson("[]");
        r.setResultChecksum(checksum);
        return r;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void saveAgentCaseResult_firstTime_insertAndRecount() {
        when(mapper.selectRunById("run-1")).thenReturn(runningRun("run-1"));
        when(mapper.selectCaseById(42L)).thenReturn(caseInDataset(42L, null));
        when(mapper.selectAgentEvalCaseResultByKey("run-1", 42L)).thenReturn(null);
        when(mapper.insertAgentEvalCaseResult(any())).thenReturn(1);
        when(mapper.updateAgentEvalProgress("run-1")).thenReturn(1);
        when(mapper.completeAgentEvalRunIfDone("run-1")).thenReturn(0); // not yet done

        String outcome = service.saveAgentCaseResult("run-1", 42L, result("abc123"));

        assertEquals("SAVED", outcome);
        verify(mapper).insertAgentEvalCaseResult(argThat(e -> "run-1".equals(e.getRunId()) && Long.valueOf(42L).equals(e.getCaseId())));
        verify(mapper).updateAgentEvalProgress("run-1");
        verify(mapper).completeAgentEvalRunIfDone("run-1");
    }

    @Test
    void saveAgentCaseResult_sameChecksum_replayed() {
        when(mapper.selectRunById("run-1")).thenReturn(runningRun("run-1"));
        when(mapper.selectCaseById(42L)).thenReturn(caseInDataset(42L, null));
        AgentEvalCaseResult existing = result("abc123");
        when(mapper.selectAgentEvalCaseResultByKey("run-1", 42L)).thenReturn(existing);

        String outcome = service.saveAgentCaseResult("run-1", 42L, result("abc123"));

        assertEquals("REPLAYED", outcome);
        verify(mapper, never()).insertAgentEvalCaseResult(any());
        verify(mapper, never()).updateAgentEvalProgress(any());
    }

    @Test
    void saveAgentCaseResult_differentChecksum_throws409Conflict() {
        when(mapper.selectRunById("run-1")).thenReturn(runningRun("run-1"));
        when(mapper.selectCaseById(42L)).thenReturn(caseInDataset(42L, null));
        when(mapper.selectAgentEvalCaseResultByKey("run-1", 42L)).thenReturn(result("abc123"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveAgentCaseResult("run-1", 42L, result("differentHash")));
        assertEquals("EVAL_RESULT_CONFLICT", ex.getMessage());
        verify(mapper, never()).insertAgentEvalCaseResult(any());
    }

    @Test
    void saveAgentCaseResult_cancelledRun_rejects() {
        RagEvalRun cancelled = runningRun("run-1");
        cancelled.setStatus("CANCELLED");
        when(mapper.selectRunById("run-1")).thenReturn(cancelled);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveAgentCaseResult("run-1", 42L, result("abc123")));
        assertEquals("EVAL_RUN_CANCELLED", ex.getMessage());
    }

    @Test
    void saveAgentCaseResult_succeededRun_rejectsAsTerminal() {
        RagEvalRun succeeded = runningRun("run-1");
        succeeded.setStatus("SUCCEEDED");
        when(mapper.selectRunById("run-1")).thenReturn(succeeded);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveAgentCaseResult("run-1", 42L, result("abc123")));
        assertEquals("EVAL_RUN_TERMINAL", ex.getMessage());
    }

    @Test
    void saveAgentCaseResult_notFound_throws() {
        when(mapper.selectRunById("no-such")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveAgentCaseResult("no-such", 1L, result("x")));
        assertEquals("EVAL_RUN_NOT_FOUND", ex.getMessage());
    }

    @Test
    void saveAgentCaseResult_rejectsCaseFromAnotherDataset() {
        RagEvalRun run = runningRun("run-1");
        run.setDatasetId(7L);
        RagEvalCaseEntity foreignCase = new RagEvalCaseEntity();
        foreignCase.setId(42L);
        foreignCase.setDatasetId(8L);
        when(mapper.selectRunById("run-1")).thenReturn(run);
        when(mapper.selectCaseById(42L)).thenReturn(foreignCase);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveAgentCaseResult("run-1", 42L, result("abc123")));

        assertEquals("EVAL_CASE_NOT_IN_RUN", ex.getMessage());
        verify(mapper, never()).insertAgentEvalCaseResult(any());
    }

    @Test
    void createRun_rejectsNonAgentDatasetBeforeCreatingRun() {
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setId(7L);
        dataset.setEvaluationType("RAG_RETRIEVAL");
        dataset.setLifecycleState("FROZEN");
        when(mapper.selectDatasetById(7L)).thenReturn(dataset);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createRun(7L, 1L, "admin"));

        assertEquals("AGENT_EVAL_DATASET_TYPE_REQUIRED", ex.getMessage());
        verify(mapper, never()).insertRun(any());
        verifyNoInteractions(workerClient);
    }

    @Test
    void createRun_rejectsAgentDatasetThatIsNotFrozen() {
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setId(8L);
        dataset.setEvaluationType("AGENT_E2E");
        dataset.setLifecycleState("DRAFT");
        when(mapper.selectDatasetById(8L)).thenReturn(dataset);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createRun(8L, 1L, "admin"));

        assertEquals("AGENT_EVAL_DATASET_NOT_FROZEN", ex.getMessage());
        verify(mapper, never()).selectAgentConfigVersionById(anyLong());
        verify(mapper, never()).insertRun(any());
        verifyNoInteractions(workerClient);
    }

    @Test
    void createRun_rejectsEmptyDatasetBeforeCreatingRun() {
        RagEvalDataset dataset = dispatchableDataset(8L);
        when(mapper.selectDatasetById(8L)).thenReturn(dataset);
        when(mapper.selectAgentConfigVersionById(1L)).thenReturn(activeVersion());
        when(mapper.selectCasesByDatasetId(8L)).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.createRun(8L, 1L, "admin"));

        assertEquals("AGENT_EVAL_DATASET_EMPTY", ex.getMessage());
        verify(mapper, never()).insertRun(any());
        verifyNoInteractions(workerClient);
    }

    @Test
    void createRun_marksRunningBeforeDispatch() {
        RagEvalDataset dataset = dispatchableDataset(8L);
        when(mapper.selectDatasetById(8L)).thenReturn(dataset);
        when(mapper.selectAgentConfigVersionById(1L)).thenReturn(activeVersion());
        when(mapper.selectCasesByDatasetId(8L)).thenReturn(List.of(agentCase(42L, 8L)));
        when(mapper.insertRun(any())).thenReturn(1);
        when(mapper.markRunStarted(anyString())).thenReturn(1);

        String runId = service.createRun(8L, 1L, "admin");

        assertNotNull(runId);
        var order = inOrder(mapper, workerClient);
        order.verify(mapper).insertRun(any());
        order.verify(mapper).markRunStarted(runId);
        order.verify(workerClient).dispatchRun(any(EvaluationDispatchRequest.class));
    }

    @Test
    void createRun_marksFailedWhenWorkerDispatchFails() {
        RagEvalDataset dataset = dispatchableDataset(8L);
        when(mapper.selectDatasetById(8L)).thenReturn(dataset);
        when(mapper.selectAgentConfigVersionById(1L)).thenReturn(activeVersion());
        when(mapper.selectCasesByDatasetId(8L)).thenReturn(List.of(agentCase(42L, 8L)));
        when(mapper.insertRun(any())).thenReturn(1);
        when(mapper.markRunStarted(anyString())).thenReturn(1);
        doThrow(new ServiceException("EVAL_WORKER_DISPATCH_FAILED"))
            .when(workerClient).dispatchRun(any(EvaluationDispatchRequest.class));

        assertThrows(ServiceException.class, () -> service.createRun(8L, 1L, "admin"));

        verify(mapper).completeRun(anyString(), eq("FAILED"), isNull(),
            eq("EVAL_WORKER_DISPATCH_FAILED"), anyString());
    }

    private static RagEvalDataset dispatchableDataset(Long id) {
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setId(id);
        dataset.setEvaluationType("AGENT_E2E");
        dataset.setLifecycleState("FROZEN");
        dataset.setChecksumSha256("dataset-sha");
        return dataset;
    }

    private static AgentConfigVersion activeVersion() {
        AgentConfigVersion version = new AgentConfigVersion();
        version.setId(1L);
        version.setVersionName("v1");
        version.setBusinessPromptText("prompt");
        version.setRoutingPromptText("route");
        version.setModel("deepseek-v3");
        version.setTemperature(BigDecimal.valueOf(0.7));
        version.setToolCatalogChecksum("tools-sha");
        version.setStatus("ACTIVE");
        return version;
    }

    private static RagEvalCaseEntity agentCase(Long id, Long datasetId) {
        RagEvalCaseEntity evalCase = new RagEvalCaseEntity();
        evalCase.setId(id);
        evalCase.setDatasetId(datasetId);
        evalCase.setCaseKey("agent-1");
        evalCase.setQueryText("测试问题");
        evalCase.setAgentSpecJson("{\"requiredTools\":[]}");
        return evalCase;
    }

    private static RagEvalCaseEntity caseInDataset(Long id, Long datasetId) {
        RagEvalCaseEntity evalCase = new RagEvalCaseEntity();
        evalCase.setId(id);
        evalCase.setDatasetId(datasetId);
        return evalCase;
    }
}
