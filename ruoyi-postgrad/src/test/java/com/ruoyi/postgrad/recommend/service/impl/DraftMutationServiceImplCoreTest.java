package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.RefillResultVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IDraftDecisionLogService;
import com.ruoyi.postgrad.recommend.service.IRefillPolicyService;

/**
 * TDD 测试：fillTier 静默失败 (H1) + replaceCandidate 双重添加 (H2)
 *
 * <p>通过标准：</p>
 * <ul>
 *   <li>fillTier 在无候选可用时返回 ok=false</li>
 *   <li>fillTier 在 workspace + DB 有候选时 added > 0</li>
 *   <li>replaceCandidate 不会导致档位候选数超出目标</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftMutationServiceImplCoreTest {

    private static final Long USER_ID = 1L;
    private static final String DRAFT_KEY = "ai:v2:draft:" + USER_ID;
    private static final String EXCLUDED_KEY = "ai:v2:excluded:" + USER_ID;

    private DraftMutationServiceImpl service;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private IDraftDecisionLogService decisionLogService;

    @Mock
    private IRefillPolicyService refillPolicyService;

    @Mock
    private RecommendationMapper recommendationMapper;

    private final AtomicReference<String> draftState = new AtomicReference<>();
    private final AtomicReference<String> excludedState = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new DraftMutationServiceImpl();
        injectField(service, "redisTemplate", redisTemplate);
        injectField(service, "decisionLogService", decisionLogService);
        injectField(service, "refillPolicyService", refillPolicyService);
        injectField(service, "recommendationMapper", recommendationMapper);

        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        when(ops.get(eq(DRAFT_KEY))).thenAnswer(inv -> draftState.get());
        doAnswer(inv -> {
            draftState.set(inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(eq(DRAFT_KEY), anyString(), any(Duration.class));

        excludedState.set("[]");
        when(ops.get(eq(EXCLUDED_KEY))).thenAnswer(inv -> excludedState.get());
        doAnswer(inv -> {
            excludedState.set(inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(eq(EXCLUDED_KEY), anyString(), any(Duration.class));

        // Mock refill: policy=null (跳过自动填充，关键 for H2 replaceCandidate 测试)
        RefillResultVO noRefill = new RefillResultVO();
        noRefill.setPolicy(null); // null policy means "no refill"
        when(refillPolicyService.evaluate(any(), any(), anyString(), any()))
            .thenReturn(noRefill);

        // Mock DB fallback: 返回空列表（fillTier DB 回退路径不可用）
        when(recommendationMapper.selectCandidates(anyString(), any(), any(), anyInt(), any(), anyString()))
            .thenReturn(Collections.emptyList());
    }

    // ═══════════ H1: fillTier 静默失败 ═══════════

    @Test
    void fillTierShouldReturnOkFalseWhenWorkspaceEmptyAndNoDbFallback() {
        // Given: draft 稳妥档 0/4, workspace 为空, 无 DB 回退
        draftState.set(JSON.toJSONString(draftWithEmptySteadyTier()));

        // When: fillTier("steady")
        DraftMutationResultVO result = service.fillTier(USER_ID, "steady", emptyWorkspace());

        // Then: ok=false, 因为没有任何候选可用
        assertFalse(result.isOk(), "fillTier should fail when no candidates available (was H1 bug)");
    }

    @Test
    void fillTierShouldReturnOkFalseWhenAllCandidatesAlreadyInDraft() {
        // Given: draft 稳妥档已有 3 个候选 (1001, 1002, 1003), workspace 只有这些已用的
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L, 1002L, 1003L)));
        CandidateWorkspaceVO ws = workspaceWithSteadyCandidates(1001L, 1002L, 1003L);

        // When: fillTier("steady") - 需要的 1 个在 workspace 里但已用过
        DraftMutationResultVO result = service.fillTier(USER_ID, "steady", ws);

        // Then: ok=false, 因为 workspace 候选全部已用且无 DB 回退
        assertFalse(result.isOk(),
            "fillTier should fail when all workspace candidates are already in draft (was H1 bug)");
    }

    @Test
    void fillTierShouldAddCandidatesWhenWorkspaceHasAvailable() {
        // Given: draft 稳妥档 3/4 (已有1001), workspace 有 1002 可用
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L)));
        CandidateWorkspaceVO ws = workspaceWithSteadyCandidates(1001L, 1002L, 1003L);

        // 只有 1001 在草稿中，1002 可供选择
        DraftMutationResultVO result = service.fillTier(USER_ID, "steady", ws);

        // Then: ok=true, added > 0
        assertTrue(result.isOk());
        assertTrue(result.getDraftCount() > 1,
            "fillTier should add available workspace candidates");
    }

    // ═══════════ H2: replaceCandidate 双重添加 ═══════════

    @Test
    void replaceCandidateShouldNotExceedTargetCount() {
        // Given: draft 冲刺档 3/3 (1001, 1002, 1003), workspace 有替换候选 2001
        draftState.set(JSON.toJSONString(draftWithReachCandidates(1001L, 1002L, 1003L)));
        CandidateWorkspaceVO ws = workspaceWithReachCandidates(2001L);

        // When: replace 1001 → 2001
        DraftMutationResultVO result = service.replaceCandidate(
            USER_ID, 1001L, 2001L, "reach", ws);

        // Then: 草稿冲刺档仍然是 3 个候选（替换，不是增加）
        DraftVO draft = result.getDraft();
        long reachCount = draft.getTiers().stream()
            .filter(t -> "reach".equals(t.getLevel()))
            .flatMap(t -> t.getCandidates().stream())
            .count();
        assertEquals(3, reachCount,
            "replaceCandidate should keep tier count at target (was H2 double-add bug)");
    }

    @Test
    void replaceCandidateShouldContainNewCandidate() {
        // Given: draft 冲刺档 3/3 (1001, 1002, 1003), workspace 有替换候选 2001
        draftState.set(JSON.toJSONString(draftWithReachCandidates(1001L, 1002L, 1003L)));
        CandidateWorkspaceVO ws = workspaceWithReachCandidates(2001L);

        // When: replace 1001 → 2001
        DraftMutationResultVO result = service.replaceCandidate(
            USER_ID, 1001L, 2001L, "reach", ws);

        // Then: 2001 在草稿中, 1001 不在
        DraftVO draft = result.getDraft();
        List<Long> programIds = draft.getTiers().stream()
            .filter(t -> "reach".equals(t.getLevel()))
            .flatMap(t -> t.getCandidates().stream())
            .map(c -> c.getFact().getProgramId())
            .toList();
        assertTrue(programIds.contains(2001L), "new candidate should be in draft");
        assertFalse(programIds.contains(1001L), "removed candidate should not be in draft");
    }

    // ═══════════ Builder Methods ═══════════

    private static DraftVO draftWithEmptySteadyTier() {
        DraftVO draft = new DraftVO();
        draft.setTiers(new ArrayList<>());
        draft.setRemovedCandidates(new ArrayList<>());
        draft.setBlockedCandidates(new ArrayList<>());

        TierCandidates reach = newTier("reach", "冲刺档", 3);
        reach.setCandidates(listOf(candidate(2001L, "ReachSchool")));
        draft.getTiers().add(reach);

        TierCandidates steady = newTier("steady", "稳妥档", 4);
        steady.setCandidates(new ArrayList<>());
        steady.setInsufficient(true);
        draft.getTiers().add(steady);

        TierCandidates safe = newTier("safe", "保底档", 3);
        safe.setCandidates(new ArrayList<>());
        safe.setInsufficient(true);
        draft.getTiers().add(safe);

        // profileBasis needed for DB fallback path
        ProfileBasisVO profile = new ProfileBasisVO();
        profile.setEstimatedScore(300);
        profile.setTargetRegions("[\"福建\"]");
        draft.setProfileBasis(profile);

        return draft;
    }

    private static DraftVO draftWithSteadyCandidates(Long... programIds) {
        DraftVO draft = new DraftVO();
        draft.setTiers(new ArrayList<>());
        draft.setRemovedCandidates(new ArrayList<>());
        draft.setBlockedCandidates(new ArrayList<>());

        TierCandidates reach = newTier("reach", "冲刺档", 3);
        reach.setCandidates(listOf(candidate(2001L, "ReachSchool")));
        draft.getTiers().add(reach);

        TierCandidates steady = newTier("steady", "稳妥档", 4);
        List<CandidateCardVO> steadyList = new ArrayList<>();
        for (Long pid : programIds) {
            steadyList.add(candidate(pid, "SteadySchool-" + pid));
        }
        steady.setCandidates(steadyList);
        steady.setInsufficient(steadyList.size() < 4);
        draft.getTiers().add(steady);

        TierCandidates safe = newTier("safe", "保底档", 3);
        safe.setCandidates(new ArrayList<>());
        safe.setInsufficient(true);
        draft.getTiers().add(safe);

        ProfileBasisVO profile = new ProfileBasisVO();
        profile.setEstimatedScore(300);
        profile.setTargetRegions("[\"福建\"]");
        draft.setProfileBasis(profile);

        return draft;
    }

    private static DraftVO draftWithReachCandidates(Long... programIds) {
        DraftVO draft = new DraftVO();
        draft.setTiers(new ArrayList<>());
        draft.setRemovedCandidates(new ArrayList<>());
        draft.setBlockedCandidates(new ArrayList<>());

        TierCandidates reach = newTier("reach", "冲刺档", 3);
        List<CandidateCardVO> reachList = new ArrayList<>();
        for (Long pid : programIds) {
            reachList.add(candidate(pid, "ReachSchool-" + pid));
        }
        reach.setCandidates(reachList);
        reach.setInsufficient(reachList.size() < 3);
        draft.getTiers().add(reach);

        TierCandidates steady = newTier("steady", "稳妥档", 4);
        steady.setCandidates(new ArrayList<>());
        steady.setInsufficient(true);
        draft.getTiers().add(steady);

        TierCandidates safe = newTier("safe", "保底档", 3);
        safe.setCandidates(new ArrayList<>());
        safe.setInsufficient(true);
        draft.getTiers().add(safe);

        ProfileBasisVO profile = new ProfileBasisVO();
        profile.setEstimatedScore(300);
        profile.setTargetRegions("[\"福建\"]");
        draft.setProfileBasis(profile);

        return draft;
    }

    private static TierCandidates newTier(String level, String label, int targetCount) {
        TierCandidates t = new TierCandidates();
        t.setLevel(level);
        t.setLabel(label);
        t.setTargetCount(targetCount);
        return t;
    }

    private static CandidateCardVO candidate(Long programId, String schoolName) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolName(schoolName);
        fact.setProgramName("Test Program");
        fact.setSchoolId(programId);
        fact.setDataCompleteness("A");
        fact.setAvgAdmittedScore(300);
        return CandidateCardVO.fromFact(fact);
    }

    private static CandidateWorkspaceVO workspaceWithSteadyCandidates(Long... programIds) {
        CandidateWorkspaceVO ws = new CandidateWorkspaceVO();
        ws.setWorkspaceId("ws-1");
        ws.setUserId(USER_ID);

        WorkspaceTierVO wsSteady = new WorkspaceTierVO();
        wsSteady.setLevel("steady");
        wsSteady.setLabel("稳妥档");
        List<CandidateCardVO> candidates = new ArrayList<>();
        for (Long pid : programIds) {
            candidates.add(candidate(pid, "WorkspaceSchool-" + pid));
        }
        wsSteady.setCandidates(candidates);
        wsSteady.setTargetCount(30);

        WorkspaceTierVO wsReach = new WorkspaceTierVO();
        wsReach.setLevel("reach");
        wsReach.setLabel("冲刺档");
        wsReach.setCandidates(new ArrayList<>());
        wsReach.setTargetCount(30);

        ws.setTiers(new ArrayList<>(List.of(wsReach, wsSteady)));
        return ws;
    }

    private static CandidateWorkspaceVO workspaceWithReachCandidates(Long... programIds) {
        CandidateWorkspaceVO ws = new CandidateWorkspaceVO();
        ws.setWorkspaceId("ws-1");
        ws.setUserId(USER_ID);

        WorkspaceTierVO wsReach = new WorkspaceTierVO();
        wsReach.setLevel("reach");
        wsReach.setLabel("冲刺档");
        List<CandidateCardVO> candidates = new ArrayList<>();
        for (Long pid : programIds) {
            candidates.add(candidate(pid, "WorkspaceReach-" + pid));
        }
        wsReach.setCandidates(candidates);
        wsReach.setTargetCount(30);

        WorkspaceTierVO wsSteady = new WorkspaceTierVO();
        wsSteady.setLevel("steady");
        wsSteady.setLabel("稳妥档");
        wsSteady.setCandidates(new ArrayList<>());
        wsSteady.setTargetCount(30);

        ws.setTiers(new ArrayList<>(List.of(wsReach, wsSteady)));
        return ws;
    }

    private static CandidateWorkspaceVO emptyWorkspace() {
        CandidateWorkspaceVO ws = new CandidateWorkspaceVO();
        ws.setWorkspaceId("ws-1");
        ws.setUserId(USER_ID);
        ws.setTiers(new ArrayList<>());
        return ws;
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        return new ArrayList<>(List.of(items));
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = DraftMutationServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
