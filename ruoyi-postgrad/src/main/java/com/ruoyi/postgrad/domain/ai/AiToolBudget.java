package com.ruoyi.postgrad.domain.ai;

import java.util.EnumMap;
import java.util.Map;

/** Per-run limits for governed Agent tool execution. */
public class AiToolBudget {

    public enum BudgetGroup {
        SEARCH, DETAIL, RAG, CONTEXT, WRITE, OTHER
    }

    public record BudgetDecision(
        boolean allowed,
        String code,
        int totalReadCalls,
        int groupCalls,
        int estimatedResultTokens
    ) {
    }

    private static final String ALLOWED = "OK";
    private static final String EXCEEDED = "TOOL_BUDGET_EXCEEDED";

    private final int maxTotalReadCalls;
    private final int maxResultTokens;
    private final Map<BudgetGroup, Integer> groupLimits;
    private final Map<BudgetGroup, Integer> groupCalls = new EnumMap<>(BudgetGroup.class);

    private int totalReadCalls;
    private int resultTokens;
    private boolean explorationLimited;

    private AiToolBudget(int maxTotalReadCalls, int maxResultTokens,
        Map<BudgetGroup, Integer> groupLimits) {
        this.maxTotalReadCalls = maxTotalReadCalls;
        this.maxResultTokens = maxResultTokens;
        this.groupLimits = new EnumMap<>(groupLimits);
        for (BudgetGroup group : BudgetGroup.values()) {
            this.groupCalls.put(group, 0);
        }
    }

    public static AiToolBudget reportDefaults() {
        return new AiToolBudget(20, 12000, limits(10, 12, 6, 10, 20, 20));
    }

    public static AiToolBudget chatTurnDefaults() {
        return new AiToolBudget(6, 3000, limits(3, 2, 2, 3, 1, 1));
    }

    public synchronized BudgetDecision tryUse(BudgetGroup group, int estimatedResultTokens) {
        BudgetGroup effectiveGroup = group == null ? BudgetGroup.OTHER : group;
        int tokenCost = Math.max(0, estimatedResultTokens);
        int currentGroupCalls = groupCalls.get(effectiveGroup);
        int groupLimit = groupLimits.getOrDefault(effectiveGroup, 0);
        boolean readTool = effectiveGroup != BudgetGroup.WRITE;

        if (currentGroupCalls + 1 > groupLimit
            || (readTool && totalReadCalls + 1 > maxTotalReadCalls)
            || resultTokens + tokenCost > maxResultTokens) {
            explorationLimited = true;
            return new BudgetDecision(false, EXCEEDED, totalReadCalls, currentGroupCalls, resultTokens);
        }

        groupCalls.put(effectiveGroup, currentGroupCalls + 1);
        if (readTool) {
            totalReadCalls++;
        }
        resultTokens += tokenCost;
        return new BudgetDecision(true, ALLOWED, totalReadCalls, currentGroupCalls + 1, resultTokens);
    }

    public synchronized boolean isExplorationLimited() {
        return explorationLimited;
    }

    private static Map<BudgetGroup, Integer> limits(int search, int detail, int rag,
        int context, int write, int other) {
        Map<BudgetGroup, Integer> limits = new EnumMap<>(BudgetGroup.class);
        limits.put(BudgetGroup.SEARCH, search);
        limits.put(BudgetGroup.DETAIL, detail);
        limits.put(BudgetGroup.RAG, rag);
        limits.put(BudgetGroup.CONTEXT, context);
        limits.put(BudgetGroup.WRITE, write);
        limits.put(BudgetGroup.OTHER, other);
        return limits;
    }
}
