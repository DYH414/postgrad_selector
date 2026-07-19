package com.ruoyi.postgrad.rag.eval.workbench;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.eval.RagEvaluationMode;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseResultEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalDatasetService;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalRunService;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import java.util.List;
import java.util.Map;

class RagEvalRunServiceTest {

    @Test
    void unfinishedRunCannotBeExported() {
        Fixture f = fixture();
        when(f.mapper.selectRunById("run-1")).thenReturn(run("run-1", "RUNNING", "sum"));
        assertThrows(ServiceException.class, () -> f.service.export("run-1", "json"));
    }

    @Test
    void unfinishedRunsCannotBeCompared() {
        Fixture f = fixture();
        when(f.mapper.selectRunById("base")).thenReturn(run("base", "SUCCEEDED", "same"));
        when(f.mapper.selectRunById("candidate")).thenReturn(run("candidate", "FAILED", "same"));
        assertThrows(ServiceException.class, () -> f.service.compare("base", "candidate"));
    }

    @Test
    void saturatedExecutorReturnsStableCapacityErrorAndClosesRun() {
        Fixture f = fixture();
        RagEvalDataset dataset = new RagEvalDataset(); dataset.setId(1L); dataset.setChecksumSha256("sum");
        RagEvalCaseEntity evalCase = new RagEvalCaseEntity(); evalCase.setId(2L);
        when(f.datasets.get(1L)).thenReturn(dataset);
        when(f.datasets.listCases(1L)).thenReturn(List.of(evalCase));
        doThrow(new TaskRejectedException("full")).when(f.executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        assertThrows(ServiceException.class,
            () -> f.service.start(1L, List.of(RagEvaluationMode.VECTOR_ONLY), Map.of(), "admin"));
        verify(f.mapper).completeRun(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("FAILED"), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("EVAL_CAPACITY_EXHAUSTED"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void completedJsonReportIsReproducibleFromPersistence() {
        Fixture f = fixture();
        RagEvalRun run = run("done", "SUCCEEDED", "same");
        run.setAggregateMetricsJson("{\"mrr\":0.5}");
        when(f.mapper.selectRunById("done")).thenReturn(run);
        when(f.mapper.selectCaseResultsByRunId("done")).thenReturn(List.of(new RagEvalCaseResultEntity()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            f.service.export("done", "json"), f.service.export("done", "json"));
    }

    @Test
    void incompatibleComparisonSuppressesMetricDeltas() {
        Fixture f = fixture();
        when(f.mapper.selectRunById("base")).thenReturn(run("base", "SUCCEEDED", "a"));
        when(f.mapper.selectRunById("candidate")).thenReturn(run("candidate", "SUCCEEDED", "b"));
        when(f.mapper.selectCaseResultsByRunId(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        Map<String, Object> comparison = f.service.compare("base", "candidate");

        org.junit.jupiter.api.Assertions.assertEquals(false, comparison.get("compatible"));
        org.junit.jupiter.api.Assertions.assertFalse(comparison.containsKey("metricDeltas"));
    }

    @Test
    void reviewNoteIsTruncatedBeforePersistence() {
        Fixture f = fixture();
        RagEvalCaseResultEntity result = new RagEvalCaseResultEntity(); result.setId(3L);
        when(f.mapper.selectCaseResultById(3L)).thenReturn(result);

        f.service.review(3L, "NO_RECALL", "x".repeat(1200), "admin");

        verify(f.mapper).reviewCaseResult(org.mockito.ArgumentMatchers.eq(3L),
            org.mockito.ArgumentMatchers.eq("NO_RECALL"),
            org.mockito.ArgumentMatchers.argThat(note -> note.length() == 1000),
            org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    void duplicateActiveSubmissionReturnsExistingRun() {
        Fixture f = fixture();
        RagEvalDataset dataset = new RagEvalDataset(); dataset.setId(1L); dataset.setChecksumSha256("sum");
        RagEvalRun active = run("existing", "RUNNING", "sum");
        when(f.datasets.get(1L)).thenReturn(dataset);
        when(f.mapper.selectActiveEquivalentRun(org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("sum"), org.mockito.ArgumentMatchers.anyString())).thenReturn(active);

        org.junit.jupiter.api.Assertions.assertEquals("existing",
            f.service.start(1L, List.of(RagEvaluationMode.VECTOR_ONLY), Map.of(), "admin"));
        verify(f.mapper, never()).insertRun(org.mockito.ArgumentMatchers.any());
    }

    private Fixture fixture() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDatasetService datasets = mock(RagEvalDatasetService.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        RagEvalRunService service = new RagEvalRunService(mapper, datasets,
            mock(RagEvaluationService.class), mock(RagCollectionManager.class),
            executor, "BAAI/bge-m3", "BAAI/bge-reranker-v2-m3");
        return new Fixture(mapper, datasets, executor, service);
    }

    private RagEvalRun run(String id, String status, String checksum) {
        RagEvalRun run = new RagEvalRun(); run.setRunId(id); run.setStatus(status); run.setDatasetChecksum(checksum);
        return run;
    }

    private record Fixture(RagEvaluationWorkbenchMapper mapper, RagEvalDatasetService datasets,
                           ThreadPoolTaskExecutor executor, RagEvalRunService service) {}
}
