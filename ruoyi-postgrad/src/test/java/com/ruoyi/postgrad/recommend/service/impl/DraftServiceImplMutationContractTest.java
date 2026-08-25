package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IDraftMutationService;

class DraftServiceImplMutationContractTest {

    private static final long USER_ID = 7L;
    private static final String DRAFT_KEY = "ai:v2:draft:7";
    private static final String WORKSPACE_KEY = "ai:v2:workspace:7";

    private DraftServiceImpl service;
    private IDraftMutationService mutationService;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new DraftServiceImpl();
        mutationService = mock(IDraftMutationService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        inject("redisTemplate", redisTemplate);
        inject("draftMutationService", mutationService);
    }

    @Test
    void addBackMustSurfaceInvariantRejectionInsteadOfReturningUnchangedDraftAsSuccess() {
        CandidateCardVO removed = candidate(99L, 8, "211");
        DraftVO draft = draft(candidate(1L, 0, "other"));
        draft.setRemovedCandidates(new ArrayList<>(List.of(removed)));
        when(valueOperations.get(DRAFT_KEY)).thenReturn(JSON.toJSONString(draft));
        when(valueOperations.get(WORKSPACE_KEY)).thenReturn(JSON.toJSONString(workspace("reach", removed)));

        DraftMutationResultVO rejected = mutation(false, draft);
        when(mutationService.addCandidateDirect(eq(USER_ID), any(), any())).thenReturn(rejected);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.addBackCandidate(USER_ID, 99L));

        assertEquals("候选无法加回，当前档位容量或唯一性校验未通过", error.getMessage());
        verify(mutationService).addCandidateDirect(eq(USER_ID), any(), any());
    }

    @Test
    void addFromWorkspaceMustSurfaceInvariantRejection() {
        CandidateCardVO existing = candidate(1L, 0, "other");
        CandidateCardVO offered = candidate(99L, 12, "211");
        DraftVO draft = draft(existing);
        when(valueOperations.get(DRAFT_KEY)).thenReturn(JSON.toJSONString(draft));
        when(valueOperations.get(WORKSPACE_KEY)).thenReturn(JSON.toJSONString(workspace("reach", offered)));
        when(mutationService.addCandidate(USER_ID, 99L, "reach", workspace("reach", offered)))
            .thenReturn(mutation(false, draft));
        when(mutationService.addCandidate(eq(USER_ID), eq(99L), eq("reach"), any()))
            .thenReturn(mutation(false, draft));

        assertThrows(IllegalStateException.class,
            () -> service.addFromWorkspace(USER_ID, "reach", "safer"));
    }

    @Test
    void replaceMustChooseCandidateUsingRequestedTierAndSaferPreference() {
        CandidateCardVO old = candidate(1L, 0, "other");
        CandidateCardVO riskier = candidate(2L, 3, "985");
        CandidateCardVO safer = candidate(3L, 18, "211");
        DraftVO draft = draft(old);
        CandidateWorkspaceVO workspace = workspace("safe", riskier, safer);
        when(valueOperations.get(DRAFT_KEY)).thenReturn(JSON.toJSONString(draft));
        when(valueOperations.get(WORKSPACE_KEY)).thenReturn(JSON.toJSONString(workspace));
        DraftMutationResultVO changed = mutation(true, draft);
        when(mutationService.replaceCandidate(eq(USER_ID), eq(1L), eq(3L), eq("safe"), any()))
            .thenReturn(changed);

        ReplaceResultVO result = service.replaceCandidate(USER_ID, 1L, "safe", "safer");

        assertEquals(3L, result.getReplacedWith().getFact().getProgramId());
        assertEquals(1L, result.getDraft().getTiers().get(0).getCandidates().get(0).getFact().getProgramId());
        verify(mutationService).replaceCandidate(eq(USER_ID), eq(1L), eq(3L), eq("safe"), any());
    }

    @Test
    void replaceMustNotReportSuccessWhenMutationRejectsSelection() {
        CandidateCardVO old = candidate(1L, 0, "other");
        CandidateCardVO offered = candidate(2L, 12, "211");
        DraftVO draft = draft(old);
        CandidateWorkspaceVO workspace = workspace("steady", offered);
        when(valueOperations.get(DRAFT_KEY)).thenReturn(JSON.toJSONString(draft));
        when(valueOperations.get(WORKSPACE_KEY)).thenReturn(JSON.toJSONString(workspace));
        when(mutationService.replaceCandidate(eq(USER_ID), eq(1L), eq(2L), eq("steady"), any()))
            .thenReturn(mutation(false, draft));

        assertThrows(IllegalStateException.class,
            () -> service.replaceCandidate(USER_ID, 1L, "steady", "safer"));
    }

    private DraftMutationResultVO mutation(boolean ok, DraftVO draft) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(ok);
        result.setDraft(draft);
        result.setDraftCount(1);
        return result;
    }

    private DraftVO draft(CandidateCardVO... candidates) {
        DraftVO draft = new DraftVO();
        TierCandidates reach = new TierCandidates();
        reach.setLevel("reach");
        reach.setLabel("冲刺档");
        reach.setTargetCount(3);
        reach.setCandidates(new ArrayList<>(List.of(candidates)));
        TierCandidates steady = emptyTier("steady", 4);
        TierCandidates safe = emptyTier("safe", 3);
        draft.setTiers(new ArrayList<>(List.of(reach, steady, safe)));
        draft.setRemovedCandidates(new ArrayList<>());
        return draft;
    }

    private TierCandidates emptyTier(String level, int target) {
        TierCandidates tier = new TierCandidates();
        tier.setLevel(level);
        tier.setLabel(level);
        tier.setTargetCount(target);
        tier.setCandidates(new ArrayList<>());
        return tier;
    }

    private CandidateWorkspaceVO workspace(String level, CandidateCardVO... candidates) {
        WorkspaceTierVO tier = new WorkspaceTierVO();
        tier.setLevel(level);
        tier.setCandidates(new ArrayList<>(List.of(candidates)));
        CandidateWorkspaceVO workspace = new CandidateWorkspaceVO();
        workspace.setUserId(USER_ID);
        workspace.setTiers(new ArrayList<>(List.of(tier)));
        return workspace;
    }

    private CandidateCardVO candidate(Long programId, int scoreGap, String schoolTier) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolId(programId);
        fact.setSchoolName("School-" + programId);
        fact.setProgramName("Program-" + programId);
        fact.setScoreGap(scoreGap);
        fact.setSchoolTier(schoolTier);
        return CandidateCardVO.fromFact(fact);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = DraftServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
