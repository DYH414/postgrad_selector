package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 草稿完整状态 —— 三档候选 + 画像依据 + 不足说明。
 * <p>存储在 Redis {@code ai:v2:draft:{userId}}。</p>
 */
public class DraftVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 三档候选 */
    private List<TierCandidates> tiers;

    /** 被移除的候选列表（可加回） */
    private List<CandidateCardVO> removedCandidates;

    /** 被 AI 校验拦截的候选及原因 */
    private List<BlockedCandidateVO> blockedCandidates;

    /** 降级档位标签列表（AI 不可用时走系统选择，如 ["冲刺档"]） */
    private List<String> fallbackTiers;

    /** 画像依据 */
    private ProfileBasisVO profileBasis;

    /** 草稿生成时间 */
    private LocalDateTime generatedAt;

    /** 工作集规模摘要（混合架构）：reach/steady/safe 各档候选数 */
    private java.util.Map<String, Integer> workspaceSummary;

    /** 待确认的填充候选 */
    private java.util.Map<String, Object> pendingRefill;

    // ── getters / setters ──

    public List<TierCandidates> getTiers() { return tiers; }
    public void setTiers(List<TierCandidates> tiers) { this.tiers = tiers; }

    public List<CandidateCardVO> getRemovedCandidates() { return removedCandidates; }
    public void setRemovedCandidates(List<CandidateCardVO> removedCandidates) { this.removedCandidates = removedCandidates; }

    public List<BlockedCandidateVO> getBlockedCandidates() { return blockedCandidates; }
    public void setBlockedCandidates(List<BlockedCandidateVO> blockedCandidates) { this.blockedCandidates = blockedCandidates; }
    public List<String> getFallbackTiers() { return fallbackTiers; }
    public void setFallbackTiers(List<String> fallbackTiers) { this.fallbackTiers = fallbackTiers; }

    public ProfileBasisVO getProfileBasis() { return profileBasis; }
    public void setProfileBasis(ProfileBasisVO profileBasis) { this.profileBasis = profileBasis; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public java.util.Map<String, Integer> getWorkspaceSummary() { return workspaceSummary; }
    public void setWorkspaceSummary(java.util.Map<String, Integer> workspaceSummary) { this.workspaceSummary = workspaceSummary; }

    public java.util.Map<String, Object> getPendingRefill() { return pendingRefill; }
    public void setPendingRefill(java.util.Map<String, Object> pendingRefill) { this.pendingRefill = pendingRefill; }
}
