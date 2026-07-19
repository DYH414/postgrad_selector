package com.ruoyi.postgrad.rag.trace;

import com.alibaba.fastjson2.JSON;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent, size-bounded representation of a RAG retrieval trace. */
public class RagRetrievalTrace {

    private static final int QUERY_LIMIT = 512;
    private static final int ERROR_LIMIT = 512;
    private static final int SOURCE_LIMIT = 256;
    private static final int CHUNK_PREVIEW_LIMIT = 500;
    private static final int MAX_SOURCES = 10;
    private static final int MAX_CHUNKS = 5;

    private Long id;
    private String traceId;
    private String operationKey;
    private String runId;
    private String retrievalRoute;
    private Long userId;
    private String querySummary;
    private String outcome;
    private String failureStage;
    private String errorSummary;
    private Integer retrievedCount;
    private Integer selectedCount;
    private String topSourcesJson;
    private String metadataFiltersJson;
    private Boolean fallbackUsed;
    private String fallbackMode;
    private Long embeddingLatencyMs;
    private Long retrievalLatencyMs;
    private Long rerankLatencyMs;
    private Long totalLatencyMs;
    private String selectedChunksSummary;
    private LocalDateTime createdAt;

    public static RagRetrievalTrace from(RagTrace source) {
        RagRetrievalTrace target = new RagRetrievalTrace();
        target.traceId = source.getTraceId();
        target.operationKey = source.getOperationKey();
        target.runId = source.getRunId();
        target.retrievalRoute = source.getRetrievalRoute();
        target.userId = source.getUserId();
        target.querySummary = truncate(source.getQuery(), QUERY_LIMIT);
        target.outcome = source.getOutcome() == null ? null : source.getOutcome().name();
        target.failureStage = source.getFailureStage();
        target.errorSummary = truncate(source.getErrorSummary(), ERROR_LIMIT);
        target.retrievedCount = source.getRetrievedCount();
        target.selectedCount = source.getSelectedCount();
        target.topSourcesJson = JSON.toJSONString(summarizeSources(source.getTopSources()));
        target.metadataFiltersJson = JSON.toJSONString(summarizeFilters(source.getMetadataFilters()));
        target.fallbackUsed = source.isFallbackUsed();
        target.fallbackMode = source.getFallbackMode();
        target.embeddingLatencyMs = source.getEmbeddingLatencyMs();
        target.retrievalLatencyMs = source.getRetrievalLatencyMs();
        target.rerankLatencyMs = source.getRerankLatencyMs();
        target.totalLatencyMs = source.getTotalLatencyMs();
        target.selectedChunksSummary = JSON.toJSONString(summarizeChunks(source.getSelectedChunks()));
        target.createdAt = source.getCreatedAt() == null ? LocalDateTime.now()
            : LocalDateTime.ofInstant(source.getCreatedAt(), ZoneId.systemDefault());
        return target;
    }

    private static List<String> summarizeSources(List<String> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String source : sources) {
            if (result.size() >= MAX_SOURCES) break;
            result.add(truncate(source, SOURCE_LIMIT));
        }
        return result;
    }

    private static Map<String, String> summarizeFilters(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        filters.forEach((key, value) -> result.put(truncate(key, 128), truncate(value, 256)));
        return result;
    }

    private static List<Map<String, String>> summarizeChunks(List<RagTrace.SelectedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (RagTrace.SelectedChunk chunk : chunks) {
            if (result.size() >= MAX_CHUNKS) break;
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("sourceName", truncate(chunk.getSourceName(), SOURCE_LIMIT));
            summary.put("contentPreview", truncate(chunk.getContentPreview(), CHUNK_PREVIEW_LIMIT));
            summary.put("vectorRank", value(chunk.getVectorRank()));
            summary.put("vectorScore", value(chunk.getVectorScore()));
            summary.put("rerankRank", value(chunk.getRerankRank()));
            summary.put("rerankScore", value(chunk.getRerankScore()));
            result.add(summary);
        }
        return result;
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getRetrievalRoute() { return retrievalRoute; }
    public void setRetrievalRoute(String retrievalRoute) { this.retrievalRoute = retrievalRoute; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuerySummary() { return querySummary; }
    public void setQuerySummary(String querySummary) { this.querySummary = querySummary; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Integer getRetrievedCount() { return retrievedCount; }
    public void setRetrievedCount(Integer retrievedCount) { this.retrievedCount = retrievedCount; }
    public Integer getSelectedCount() { return selectedCount; }
    public void setSelectedCount(Integer selectedCount) { this.selectedCount = selectedCount; }
    public String getTopSourcesJson() { return topSourcesJson; }
    public void setTopSourcesJson(String topSourcesJson) { this.topSourcesJson = topSourcesJson; }
    public String getMetadataFiltersJson() { return metadataFiltersJson; }
    public void setMetadataFiltersJson(String metadataFiltersJson) { this.metadataFiltersJson = metadataFiltersJson; }
    public Boolean getFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(Boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getFallbackMode() { return fallbackMode; }
    public void setFallbackMode(String fallbackMode) { this.fallbackMode = fallbackMode; }
    public Long getEmbeddingLatencyMs() { return embeddingLatencyMs; }
    public void setEmbeddingLatencyMs(Long embeddingLatencyMs) { this.embeddingLatencyMs = embeddingLatencyMs; }
    public Long getRetrievalLatencyMs() { return retrievalLatencyMs; }
    public void setRetrievalLatencyMs(Long retrievalLatencyMs) { this.retrievalLatencyMs = retrievalLatencyMs; }
    public Long getRerankLatencyMs() { return rerankLatencyMs; }
    public void setRerankLatencyMs(Long rerankLatencyMs) { this.rerankLatencyMs = rerankLatencyMs; }
    public Long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(Long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
    public String getSelectedChunksSummary() { return selectedChunksSummary; }
    public void setSelectedChunksSummary(String selectedChunksSummary) { this.selectedChunksSummary = selectedChunksSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
