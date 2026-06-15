package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 待确认的填充候选（精简视图）。
 */
public class RefillCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long programId;
    private String schoolName;
    private String programName;
    private String tier;
    private String riskLevel;
    private String reason;      // 推荐理由

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
