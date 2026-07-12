package com.ruoyi.postgrad.domain.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.domain.ai.AiToolBudget.BudgetGroup;
import org.junit.jupiter.api.Test;

class AiToolBudgetTest {
    @Test
    void fourthSearchAndSecondWriteAreRejected() {
        AiToolBudget budget = AiToolBudget.chatTurnDefaults();
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertFalse(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertTrue(budget.isExplorationLimited());

        AiToolBudget writeBudget = AiToolBudget.chatTurnDefaults();
        assertTrue(writeBudget.tryUse(BudgetGroup.WRITE, 100).allowed());
        assertFalse(writeBudget.tryUse(BudgetGroup.WRITE, 100).allowed());
    }
}
