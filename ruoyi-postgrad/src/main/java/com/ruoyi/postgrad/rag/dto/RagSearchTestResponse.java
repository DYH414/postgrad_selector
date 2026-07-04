package com.ruoyi.postgrad.rag.dto;

import java.util.List;

/**
 * RAG 检索测试响应。
 */
public class RagSearchTestResponse {

    private String query;
    private String retrievalMode = "dense-vector-rerank";
    private int retrievedCount;
    private int selectedCount;
    private boolean rerankEnabled;
    private boolean rerankFailed;
    private String errorSummary;

    private RagSearchLatency latency = new RagSearchLatency();
    private List<RagChunkResult> chunks = List.of();

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public int getRetrievedCount() { return retrievedCount; }
    public void setRetrievedCount(int retrievedCount) { this.retrievedCount = retrievedCount; }
    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public boolean isRerankEnabled() { return rerankEnabled; }
    public void setRerankEnabled(boolean rerankEnabled) { this.rerankEnabled = rerankEnabled; }
    public boolean isRerankFailed() { return rerankFailed; }
    public void setRerankFailed(boolean rerankFailed) { this.rerankFailed = rerankFailed; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public RagSearchLatency getLatency() { return latency; }
    public void setLatency(RagSearchLatency latency) { this.latency = latency; }
    public List<RagChunkResult> getChunks() { return chunks; }
    public void setChunks(List<RagChunkResult> chunks) { this.chunks = chunks; }

    public static class RagSearchLatency {
        private Long embeddingMs;
        private Long retrievalMs;
        private Long rerankMs;
        private long totalMs;

        public Long getEmbeddingMs() { return embeddingMs; }
        public void setEmbeddingMs(Long embeddingMs) { this.embeddingMs = embeddingMs; }
        public Long getRetrievalMs() { return retrievalMs; }
        public void setRetrievalMs(Long retrievalMs) { this.retrievalMs = retrievalMs; }
        public Long getRerankMs() { return rerankMs; }
        public void setRerankMs(Long rerankMs) { this.rerankMs = rerankMs; }
        public long getTotalMs() { return totalMs; }
        public void setTotalMs(long totalMs) { this.totalMs = totalMs; }
    }
}
