package com.ruoyi.postgrad.domain;

public class AiToolBudget {
    private final int maxTotalCalls;
    private final int maxDetailCalls;
    private final int maxExpandCalls;
    private final int maxVerificationCalls;
    private final int maxResultTokens;

    private int totalCalls;
    private int detailCalls;
    private int expandCalls;
    private int verificationCalls;
    private int resultTokens;
    private boolean explorationLimited;

    public AiToolBudget(int maxTotalCalls, int maxDetailCalls, int maxExpandCalls,
        int maxVerificationCalls, int maxResultTokens) {
        this.maxTotalCalls = maxTotalCalls;
        this.maxDetailCalls = maxDetailCalls;
        this.maxExpandCalls = maxExpandCalls;
        this.maxVerificationCalls = maxVerificationCalls;
        this.maxResultTokens = maxResultTokens;
    }

    public static AiToolBudget reportDefaults() {
        return new AiToolBudget(20, 12, 3, 5, 12000);
    }

    public static AiToolBudget chatTurnDefaults() {
        return new AiToolBudget(8, 5, 2, 3, 5000);
    }

    public boolean tryUse(String toolName, int estimatedResultTokens) {
        if (totalCalls + 1 > maxTotalCalls || resultTokens + estimatedResultTokens > maxResultTokens) {
            explorationLimited = true;
            return false;
        }
        if ("getProgramDetail".equals(toolName) && detailCalls + 1 > maxDetailCalls) {
            explorationLimited = true;
            return false;
        }
        if ("expandCandidatePool".equals(toolName) && expandCalls + 1 > maxExpandCalls) {
            explorationLimited = true;
            return false;
        }
        if ("verifyOfficialInfo".equals(toolName) && verificationCalls + 1 > maxVerificationCalls) {
            explorationLimited = true;
            return false;
        }
        totalCalls++;
        resultTokens += Math.max(0, estimatedResultTokens);
        if ("getProgramDetail".equals(toolName)) detailCalls++;
        if ("expandCandidatePool".equals(toolName)) expandCalls++;
        if ("verifyOfficialInfo".equals(toolName)) verificationCalls++;
        return true;
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }
}
