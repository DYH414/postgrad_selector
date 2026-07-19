package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.time.LocalDateTime;

public class RagEvalCaseResultEntity {
    private Long id;
    private String runId;
    private Long caseId;
    private String evalMode;
    private Boolean passed;
    private Integer firstRelevantRank;
    private Integer relevantInTopK;
    private Double reciprocalRank;
    private String effectiveFiltersJson;
    private String candidateSummaryJson;
    private String selectedEvidenceJson;
    private Long latencyMs;
    private String failureReason;
    private String technicalErrorCode;
    private String technicalErrorSummary;
    private String humanFailureCategory;
    private String reviewerNote;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getEvalMode() { return evalMode; }
    public void setEvalMode(String evalMode) { this.evalMode = evalMode; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public Integer getFirstRelevantRank() { return firstRelevantRank; }
    public void setFirstRelevantRank(Integer firstRelevantRank) { this.firstRelevantRank = firstRelevantRank; }
    public Integer getRelevantInTopK() { return relevantInTopK; }
    public void setRelevantInTopK(Integer relevantInTopK) { this.relevantInTopK = relevantInTopK; }
    public Double getReciprocalRank() { return reciprocalRank; }
    public void setReciprocalRank(Double reciprocalRank) { this.reciprocalRank = reciprocalRank; }
    public String getEffectiveFiltersJson() { return effectiveFiltersJson; }
    public void setEffectiveFiltersJson(String value) { this.effectiveFiltersJson = value; }
    public String getCandidateSummaryJson() { return candidateSummaryJson; }
    public void setCandidateSummaryJson(String value) { this.candidateSummaryJson = value; }
    public String getSelectedEvidenceJson() { return selectedEvidenceJson; }
    public void setSelectedEvidenceJson(String value) { this.selectedEvidenceJson = value; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getTechnicalErrorCode() { return technicalErrorCode; }
    public void setTechnicalErrorCode(String value) { this.technicalErrorCode = value; }
    public String getTechnicalErrorSummary() { return technicalErrorSummary; }
    public void setTechnicalErrorSummary(String value) { this.technicalErrorSummary = value; }
    public String getHumanFailureCategory() { return humanFailureCategory; }
    public void setHumanFailureCategory(String value) { this.humanFailureCategory = value; }
    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
