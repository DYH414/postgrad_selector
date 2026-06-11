package com.ruoyi.postgrad.domain.vo;

import com.ruoyi.postgrad.domain.dto.ProgramSummaryDTO;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 规则筛选推荐结果 —— 替代 {@code ProgramRecommendationServiceImpl.generateRecommendation()} 返回的裸 Map。
 *
 * <p>前端 Results.vue / AiRecommend.vue 消费此结构。JSON 序列化格式与旧 Map 完全一致。</p>
 */
public class RecommendResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleVersion;
    private String dataVersion;
    private Map<String, Object> request;      // 请求快照（保留 Map，字段少且稳定）
    private int totalCandidates;
    private List<String> globalWarnings;
    private List<ProgramSummaryDTO> items;    // 归一化后的学校列表
    private List<ResultGroup> groups;         // 分组（当前仅 "matches" 组）
    private Long recommendationId;

    /** 分组 */
    public static class ResultGroup implements Serializable {
        private static final long serialVersionUID = 1L;
        private String groupKey;
        private String groupName;
        private String description;
        private List<ProgramSummaryDTO> items;

        public String getGroupKey() { return groupKey; }
        public void setGroupKey(String v) { this.groupKey = v; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String v) { this.groupName = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public List<ProgramSummaryDTO> getItems() { return items; }
        public void setItems(List<ProgramSummaryDTO> v) { this.items = v; }
    }

    // ── getters / setters ──

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String v) { this.ruleVersion = v; }
    public String getDataVersion() { return dataVersion; }
    public void setDataVersion(String v) { this.dataVersion = v; }
    public Map<String, Object> getRequest() { return request; }
    public void setRequest(Map<String, Object> v) { this.request = v; }
    public int getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(int v) { this.totalCandidates = v; }
    public List<String> getGlobalWarnings() { return globalWarnings; }
    public void setGlobalWarnings(List<String> v) { this.globalWarnings = v; }
    public List<ProgramSummaryDTO> getItems() { return items; }
    public void setItems(List<ProgramSummaryDTO> v) { this.items = v; }
    public List<ResultGroup> getGroups() { return groups; }
    public void setGroups(List<ResultGroup> v) { this.groups = v; }
    public Long getRecommendationId() { return recommendationId; }
    public void setRecommendationId(Long v) { this.recommendationId = v; }
}
