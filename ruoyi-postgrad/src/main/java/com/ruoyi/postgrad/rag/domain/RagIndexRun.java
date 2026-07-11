package com.ruoyi.postgrad.rag.domain;

import java.time.LocalDateTime;

public class RagIndexRun {

    private static final long serialVersionUID = 1L;

    private String runId;
    private String status;
    private String candidateCollection;
    private String previousCollection;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Integer startYear;
    private Integer endYear;
    private Integer generalDocumentCount;
    private Integer databaseDocumentCount;
    private Integer chunkCount;
    private Integer indexedChunkCount;
    private String progressStage;
    private Integer progressPercent;
    private String errorCode;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCandidateCollection() { return candidateCollection; }
    public void setCandidateCollection(String candidateCollection) { this.candidateCollection = candidateCollection; }
    public String getPreviousCollection() { return previousCollection; }
    public void setPreviousCollection(String previousCollection) { this.previousCollection = previousCollection; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(Integer embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public Integer getStartYear() { return startYear; }
    public void setStartYear(Integer startYear) { this.startYear = startYear; }
    public Integer getEndYear() { return endYear; }
    public void setEndYear(Integer endYear) { this.endYear = endYear; }
    public Integer getGeneralDocumentCount() { return generalDocumentCount; }
    public void setGeneralDocumentCount(Integer generalDocumentCount) { this.generalDocumentCount = generalDocumentCount; }
    public Integer getDatabaseDocumentCount() { return databaseDocumentCount; }
    public void setDatabaseDocumentCount(Integer databaseDocumentCount) { this.databaseDocumentCount = databaseDocumentCount; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public Integer getIndexedChunkCount() { return indexedChunkCount; }
    public void setIndexedChunkCount(Integer indexedChunkCount) { this.indexedChunkCount = indexedChunkCount; }
    public String getProgressStage() { return progressStage; }
    public void setProgressStage(String progressStage) { this.progressStage = progressStage; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
