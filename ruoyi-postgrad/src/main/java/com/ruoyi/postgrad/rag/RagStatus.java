package com.ruoyi.postgrad.rag;

import java.time.LocalDateTime;

public class RagStatus {

    private boolean indexed;
    private boolean enabled;
    private int documentCount;
    private int chunkCount;
    private LocalDateTime lastIndexedAt;
    private String lastError;
    private String lastRunMode;
    private String backend;
    private String collectionName;
    private boolean reachable;
    private String activeCollection;
    private String latestRunId;
    private String latestRunStatus;
    private Integer buildProgressPercent;
    private String buildProgressStage;
    private String indexConsistency;
    private String indexConsistencyMessage;
    private Integer expectedDocumentCount;
    private Integer expectedChunkCount;
    private Long actualPointCount;

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public LocalDateTime getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(LocalDateTime lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getLastRunMode() {
        return lastRunMode;
    }

    public void setLastRunMode(String lastRunMode) {
        this.lastRunMode = lastRunMode;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public String getActiveCollection() {
        return activeCollection;
    }

    public void setActiveCollection(String activeCollection) {
        this.activeCollection = activeCollection;
    }

    public String getLatestRunId() {
        return latestRunId;
    }

    public void setLatestRunId(String latestRunId) {
        this.latestRunId = latestRunId;
    }

    public String getLatestRunStatus() {
        return latestRunStatus;
    }

    public void setLatestRunStatus(String latestRunStatus) {
        this.latestRunStatus = latestRunStatus;
    }

    public Integer getBuildProgressPercent() {
        return buildProgressPercent;
    }

    public void setBuildProgressPercent(Integer buildProgressPercent) {
        this.buildProgressPercent = buildProgressPercent;
    }

    public String getBuildProgressStage() {
        return buildProgressStage;
    }

    public void setBuildProgressStage(String buildProgressStage) {
        this.buildProgressStage = buildProgressStage;
    }

    public String getIndexConsistency() {
        return indexConsistency;
    }

    public void setIndexConsistency(String indexConsistency) {
        this.indexConsistency = indexConsistency;
    }

    public String getIndexConsistencyMessage() {
        return indexConsistencyMessage;
    }

    public void setIndexConsistencyMessage(String indexConsistencyMessage) {
        this.indexConsistencyMessage = indexConsistencyMessage;
    }

    public Integer getExpectedDocumentCount() {
        return expectedDocumentCount;
    }

    public void setExpectedDocumentCount(Integer expectedDocumentCount) {
        this.expectedDocumentCount = expectedDocumentCount;
    }

    public Integer getExpectedChunkCount() {
        return expectedChunkCount;
    }

    public void setExpectedChunkCount(Integer expectedChunkCount) {
        this.expectedChunkCount = expectedChunkCount;
    }

    public Long getActualPointCount() {
        return actualPointCount;
    }

    public void setActualPointCount(Long actualPointCount) {
        this.actualPointCount = actualPointCount;
    }

}
