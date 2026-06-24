package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

import com.ruoyi.postgrad.domain.RowMap;

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
     * 档位分类规则 —— 系统唯一真相来源。
     * <p>reach: -15 ≤ gap ≤ 5, steady: 6 ≤ gap ≤ 14, safe: gap ≥ 15 且 canBeSafe。
     * gap < -15 返回 null（不入档），gap ≥ 15 但不满足保底条件降级为 steady。</p>
     *
     * @param gap       分数差距（estimatedScore - avgAdmittedScore）
     * @param canBeSafe 是否满足保底条件
     * @return reach / steady / safe / null（null 表示不入档）
     */
    public static String classifyTier(int gap, Boolean canBeSafe) {
        if (gap < -15) return null;                       // 差距过大，不入档
        if (gap >= -15 && gap <= 5) return "reach";       // 冲刺档
        if (gap <= 14) return "steady";                   // 稳妥档
        if (gap >= 15 && Boolean.TRUE.equals(canBeSafe)) return "safe"; // 保底档
        return "steady";                                  // gap≥15 不满足保底条件 → 降级
    }

    /**
     * 根据当前实例的 gap + canBeSafe 推断档位。
     * <p>委托给 {@link #classifyTier(int, Boolean)}，gap < -15 时返回 null（差距过大，不应推荐）。</p>
     *
     * @return reach / steady / safe / null（差距超 15 分，不建议推荐）
     */
    public String inferTier() {
        int gap = this.scoreGap != null ? this.scoreGap : 0;
        return classifyTier(gap, this.canBeSafe);
    }

    // ── 静态工厂 / 类型转换工具（系统唯一副本） ──

    /**
     * 从 MyBatis RowMap 构建 SchoolFact（仅填充 DB 字段，不含计算字段）。
     * <p>计算字段（gap/canBeSafe/quotaRisk 等）由调用方按需填充。</p>
     */
    public static SchoolFact fromRow(RowMap row) {
        SchoolFact f = new SchoolFact();
        f.setProgramId(longVal(row.get("programId")));
        f.setSchoolId(longVal(row.get("schoolId")));
        f.setSchoolName(strVal(row.get("schoolName")));
        f.setSchoolTier(tierLabel(strVal(row.get("schoolTier"))));
        f.setCity(strVal(row.get("city")));
        f.setProvince(strVal(row.get("province")));
        f.setCollegeName(strVal(row.get("collegeName")));
        f.setProgramName(strVal(row.get("programName")));
        f.setProgramCode(strVal(row.get("programCode")));
        f.setDegreeType(strVal(row.get("degreeType")));
        f.setExamCombo(strVal(row.get("examCombo")));
        f.setScoreLine(intVal(row.get("scoreLine")));
        f.setAvgAdmittedScore(intVal(row.get("avgAdmittedScore")));
        f.setAdmissionLow(intVal(row.get("admissionLow")));
        f.setAdmissionHigh(intVal(row.get("admissionHigh")));
        Integer low = f.getAdmissionLow();
        Integer high = f.getAdmissionHigh();
        f.setAdmissionRange(low != null && high != null ? low + "-" + high
            : low != null ? String.valueOf(low) : high != null ? String.valueOf(high) : null);
        f.setPlanCount(intVal(row.get("planCount")));
        f.setUnifiedExamQuota(intVal(row.get("unifiedExamQuota")));
        f.setAdmittedCount(intVal(row.get("admittedCount")));
        f.setRetestCount(intVal(row.get("retestCount")));
        f.setDataYear(intVal(row.get("dataYear")));
        f.setDataCompleteness(strVal(row.get("dataCompleteness")));
        f.setSourceUrl(strVal(row.get("sourceUrl")));
        f.setSourceOwner(strVal(row.get("sourceOwner")));
        return f;
    }

    /** 学校层次原始值 → 中文标签 */
    public static String tierLabel(String raw) {
        if (raw == null) return "其他";
        return switch (raw) {
            case "985" -> "985";
            case "211" -> "211";
            case "DOUBLE_FIRST" -> "双一流";
            default -> "其他";
        };
    }

    /**
     * 判断候选是否满足保底条件（系统唯一副本）。
     * <p>规则：名额 &gt; 3 且（名额 ≥ 10 或（数据完整度非 C 且有录取区间））。</p>
     *
     * @param quota         统考名额（unifiedExamQuota 或 planCount）
     * @param completeness  数据完整度 A/B/C
     * @param admissionLow  录取最低分（可为 null）
     * @param admissionHigh 录取最高分（可为 null）
     * @return true 表示可作保底
     */
    public static boolean canBeSafe(int quota, String completeness,
                                    Integer admissionLow, Integer admissionHigh) {
        if (quota <= 3) return false;
        if (quota < 10) {
            boolean hasRange = admissionLow != null || admissionHigh != null;
            if ("C".equalsIgnoreCase(completeness) || !hasRange) return false;
        }
        return true;
    }

    /** RowMap 安全取值 → String */
    public static String strVal(Object v) { return v == null ? null : v.toString(); }

    /** RowMap 安全取值 → Integer */
    public static Integer intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** RowMap 安全取值 → Long */
    public static Long longVal(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
