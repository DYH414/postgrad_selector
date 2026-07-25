package com.ruoyi.postgrad.recommend.domain;

/**
 * 一次隔离只读 Agent 评测调用的终态结果。
 * status ∈ {SUCCEEDED, FAILED, UNSUPPORTED_SIDE_EFFECT, TIMED_OUT}。
 * 错误即数据：任何失败都归一化为带 status 的结果，绝不抛异常炸掉扇出。
 */
public record EvaluationAgentInvocationResult(
    String agentRunId,
    String status,
    String route,
    String routeReason,
    String answer,
    long durationMs,
    boolean draftChanged,
    String errorCode,
    String errorSummary
) {
    public static EvaluationAgentInvocationResult unsupportedSideEffect(
        String agentRunId, String route, String routeReason, long durationMs) {
        return new EvaluationAgentInvocationResult(agentRunId, "UNSUPPORTED_SIDE_EFFECT",
            route, routeReason, null, durationMs, false,
            "UNSUPPORTED_SIDE_EFFECT", "写路由/写工具在只读评测中被拒绝");
    }

    public static EvaluationAgentInvocationResult failed(
        String agentRunId, String route, long durationMs, String errorSummary) {
        return new EvaluationAgentInvocationResult(agentRunId, "FAILED",
            route, null, null, durationMs, false, "AGENT_INVOCATION_FAILED", errorSummary);
    }
}
