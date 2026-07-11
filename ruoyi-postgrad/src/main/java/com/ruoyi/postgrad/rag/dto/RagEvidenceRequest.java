package com.ruoyi.postgrad.rag.dto;

/**
 * Recommendation-facing RAG evidence retrieval request.
 */
public class RagEvidenceRequest {

    private String query;
    private Long programId;
    private Long schoolId;
    private Integer year;
    private String docType;
    private String section;
    private Integer maxResults = 5;
    private Double minScore = 0.0;
    private Integer rerankTopN = 3;
    private String runId;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public Integer getMaxResults() { return maxResults; }
    public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
    public Double getMinScore() { return minScore; }
    public void setMinScore(Double minScore) { this.minScore = minScore; }
    public Integer getRerankTopN() { return rerankTopN; }
    public void setRerankTopN(Integer rerankTopN) { this.rerankTopN = rerankTopN; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
}
