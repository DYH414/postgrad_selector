package com.ruoyi.postgrad.rag.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 检索测试请求。
 */
public class RagSearchTestRequest {

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int MAX_RESULTS_CAP = 20;
    private static final int DEFAULT_RERANK_TOP_N = 3;
    private static final int RERANK_TOP_N_CAP = 10;

    @NotBlank(message = "查询内容不能为空")
    private String query;

    private Integer maxResults = DEFAULT_MAX_RESULTS;
    private Double minScore = 0.6;
    private Integer rerankTopN = DEFAULT_RERANK_TOP_N;
    private boolean skipRerank = false;
    private String schoolId;
    private String programId;
    private String year;
    private String docType;
    private String section;

    public RagSearchTestRequest() {}

    public RagSearchTestRequest(String query, Integer maxResults, Double minScore, Integer rerankTopN) {
        this.query = query;
        setMaxResults(maxResults);
        this.minScore = minScore;
        setRerankTopN(rerankTopN);
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getMaxResults() { return maxResults; }
    public void setMaxResults(Integer maxResults) { this.maxResults = clamp(maxResults, DEFAULT_MAX_RESULTS, MAX_RESULTS_CAP); }
    public Double getMinScore() { return minScore; }
    public void setMinScore(Double minScore) { this.minScore = minScore; }
    public Integer getRerankTopN() { return rerankTopN; }
    public void setRerankTopN(Integer rerankTopN) { this.rerankTopN = clamp(rerankTopN, DEFAULT_RERANK_TOP_N, RERANK_TOP_N_CAP); }
    public boolean isSkipRerank() { return skipRerank; }
    public void setSkipRerank(boolean skipRerank) { this.skipRerank = skipRerank; }
    public String getSchoolId() { return schoolId; }
    public void setSchoolId(String schoolId) { this.schoolId = schoolId; }
    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Map<String, String> getMetadataFilters() {
        Map<String, String> filters = new LinkedHashMap<>();
        putIfPresent(filters, "school_id", schoolId);
        putIfPresent(filters, "program_id", programId);
        putIfPresent(filters, "year", year);
        putIfPresent(filters, "doc_type", docType);
        putIfPresent(filters, "section", section);
        return filters;
    }

    private static void putIfPresent(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.put(key, value.trim());
        }
    }

    private static int clamp(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
