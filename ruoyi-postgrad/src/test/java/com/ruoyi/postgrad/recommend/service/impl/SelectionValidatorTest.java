package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.SelectedItem;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;

/**
 * TDD: 5 层校验（AI 幻觉防线）
 *
 * <p>通过标准：</p>
 * <ul>
 *   <li>L1 池外检测：AI 返回的 programId 不在候选池 → 拦截</li>
 *   <li>L2 去重：同一 programId 出现多次 → 只保留首次</li>
 *   <li>L3 数据不足：dataCompleteness=C → 拦截</li>
 *   <li>L4 档位上限：超过 limit → 溢出拦截</li>
 *   <li>L5 通过：正常候选正常通过</li>
 * </ul>
 */
class SelectionValidatorTest {

    private SelectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SelectionValidator();
    }

    // ═══════════ L1: 池外检测（AI 幻觉拦截） ═══════════

    @Test
    void shouldBlockProgramNotInPool() {
        List<SelectedItem> selected = listOf(item(9999L, "good reason"));
        List<CandidateCardVO> pool = listOf(card(1001L, "Real School", "A"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(0, result.getSelected().size(), "hallucinated program should be blocked");
        assertEquals(1, result.getBlocked().size());
        assertTrue(result.getBlocked().get(0).getBlockReason().contains("幻觉"),
            "block reason should mention hallucination");
    }

    @Test
    void shouldBlockNullProgramId() {
        List<SelectedItem> selected = listOf(item(null, "reason with no id"));
        List<CandidateCardVO> pool = listOf(card(1001L, "Real School", "A"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(0, result.getSelected().size());
        assertEquals(1, result.getBlocked().size());
    }

    // ═══════════ L2: 去重 ═══════════

    @Test
    void shouldBlockDuplicateProgramId() {
        List<SelectedItem> selected = listOf(
            item(1001L, "first pick"),
            item(1001L, "duplicate pick")
        );
        List<CandidateCardVO> pool = listOf(card(1001L, "Dupe School", "A"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(1, result.getSelected().size(), "duplicate should be blocked, only first survives");
        assertEquals(1, result.getBlocked().size());
        assertEquals(1001L, result.getSelected().get(0).getProgramId());
    }

    // ═══════════ L3: 数据完整度 C 拦截 ═══════════

    @Test
    void shouldBlockDataCompletenessC() {
        List<SelectedItem> selected = listOf(item(1001L, "seems good"));
        List<CandidateCardVO> pool = listOf(card(1001L, "Incomplete School", "C"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(0, result.getSelected().size(),
            "data completeness C should be blocked from recommendation");
        assertEquals(1, result.getBlocked().size());
        assertTrue(result.getBlocked().get(0).getBlockReason().contains("数据完整度"));
    }

    @Test
    void shouldAllowDataCompletenessA() {
        List<SelectedItem> selected = listOf(item(1001L, "good"));
        List<CandidateCardVO> pool = listOf(card(1001L, "Complete School", "A"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(1, result.getSelected().size());
        assertEquals(0, result.getBlocked().size());
    }

    @Test
    void shouldAllowDataCompletenessB() {
        List<SelectedItem> selected = listOf(item(1001L, "ok"));
        List<CandidateCardVO> pool = listOf(card(1001L, "Partly Complete", "B"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(1, result.getSelected().size());
    }

    // ═══════════ L4: 档位上限裁剪 ═══════════

    @Test
    void shouldClipReachTo3() {
        List<SelectedItem> selected = listOf(
            item(1001L, "1"), item(1002L, "2"), item(1003L, "3"), item(1004L, "4")
        );
        List<CandidateCardVO> pool = listOf(
            card(1001L, "S1", "A"), card(1002L, "S2", "A"),
            card(1003L, "S3", "A"), card(1004L, "S4", "A")
        );

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(3, result.getSelected().size(), "reach tier should clip to 3");
        assertEquals(1, result.getBlocked().size(), "4th candidate should overflow");
    }

    @Test
    void shouldClipSteadyTo4() {
        List<SelectedItem> all = new ArrayList<>();
        List<CandidateCardVO> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(item((long) (1001 + i), "r" + i));
            pool.add(card((long) (1001 + i), "S" + i, "A"));
        }

        AiSelectionResult result = validator.validate("steady", all, pool);

        assertEquals(4, result.getSelected().size(), "steady tier clip");
        assertEquals(2, result.getBlocked().size());
    }

    @Test
    void shouldClipSafeTo3() {
        List<SelectedItem> selected = listOf(
            item(1001L, "1"), item(1002L, "2"), item(1003L, "3"), item(1004L, "4")
        );
        List<CandidateCardVO> pool = listOf(
            card(1001L, "S1", "A"), card(1002L, "S2", "A"),
            card(1003L, "S3", "A"), card(1004L, "S4", "A")
        );

        AiSelectionResult result = validator.validate("safe", selected, pool);

        assertEquals(3, result.getSelected().size(), "safe tier clip");
        assertEquals(1, result.getBlocked().size());
    }

    @Test
    void unknownTierShouldDefaultTo3() {
        List<SelectedItem> selected = listOf(
            item(1001L, "1"), item(1002L, "2"), item(1003L, "3"), item(1004L, "4")
        );
        List<CandidateCardVO> pool = listOf(
            card(1001L, "S1", "A"), card(1002L, "S2", "A"),
            card(1003L, "S3", "A"), card(1004L, "S4", "A")
        );

        AiSelectionResult result = validator.validate("unknown_tier", selected, pool);

        assertEquals(3, result.getSelected().size(), "unknown tier defaults to 3");
    }

    // ═══════════ L5: 综合场景 ═══════════

    @Test
    void emptySelectedShouldReturnEmpty() {
        AiSelectionResult result = validator.validate("reach",
            Collections.emptyList(), listOf(card(1001L, "S", "A")));

        assertEquals(0, result.getSelected().size());
        assertEquals(0, result.getBlocked().size());
    }

    @Test
    void allBlockedShouldReturnEmptyPassed() {
        // 幻觉 + C 数据 + 重复 → 全拦截
        List<SelectedItem> selected = listOf(
            item(9999L, "hallucination"),
            item(1001L, "c-grade data"),
            item(1001L, "duplicate c-grade")
        );
        List<CandidateCardVO> pool = listOf(card(1001L, "Bad School", "C"));

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(0, result.getSelected().size(), "all should be blocked");
        assertEquals(3, result.getBlocked().size());
    }

    @Test
    void mixedScenarioShouldPassSomeBlockSome() {
        List<SelectedItem> selected = listOf(
            item(1001L, "good one"),
            item(9999L, "hallucination"),
            item(1002L, "another good"),
            item(1003L, "c-grade")
        );
        List<CandidateCardVO> pool = listOf(
            card(1001L, "Good A", "A"),
            card(1002L, "Good B", "B"),
            card(1003L, "Bad C", "C")
        );

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(2, result.getSelected().size(),
            "should pass only valid candidates (A+B), block hallucination+C");
        assertEquals(2, result.getBlocked().size());
    }

    @Test
    void poolWithDuplicateIdsShouldKeepFirst() {
        List<SelectedItem> selected = listOf(item(1001L, "test"));
        // pool has duplicate programId — toMap merge keeps first
        CandidateCardVO first = card(1001L, "First School", "A");
        CandidateCardVO second = card(1001L, "Second School", "C");
        List<CandidateCardVO> pool = listOf(first, second);

        AiSelectionResult result = validator.validate("reach", selected, pool);

        assertEquals(1, result.getSelected().size(),
            "first entry in pool (completeness A) should be used");
    }

    // ═══════════ Helpers ═══════════

    private static SelectedItem item(Long programId, String reason) {
        SelectedItem item = new SelectedItem();
        item.setProgramId(programId);
        item.setReason(reason);
        item.setRisks(Collections.emptyList());
        item.setPros(Collections.emptyList());
        item.setCons(Collections.emptyList());
        return item;
    }

    private static CandidateCardVO card(Long programId, String schoolName, String completeness) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolName(schoolName);
        fact.setDataCompleteness(completeness);
        return CandidateCardVO.fromFact(fact);
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        return new ArrayList<>(List.of(items));
    }
}
