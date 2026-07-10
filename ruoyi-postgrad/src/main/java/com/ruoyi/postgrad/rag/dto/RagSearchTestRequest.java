package com.ruoyi.postgrad.rag.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 检索测试请求。
 */
public class RagSearchTestRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    private Integer maxResults = 5;
    private Double minScore = 0.6;
    private Integer rerankTopN = 3;
    private String schoolId;
    private String programId;
    private String year;
    private String docType;
    private String section;

    public RagSearchTestRequest() {}

    public RagSearchTestRequest(String query, Integer maxResults, Double minScore, Integer rerankTopN) {
        this.query = query;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.rerankTopN = rerankTopN;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getMaxResults() { return maxResults; }
    public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    public Double getMinScore() { return minScore; }
    public void setMinScore(Double minScore) { this.minScore = minScore; }
    public Integer getRerankTopN() { return rerankTopN; }
    public void setRerankTopN(Integer rerankTopN) { this.rerankTopN = rerankTopN; }
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
}
