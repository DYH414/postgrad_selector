package com.ruoyi.postgrad.rag.dto;

/**
 * RAG 检索测试的单个 chunk 结果。
 */
public class RagChunkResult {

    private int vectorRank;
    private Integer rerankRank;
    private Double vectorScore;
    private Double rerankScore;
    private boolean selectedForPrompt;

    private String sourceName;
    private String sourceId;
    private String sourceUrl;
    private String docType;
    private String schoolId;
    private String programId;
    private String schoolName;
    private String collegeName;
    private String programName;
    private String year;
    private String section;
    private String chunkIndex;
    private String chunkTitle;
    private String contentHash;
    private String contentPreview;

    public int getVectorRank() { return vectorRank; }
    public void setVectorRank(int vectorRank) { this.vectorRank = vectorRank; }
    public Integer getRerankRank() { return rerankRank; }
    public void setRerankRank(Integer rerankRank) { this.rerankRank = rerankRank; }
    public Double getVectorScore() { return vectorScore; }
    public void setVectorScore(Double vectorScore) { this.vectorScore = vectorScore; }
    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }
    public boolean isSelectedForPrompt() { return selectedForPrompt; }
    public void setSelectedForPrompt(boolean selectedForPrompt) { this.selectedForPrompt = selectedForPrompt; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSchoolId() { return schoolId; }
    public void setSchoolId(String schoolId) { this.schoolId = schoolId; }
    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(String chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkTitle() { return chunkTitle; }
    public void setChunkTitle(String chunkTitle) { this.chunkTitle = chunkTitle; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getContentPreview() { return contentPreview; }
    public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }
}
