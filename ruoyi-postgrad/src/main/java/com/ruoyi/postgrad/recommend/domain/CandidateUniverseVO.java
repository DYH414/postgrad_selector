package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选宇宙 —— 一次画像/生成运行的广泛候选供给。
 * <p>用户移除草稿学校时不应修改 Universe。是整个推荐体系的原始数据源。</p>
 */
public class CandidateUniverseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 宇宙 ID（UUID） */
    private String universeId;

    /** 所属用户 */
    private Long userId;

    /** 生成时的画像快照 */
    private ProfileBasisVO profileSnapshot;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 所有候选（SchoolFact 列表） */
    private List<SchoolFact> candidates = new ArrayList<>();

    /** 来源摘要：rawCount, filteredCount, tierCounts 等 */
    private Map<String, Object> sourceSummary = new LinkedHashMap<>();

    /** 策略版本号 */
    private String policyVersion;

    // ── getters / setters ──

    public String getUniverseId() { return universeId; }
    public void setUniverseId(String universeId) { this.universeId = universeId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public ProfileBasisVO getProfileSnapshot() { return profileSnapshot; }
    public void setProfileSnapshot(ProfileBasisVO profileSnapshot) { this.profileSnapshot = profileSnapshot; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public List<SchoolFact> getCandidates() { return candidates; }
    public void setCandidates(List<SchoolFact> candidates) { this.candidates = candidates; }

    public Map<String, Object> getSourceSummary() { return sourceSummary; }
    public void setSourceSummary(Map<String, Object> sourceSummary) { this.sourceSummary = sourceSummary; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    /** 候选总数 */
    public int candidateCount() { return candidates != null ? candidates.size() : 0; }
}
