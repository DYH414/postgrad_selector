package com.ruoyi.postgrad.recommend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * TDD 测试：inferTier / classifyTier 边界行为 (H7)
 *
 * <p>通过标准：</p>
 * <ul>
 *   <li>gap >= -15 且 <= 5 → "reach"</li>
 *   <li>gap >= 6 且 <= 14 → "steady"</li>
 *   <li>gap >= 15 且 canBeSafe → "safe"</li>
 *   <li>gap < -15 → inferTier 返回 null（不应推荐）</li>
 * </ul>
 */
class SchoolFactTierTest {

    @Test
    void inferTierShouldReturnNullWhenGapLessThanMinus15() {
        // H7 bug: inferTier 原来返回 "reach" 而非 null
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(-20);
        fact.setCanBeSafe(false);

        assertNull(fact.inferTier(),
            "H7: gap < -15 should return null, not 'reach'");
    }

    @Test
    void inferTierShouldReturnNullWhenGapMinus30() {
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(-30);
        fact.setCanBeSafe(false);

        assertNull(fact.inferTier(),
            "H7: gap=-30 should return null (far below avg)");
    }

    @Test
    void inferTierShouldReturnReachWhenGapMinus15() {
        // -15 是 reach 边界
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(-15);
        fact.setCanBeSafe(false);

        assertEquals("reach", fact.inferTier(),
            "gap=-15 should classify as reach (boundary value)");
    }

    @Test
    void inferTierShouldReturnReachWhenGapMinus14() {
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(-14);
        fact.setCanBeSafe(false);

        assertEquals("reach", fact.inferTier());
    }

    @Test
    void inferTierShouldReturnReachWhenGap0() {
        // 分数持平 = reach (匹配但不是保底)
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(0);
        fact.setCanBeSafe(false);

        assertEquals("reach", fact.inferTier());
    }

    @Test
    void inferTierShouldReturnReachWhenGap5() {
        // +5 是 reach 上界
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(5);
        fact.setCanBeSafe(false);

        assertEquals("reach", fact.inferTier(),
            "gap=5 should classify as reach (upper boundary)");
    }

    @Test
    void inferTierShouldReturnSteadyWhenGap6() {
        // +6 切换到 steady
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(6);
        fact.setCanBeSafe(false);

        assertEquals("steady", fact.inferTier(),
            "gap=6 should classify as steady (lower boundary)");
    }

    @Test
    void inferTierShouldReturnSteadyWhenGap14() {
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(14);
        fact.setCanBeSafe(false);

        assertEquals("steady", fact.inferTier(),
            "gap=14 should classify as steady (upper boundary)");
    }

    @Test
    void inferTierShouldReturnSafeWhenGap15AndCanBeSafe() {
        // +15 且 canBeSafe → safe
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(15);
        fact.setCanBeSafe(true);

        assertEquals("safe", fact.inferTier(),
            "gap=15 and canBeSafe should classify as safe");
    }

    @Test
    void inferTierShouldReturnSteadyWhenGap15ButNotSafe() {
        // +15 但不满足保底条件 → 降级到 steady
        SchoolFact fact = new SchoolFact();
        fact.setScoreGap(15);
        fact.setCanBeSafe(false);

        assertEquals("steady", fact.inferTier(),
            "gap=15 and NOT canBeSafe should downgrade to steady");
    }

    @Test
    void classifyTierShouldReturnNullForGapMinus20() {
        // 静态方法不应变
        assertNull(SchoolFact.classifyTier(-20, false));
    }
}
