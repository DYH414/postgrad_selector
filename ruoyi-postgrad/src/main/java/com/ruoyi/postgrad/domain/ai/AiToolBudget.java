package com.ruoyi.postgrad.domain.ai;

public class AiToolBudget {
    private final int maxTotalCalls;
    private final int maxDetailCalls;
    private final int maxExpandCalls;
    private final int maxResultTokens;
    private final int maxSearchCalls;
    private final int maxBookmarkCalls;

    private int totalCalls;
    private int detailCalls;
    private int expandCalls;
    private int searchCalls;
    private int bookmarkCalls;
    private int resultTokens;
    private boolean explorationLimited;

    public AiToolBudget(int maxTotalCalls, int maxDetailCalls, int maxExpandCalls,
        int maxResultTokens,
        int maxSearchCalls, int maxBookmarkCalls) {
        this.maxTotalCalls = maxTotalCalls;
        this.maxDetailCalls = maxDetailCalls;
        this.maxExpandCalls = maxExpandCalls;
        this.maxResultTokens = maxResultTokens;
        this.maxSearchCalls = maxSearchCalls;
        this.maxBookmarkCalls = maxBookmarkCalls;
    }

    public static AiToolBudget reportDefaults() {
        return new AiToolBudget(20, 12, 3, 12000, 10, 20);
    }

    public static AiToolBudget chatTurnDefaults() {
        // totalCalls=6 (only counts query tools), detailCalls=1, searchCalls=3, bookmarkCalls=10
        return new AiToolBudget(6, 1, 2, 3000, 3, 10);
    }

    public boolean tryUse(String toolName, int estimatedResultTokens) {
        boolean isBookmarkTool = "addToReport".equals(toolName) || "removeFromReport".equals(toolName);

        // 查询工具受总预算限制；写入工具（书签）不占查询配额
        if (!isBookmarkTool) {
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
        if ("addToReport".equals(toolName) && bookmarkCalls + 1 > maxBookmarkCalls) {
            explorationLimited = true;
            return false;
        }
        if ("expandCandidatePool".equals(toolName) && expandCalls + 1 > maxExpandCalls) {
            explorationLimited = true;
            return false;
        }
        if (!isBookmarkTool) {
            totalCalls++;
            resultTokens += Math.max(0, estimatedResultTokens);
        }
        if ("getProgramDetail".equals(toolName)) detailCalls++;
        if ("searchPrograms".equals(toolName)) searchCalls++;
        if ("addToReport".equals(toolName)) bookmarkCalls++;
        if ("expandCandidatePool".equals(toolName)) expandCalls++;
        return true;
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }
}
