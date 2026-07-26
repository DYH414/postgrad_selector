package com.ruoyi.postgrad.rag.eval.workbench;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.AgentEvalRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentEvalRunServiceTest {

    private RagEvaluationWorkbenchMapper mapper;
    private AgentEvalRunService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RagEvaluationWorkbenchMapper.class);
        service = new AgentEvalRunService(mapper);
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
}
