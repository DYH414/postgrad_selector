package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementItemResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IDraftDecisionLogService;
import com.ruoyi.postgrad.recommend.service.IRefillPolicyService;

/**
 * Unit tests for {@link DraftMutationServiceImpl#batchReplace}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftMutationServiceImplBatchReplaceTest {

    private static final Long USER_ID = 1L;
    private static final String DRAFT_KEY = "ai:v2:draft:1";
    private static final String EXCLUDED_KEY = "ai:v2:excluded:1";

    private DraftMutationServiceImpl service;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private IDraftDecisionLogService decisionLogService;

    @Mock
    private IRefillPolicyService refillPolicyService;

    private final AtomicReference<String> draftState = new AtomicReference<>();
    private final AtomicReference<String> excludedState = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new DraftMutationServiceImpl();
        injectField(service, "redisTemplate", redisTemplate);
        injectField(service, "decisionLogService", decisionLogService);
        injectField(service, "refillPolicyService", refillPolicyService);

        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        // Simulate Redis draft key get/set
        when(ops.get(eq(DRAFT_KEY))).thenAnswer(inv -> draftState.get());
        doAnswer(inv -> {
            draftState.set(inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(eq(DRAFT_KEY), anyString(), any(Duration.class));

        // Simulate Redis excluded key get/set
        excludedState.set("[]");
        when(ops.get(eq(EXCLUDED_KEY))).thenAnswer(inv -> excludedState.get());
        doAnswer(inv -> {
            excludedState.set(inv.getArgument(1, String.class));
            return null;
        }).when(ops).set(eq(EXCLUDED_KEY), anyString(), any(Duration.class));
    }

    // ── Test 1: basic batch replace ──

    @Test
    void shouldReplaceMultipleAndKeepDraftCount() {
        // Draft: 3 candidates in steady tier (1001, 1002, 1003)
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L, 1002L, 1003L)));

        // Workspace: replacement candidates 2001, 2002 in steady tier
        CandidateWorkspaceVO workspace = workspaceWithSteadyCandidates(2001L, 2002L);

        List<DraftReplacementRequest> replacements = new ArrayList<>();
        replacements.add(req(1001L, 2001L));
        replacements.add(req(1002L, 2002L));

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace, null);

        assertEquals("batch_replace", result.get("action"));
        assertEquals(2, result.get("requested"));
        assertTrue((Boolean) result.get("ok"));
        assertEquals(2, result.get("replaced"));
        assertEquals(0, result.get("failed"));
        assertEquals(3, result.get("draftCount")); // count unchanged: 3 removed 2 added 2 = 3
        assertNotNull(result.get("items"));
    }

    // ── Test 2: duplicate candidate ──

    @Test
    void shouldNotRemoveOldWhenAddIsDuplicate() {
        // Draft: candidate 1001 already in draft
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L, 1002L)));

        CandidateWorkspaceVO workspace = emptyWorkspace(); // workspace not needed for duplicate check
        List<DraftReplacementRequest> replacements = new ArrayList<>();
        // Replace 1002 with 1001 (1001 is already in draft)
        replacements.add(req(1002L, 1001L));

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace, null);

        assertFalse((Boolean) result.get("ok"));
        assertEquals(0, result.get("replaced"));
        assertEquals(1, result.get("failed"));

        @SuppressWarnings("unchecked")
        List<DraftReplacementItemResultVO> items = (List<DraftReplacementItemResultVO>) result.get("items");
        assertEquals(1, items.size());
        assertFalse(items.get(0).isOk());
        assertEquals("duplicate_candidate", items.get(0).getError());

        // Verify 1002 was NOT removed — draft still has both candidates
        DraftVO draft = JSON.parseObject(draftState.get(), DraftVO.class);
        assertEquals(2, countDraft(draft));
    }

    // ── Test 3: replacement not found ──

    @Test
    void shouldNotRemoveOldWhenAddNotFound() {
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L)));

        // Workspace does NOT contain 9999, and externalResolver returns null
        CandidateWorkspaceVO workspace = emptyWorkspace();
        List<DraftReplacementRequest> replacements = new ArrayList<>();
        replacements.add(req(1001L, 9999L));

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace,
            pid -> null); // externalCandidateResolver returns null

        assertFalse((Boolean) result.get("ok"));
        assertEquals(0, result.get("replaced"));
        assertEquals(1, result.get("failed"));

        @SuppressWarnings("unchecked")
        List<DraftReplacementItemResultVO> items = (List<DraftReplacementItemResultVO>) result.get("items");
        assertEquals("not_found", items.get(0).getError());

        // Verify 1001 was NOT removed — draft still has the candidate
        DraftVO draft = JSON.parseObject(draftState.get(), DraftVO.class);
        assertEquals(1, countDraft(draft));
    }

    // ── Test 4: removeProgramId not in draft ──

    @Test
    void shouldSkipWhenRemoveNotInDraft() {
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L)));

        CandidateWorkspaceVO workspace = emptyWorkspace();
        List<DraftReplacementRequest> replacements = new ArrayList<>();
        replacements.add(req(8888L, 2001L)); // 8888 not in draft

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace, null);

        assertFalse((Boolean) result.get("ok"));
        assertEquals(0, result.get("replaced"));
        assertEquals(1, result.get("failed"));

        @SuppressWarnings("unchecked")
        List<DraftReplacementItemResultVO> items = (List<DraftReplacementItemResultVO>) result.get("items");
        assertEquals("not_in_draft", items.get(0).getError());
    }

    // ── Test 5: partial success ──

    @Test
    void shouldReturnOkTrueWhenPartiallySucceeded() {
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L, 1002L, 1003L)));

        // Workspace has 2001 but NOT 2002
        CandidateWorkspaceVO workspace = workspaceWithSteadyCandidates(2001L);

        List<DraftReplacementRequest> replacements = new ArrayList<>();
        replacements.add(req(1001L, 2001L)); // succeeds
        replacements.add(req(1002L, 2002L)); // fails: 2002 not in workspace, no fallback
        replacements.add(req(1003L, 1001L)); // fails: 1001 was just added (duplicate after first replace)

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace,
            pid -> null);

        assertTrue((Boolean) result.get("ok")); // at least one succeeded
        assertEquals(1, result.get("replaced"));
        assertEquals(2, result.get("failed"));
    }

    // ── Test 6: all failed ──

    @Test
    void shouldReturnOkFalseWhenAllFailed() {
        draftState.set(JSON.toJSONString(draftWithSteadyCandidates(1001L, 1002L)));

        CandidateWorkspaceVO workspace = emptyWorkspace(); // no replacements available
        List<DraftReplacementRequest> replacements = new ArrayList<>();
        replacements.add(req(1001L, 9999L)); // not found
        replacements.add(req(1002L, 1001L)); // duplicate

        Map<String, Object> result = service.batchReplace(USER_ID, replacements, workspace,
            pid -> null);

        assertFalse((Boolean) result.get("ok"));
        assertEquals(0, result.get("replaced"));
        assertEquals(2, result.get("failed"));
    }

    // ── helpers ──

    private static DraftReplacementRequest req(Long removeId, Long addId) {
        DraftReplacementRequest r = new DraftReplacementRequest();
        r.setRemoveProgramId(removeId);
        r.setAddProgramId(addId);
        return r;
    }

    private static DraftVO draftWithSteadyCandidates(Long... programIds) {
        DraftVO draft = new DraftVO();
        List<TierCandidates> tiers = new ArrayList<>(3);

        TierCandidates reach = newTier("reach", "冲刺档", 3);
        reach.setCandidates(new ArrayList<>());
        tiers.add(reach);

        TierCandidates steady = newTier("steady", "稳妥档", 4);
        List<CandidateCardVO> steadyCandidates = new ArrayList<>();
        for (Long pid : programIds) {
            steadyCandidates.add(candidate(pid, "School-" + pid));
        }
        steady.setCandidates(steadyCandidates);
        steady.setInsufficient(steadyCandidates.size() < 4);
        tiers.add(steady);

        TierCandidates safe = newTier("safe", "保底档", 3);
        safe.setCandidates(new ArrayList<>());
        safe.setInsufficient(true);
        safe.setInsufficientReason("点击「生成 AI 推荐草稿」开始");
        tiers.add(safe);

        draft.setTiers(tiers);
        draft.setRemovedCandidates(new ArrayList<>());
        draft.setBlockedCandidates(new ArrayList<>());
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
        fact.setProgramName("Electronic Information");
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
            candidates.add(candidate(pid, "New-School-" + pid));
        }
        wsSteady.setCandidates(candidates);

        ws.setTiers(new ArrayList<>(List.of(wsSteady)));
        return ws;
    }

    private static CandidateWorkspaceVO emptyWorkspace() {
        CandidateWorkspaceVO ws = new CandidateWorkspaceVO();
        ws.setWorkspaceId("ws-1");
        ws.setUserId(USER_ID);
        ws.setTiers(new ArrayList<>());
        return ws;
    }

    private static int countDraft(DraftVO draft) {
        if (draft.getTiers() == null) return 0;
        return draft.getTiers().stream()
            .mapToInt(t -> t.getCandidates() != null ? t.getCandidates().size() : 0)
            .sum();
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = DraftMutationServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
