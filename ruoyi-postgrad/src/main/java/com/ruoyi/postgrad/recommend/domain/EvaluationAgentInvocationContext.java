package com.ruoyi.postgrad.recommend.domain;

/**
 * 隔离只读 Agent 评测调用的输入上下文。
 * 与生产 chat() 的本质区别：不加 Redis 锁、不读写聊天历史、不持久化会话。
 */
public record EvaluationAgentInvocationContext(
    String evaluationRunId,
    Long caseId,
    String invocationKey,
    Long subjectUserId,
    Long agentConfigVersionId,
    String query,
    String businessPromptText,
    boolean evaluationMode,
    boolean persistConversation,
    boolean readOnly
) {
    /** 标准评测上下文：evaluationMode=true, persistConversation=false, readOnly=true。 */
    public static EvaluationAgentInvocationContext of(
        String evaluationRunId, Long caseId, String invocationKey, Long subjectUserId,
        Long agentConfigVersionId, String query, String businessPromptText) {
        return new EvaluationAgentInvocationContext(evaluationRunId, caseId, invocationKey,
            subjectUserId, agentConfigVersionId, query, businessPromptText, true, false, true);
    }
}
