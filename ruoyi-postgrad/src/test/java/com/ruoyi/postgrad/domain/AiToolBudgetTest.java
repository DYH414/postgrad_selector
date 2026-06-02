package com.ruoyi.postgrad.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiToolBudgetTest {
    @Test
    void budgetStopsAfterConfiguredToolLimits() {
        AiToolBudget budget = AiToolBudget.reportDefaults();
        for (int i = 0; i < 12; i++) {
            assertTrue(budget.tryUse("getProgramDetail", 100));
        }
        assertFalse(budget.tryUse("getProgramDetail", 100));
        assertTrue(budget.isExplorationLimited());
    }

    @Test
    void budgetStopsAfterTokenLimit() {
        AiToolBudget budget = new AiToolBudget(20, 12, 3, 5, 200);
        assertTrue(budget.tryUse("searchPrograms", 150));
        assertFalse(budget.tryUse("searchPrograms", 60));
        assertTrue(budget.isExplorationLimited());
    }
}
