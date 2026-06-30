package com.ruoyi.postgrad.observe.domain;

public class RagQuickStatusVO {

    private boolean enabled;
    private boolean indexed;
    private int documentCount;
    private int chunkCount;
    private String lastIndexedAt;
    private String lastError;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
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

    public String getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(String lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
