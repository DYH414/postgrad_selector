package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class AdmissionResult extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long programId;
    private Integer year;
    private Integer admittedCount;
    private Integer minAdmittedScore;
    private java.math.BigDecimal avgAdmittedScore;
    private Integer maxAdmittedScore;
    private Integer hasTransfer;
    private String verifyStatus;
    private Long sourceId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getAdmittedCount() { return admittedCount; }
    public void setAdmittedCount(Integer admittedCount) { this.admittedCount = admittedCount; }
    public Integer getMinAdmittedScore() { return minAdmittedScore; }
    public void setMinAdmittedScore(Integer minAdmittedScore) { this.minAdmittedScore = minAdmittedScore; }
    public java.math.BigDecimal getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(java.math.BigDecimal avgAdmittedScore) { this.avgAdmittedScore = avgAdmittedScore; }
    public Integer getMaxAdmittedScore() { return maxAdmittedScore; }
    public void setMaxAdmittedScore(Integer maxAdmittedScore) { this.maxAdmittedScore = maxAdmittedScore; }
    public Integer getHasTransfer() { return hasTransfer; }
    public void setHasTransfer(Integer hasTransfer) { this.hasTransfer = hasTransfer; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
}
