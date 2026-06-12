package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 学校事实数据 —— 来自 DB 或后端计算，AI 不得编造。
 * <p>包含三部分：DB 原始字段、DB 派生字段（admissionRange）、后端计算字段（gap/quotaRisk/canBeSafe）。</p>
 * <p>这是 CandidateCardVO 的事实层，单独拆出以防止 VO 膨胀成垃圾桶。</p>
 */
public class SchoolFact implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 标识 ──

    /** 专业项目 ID */
    private Long programId;

    /** 学校 ID */
    private Long schoolId;

    // ── 学校信息 ──

    /** 学校名称 */
    private String schoolName;

    /** 学校层次（中文标签：985 / 211 / 双一流 / 其他） */
    private String schoolTier;

    /** 城市 */
    private String city;

    /** 省份 */
    private String province;

    // ── 学院/专业 ──

    /** 学院名称 */
    private String collegeName;

    /** 专业名称 */
    private String programName;

    /** 专业代码 */
    private String programCode;

    /** 学位类型（学硕/专硕） */
    private String degreeType;

    /** 考试科目组合 */
    private String examCombo;

    // ── 分数 ──

    /** 复试线 */
    private Integer scoreLine;

    /** 录取均分 */
    private Integer avgAdmittedScore;

    /** 录取最低分 */
    private Integer admissionLow;

    /** 录取最高分 */
    private Integer admissionHigh;

    /** 录取分数区间（派生字段，如 "345-360"） */
    private String admissionRange;

    // ── 招生 ──

    /** 总计划数 */
    private Integer planCount;

    /** 统考名额 */
    private Integer unifiedExamQuota;

    /** 录取人数 */
    private Integer admittedCount;

    /** 复试人数 */
    private Integer retestCount;

    // ── 数据质量 ──

    /** 数据年份 */
    private Integer dataYear;

    /** 数据完整度：A / B / C */
    private String dataCompleteness;

    /** 数据来源 URL */
    private String sourceUrl;

    /** 数据来源方 */
    private String sourceOwner;

    // ── 后端计算字段 ──

    /** 分数差距（estimatedScore - avgAdmittedScore） */
    private Integer scoreGap;

    /** 差距标签（如 "+10" / "-5"） */
    private String gapLabel;

    /** 名额标签（如 "名额充裕" / "名额偏少"） */
    private String quotaLabel;

    /** 名额风险等级：normal / medium / high / very_high / unknown */
    private String quotaRisk;

    /** 是否满足严格保底条件 */
    private Boolean canBeSafe;

    /** 不满足保底条件的原因（仅 canBeSafe=false 时有值） */
    private String safeBlockReason;

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

    // ── 派生方法 ──

    /**
     * 根据 gap + canBeSafe 推断该候选的档位。
     * <p>统一规则：reach: -15~5, steady: 6~14, safe: ≥15 + canBeSafe。
     * gap < -15 兜底归为 reach（理论上 pool 已过滤，仅展示层调用）。</p>
     *
     * @return reach / steady / safe
     */
    public String inferTier() {
        int gap = this.scoreGap != null ? this.scoreGap : 0;
        if (gap >= -15 && gap <= 5) return "reach";
        if (gap <= 14) return "steady";
        if (gap >= 15 && Boolean.TRUE.equals(this.canBeSafe)) return "safe";
        if (gap < -15) return "reach"; // 差距过大，兜底
        return "steady";
    }
}
