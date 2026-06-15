package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排除候选记录 —— 跟踪用户或系统移除的候选，
 * 供 RefillPolicy 使用以避免重新添加刚被移除的学校。
 */
public class ExcludedCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long programId;
    private String schoolName;
    private String reasonType;      // user_removed / user_blocked / system_risk_blocked / replaced
    private String tierAtRemoval;   // 移除时所在档位
    private LocalDateTime createdAt;
    private String reasonText;

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getReasonType() { return reasonType; }
    public void setReasonType(String reasonType) { this.reasonType = reasonType; }

    public String getTierAtRemoval() { return tierAtRemoval; }
    public void setTierAtRemoval(String tierAtRemoval) { this.tierAtRemoval = tierAtRemoval; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getReasonText() { return reasonText; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
}
