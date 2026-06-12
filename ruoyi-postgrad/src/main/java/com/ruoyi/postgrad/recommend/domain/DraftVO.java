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

    /** 画像依据 */
    private ProfileBasisVO profileBasis;

    /** 草稿生成时间 */
    private LocalDateTime generatedAt;

    // ── getters / setters ──

    public List<TierCandidates> getTiers() { return tiers; }
    public void setTiers(List<TierCandidates> tiers) { this.tiers = tiers; }

    public List<CandidateCardVO> getRemovedCandidates() { return removedCandidates; }
    public void setRemovedCandidates(List<CandidateCardVO> removedCandidates) { this.removedCandidates = removedCandidates; }

    public List<BlockedCandidateVO> getBlockedCandidates() { return blockedCandidates; }
    public void setBlockedCandidates(List<BlockedCandidateVO> blockedCandidates) { this.blockedCandidates = blockedCandidates; }

    public ProfileBasisVO getProfileBasis() { return profileBasis; }
    public void setProfileBasis(ProfileBasisVO profileBasis) { this.profileBasis = profileBasis; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
