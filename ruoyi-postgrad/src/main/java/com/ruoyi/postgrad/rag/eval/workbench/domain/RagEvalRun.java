package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.time.LocalDateTime;

public class RagEvalRun {
    private String runId;
    private Long datasetId;
    private String datasetChecksum;
    private String status;
    private String evaluationType;
    private String modesJson;
    private String parameterSnapshotJson;
    private String qdrantAlias;
    private String qdrantCollection;
    private String embeddingModel;
    private String rerankModel;
    private String gitCommit;
    private Integer totalCases;
    private Integer completedCases;
    private String aggregateMetricsJson;
    private String errorCode;
    private String errorSummary;
    private Boolean cancelRequested;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String evaluationType) { this.evaluationType = evaluationType; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getDatasetChecksum() { return datasetChecksum; }
    public void setDatasetChecksum(String datasetChecksum) { this.datasetChecksum = datasetChecksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModesJson() { return modesJson; }
    public void setModesJson(String modesJson) { this.modesJson = modesJson; }
    public String getParameterSnapshotJson() { return parameterSnapshotJson; }
    public void setParameterSnapshotJson(String parameterSnapshotJson) { this.parameterSnapshotJson = parameterSnapshotJson; }
    public String getQdrantAlias() { return qdrantAlias; }
    public void setQdrantAlias(String qdrantAlias) { this.qdrantAlias = qdrantAlias; }
    public String getQdrantCollection() { return qdrantCollection; }
    public void setQdrantCollection(String qdrantCollection) { this.qdrantCollection = qdrantCollection; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getRerankModel() { return rerankModel; }
    public void setRerankModel(String rerankModel) { this.rerankModel = rerankModel; }
    public String getGitCommit() { return gitCommit; }
    public void setGitCommit(String gitCommit) { this.gitCommit = gitCommit; }
    public Integer getTotalCases() { return totalCases; }
    public void setTotalCases(Integer totalCases) { this.totalCases = totalCases; }
    public Integer getCompletedCases() { return completedCases; }
    public void setCompletedCases(Integer completedCases) { this.completedCases = completedCases; }
    public String getAggregateMetricsJson() { return aggregateMetricsJson; }
    public void setAggregateMetricsJson(String aggregateMetricsJson) { this.aggregateMetricsJson = aggregateMetricsJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Boolean getCancelRequested() { return cancelRequested; }
    public void setCancelRequested(Boolean cancelRequested) { this.cancelRequested = cancelRequested; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
