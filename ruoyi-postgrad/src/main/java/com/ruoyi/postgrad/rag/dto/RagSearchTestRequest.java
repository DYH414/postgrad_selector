package com.ruoyi.postgrad.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 检索测试请求。
 */
public class RagSearchTestRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    private Integer maxResults = 5;
    private Double minScore = 0.6;
    private Integer rerankTopN = 3;

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
}
