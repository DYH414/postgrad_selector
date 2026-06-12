package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 单个候选的事实卡 —— 前端草稿卡片和报告行的数据来源。
 * <p>包含两部分：系统事实（来自 DB，AI 不得编造）+ AI 观点（理由、风险、pros/cons）。</p>
 */
public class CandidateCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 系统事实（来自 DB）──

    private Long programId;
    private Long schoolId;
    private String schoolName;
    private String schoolTier;       // 中文标签：985 / 211 / 双一流 / 其他
    private String city;
    private String province;
    private String collegeName;
    private String programName;
    private String programCode;
    private String degreeType;
    private String examCombo;

    private Integer scoreLine;
    private Integer avgAdmittedScore;
    private Integer admissionLow;
    private Integer admissionHigh;
    private String admissionRange;

    private Integer planCount;
    private Integer unifiedExamQuota;
    private Integer admittedCount;
    private Integer retestCount;

    private Integer dataYear;
    private String dataCompleteness;
    private String sourceUrl;
    private String sourceOwner;

    // ── 后端计算字段 ──

    private Integer scoreGap;
    private String gapLabel;
    private String quotaLabel;
    private String quotaRisk;
    private Boolean canBeSafe;
    private String safeBlockReason;

    // ── AI 观点 ──

    private String reason;
    private List<String> risks;
    private List<String> pros;
    private List<String> cons;
    private List<String> tradeoffs;
    private String recommendedAction;

    /** 当前状态：selected | removed | verified_pending */
    private String status;

    /** AI 原始档位判断（可能被后端降级） */
    private String aiJudgement;
    /** 后端裁决后的最终档位 */
    private String finalJudgement;
    /** 是否被后端降级 */
    private Boolean adjusted;
    /** 降级原因 */
    private String adjustReason;

    /** 展示标签 */
    private List<String> tags;

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getSchoolTier() { return schoolTier; }
    public void setSchoolTier(String schoolTier) { this.schoolTier = schoolTier; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String degreeType) { this.degreeType = degreeType; }

    public String getExamCombo() { return examCombo; }
    public void setExamCombo(String examCombo) { this.examCombo = examCombo; }

    public Integer getScoreLine() { return scoreLine; }
    public void setScoreLine(Integer scoreLine) { this.scoreLine = scoreLine; }

    public Integer getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(Integer avgAdmittedScore) { this.avgAdmittedScore = avgAdmittedScore; }

    public Integer getAdmissionLow() { return admissionLow; }
    public void setAdmissionLow(Integer admissionLow) { this.admissionLow = admissionLow; }

    public Integer getAdmissionHigh() { return admissionHigh; }
    public void setAdmissionHigh(Integer admissionHigh) { this.admissionHigh = admissionHigh; }

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

    public Integer getScoreGap() { return scoreGap; }
    public void setScoreGap(Integer scoreGap) { this.scoreGap = scoreGap; }

    public String getGapLabel() { return gapLabel; }
    public void setGapLabel(String gapLabel) { this.gapLabel = gapLabel; }

    public String getQuotaLabel() { return quotaLabel; }
    public void setQuotaLabel(String quotaLabel) { this.quotaLabel = quotaLabel; }

    public String getQuotaRisk() { return quotaRisk; }
    public void setQuotaRisk(String quotaRisk) { this.quotaRisk = quotaRisk; }

    public Boolean getCanBeSafe() { return canBeSafe; }
    public void setCanBeSafe(Boolean canBeSafe) { this.canBeSafe = canBeSafe; }

    public String getSafeBlockReason() { return safeBlockReason; }
    public void setSafeBlockReason(String safeBlockReason) { this.safeBlockReason = safeBlockReason; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<String> getRisks() { return risks; }
    public void setRisks(List<String> risks) { this.risks = risks; }

    public List<String> getPros() { return pros; }
    public void setPros(List<String> pros) { this.pros = pros; }

    public List<String> getCons() { return cons; }
    public void setCons(List<String> cons) { this.cons = cons; }

    public List<String> getTradeoffs() { return tradeoffs; }
    public void setTradeoffs(List<String> tradeoffs) { this.tradeoffs = tradeoffs; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAiJudgement() { return aiJudgement; }
    public void setAiJudgement(String aiJudgement) { this.aiJudgement = aiJudgement; }

    public String getFinalJudgement() { return finalJudgement; }
    public void setFinalJudgement(String finalJudgement) { this.finalJudgement = finalJudgement; }

    public Boolean getAdjusted() { return adjusted; }
    public void setAdjusted(Boolean adjusted) { this.adjusted = adjusted; }

    public String getAdjustReason() { return adjustReason; }
    public void setAdjustReason(String adjustReason) { this.adjustReason = adjustReason; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
