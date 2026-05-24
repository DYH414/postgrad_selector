package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class DataQuality extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long programId;
    private Integer year;
    private Integer hasScore;
    private Integer hasPlan;
    private Integer hasResult;
    private Integer hasOfficialSource;
    private Integer hasConflict;
    private String completenessLevel;
    private String missingFields;
    private String lastCheckedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getHasScore() { return hasScore; }
    public void setHasScore(Integer hasScore) { this.hasScore = hasScore; }
    public Integer getHasPlan() { return hasPlan; }
    public void setHasPlan(Integer hasPlan) { this.hasPlan = hasPlan; }
    public Integer getHasResult() { return hasResult; }
    public void setHasResult(Integer hasResult) { this.hasResult = hasResult; }
    public Integer getHasOfficialSource() { return hasOfficialSource; }
    public void setHasOfficialSource(Integer hasOfficialSource) { this.hasOfficialSource = hasOfficialSource; }
    public Integer getHasConflict() { return hasConflict; }
    public void setHasConflict(Integer hasConflict) { this.hasConflict = hasConflict; }
    public String getCompletenessLevel() { return completenessLevel; }
    public void setCompletenessLevel(String completenessLevel) { this.completenessLevel = completenessLevel; }
    public String getMissingFields() { return missingFields; }
    public void setMissingFields(String missingFields) { this.missingFields = missingFields; }
    public String getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(String lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
