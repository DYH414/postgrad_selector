package com.ruoyi.postgrad.recommend.tool.runtime;

import com.ruoyi.postgrad.domain.ai.AiToolBudget;

/** Immutable execution metadata for one Agent tool. */
public record AgentToolDefinition(
    String name,
    ToolCategory category,
    AiToolBudget.BudgetGroup budgetGroup,
    int estimatedResultTokens,
    boolean confirmationRequired,
    boolean concurrencySafe
) {
    public enum ToolCategory {
        READ, WRITE, USER_INTERACTION
    }
}
