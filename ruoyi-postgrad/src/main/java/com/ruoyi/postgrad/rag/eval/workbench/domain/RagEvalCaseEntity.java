package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.time.LocalDateTime;

public class RagEvalCaseEntity {
    private Long id;
    private Long datasetId;
    private String caseKey;
    private String queryText;
    private String category;
    private String difficulty;
    private String expectedMetadataJson;
    private String relevantConditionsJson;
    private String relevantSourcesJson;
    private Boolean noAnswer;
    private Boolean sourceDataAvailable;
    private Boolean expectedFallback;
    private String expectedRoute;
    private String agentSpecJson;
    private String tagsJson;
    private String reviewerNote;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getCaseKey() { return caseKey; }
    public void setCaseKey(String caseKey) { this.caseKey = caseKey; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getExpectedMetadataJson() { return expectedMetadataJson; }
    public void setExpectedMetadataJson(String value) { this.expectedMetadataJson = value; }
    public String getRelevantConditionsJson() { return relevantConditionsJson; }
    public void setRelevantConditionsJson(String value) { this.relevantConditionsJson = value; }
    public String getRelevantSourcesJson() { return relevantSourcesJson; }
    public void setRelevantSourcesJson(String value) { this.relevantSourcesJson = value; }
    public Boolean getNoAnswer() { return noAnswer; }
    public void setNoAnswer(Boolean noAnswer) { this.noAnswer = noAnswer; }
    public Boolean getSourceDataAvailable() { return sourceDataAvailable; }
    public void setSourceDataAvailable(Boolean sourceDataAvailable) { this.sourceDataAvailable = sourceDataAvailable; }
    public Boolean getExpectedFallback() { return expectedFallback; }
    public void setExpectedFallback(Boolean expectedFallback) { this.expectedFallback = expectedFallback; }
    public String getExpectedRoute() { return expectedRoute; }
    public void setExpectedRoute(String expectedRoute) { this.expectedRoute = expectedRoute; }
    public String getAgentSpecJson() { return agentSpecJson; }
    public void setAgentSpecJson(String agentSpecJson) { this.agentSpecJson = agentSpecJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
