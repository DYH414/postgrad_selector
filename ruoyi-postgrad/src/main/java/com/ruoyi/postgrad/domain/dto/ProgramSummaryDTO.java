package com.ruoyi.postgrad.domain.dto;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.ai.AiRecommendationSafety;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 规则筛选归一化学校数据 —— 替代 {@code ProgramRecommendationServiceImpl.normalizeProgram()} 返回的裸 Map。
 *
 * <p>职责：从 MyBatis RowMap 转换而来，携带 DB 原始字段 + 后端计算字段（gap / fitLevel / warnings 等），
 * 在规则筛选链路中流转，最终序列化到 API 响应。</p>
 *
 * <p>与 {@link CandidateProgramDTO} 的区别：本类面向规则筛选结果页，包含 fitLevel / warnings /
 * retestAdmissionRatio 等规则筛选特有计算字段；CandidateProgramDTO 面向 AI 推荐候选池。</p>
 */
public class ProgramSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 标识 ──
    private Long programId;
    private Long schoolId;

    // ── 学校信息 ──
    private String schoolName;
    private String province;
    private String city;
    private Boolean officialVerified;

    // ── 学院/专业 ──
    private String collegeName;
    private String programName;
    private String programCode;
    private String researchDirection;
    private String degreeType;
    private String degreeTypeLabel;

    // ── 考试科目 ──
    private String subjectCodes;
    private String examCombo;
    private String examSubjectsLabel;

    // ── 分数 ──
    private Integer scoreLine;
    private Integer avgAdmittedScore;
    private Integer admissionLow;
    private Integer admissionHigh;
    private Integer scoreLineGap;
    private Integer admissionLowGap;
    private Integer admissionHighGap;
    private Integer avgScoreGap;
    private String admissionRangeLabel;

    // ── 招生 ──
    private Integer planCount;
    private Integer unifiedExamQuota;
    private Integer admittedCount;
    private Integer retestCount;
    private BigDecimal retestAdmissionRatio;

    // ── 数据质量 ──
    private Integer dataYear;
    private String dataCompleteness;
    private String dataCompletenessText;
    private String sourceUrl;
    private String sourceTitle;
    private String sourceOwner;

    // ── 筛选分级 ──
    private String fitLevel;
    private String fitLevelLabel;
    private List<String> warnings;

    // ── 来源标记（硬编码）──
    private String sourceName = "N诺";
    private String sourceType = "third_party";

    // ── 工厂方法 ──

    /** 从 MyBatis RowMap 创建 DTO，计算所有归一化字段 */
    public static ProgramSummaryDTO fromRowMap(RowMap row, int estimatedScore) {
        ProgramSummaryDTO d = new ProgramSummaryDTO();
        // 原始字段
        d.programId = toLong(row.get("programId"));
        d.schoolId = toLong(row.get("schoolId"));
        d.schoolName = str(row.get("schoolName"));
        d.province = str(row.get("province"));
        d.city = str(row.get("city"));
        d.officialVerified = bool(row.get("officialVerified"), false);
        d.collegeName = str(row.get("collegeName"));
        d.programName = str(row.get("programName"));
        d.programCode = str(row.get("programCode"));
        d.researchDirection = str(row.get("researchDirection"));
        d.degreeType = str(row.get("degreeType"));
        d.subjectCodes = str(row.get("subjectCodes"));
        d.scoreLine = toInt(row.get("scoreLine"));
        d.avgAdmittedScore = toInt(row.get("avgAdmittedScore"));
        d.admissionLow = toInt(row.get("admissionLow"));
        d.admissionHigh = toInt(row.get("admissionHigh"));
        d.planCount = toInt(row.get("planCount"));
        d.unifiedExamQuota = toInt(row.get("unifiedExamQuota"));
        d.admittedCount = toInt(row.get("admittedCount"));
        d.retestCount = toInt(row.get("retestCount"));
        d.dataYear = toInt(row.get("dataYear"));
        d.sourceUrl = str(row.get("sourceUrl"));
        d.sourceTitle = str(row.get("sourceTitle"));
        d.sourceOwner = str(row.get("sourceOwner"));

        // 计算字段
        d.examCombo = examComboBySubjects(d.subjectCodes);
        d.examSubjectsLabel = subjectsLabel(d.examCombo);
        d.degreeTypeLabel = degreeTypeLabel(d.degreeType);

        int score = estimatedScore <= 0 ? 0 : estimatedScore;
        d.scoreLineGap = d.scoreLine == null || score <= 0 ? null : score - d.scoreLine;
        d.admissionLowGap = d.admissionLow == null || score <= 0 ? null : score - d.admissionLow;
        d.admissionHighGap = d.admissionHigh == null || score <= 0 ? null : score - d.admissionHigh;
        d.avgScoreGap = d.avgAdmittedScore == null || score <= 0 ? null : score - d.avgAdmittedScore;

        d.admissionRangeLabel = rangeLabel(d.admissionLow, d.admissionHigh);
        d.retestAdmissionRatio = ratio(d.retestCount, d.admittedCount);

        String completeness = AiRecommendationSafety.computedCompleteness(row);
        d.dataCompleteness = completeness;
        d.dataCompletenessText = dataCompletenessDescription(completeness);

        d.fitLevel = fitLevel(d.avgScoreGap, completeness);
        d.fitLevelLabel = fitLevelLabel(d.fitLevel);
        d.warnings = warnings(d, completeness);
        return d;
    }

    // ── 计算逻辑（从 ProgramRecommendationServiceImpl 移入）──

    private static String examComboBySubjects(String codes) {
        return "101,204,302,408".equals(codes) ? "22408" : "11408";
    }

    private static String subjectsLabel(String combo) {
        return "22408".equals(combo) ? "政治 + 英语二 + 数学二 + 408"
            : "政治 + 英语一 + 数学一 + 408";
    }

    private static String degreeTypeLabel(String s) {
        return "academic".equals(s) ? "学硕" : "专硕";
    }

    static String fitLevel(Integer avgScoreGap, String completeness) {
        if (!"A".equals(completeness) && !"B".equals(completeness)) return "insufficient_data";
        if (avgScoreGap == null) return "insufficient_data";
        if (avgScoreGap < -20) return "sprint";
        if (avgScoreGap < -5) return "balanced_sprint";
        if (avgScoreGap >= 15) return "safe";
        return "steady";
    }

    private static String fitLevelLabel(String key) {
        return switch (key) {
            case "matches" -> "匹配院校";
            case "sprint" -> "冲刺";
            case "balanced_sprint" -> "稳中偏冲";
            case "steady" -> "稳妥候选";
            case "safe" -> "保底候选";
            default -> "数据不足";
        };
    }

    private static String rangeLabel(Integer low, Integer high) {
        if (low == null && high == null) return null;
        if (Objects.equals(low, high)) return String.valueOf(low);
        return (low == null ? "-" : low) + "-" + (high == null ? "-" : high);
    }

    private static BigDecimal ratio(Integer num, Integer den) {
        if (num == null || den == null || den == 0) return null;
        return BigDecimal.valueOf(num).divide(BigDecimal.valueOf(den), 2, RoundingMode.HALF_UP);
    }

    private static String dataCompletenessDescription(String level) {
        return switch (level) {
            case "A" -> "含复试线、拟录取区间、人数等字段";
            case "B" -> "含主要分数字段，部分字段缺失";
            default -> "仅有复试线或基础字段";
        };
    }

    private static List<String> warnings(ProgramSummaryDTO d, String completeness) {
        List<String> w = new ArrayList<>();
        w.add("复试线不是最低录取分。");
        w.add("筛选学校不代表只有这些学校可以报。");
        if (!"A".equals(completeness)) w.add("N诺数据字段不完整，请重点核对院校官方公告。");
        if (d.unifiedExamQuota != null && d.unifiedExamQuota < 10) w.add("统考名额较少，波动风险较高。");
        if (!Boolean.TRUE.equals(d.officialVerified)) w.add("保护一志愿信息不明确或不保护，需单独核实。");
        return w;
    }

    // ── 类型转换 ──

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }
    private static Integer toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
    private static Boolean bool(Object v, boolean fb) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v == null) return fb;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long v) { this.programId = v; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long v) { this.schoolId = v; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String v) { this.schoolName = v; }
    public String getProvince() { return province; }
    public void setProvince(String v) { this.province = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public Boolean getOfficialVerified() { return officialVerified; }
    public void setOfficialVerified(Boolean v) { this.officialVerified = v; }
    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String v) { this.collegeName = v; }
    public String getProgramName() { return programName; }
    public void setProgramName(String v) { this.programName = v; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String v) { this.programCode = v; }
    public String getResearchDirection() { return researchDirection; }
    public void setResearchDirection(String v) { this.researchDirection = v; }
    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String v) { this.degreeType = v; }
    public String getDegreeTypeLabel() { return degreeTypeLabel; }
    public void setDegreeTypeLabel(String v) { this.degreeTypeLabel = v; }
    public String getSubjectCodes() { return subjectCodes; }
    public void setSubjectCodes(String v) { this.subjectCodes = v; }
    public String getExamCombo() { return examCombo; }
    public void setExamCombo(String v) { this.examCombo = v; }
    public String getExamSubjectsLabel() { return examSubjectsLabel; }
    public void setExamSubjectsLabel(String v) { this.examSubjectsLabel = v; }
    public Integer getScoreLine() { return scoreLine; }
    public void setScoreLine(Integer v) { this.scoreLine = v; }
    public Integer getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(Integer v) { this.avgAdmittedScore = v; }
    public Integer getAdmissionLow() { return admissionLow; }
    public void setAdmissionLow(Integer v) { this.admissionLow = v; }
    public Integer getAdmissionHigh() { return admissionHigh; }
    public void setAdmissionHigh(Integer v) { this.admissionHigh = v; }
    public Integer getScoreLineGap() { return scoreLineGap; }
    public void setScoreLineGap(Integer v) { this.scoreLineGap = v; }
    public Integer getAdmissionLowGap() { return admissionLowGap; }
    public void setAdmissionLowGap(Integer v) { this.admissionLowGap = v; }
    public Integer getAdmissionHighGap() { return admissionHighGap; }
    public void setAdmissionHighGap(Integer v) { this.admissionHighGap = v; }
    public Integer getAvgScoreGap() { return avgScoreGap; }
    public void setAvgScoreGap(Integer v) { this.avgScoreGap = v; }
    public String getAdmissionRangeLabel() { return admissionRangeLabel; }
    public void setAdmissionRangeLabel(String v) { this.admissionRangeLabel = v; }
    public Integer getPlanCount() { return planCount; }
    public void setPlanCount(Integer v) { this.planCount = v; }
    public Integer getUnifiedExamQuota() { return unifiedExamQuota; }
    public void setUnifiedExamQuota(Integer v) { this.unifiedExamQuota = v; }
    public Integer getAdmittedCount() { return admittedCount; }
    public void setAdmittedCount(Integer v) { this.admittedCount = v; }
    public Integer getRetestCount() { return retestCount; }
    public void setRetestCount(Integer v) { this.retestCount = v; }
    public BigDecimal getRetestAdmissionRatio() { return retestAdmissionRatio; }
    public void setRetestAdmissionRatio(BigDecimal v) { this.retestAdmissionRatio = v; }
    public Integer getDataYear() { return dataYear; }
    public void setDataYear(Integer v) { this.dataYear = v; }
    public String getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(String v) { this.dataCompleteness = v; }
    public String getDataCompletenessText() { return dataCompletenessText; }
    public void setDataCompletenessText(String v) { this.dataCompletenessText = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String v) { this.sourceTitle = v; }
    public String getSourceOwner() { return sourceOwner; }
    public void setSourceOwner(String v) { this.sourceOwner = v; }
    public String getFitLevel() { return fitLevel; }
    public void setFitLevel(String v) { this.fitLevel = v; }
    public String getFitLevelLabel() { return fitLevelLabel; }
    public void setFitLevelLabel(String v) { this.fitLevelLabel = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { this.sourceName = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { this.sourceType = v; }
}
