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
import static org.mockito.Mockito.argThat;

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

    @Test
    void agentCaseRoundTripsThroughImportAndExportWithoutLosingSpec() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        List<RagEvalCaseEntity> stored = new ArrayList<>();
        doAnswer(invocation -> { ((RagEvalDataset) invocation.getArgument(0)).setId(7L); return 1; })
            .when(mapper).insertDataset(any());
        doAnswer(invocation -> {
            RagEvalCaseEntity e = invocation.getArgument(0);
            e.setId((long) (stored.size() + 1));
            stored.add(e);
            return 1;
        }).when(mapper).insertCase(any());
        when(mapper.selectCasesByDatasetId(7L)).thenReturn(stored);

        String jsonl = "{\"id\":\"agent-01\",\"query\":\"深圳大学计算机专硕近两年录取分数怎样？\","
            + "\"expectedRoute\":\"PROGRAM_FACTS\",\"category\":\"program_facts\",\"difficulty\":\"medium\","
            + "\"agent\":{\"requiredTools\":[\"searchPrograms\"],\"forbiddenTools\":[\"addDraftCandidate\"],"
            + "\"expectedAbstention\":false,\"expectedClaims\":[\"必须说明数据年份\"],\"forbiddenClaims\":[\"保证录取\"],"
            + "\"referenceEvidence\":[{\"evidenceKey\":\"szu-cs-2025\",\"citationTokens\":[\"深圳大学\",\"2025\"]}]}}";

        RagEvalDatasetService service = new RagEvalDatasetService(mapper);
        service.importJsonl("agent-set", "Agent Set", "v1", "DEV", jsonl, "admin");

        // 入库时 agent 规格被序列化保存
        assertEquals(1, stored.size());
        org.junit.jupiter.api.Assertions.assertNotNull(stored.get(0).getAgentSpecJson());
        // 导出往返：agent 块完整保留（含工具/断言/证据 token）
        String exported = service.exportJsonl(7L);
        org.junit.jupiter.api.Assertions.assertTrue(exported.contains("\"searchPrograms\""));
        org.junit.jupiter.api.Assertions.assertTrue(exported.contains("\"必须说明数据年份\""));
        org.junit.jupiter.api.Assertions.assertTrue(exported.contains("\"szu-cs-2025\""));
        org.junit.jupiter.api.Assertions.assertTrue(exported.contains("\"2025\""));
    }

    @Test
    void agentOnlyPositiveCasePassesValidation() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        when(mapper.selectDatasetById(1L)).thenReturn(dataset(1L, "DRAFT"));
        RagEvalCaseEntity evalCase = new RagEvalCaseEntity();
        evalCase.setDatasetId(1L); evalCase.setCaseKey("agent-1"); evalCase.setQueryText("成电408要多少分");
        evalCase.setNoAnswer(false); evalCase.setRelevantConditionsJson("[]"); evalCase.setRelevantSourcesJson("[]");
        evalCase.setAgentSpecJson("{\"requiredTools\":[\"searchPrograms\"]}");

        // 无 conditions/sources 但有 agentSpec → 不应被拒
        new RagEvalDatasetService(mapper).saveCase(evalCase, "admin");
        verify(mapper, times(1)).insertCase(any());
    }

    @Test
    void createPreservesAgentEvaluationType() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDataset requested = dataset(null, "DRAFT");
        requested.setEvaluationType("AGENT_E2E");

        RagEvalDataset created = new RagEvalDatasetService(mapper).create(requested, "admin");

        assertEquals("AGENT_E2E", created.getEvaluationType());
        verify(mapper).insertDataset(argThat(d -> "AGENT_E2E".equals(d.getEvaluationType())));
    }

    @Test
    void freezePreservesAgentEvaluationType() {
        RagEvaluationWorkbenchMapper mapper = mock(RagEvaluationWorkbenchMapper.class);
        RagEvalDataset source = dataset(1L, "READY");
        source.setEvaluationType("AGENT_E2E");
        when(mapper.selectDatasetById(1L)).thenReturn(source);
        when(mapper.selectCasesByDatasetId(1L)).thenReturn(List.of(validAgentCase(1L)));
        doAnswer(invocation -> { ((RagEvalDataset) invocation.getArgument(0)).setId(2L); return 1; })
            .when(mapper).insertDataset(any());

        RagEvalDataset frozen = new RagEvalDatasetService(mapper, tempDir)
            .freeze(1L, "agent-frozen-v1", "admin");

        assertEquals("AGENT_E2E", frozen.getEvaluationType());
        verify(mapper).insertDataset(argThat(d -> "AGENT_E2E".equals(d.getEvaluationType())));
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

    private RagEvalCaseEntity validAgentCase(Long datasetId) {
        RagEvalCaseEntity evalCase = validCase(datasetId);
        evalCase.setRelevantConditionsJson("[]");
        evalCase.setAgentSpecJson("{\"requiredTools\":[]}");
        return evalCase;
    }
}
