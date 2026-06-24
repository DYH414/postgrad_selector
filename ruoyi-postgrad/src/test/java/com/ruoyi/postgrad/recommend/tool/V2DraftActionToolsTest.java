package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IDraftMutationService;
import com.ruoyi.postgrad.recommend.service.IDraftService;

class V2DraftActionToolsTest {

    @AfterEach
    void clearContext() {
        V2ChatToolContext.clear();
    }

    @Test
    void contextTracksDraftWriteStateForCurrentTurn() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);

        V2ChatToolContext.init(1L, redis, mapper);

        assertFalse(V2ChatToolContext.writeExecuted());
        assertFalse(V2ChatToolContext.draftChanged());

        V2ChatToolContext.markWriteExecuted("{\"ok\":true}");

        assertTrue(V2ChatToolContext.writeExecuted());
        assertTrue(V2ChatToolContext.draftChanged());
        assertEquals("{\"ok\":true}", V2ChatToolContext.lastActionResultJson());
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeDraftCandidateDelegatesToDraftServiceAndRecordsToolResult() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);
        // inject redis via reflection
        try {
            var f = V2DraftActionTools.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(tools, redis);
        } catch (Exception e) { throw new RuntimeException(e); }

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        DraftVO before = draftWithCandidate(1764L, "South China Agricultural University");
        DraftVO after = emptyDraft();
        after.setRemovedCandidates(List.of(candidate(1764L, "South China Agricultural University")));
        when(draftService.getDraft(1L)).thenReturn(before);

        DraftMutationResultVO mutation = new DraftMutationResultVO();
        mutation.setOk(true);
        mutation.setAction("remove");
        mutation.setDraft(after);
        mutation.setDraftCount(0);
        when(mutationService.removeCandidate(eq(1L), eq(1764L), any())).thenReturn(mutation);

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertEquals("remove", result.get("action"));
        assertEquals(1764, ((Number) result.get("programId")).intValue());
        assertEquals("South China Agricultural University", result.get("schoolName"));
        assertEquals(0, ((Number) result.get("draftCount")).intValue());
        assertTrue(V2ChatToolContext.writeExecuted());
        assertEquals(json, V2ChatToolContext.lastActionResultJson());
        verify(mutationService).removeCandidate(eq(1L), eq(1764L), any());
    }

    @Test
    void removeDraftCandidateRejectsProgramOutsideCurrentDraft() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        when(draftService.getDraft(1L)).thenReturn(draftWithCandidate(1764L, "South China Agricultural University"));

        String json = tools.removeDraftCandidate(9999L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("program_not_in_draft", result.get("error"));
        assertFalse(V2ChatToolContext.writeExecuted());
        assertNull(V2ChatToolContext.lastActionResultJson());
        verify(mutationService, never()).removeCandidate(anyLong(), anyLong(), any());
    }

    @Test
    void removeDraftCandidateRejectsSecondWriteInSameTurn() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);
        V2ChatToolContext.markWriteExecuted("{\"ok\":true}");

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("write_already_executed", result.get("error"));
        verify(mutationService, never()).removeCandidate(anyLong(), anyLong(), any());
    }

    @Test
    void removeDraftCandidateRejectsMissingToolContext() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("no_tool_context", result.get("error"));
        verify(mutationService, never()).removeCandidate(anyLong(), anyLong(), any());
    }

    @Test
    void boundDraftActionToolCarriesContextAcrossWorkerThread() throws Exception {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);
        try {
            var f = V2DraftActionTools.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(tools, redis);
        } catch (Exception e) { throw new RuntimeException(e); }

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.Context context = V2ChatToolContext.init(1L, redis, mapper);
        V2ChatToolContext.clear();

        DraftVO before = draftWithCandidate(1764L, "South China Agricultural University");
        DraftVO after = emptyDraft();
        when(draftService.getDraft(1L)).thenReturn(before);

        DraftMutationResultVO mutation = new DraftMutationResultVO();
        mutation.setOk(true);
        mutation.setAction("remove");
        mutation.setDraft(after);
        mutation.setDraftCount(0);
        when(mutationService.removeCandidate(eq(1L), eq(1764L), any())).thenReturn(mutation);

        V2BoundDraftActionTools boundTools = new V2BoundDraftActionTools(tools, context);
        String json = CompletableFuture.supplyAsync(() -> boundTools.removeDraftCandidate(1764L)).get();
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertTrue(context.draftChanged());
        assertEquals(json, context.lastActionResultJson());
        verify(mutationService).removeCandidate(eq(1L), eq(1764L), any());
    }

    @Test
    void batchReplaceDelegatesAndMarksWriteWhenAnyItemSucceeds() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        // Workspace exists in Redis
        CandidateWorkspaceVO workspace = new CandidateWorkspaceVO();
        workspace.setWorkspaceId("ws-1");
        workspace.setUserId(1L);
        WorkspaceTierVO wsTier = new WorkspaceTierVO();
        wsTier.setLevel("steady");
        wsTier.setCandidates(new ArrayList<>());
        workspace.setTiers(List.of(wsTier));
        when(ops.get("ai:v2:workspace:1")).thenReturn(JSON.toJSONString(workspace));

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);
        try {
            var f = V2DraftActionTools.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(tools, redis);
        } catch (Exception e) { throw new RuntimeException(e); }

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        // Mock batchReplace returns ok=true
        Map<String, Object> serviceResult = new LinkedHashMap<>();
        serviceResult.put("ok", true);
        serviceResult.put("action", "batch_replace");
        serviceResult.put("requested", 2);
        serviceResult.put("replaced", 2);
        serviceResult.put("failed", 0);
        serviceResult.put("draftCount", 3);
        when(mutationService.batchReplace(eq(1L), any(), any(), any())).thenReturn(serviceResult);

        DraftReplacementRequest req = new DraftReplacementRequest();
        req.setRemoveProgramId(1001L);
        req.setAddProgramId(2001L);

        String json = tools.batchReplaceDraftCandidates(List.of(req));
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertEquals("batch_replace", result.get("action"));
        assertTrue(V2ChatToolContext.writeExecuted());
        assertEquals(json, V2ChatToolContext.lastActionResultJson());
    }

    @Test
    void batchReplaceRejectsEmptyInput() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        String json = tools.batchReplaceDraftCandidates(List.of());
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("empty_replacements", result.get("error"));
        assertFalse(V2ChatToolContext.writeExecuted());
    }

    @Test
    void batchReplaceRejectsSecondWriteInSameTurn() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);
        V2ChatToolContext.markWriteExecuted("{\"ok\":true}");

        DraftReplacementRequest req = new DraftReplacementRequest();
        req.setRemoveProgramId(1001L);
        req.setAddProgramId(2001L);

        String json = tools.batchReplaceDraftCandidates(List.of(req));
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("write_already_executed", result.get("error"));
        verify(mutationService, never()).batchReplace(anyLong(), any(), any(), any());
    }

    // ═══════════ H2: replaceCandidate DB fallback 不双重添加 ═══════════

    @Test
    @SuppressWarnings("unchecked")
    void replaceDraftCandidateDbFallbackShouldNotDoubleAdd() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        // workspace 没有候选 → 触发 DB fallback
        when(ops.get(any())).thenReturn(null);

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);
        injectRedis(tools, redis);

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        // draft 中有一个候选 (1001L)
        DraftVO draft = draftWithCandidate(1001L, "Old School");
        when(draftService.getDraft(1L)).thenReturn(draft);

        // DB 中有替换候选 (2001L)
        com.ruoyi.postgrad.domain.RowMap row = new com.ruoyi.postgrad.domain.RowMap();
        row.put("programId", 2001L);
        row.put("schoolName", "New School");
        row.put("programName", "CS");
        row.put("schoolTier", "211");
        row.put("city", "厦门");
        row.put("province", "福建");
        row.put("avgAdmittedScore", 300);
        row.put("unifiedExamQuota", 15);
        row.put("planCount", 10);
        row.put("dataCompleteness", "A");
        row.put("admissionLow", 290);
        row.put("admissionHigh", 310);
        when(mapper.selectProgramForRecommendation(2001L)).thenReturn(row);

        // Mock removeFromDraftDirect → should be called (NOT removeCandidate)
        DraftMutationResultVO removeResult = new DraftMutationResultVO();
        removeResult.setOk(true);
        removeResult.setAction("remove_direct");
        removeResult.setDraft(draft);
        removeResult.setDraftCount(0);
        when(mutationService.removeFromDraftDirect(eq(1L), eq(1001L))).thenReturn(removeResult);

        // Mock addCandidateDirect
        DraftMutationResultVO addResult = new DraftMutationResultVO();
        addResult.setOk(true);
        addResult.setAction("add_external");
        addResult.setDraft(draft);
        addResult.setDraftCount(1);
        when(mutationService.addCandidateDirect(eq(1L), any(), eq("reach"))).thenReturn(addResult);

        String json = tools.replaceDraftCandidate(1001L, 2001L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertTrue((Boolean) result.get("ok"), "H2: replace should succeed");
        assertEquals("replace", result.get("action"));
        // 关键：verify removeFromDraftDirect 被调用（不是 removeCandidate）
        verify(mutationService).removeFromDraftDirect(1L, 1001L);
    }

    // ═══════════ H5: confirmRefillCandidate draft tier 回退 ═══════════

    @Test
    @SuppressWarnings("unchecked")
    void confirmRefillCandidateShouldFallbackToDraftTier() {
        IDraftService draftService = mock(IDraftService.class);
        IDraftMutationService mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null); // workspace 为空

        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);
        tools.setDraftMutationServiceForTest(mutationService);
        injectRedis(tools, redis);

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        // Workspace 为空 → findTierInWorkspace 返回 null
        // Draft 中稳妥档有候选 2001L
        DraftVO draft = draftWithCandidate(2001L, "DB Fallback School");
        draft.getTiers().get(0).setLevel("steady");
        draft.getTiers().get(0).setLabel("steady");
        when(draftService.getDraft(1L)).thenReturn(draft);

        DraftMutationResultVO confirmResult = new DraftMutationResultVO();
        confirmResult.setOk(true);
        confirmResult.setAction("confirm_refill");
        confirmResult.setDraft(draft);
        confirmResult.setDraftCount(3);
        when(mutationService.confirmRefillCandidate(eq(1L), eq(2001L), eq("steady"), any()))
            .thenReturn(confirmResult);

        String json = tools.confirmRefillCandidate(2001L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertTrue((Boolean) result.get("ok"), "H5: confirm should succeed via draft tier fallback");
        assertEquals("steady", result.get("tier"));
        assertEquals(2001, ((Number) result.get("programId")).intValue());
    }

    private void injectRedis(V2DraftActionTools tools, StringRedisTemplate redis) {
        try {
            var f = V2DraftActionTools.class.getDeclaredField("redisTemplate");
            f.setAccessible(true);
            f.set(tools, redis);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private DraftVO draftWithCandidate(Long programId, String schoolName) {
        DraftVO draft = new DraftVO();
        TierCandidates tier = new TierCandidates();
        tier.setLevel("reach");
        tier.setLabel("reach");
        tier.setTargetCount(3);
        tier.setCandidates(new ArrayList<>(List.of(candidate(programId, schoolName))));
        draft.setTiers(List.of(tier));
        draft.setRemovedCandidates(new ArrayList<>());
        return draft;
    }

    private DraftVO emptyDraft() {
        DraftVO draft = new DraftVO();
        TierCandidates tier = new TierCandidates();
        tier.setLevel("reach");
        tier.setLabel("reach");
        tier.setTargetCount(3);
        tier.setCandidates(new ArrayList<>());
        draft.setTiers(List.of(tier));
        draft.setRemovedCandidates(new ArrayList<>());
        return draft;
    }

    private CandidateCardVO candidate(Long programId, String schoolName) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolName(schoolName);
        fact.setProgramName("Electronic Information");
        return CandidateCardVO.fromFact(fact);
    }
}
