package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IDraftDecisionLogService;
import com.ruoyi.postgrad.recommend.service.IRefillPolicyService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftMutationServiceImplInvariantTest {

    private static final Long USER_ID = 1L;
    private static final String DRAFT_KEY = "ai:v2:draft:1";

    private DraftMutationServiceImpl service;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private IDraftDecisionLogService decisionLogService;
    @Mock private IRefillPolicyService refillPolicyService;
    @Mock private RecommendationMapper recommendationMapper;

    private final AtomicReference<String> draftState = new AtomicReference<>();
    private final AtomicInteger draftWrites = new AtomicInteger();
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new DraftMutationServiceImpl();
        injectField(service, "redisTemplate", redisTemplate);
        injectField(service, "decisionLogService", decisionLogService);
        injectField(service, "refillPolicyService", refillPolicyService);
        injectField(service, "recommendationMapper", recommendationMapper);

        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq(DRAFT_KEY))).thenAnswer(inv -> draftState.get());
        doAnswer(inv -> {
            draftWrites.incrementAndGet();
            draftState.set(inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(eq(DRAFT_KEY), anyString(), any(Duration.class));
    }

    @Test
    void addBackMustRejectWhenTierIsFullWithoutWritingDraft() {
        DraftVO original = canonicalDraft(3, 4, 3);
        draftState.set(JSON.toJSONString(original));

        DraftMutationResultVO result = service.addCandidateDirect(USER_ID, candidate(99L), "safe");

        assertRejectedWithoutMutation(result, original);
    }

    @Test
    void workspaceAddMustRejectWhenTierIsFullWithoutWritingDraft() {
        DraftVO original = canonicalDraft(3, 4, 2);
        draftState.set(JSON.toJSONString(original));
        CandidateWorkspaceVO workspace = workspace("reach", candidate(99L));

        DraftMutationResultVO result = service.addCandidate(USER_ID, 99L, "reach", workspace);

        assertRejectedWithoutMutation(result, original);
    }

    @Test
    void addMustRejectCandidateAlreadyPresentInAnotherTierWithoutWritingDraft() {
        DraftVO original = canonicalDraft(1, 0, 0);
        draftState.set(JSON.toJSONString(original));

        DraftMutationResultVO result = service.addCandidateDirect(USER_ID, candidate(1L), "steady");

        assertRejectedWithoutMutation(result, original);
    }

    @Test
    void addMustRejectUnknownTierWithoutWritingDraft() {
        DraftVO original = canonicalDraft(1, 0, 0);
        draftState.set(JSON.toJSONString(original));

        DraftMutationResultVO result = service.addCandidateDirect(USER_ID, candidate(99L), "unknown");

        assertRejectedWithoutMutation(result, original);
    }

    @Test
    void addMustRejectWhenDraftAlreadyHasTenCandidatesEvenIfTierTargetIsHigher() {
        DraftVO original = draftWithCustomTargets(4, 3, 3, 5, 5, 5);
        draftState.set(JSON.toJSONString(original));

        DraftMutationResultVO result = service.addCandidateDirect(USER_ID, candidate(99L), "safe");

        assertRejectedWithoutMutation(result, original);
    }

    @Test
    void confirmRefillMustUseSameInvariantBoundary() {
        DraftVO original = canonicalDraft(3, 4, 3);
        draftState.set(JSON.toJSONString(original));
        CandidateWorkspaceVO workspace = workspace("safe", candidate(99L));
        when(refillPolicyService.confirmCandidate(workspace, 99L, "safe")).thenReturn(candidate(99L));

        DraftMutationResultVO result = service.confirmRefillCandidate(USER_ID, 99L, "safe", workspace);

        assertRejectedWithoutMutation(result, original);
    }

    private void assertRejectedWithoutMutation(DraftMutationResultVO result, DraftVO original) {
        assertFalse(result.isOk());
        assertEquals(countDraft(original), result.getDraftCount());
        assertEquals(JSON.toJSONString(original), draftState.get());
        assertEquals(0, draftWrites.get());
        verify(decisionLogService, never()).append(any(Long.class), any());
    }

    private static DraftVO canonicalDraft(int reach, int steady, int safe) {
        return draftWithCustomTargets(reach, steady, safe, 3, 4, 3);
    }

    private static DraftVO draftWithCustomTargets(int reach, int steady, int safe,
                                                   int reachTarget, int steadyTarget, int safeTarget) {
        DraftVO draft = new DraftVO();
        draft.setTiers(new ArrayList<>(List.of(
            tier("reach", reachTarget, 1L, reach),
            tier("steady", steadyTarget, 100L, steady),
            tier("safe", safeTarget, 200L, safe))));
        draft.setRemovedCandidates(new ArrayList<>());
        draft.setBlockedCandidates(new ArrayList<>());
        return draft;
    }

    private static TierCandidates tier(String level, int target, long firstId, int count) {
        TierCandidates tier = new TierCandidates();
        tier.setLevel(level);
        tier.setLabel(level);
        tier.setTargetCount(target);
        List<CandidateCardVO> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) candidates.add(candidate(firstId + i));
        tier.setCandidates(candidates);
        tier.setInsufficient(count < target);
        return tier;
    }

    private static CandidateCardVO candidate(Long programId) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolId(programId);
        fact.setSchoolName("School-" + programId);
        fact.setProgramName("Program-" + programId);
        return CandidateCardVO.fromFact(fact);
    }

    private static CandidateWorkspaceVO workspace(String level, CandidateCardVO... candidates) {
        WorkspaceTierVO tier = new WorkspaceTierVO();
        tier.setLevel(level);
        tier.setCandidates(new ArrayList<>(List.of(candidates)));
        CandidateWorkspaceVO workspace = new CandidateWorkspaceVO();
        workspace.setUserId(USER_ID);
        workspace.setTiers(new ArrayList<>(List.of(tier)));
        return workspace;
    }

    private static int countDraft(DraftVO draft) {
        return draft.getTiers().stream()
            .mapToInt(t -> t.getCandidates() == null ? 0 : t.getCandidates().size())
            .sum();
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = DraftMutationServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
