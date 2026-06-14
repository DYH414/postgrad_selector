package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
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
    void removeDraftCandidateDelegatesToDraftServiceAndRecordsToolResult() {
        IDraftService draftService = mock(IDraftService.class);
        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);

        DraftVO before = draftWithCandidate(1764L, "South China Agricultural University");
        DraftVO after = emptyDraft();
        after.setRemovedCandidates(List.of(candidate(1764L, "South China Agricultural University")));
        when(draftService.getDraft(1L)).thenReturn(before);
        when(draftService.removeCandidate(1L, 1764L)).thenReturn(after);

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertEquals("remove", result.get("action"));
        assertEquals(1764, ((Number) result.get("programId")).intValue());
        assertEquals("South China Agricultural University", result.get("schoolName"));
        assertEquals(0, ((Number) result.get("draftCount")).intValue());
        assertTrue(V2ChatToolContext.writeExecuted());
        assertEquals(json, V2ChatToolContext.lastActionResultJson());
        verify(draftService).removeCandidate(1L, 1764L);
    }

    @Test
    void removeDraftCandidateRejectsProgramOutsideCurrentDraft() {
        IDraftService draftService = mock(IDraftService.class);
        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);

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
        verify(draftService, never()).removeCandidate(eq(1L), eq(9999L));
    }

    @Test
    void removeDraftCandidateRejectsSecondWriteInSameTurn() {
        IDraftService draftService = mock(IDraftService.class);
        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.init(1L, redis, mapper);
        V2ChatToolContext.markWriteExecuted("{\"ok\":true}");

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("write_already_executed", result.get("error"));
        verify(draftService, never()).removeCandidate(eq(1L), eq(1764L));
    }

    @Test
    void removeDraftCandidateRejectsMissingToolContext() {
        IDraftService draftService = mock(IDraftService.class);
        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);

        String json = tools.removeDraftCandidate(1764L);
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(false, result.get("ok"));
        assertEquals("no_tool_context", result.get("error"));
        verify(draftService, never()).removeCandidate(eq(1L), eq(1764L));
    }

    @Test
    void boundDraftActionToolCarriesContextAcrossWorkerThread() throws Exception {
        IDraftService draftService = mock(IDraftService.class);
        V2DraftActionTools tools = new V2DraftActionTools();
        tools.setDraftServiceForTest(draftService);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        V2ChatToolContext.Context context = V2ChatToolContext.init(1L, redis, mapper);
        V2ChatToolContext.clear();

        DraftVO before = draftWithCandidate(1764L, "South China Agricultural University");
        DraftVO after = emptyDraft();
        when(draftService.getDraft(1L)).thenReturn(before);
        when(draftService.removeCandidate(1L, 1764L)).thenReturn(after);

        V2BoundDraftActionTools boundTools = new V2BoundDraftActionTools(tools, context);
        String json = CompletableFuture.supplyAsync(() -> boundTools.removeDraftCandidate(1764L)).get();
        Map<String, Object> result = JSON.parseObject(json, Map.class);

        assertEquals(true, result.get("ok"));
        assertTrue(context.draftChanged());
        assertEquals(json, context.lastActionResultJson());
        verify(draftService).removeCandidate(1L, 1764L);
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
