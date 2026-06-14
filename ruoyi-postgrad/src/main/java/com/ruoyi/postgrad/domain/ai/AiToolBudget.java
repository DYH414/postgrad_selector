package com.ruoyi.postgrad.domain.ai;

public class AiToolBudget {
    private final int maxTotalCalls;
    private final int maxDetailCalls;
    private final int maxExpandCalls;
    private final int maxResultTokens;
    private final int maxSearchCalls;
    private final int maxWriteCalls;

    private int totalCalls;
    private int detailCalls;
    private int expandCalls;
    private int searchCalls;
    private int writeCalls;
    private int resultTokens;
    private boolean explorationLimited;

    public AiToolBudget(int maxTotalCalls, int maxDetailCalls, int maxExpandCalls,
        int maxResultTokens,
        int maxSearchCalls, int maxWriteCalls) {
        this.maxTotalCalls = maxTotalCalls;
        this.maxDetailCalls = maxDetailCalls;
        this.maxExpandCalls = maxExpandCalls;
        this.maxResultTokens = maxResultTokens;
        this.maxSearchCalls = maxSearchCalls;
        this.maxWriteCalls = maxWriteCalls;
    }

    public static AiToolBudget reportDefaults() {
        return new AiToolBudget(20, 12, 3, 12000, 10, 20);
    }

    public static AiToolBudget chatTurnDefaults() {
        // totalCalls=6 (only counts query tools), detailCalls=1, searchCalls=3, writeCalls=10
        return new AiToolBudget(6, 1, 2, 3000, 3, 10);
    }

    public boolean tryUse(String toolName, int estimatedResultTokens) {
        boolean isWriteTool = isWriteTool(toolName);

        // 查询工具受总预算限制；写入工具不占查询配额
        if (!isWriteTool) {
            if (totalCalls + 1 > maxTotalCalls || resultTokens + estimatedResultTokens > maxResultTokens) {
                explorationLimited = true;
                return false;
            }
        }
        if ("getProgramDetail".equals(toolName) && detailCalls + 1 > maxDetailCalls) {
            explorationLimited = true;
            return false;
        }
        if ("searchPrograms".equals(toolName) && searchCalls + 1 > maxSearchCalls) {
            explorationLimited = true;
            return false;
        }
        if (isWriteTool && writeCalls + 1 > maxWriteCalls) {
            explorationLimited = true;
            return false;
        }
        if ("expandCandidatePool".equals(toolName) && expandCalls + 1 > maxExpandCalls) {
            explorationLimited = true;
            return false;
        }
        if (!isWriteTool) {
            totalCalls++;
            resultTokens += Math.max(0, estimatedResultTokens);
        }
        if ("getProgramDetail".equals(toolName)) detailCalls++;
        if ("searchPrograms".equals(toolName)) searchCalls++;
        if (isWriteTool) writeCalls++;
        if ("expandCandidatePool".equals(toolName)) expandCalls++;
        return true;
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }

    private boolean isWriteTool(String toolName) {
        return "removeDraftCandidate".equals(toolName)
            || "replaceDraftCandidate".equals(toolName)
            || "addBackDraftCandidate".equals(toolName);
    }
}
