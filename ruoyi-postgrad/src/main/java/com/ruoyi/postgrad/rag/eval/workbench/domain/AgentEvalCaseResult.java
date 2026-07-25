package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单个 AGENT_E2E 用例的评测结果（规则 + Judge），与排名表 rag_eval_case_result 分离。 */
public class AgentEvalCaseResult {
    private Long id;
    private String runId;
    private Long caseId;
    private String agentRunId;
    private Boolean passed;
    private Boolean agentSuccess;
    private Boolean routeAccuracy;
    private Boolean requiredToolHit;
    private Boolean forbiddenToolCalled;
    private BigDecimal toolSuccessRate;
    private Boolean citationPresent;
    private Boolean expectedAbstention;
    private Boolean traceComplete;
    private BigDecimal faithfulness;
    private BigDecimal answerRelevance;
    private BigDecimal citationCorrectness;
    private BigDecimal judgeVariance;
    private Boolean judgePassed;
    private String ruleResultJson;
    private String judgeResultJson;
    private String failureReasonsJson;
    private Long latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private String resultChecksum;
    private String technicalErrorCode;
    private String technicalErrorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public Boolean getAgentSuccess() { return agentSuccess; }
    public void setAgentSuccess(Boolean agentSuccess) { this.agentSuccess = agentSuccess; }
    public Boolean getRouteAccuracy() { return routeAccuracy; }
    public void setRouteAccuracy(Boolean routeAccuracy) { this.routeAccuracy = routeAccuracy; }
    public Boolean getRequiredToolHit() { return requiredToolHit; }
    public void setRequiredToolHit(Boolean requiredToolHit) { this.requiredToolHit = requiredToolHit; }
    public Boolean getForbiddenToolCalled() { return forbiddenToolCalled; }
    public void setForbiddenToolCalled(Boolean v) { this.forbiddenToolCalled = v; }
    public BigDecimal getToolSuccessRate() { return toolSuccessRate; }
    public void setToolSuccessRate(BigDecimal v) { this.toolSuccessRate = v; }
    public Boolean getCitationPresent() { return citationPresent; }
    public void setCitationPresent(Boolean v) { this.citationPresent = v; }
    public Boolean getExpectedAbstention() { return expectedAbstention; }
    public void setExpectedAbstention(Boolean v) { this.expectedAbstention = v; }
    public Boolean getTraceComplete() { return traceComplete; }
    public void setTraceComplete(Boolean v) { this.traceComplete = v; }
    public BigDecimal getFaithfulness() { return faithfulness; }
    public void setFaithfulness(BigDecimal v) { this.faithfulness = v; }
    public BigDecimal getAnswerRelevance() { return answerRelevance; }
    public void setAnswerRelevance(BigDecimal v) { this.answerRelevance = v; }
    public BigDecimal getCitationCorrectness() { return citationCorrectness; }
    public void setCitationCorrectness(BigDecimal v) { this.citationCorrectness = v; }
    public BigDecimal getJudgeVariance() { return judgeVariance; }
    public void setJudgeVariance(BigDecimal v) { this.judgeVariance = v; }
    public Boolean getJudgePassed() { return judgePassed; }
    public void setJudgePassed(Boolean judgePassed) { this.judgePassed = judgePassed; }
    public String getRuleResultJson() { return ruleResultJson; }
    public void setRuleResultJson(String v) { this.ruleResultJson = v; }
    public String getJudgeResultJson() { return judgeResultJson; }
    public void setJudgeResultJson(String v) { this.judgeResultJson = v; }
    public String getFailureReasonsJson() { return failureReasonsJson; }
    public void setFailureReasonsJson(String v) { this.failureReasonsJson = v; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public String getResultChecksum() { return resultChecksum; }
    public void setResultChecksum(String v) { this.resultChecksum = v; }
    public String getTechnicalErrorCode() { return technicalErrorCode; }
    public void setTechnicalErrorCode(String v) { this.technicalErrorCode = v; }
    public String getTechnicalErrorSummary() { return technicalErrorSummary; }
    public void setTechnicalErrorSummary(String v) { this.technicalErrorSummary = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
