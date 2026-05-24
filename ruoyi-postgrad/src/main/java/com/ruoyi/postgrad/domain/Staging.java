package com.ruoyi.postgrad.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Staging extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private Long sourceId;
    private Long matchedProgramId;
    private String sourceType;
    private String schoolName;
    private String collegeName;
    private String city;
    private String programCode;
    private String programName;
    private String examSubjects;
    private Integer year;
    private Integer scoreLine;
    private Integer singleMath;
    private Integer singleEnglish;
    private Integer singlePolitics;
    private Integer singleProfessional;
    private Integer minAdmitted;
    private BigDecimal avgAdmitted;
    private Integer planCount;
    private Integer retestCount;
    private Integer admittedCount;
    private String confidence;
    private String sourceUrl;
    private String rawText;
    private String extractJson;
    private String status;
    private String errorMessage;
    private Long reviewerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewedAt;
    private String reviewNote;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    // -- getters / setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getMatchedProgramId() { return matchedProgramId; }
    public void setMatchedProgramId(Long matchedProgramId) { this.matchedProgramId = matchedProgramId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getExamSubjects() { return examSubjects; }
    public void setExamSubjects(String examSubjects) { this.examSubjects = examSubjects; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getScoreLine() { return scoreLine; }
    public void setScoreLine(Integer scoreLine) { this.scoreLine = scoreLine; }
    public Integer getSingleMath() { return singleMath; }
    public void setSingleMath(Integer singleMath) { this.singleMath = singleMath; }
    public Integer getSingleEnglish() { return singleEnglish; }
    public void setSingleEnglish(Integer singleEnglish) { this.singleEnglish = singleEnglish; }
    public Integer getSinglePolitics() { return singlePolitics; }
    public void setSinglePolitics(Integer singlePolitics) { this.singlePolitics = singlePolitics; }
    public Integer getSingleProfessional() { return singleProfessional; }
    public void setSingleProfessional(Integer singleProfessional) { this.singleProfessional = singleProfessional; }
    public Integer getMinAdmitted() { return minAdmitted; }
    public void setMinAdmitted(Integer minAdmitted) { this.minAdmitted = minAdmitted; }
    public BigDecimal getAvgAdmitted() { return avgAdmitted; }
    public void setAvgAdmitted(BigDecimal avgAdmitted) { this.avgAdmitted = avgAdmitted; }
    public Integer getPlanCount() { return planCount; }
    public void setPlanCount(Integer planCount) { this.planCount = planCount; }
    public Integer getRetestCount() { return retestCount; }
    public void setRetestCount(Integer retestCount) { this.retestCount = retestCount; }
    public Integer getAdmittedCount() { return admittedCount; }
    public void setAdmittedCount(Integer admittedCount) { this.admittedCount = admittedCount; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getExtractJson() { return extractJson; }
    public void setExtractJson(String extractJson) { this.extractJson = extractJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("schoolName", getSchoolName())
            .append("programCode", getProgramCode())
            .append("programName", getProgramName())
            .append("year", getYear())
            .append("scoreLine", getScoreLine())
            .append("status", getStatus())
            .append("confidence", getConfidence())
            .toString();
    }
}
