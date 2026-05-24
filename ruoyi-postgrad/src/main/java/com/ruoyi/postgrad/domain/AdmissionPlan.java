package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class AdmissionPlan extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long programId;
    private Integer year;
    private Integer totalPlan;
    private Integer unifiedExamQuota;
    private Integer retestCount;
    private String verifyStatus;
    private Long sourceId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getTotalPlan() { return totalPlan; }
    public void setTotalPlan(Integer totalPlan) { this.totalPlan = totalPlan; }
    public Integer getUnifiedExamQuota() { return unifiedExamQuota; }
    public void setUnifiedExamQuota(Integer unifiedExamQuota) { this.unifiedExamQuota = unifiedExamQuota; }
    public Integer getRetestCount() { return retestCount; }
    public void setRetestCount(Integer retestCount) { this.retestCount = retestCount; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
}
