package com.ruoyi.postgrad.recommend.domain;

import java.util.UUID;

import com.ruoyi.postgrad.domain.ai.AiToolBudget;

/** Identifies one accepted AI chat run across asynchronous boundaries. */
public final class AgentRunContext {

    private final Long userId;
    private final String runId;
    private final AiToolBudget toolBudget;

    private AgentRunContext(Long userId, String runId, AiToolBudget toolBudget) {
        this.userId = userId;
        this.runId = runId;
        this.toolBudget = toolBudget;
    }

    public static AgentRunContext start(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return new AgentRunContext(userId, UUID.randomUUID().toString(), AiToolBudget.chatTurnDefaults());
    }

    public Long getUserId() {
        return userId;
    }

    public String getRunId() {
        return runId;
    }

    public AiToolBudget getToolBudget() {
        return toolBudget;
    }
}
