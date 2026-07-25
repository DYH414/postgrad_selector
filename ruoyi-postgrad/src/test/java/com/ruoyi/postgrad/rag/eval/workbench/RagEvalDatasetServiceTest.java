package com.ruoyi.postgrad.rag.eval.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalDatasetService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class RagEvalDatasetServiceTest {
    @TempDir Path tempDir;

    @Test
    void frozenDatasetRejectsCaseMutation() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDataset frozen = dataset(1L, "FROZEN");
        when(mapper.selectDatasetById(1L)).thenReturn(frozen);
        RagEvalCaseEntity evalCase = validCase(1L);

        assertThrows(ServiceException.class,
            () -> new RagEvalDatasetService(mapper).saveCase(evalCase, "admin"));
        verify(mapper, never()).insertCase(any());
    }

    @Test
    void positiveCaseRequiresRelevantGroundTruth() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        when(mapper.selectDatasetById(1L)).thenReturn(dataset(1L, "DRAFT"));
        RagEvalCaseEntity evalCase = validCase(1L);
        evalCase.setRelevantConditionsJson("[]");

        assertThrows(ServiceException.class,
            () -> new RagEvalDatasetService(mapper).saveCase(evalCase, "admin"));
    }

    @Test
    void bundledImportIsIdempotent() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDataset existing = dataset(9L, "FROZEN");
        existing.setDatasetKey("frozen-v3");
        existing.setVersion("v3");
        when(mapper.selectDatasetByKeyVersion("frozen-v3", "v3")).thenReturn(existing);

        RagEvalDataset actual = new RagEvalDatasetService(mapper).importBundled("frozen-v3", "admin");

        assertEquals(9L, actual.getId());
        verify(mapper, never()).insertDataset(any());
    }

    @Test
    void obsoleteBundledV1IsRejected() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);

        assertThrows(ServiceException.class,
            () -> new RagEvalDatasetService(mapper).importBundled("frozen-v1", "admin"));
        verify(mapper, never()).selectDatasetByKeyVersion("frozen-v1", "v1");
    }

    @Test
    void canonicalExportIsStableRegardlessOfMapperOrder() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalCaseEntity a = validCase(1L); a.setCaseKey("a");
        RagEvalCaseEntity b = validCase(1L); b.setCaseKey("b");
        when(mapper.selectCasesByDatasetId(1L))
            .thenReturn(new ArrayList<>(List.of(b, a)))
            .thenReturn(new ArrayList<>(List.of(a, b)));
        RagEvalDatasetService service = new RagEvalDatasetService(mapper);

        assertEquals(service.exportJsonl(1L), service.exportJsonl(1L));
    }

    @Test
    void jsonlImportCreatesEditableDatasetAndCases() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        doAnswer(invocation -> { ((RagEvalDataset) invocation.getArgument(0)).setId(7L); return 1; })
            .when(mapper).insertDataset(any());
        doAnswer(invocation -> { ((RagEvalCaseEntity) invocation.getArgument(0)).setId(11L); return 1; })
            .when(mapper).insertCase(any());
        String jsonl = "{\"id\":\"case-a\",\"query\":\"深圳大学\",\"difficulty\":\"medium\","
            + "\"expectedMetadata\":{},\"relevantSources\":[\"source-a\"]}";

        RagEvalDataset dataset = new RagEvalDatasetService(mapper)
            .importJsonl("uploaded", "Uploaded", "v1", "DEV", jsonl, "admin");

        assertEquals(7L, dataset.getId());
        assertEquals(1, dataset.getCaseCount());
        verify(mapper).updateDatasetCount(7L, 1);
    }

    @Test
    void freezeCopiesCasesAndWritesMatchingSnapshot() throws Exception {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDataset source = dataset(1L, "READY");
        RagEvalCaseEntity evalCase = validCase(1L);
        when(mapper.selectDatasetById(1L)).thenReturn(source);
        when(mapper.selectCasesByDatasetId(1L)).thenReturn(List.of(evalCase));
        doAnswer(invocation -> { ((RagEvalDataset) invocation.getArgument(0)).setId(2L); return 1; })
            .when(mapper).insertDataset(any());

        RagEvalDataset frozen = new RagEvalDatasetService(mapper, tempDir)
            .freeze(1L, "frozen-v2", "admin");

        assertEquals("FROZEN", frozen.getLifecycleState());
        assertEquals(1, frozen.getCaseCount());
        verify(mapper).insertCase(org.mockito.ArgumentMatchers.argThat(c -> c.getDatasetId().equals(2L)));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(tempDir.resolve("dev-frozen-v2.jsonl")));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(tempDir.resolve("dev-frozen-v2.manifest.json")));
    }

    @Test
    void snapshotFailureAbortsFreezeBeforeSuccessIsReturned() throws Exception {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        when(mapper.selectDatasetById(1L)).thenReturn(dataset(1L, "READY"));
        when(mapper.selectCasesByDatasetId(1L)).thenReturn(List.of(validCase(1L)));
        doAnswer(invocation -> { ((RagEvalDataset) invocation.getArgument(0)).setId(2L); return 1; })
            .when(mapper).insertDataset(any());
        Path notDirectory = tempDir.resolve("file");
        Files.writeString(notDirectory, "occupied");

        assertThrows(ServiceException.class,
            () -> new RagEvalDatasetService(mapper, notDirectory).freeze(1L, "broken", "admin"));
    }

    private RagEvalDataset dataset(Long id, String state) {
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setId(id); dataset.setDatasetKey("dev"); dataset.setName("Dev"); dataset.setVersion("v1");
        dataset.setDatasetType("DEV"); dataset.setLifecycleState(state); dataset.setLockVersion(0);
        return dataset;
    }

    private RagEvalCaseEntity validCase(Long datasetId) {
        RagEvalCaseEntity evalCase = new RagEvalCaseEntity();
        evalCase.setDatasetId(datasetId); evalCase.setCaseKey("case-1"); evalCase.setQueryText("深圳大学复试线");
        evalCase.setDifficulty("medium"); evalCase.setNoAnswer(false); evalCase.setRelevantConditionsJson("[{\"programId\":\"1\"}]");
        evalCase.setRelevantSourcesJson("[]"); evalCase.setExpectedMetadataJson("{}"); evalCase.setTagsJson("[]");
        return evalCase;
    }
}
