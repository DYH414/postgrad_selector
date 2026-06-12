package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 画像依据 —— 展示本次推荐使用的用户画像关键信息。
 * <p>出现在草稿和最终报告中，用于说明推荐依据。</p>
 */
public class ProfileBasisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预估分数 */
    private Integer estimatedScore;

    /** 目标地区（中文展示，如 "北京、上海"） */
    private String targetRegions;

    /** 本科层次（中文标签） */
    private String undergradTier;

    /** 是否跨专业 */
    private String isCrossMajor;

    /** 风险偏好标签 */
    private String riskPreference;

    /** 学校层次偏好标签 */
    private String schoolTierPreference;

    /** 地区策略标签 */
    private String regionStrategy;

    /** 候选池范围说明 */
    private String candidateScope;

    // ── getters / setters ──

    public Integer getEstimatedScore() { return estimatedScore; }
    public void setEstimatedScore(Integer estimatedScore) { this.estimatedScore = estimatedScore; }

    public String getTargetRegions() { return targetRegions; }
    public void setTargetRegions(String targetRegions) { this.targetRegions = targetRegions; }

    public String getUndergradTier() { return undergradTier; }
    public void setUndergradTier(String undergradTier) { this.undergradTier = undergradTier; }

    public String getIsCrossMajor() { return isCrossMajor; }
    public void setIsCrossMajor(String isCrossMajor) { this.isCrossMajor = isCrossMajor; }

    public String getRiskPreference() { return riskPreference; }
    public void setRiskPreference(String riskPreference) { this.riskPreference = riskPreference; }

    public String getSchoolTierPreference() { return schoolTierPreference; }
    public void setSchoolTierPreference(String schoolTierPreference) { this.schoolTierPreference = schoolTierPreference; }

    public String getRegionStrategy() { return regionStrategy; }
    public void setRegionStrategy(String regionStrategy) { this.regionStrategy = regionStrategy; }

    public String getCandidateScope() { return candidateScope; }
    public void setCandidateScope(String candidateScope) { this.candidateScope = candidateScope; }
}
