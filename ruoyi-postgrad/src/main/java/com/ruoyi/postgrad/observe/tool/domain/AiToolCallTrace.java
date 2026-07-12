package com.ruoyi.postgrad.observe.tool.domain;

import java.time.LocalDateTime;

public class AiToolCallTrace {
    private Long id;
    private String callId;
    private String parentCallId;
    private String runId;
    private Long userId;
    private String operationKey;
    private String toolName;
    private String toolCategory;
    private String budgetGroup;
    private String status;
    private String resultCode;
    private Boolean retryable;
    private Boolean confirmationRequired;
    private String argumentsSummary;
    private String resultSummary;
    private Integer resultTokenEstimate;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmationExpiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getParentCallId() { return parentCallId; }
    public void setParentCallId(String parentCallId) { this.parentCallId = parentCallId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolCategory() { return toolCategory; }
    public void setToolCategory(String toolCategory) { this.toolCategory = toolCategory; }
    public String getBudgetGroup() { return budgetGroup; }
    public void setBudgetGroup(String budgetGroup) { this.budgetGroup = budgetGroup; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }
    public Boolean getConfirmationRequired() { return confirmationRequired; }
    public void setConfirmationRequired(Boolean confirmationRequired) { this.confirmationRequired = confirmationRequired; }
    public String getArgumentsSummary() { return argumentsSummary; }
    public void setArgumentsSummary(String argumentsSummary) { this.argumentsSummary = argumentsSummary; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Integer getResultTokenEstimate() { return resultTokenEstimate; }
    public void setResultTokenEstimate(Integer resultTokenEstimate) { this.resultTokenEstimate = resultTokenEstimate; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getConfirmationExpiresAt() { return confirmationExpiresAt; }
    public void setConfirmationExpiresAt(LocalDateTime confirmationExpiresAt) { this.confirmationExpiresAt = confirmationExpiresAt; }
}
