package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class AdmissionScore extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long programId;
    private Integer year;
    private Integer scoreLine;
    private Integer singleMath;
    private Integer singleEnglish;
    private Integer singlePolitics;
    private Integer singleProfessional;
    private String verifyStatus;
    private Long sourceId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
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
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
}
