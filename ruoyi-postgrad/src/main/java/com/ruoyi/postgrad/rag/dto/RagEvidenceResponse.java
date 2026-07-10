package com.ruoyi.postgrad.rag.dto;

import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import java.util.List;
import java.util.Map;

/**
 * Recommendation-facing RAG evidence retrieval response.
 */
public class RagEvidenceResponse {

    private String query;
    private boolean fallbackUsed;
    private String fallbackMode;
    private String errorSummary;
    private Map<String, String> appliedFilters = Map.of();
    private List<RagChunkResult> selectedChunks = List.of();
    private List<RagCitation> citations = List.of();

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getFallbackMode() { return fallbackMode; }
    public void setFallbackMode(String fallbackMode) { this.fallbackMode = fallbackMode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Map<String, String> getAppliedFilters() { return appliedFilters; }
    public void setAppliedFilters(Map<String, String> appliedFilters) {
        this.appliedFilters = appliedFilters != null ? appliedFilters : Map.of();
    }
    public List<RagChunkResult> getSelectedChunks() { return selectedChunks; }
    public void setSelectedChunks(List<RagChunkResult> selectedChunks) {
        this.selectedChunks = selectedChunks != null ? selectedChunks : List.of();
    }
    public List<RagCitation> getCitations() { return citations; }
    public void setCitations(List<RagCitation> citations) {
        this.citations = citations != null ? citations : List.of();
    }
}
