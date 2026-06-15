package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选工作集 —— 从 Universe 派生的分层活动候选集。
 * <p>每档最多 30 个候选，是 AI 选择、替换、搜索的来源。应比 Draft 更宽。</p>
 */
public class CandidateWorkspaceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workspaceId;
    private Long userId;
    private String universeId;
    private List<WorkspaceTierVO> tiers = new ArrayList<>(3);
    private Map<String, Object> metadata = new LinkedHashMap<>();

    // ── getters / setters ──

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUniverseId() { return universeId; }
    public void setUniverseId(String universeId) { this.universeId = universeId; }

    public List<WorkspaceTierVO> getTiers() { return tiers; }
    public void setTiers(List<WorkspaceTierVO> tiers) { this.tiers = tiers; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /** 按 level 查找档位 */
    public WorkspaceTierVO tierByLevel(String level) {
        if (tiers == null) return null;
        return tiers.stream().filter(t -> level.equals(t.getLevel())).findFirst().orElse(null);
    }

    /** 所有候选总数 */
    public int totalCandidates() {
        if (tiers == null) return 0;
        return tiers.stream().mapToInt(t -> t.getCandidates() != null ? t.getCandidates().size() : 0).sum();
    }
}
