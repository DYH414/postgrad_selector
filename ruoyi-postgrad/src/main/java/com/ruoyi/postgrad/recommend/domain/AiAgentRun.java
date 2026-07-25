package com.ruoyi.postgrad.recommend.domain;

import java.time.LocalDateTime;

/** 可观测的 Chat Agent 运行主记录。生产与评测调用共用同一 runId 传播机制。 */
public class AiAgentRun {
    private String runId;
    private String invocationKey;
    private String evaluationRunId;
    private Long caseId;
    private Long userId;
    private Boolean evaluationMode;
    private String queryText;
    private String route;
    private String routeReason;
    private String finalAnswer;
    private Long agentConfigVersionId;
    private String status; // RUNNING/SUCCEEDED/FAILED/UNSUPPORTED_SIDE_EFFECT/TIMED_OUT
    private Long durationMs;
    private Boolean draftChanged;
    private String errorCode;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getInvocationKey() { return invocationKey; }
    public void setInvocationKey(String invocationKey) { this.invocationKey = invocationKey; }
    public String getEvaluationRunId() { return evaluationRunId; }
    public void setEvaluationRunId(String evaluationRunId) { this.evaluationRunId = evaluationRunId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(Boolean evaluationMode) { this.evaluationMode = evaluationMode; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getRouteReason() { return routeReason; }
    public void setRouteReason(String routeReason) { this.routeReason = routeReason; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public Long getAgentConfigVersionId() { return agentConfigVersionId; }
    public void setAgentConfigVersionId(Long v) { this.agentConfigVersionId = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Boolean getDraftChanged() { return draftChanged; }
    public void setDraftChanged(Boolean draftChanged) { this.draftChanged = draftChanged; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
