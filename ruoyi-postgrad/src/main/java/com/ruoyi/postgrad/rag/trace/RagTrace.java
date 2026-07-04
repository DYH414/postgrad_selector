package com.ruoyi.postgrad.rag.trace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RAG 检索 Trace —— 记录 AI 对话或检索测试中一次 RAG 调用的完整信息。
 */
public class RagTrace {

    public enum Outcome { SUCCESS, FAILURE }

    private String traceId;
    private String operationKey;
    private Long userId;
    private String query;
    private Outcome outcome;
    private String failureStage;
    private String errorSummary;

    private int retrievedCount;
    private int selectedCount;
    private List<String> topSources;

    private Long embeddingLatencyMs;
    private Long retrievalLatencyMs;
    private Long rerankLatencyMs;
    private long totalLatencyMs;

    private List<SelectedChunk> selectedChunks;
    private Instant createdAt;

    public RagTrace() {
        this.traceId = UUID.randomUUID().toString().substring(0, 8);
        this.createdAt = Instant.now();
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public int getRetrievedCount() { return retrievedCount; }
    public void setRetrievedCount(int retrievedCount) { this.retrievedCount = retrievedCount; }
    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public List<String> getTopSources() { return topSources; }
    public void setTopSources(List<String> topSources) { this.topSources = topSources; }
    public Long getEmbeddingLatencyMs() { return embeddingLatencyMs; }
    public void setEmbeddingLatencyMs(Long embeddingLatencyMs) { this.embeddingLatencyMs = embeddingLatencyMs; }
    public Long getRetrievalLatencyMs() { return retrievalLatencyMs; }
    public void setRetrievalLatencyMs(Long retrievalLatencyMs) { this.retrievalLatencyMs = retrievalLatencyMs; }
    public Long getRerankLatencyMs() { return rerankLatencyMs; }
    public void setRerankLatencyMs(Long rerankLatencyMs) { this.rerankLatencyMs = rerankLatencyMs; }
    public long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
    public List<SelectedChunk> getSelectedChunks() { return selectedChunks; }
    public void setSelectedChunks(List<SelectedChunk> selectedChunks) { this.selectedChunks = selectedChunks; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class SelectedChunk {
        private String sourceName;
        private String contentPreview;

        public SelectedChunk() {}
        public SelectedChunk(String sourceName, String contentPreview) {
            this.sourceName = sourceName;
            this.contentPreview = contentPreview;
        }
        public String getSourceName() { return sourceName; }
        public void setSourceName(String sourceName) { this.sourceName = sourceName; }
        public String getContentPreview() { return contentPreview; }
        public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }
    }
}
