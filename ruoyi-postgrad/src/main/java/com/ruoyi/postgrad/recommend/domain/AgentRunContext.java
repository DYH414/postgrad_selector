package com.ruoyi.postgrad.recommend.domain;

import java.util.UUID;

/** Identifies one accepted AI chat run across asynchronous boundaries. */
public final class AgentRunContext {

    private final Long userId;
    private final String runId;

    private AgentRunContext(Long userId, String runId) {
        this.userId = userId;
        this.runId = runId;
    }

    public static AgentRunContext start(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return new AgentRunContext(userId, UUID.randomUUID().toString());
    }

    public Long getUserId() {
        return userId;
    }

    public String getRunId() {
        return runId;
    }
}
