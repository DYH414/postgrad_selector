package com.ruoyi.postgrad.rag.knowledge;

import com.ruoyi.postgrad.recommend.domain.SchoolFact;

public class RagCitation {

    private String title;
    private String sourceName;
    private String sourceUrl;
    private String schoolName;
    private String programName;
    private String year;
    private String section;
    private String contentHash;
    private String contentPreview;

    public static RagCitation fromChunk(StructuredRagChunk chunk) {
        RagCitation citation = new RagCitation();
        citation.setTitle(chunk.getChunkTitle());
        citation.setSourceName(chunk.getMetadata().get("source_owner"));
        citation.setSourceUrl(chunk.getMetadata().get("source_url"));
        citation.setSchoolName(chunk.getMetadata().get("school_name"));
        citation.setProgramName(chunk.getMetadata().get("program_name"));
        citation.setYear(chunk.getMetadata().get("year"));
        citation.setSection(chunk.getMetadata().get("section"));
        citation.setContentHash(chunk.getMetadata().get("content_hash"));
        citation.setContentPreview(preview(chunk.getText(), 180));
        return citation;
    }

    public static RagCitation fromSchoolFact(SchoolFact fact) {
        RagCitation citation = new RagCitation();
        citation.setTitle(fact.getSchoolName() + " " + fact.getProgramName() + " 推荐事实");
        citation.setSourceName(fact.getSourceOwner());
        citation.setSourceUrl(fact.getSourceUrl());
        citation.setSchoolName(fact.getSchoolName());
        citation.setProgramName(fact.getProgramName());
        citation.setYear(fact.getDataYear() != null ? String.valueOf(fact.getDataYear()) : null);
        citation.setSection("recommendation_fact");
        citation.setContentHash(null);
        citation.setContentPreview("复试线 " + nullToDash(fact.getScoreLine())
            + "，计划 " + nullToDash(fact.getPlanCount())
            + "，最低录取分 " + nullToDash(fact.getAdmissionLow())
            + "，平均录取分 " + nullToDash(fact.getAvgAdmittedScore()));
        return citation;
    }

    private static String preview(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getContentPreview() { return contentPreview; }
    public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }
}
