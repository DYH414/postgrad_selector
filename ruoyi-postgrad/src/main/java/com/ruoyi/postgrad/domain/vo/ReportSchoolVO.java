package com.ruoyi.postgrad.domain.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 报告页学校视图对象 —— 定义报告 API 返回的单个学校数据结构。
 *
 * <p>定义报告页可直接消费的结构化学校数据，避免返回裸 Map。</p>
 *
 * <p><b>关键修复：</b>dataYear 作为独立字段保留，不再被 sanitize 逻辑删除。
 * 前端可直接展示年份，不再依赖 schoolDataYear() 兜底。</p>
 */
public class ReportSchoolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 标识 ──
    private Long programId;
    private Long schoolId;

    // ── 基础信息 ──
    private String schoolName;
    private String province;
    private String city;
    private String collegeName;
    private String programName;
    private String programCode;
    private String degreeType;
    private String schoolTier;       // 中文标签（985/211/双一流/其他）
    private String examCombo;        // "11408：政治+英语一+数学一+408"

    // ── 分数 ──
    private Integer scoreLine;
    private Integer admissionLow;
    private Integer admissionHigh;
    private Integer avgAdmittedScore;
    private Integer avgScoreGap;                        // 预估分 - 均分
    private String admissionRange;                       // "320-350"

    // ── 招生 ──
    private Integer planCount;
    private Integer unifiedExamQuota;
    private Integer admittedCount;
    private Integer retestCount;

    // ── 数据质量 ──
    private Integer dataYear;         // ★ 保留，不再被 sanitize 删除
    private String dataCompleteness;  // A / B / C
    private String sourceUrl;
    private String sourceOwner;

    // ── 推荐意见（从 opinion 镜像到顶层，前端直接读）──
    private String judgement;         // safe / steady / small_reach / high_risk_reach
    private String risk;              // high / medium / low
    private String decision;          // 决策描述
    private String reason;            // 推荐理由
    private List<String> pros;        // 优势
    private List<String> cons;        // 劣势
    private List<String> tradeoffs;   // 取舍
    private String recommendedAction; // 行动建议

    // ── 标签 ──
    private List<String> tags;        // "数据完整度:A", "不满足严格保底条件" 等

    // ── 嵌套 opinion（可选，前端可忽略）──
    private OpinionVO opinion;

    /** 嵌套推荐意见 */
    public static class OpinionVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String judgement;
        private String risk;
        private String decision;
        private String reason;
        private List<String> pros;
        private List<String> cons;
        private List<String> tradeoffs;
        private String recommendedAction;

        public String getJudgement() { return judgement; }
        public void setJudgement(String judgement) { this.judgement = judgement; }
        public String getRisk() { return risk; }
        public void setRisk(String risk) { this.risk = risk; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<String> getPros() { return pros; }
        public void setPros(List<String> pros) { this.pros = pros; }
        public List<String> getCons() { return cons; }
        public void setCons(List<String> cons) { this.cons = cons; }
        public List<String> getTradeoffs() { return tradeoffs; }
        public void setTradeoffs(List<String> tradeoffs) { this.tradeoffs = tradeoffs; }
        public String getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    }

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String degreeType) { this.degreeType = degreeType; }
    public String getSchoolTier() { return schoolTier; }
    public void setSchoolTier(String schoolTier) { this.schoolTier = schoolTier; }
    public String getExamCombo() { return examCombo; }
    public void setExamCombo(String examCombo) { this.examCombo = examCombo; }
    public Integer getScoreLine() { return scoreLine; }
    public void setScoreLine(Integer scoreLine) { this.scoreLine = scoreLine; }
    public Integer getAdmissionLow() { return admissionLow; }
    public void setAdmissionLow(Integer admissionLow) { this.admissionLow = admissionLow; }
    public Integer getAdmissionHigh() { return admissionHigh; }
    public void setAdmissionHigh(Integer admissionHigh) { this.admissionHigh = admissionHigh; }
    public Integer getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(Integer avgAdmittedScore) { this.avgAdmittedScore = avgAdmittedScore; }
    public Integer getAvgScoreGap() { return avgScoreGap; }
    public void setAvgScoreGap(Integer avgScoreGap) { this.avgScoreGap = avgScoreGap; }
    public String getAdmissionRange() { return admissionRange; }
    public void setAdmissionRange(String admissionRange) { this.admissionRange = admissionRange; }
    public Integer getPlanCount() { return planCount; }
    public void setPlanCount(Integer planCount) { this.planCount = planCount; }
    public Integer getUnifiedExamQuota() { return unifiedExamQuota; }
    public void setUnifiedExamQuota(Integer unifiedExamQuota) { this.unifiedExamQuota = unifiedExamQuota; }
    public Integer getAdmittedCount() { return admittedCount; }
    public void setAdmittedCount(Integer admittedCount) { this.admittedCount = admittedCount; }
    public Integer getRetestCount() { return retestCount; }
    public void setRetestCount(Integer retestCount) { this.retestCount = retestCount; }
    public Integer getDataYear() { return dataYear; }
    public void setDataYear(Integer dataYear) { this.dataYear = dataYear; }
    public String getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(String dataCompleteness) { this.dataCompleteness = dataCompleteness; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceOwner() { return sourceOwner; }
    public void setSourceOwner(String sourceOwner) { this.sourceOwner = sourceOwner; }
    public String getJudgement() { return judgement; }
    public void setJudgement(String judgement) { this.judgement = judgement; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getPros() { return pros; }
    public void setPros(List<String> pros) { this.pros = pros; }
    public List<String> getCons() { return cons; }
    public void setCons(List<String> cons) { this.cons = cons; }
    public List<String> getTradeoffs() { return tradeoffs; }
    public void setTradeoffs(List<String> tradeoffs) { this.tradeoffs = tradeoffs; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public OpinionVO getOpinion() { return opinion; }
    public void setOpinion(OpinionVO opinion) { this.opinion = opinion; }
}
