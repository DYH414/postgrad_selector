package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
/**
 * 最终报告 —— 草稿快照 + 生成元数据。
 * <p>存储在 Redis {@code ai:v2:report:{reportId}} 和 DB recommendation_log.result_json。</p>
 */
public class ReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long reportId;

    /** 报告摘要 */
    private String summary;

    /** 三档候选（与生成时草稿完全一致） */
    private List<TierCandidates> tiers;

    /** 画像依据（快照） */
    private ProfileBasisVO profileBasis;

    /** 报告状态：PENDING / COMPLETED / FAILED */
    private String status;

    /** 备注 */
    private String note;

    private LocalDateTime createdAt;

    // ── getters / setters ──

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<TierCandidates> getTiers() { return tiers; }
    public void setTiers(List<TierCandidates> tiers) { this.tiers = tiers; }

    public ProfileBasisVO getProfileBasis() { return profileBasis; }
    public void setProfileBasis(ProfileBasisVO profileBasis) { this.profileBasis = profileBasis; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
