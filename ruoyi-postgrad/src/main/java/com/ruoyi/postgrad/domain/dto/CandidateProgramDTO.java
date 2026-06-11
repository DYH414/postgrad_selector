package com.ruoyi.postgrad.domain.dto;

import com.ruoyi.postgrad.domain.RowMap;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选学校传输对象 —— 替代裸 Map 在 AI 模块内部传递候选学校数据。
 *
 * <p>职责：从 MyBatis RowMap 转换而来，携带 DB 原始字段 + 后端计算字段（gap / canBeSafe 等），
 * 在 AiCandidatePoolService → AiRecommendationService → AiRecommendationTools →
 * AiReportBuilder → PreselectService 之间流转。</p>
 *
 * <p>不直接暴露给前端 API。</p>
 */
public class CandidateProgramDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 标识 ──
    private Long programId;

    // ── 学校信息 ──
    private String schoolName;
    private String schoolTier;
    private String city;
    private String province;
    private Boolean is985;
    private Boolean is211;
    private Boolean isDoubleFirst;

    // ── 学院/专业 ──
    private String collegeName;
    private String programName;
    private String programCode;
    private String degreeType;
    private String studyMode;

    // ── 考试科目 ──
    private String examCombo;
    private String subjectCodes;
    private String subjectNames;

    // ── 分数 ──
    private Integer scoreLine;
    private Integer avgAdmittedScore;
    private Integer admissionLow;
    private Integer admissionHigh;

    // ── 招生 ──
    private Integer planCount;
    private Integer unifiedExamQuota;
    private Integer admittedCount;
    private Integer retestCount;

    // ── 数据质量 ──
    private Integer dataYear;
    private String dataCompleteness;
    private String sourceUrl;
    private String sourceOwner;

    // ── 计算字段 ──
    private int gap;
    private boolean canBeSafe;
    private String quotaRisk;
    private String safeBlockReason;

    // ── 工厂方法 ──

    /** 从 MyBatis RowMap 创建 DTO，同步计算 gap（需要 estimatedScore） */
    public static CandidateProgramDTO fromRowMap(RowMap row, int estimatedScore) {
        CandidateProgramDTO dto = new CandidateProgramDTO();
        dto.programId = longVal(row.get("programId"));
        dto.schoolName = strVal(row.get("schoolName"));
        dto.schoolTier = strVal(row.get("schoolTier"));
        dto.city = strVal(row.get("city"));
        dto.province = strVal(row.get("province"));
        dto.is985 = boolVal(row.get("is985"));
        dto.is211 = boolVal(row.get("is211"));
        dto.isDoubleFirst = boolVal(row.get("isDoubleFirst"));
        dto.collegeName = strVal(row.get("collegeName"));
        dto.programName = strVal(row.get("programName"));
        dto.programCode = strVal(row.get("programCode"));
        dto.degreeType = strVal(row.get("degreeType"));
        dto.studyMode = strVal(row.get("studyMode"));
        dto.examCombo = strVal(row.get("examCombo"));
        dto.subjectCodes = strVal(row.get("subjectCodes"));
        dto.subjectNames = strVal(row.get("subjectNames"));
        dto.scoreLine = intVal(row.get("scoreLine"));
        dto.avgAdmittedScore = intVal(row.get("avgAdmittedScore"));
        dto.admissionLow = intVal(row.get("admissionLow"));
        dto.admissionHigh = intVal(row.get("admissionHigh"));
        dto.planCount = intVal(row.get("planCount"));
        dto.unifiedExamQuota = intVal(row.get("unifiedExamQuota"));
        dto.admittedCount = intVal(row.get("admittedCount"));
        dto.retestCount = intVal(row.get("retestCount"));
        dto.dataYear = intVal(row.get("dataYear"));
        dto.dataCompleteness = strVal(row.get("dataCompleteness"));
        dto.sourceUrl = strVal(row.get("sourceUrl"));
        dto.sourceOwner = strVal(row.get("sourceOwner"));
        // 计算字段
        Integer avg = dto.avgAdmittedScore;
        dto.gap = avg != null ? estimatedScore - avg : 0;
        return dto;
    }

    /** 转为 Map（向后兼容，供 JSON 序列化到 Redis 等场景使用） */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programId", programId);
        m.put("schoolName", schoolName);
        m.put("schoolTier", schoolTier);
        m.put("city", city);
        m.put("province", province);
        m.put("is985", is985);
        m.put("is211", is211);
        m.put("isDoubleFirst", isDoubleFirst);
        m.put("collegeName", collegeName);
        m.put("programName", programName);
        m.put("programCode", programCode);
        m.put("degreeType", degreeType);
        m.put("studyMode", studyMode);
        m.put("examCombo", examCombo);
        m.put("subjectCodes", subjectCodes);
        m.put("subjectNames", subjectNames);
        m.put("scoreLine", scoreLine);
        m.put("avgAdmittedScore", avgAdmittedScore);
        m.put("admissionLow", admissionLow);
        m.put("admissionHigh", admissionHigh);
        m.put("planCount", planCount);
        m.put("unifiedExamQuota", unifiedExamQuota);
        m.put("admittedCount", admittedCount);
        m.put("retestCount", retestCount);
        m.put("dataYear", dataYear);
        m.put("dataCompleteness", dataCompleteness);
        m.put("sourceUrl", sourceUrl);
        m.put("sourceOwner", sourceOwner);
        m.put("gap", gap);
        m.put("canBeSafe", canBeSafe);
        m.put("quotaRisk", quotaRisk);
        m.put("safeBlockReason", safeBlockReason);
        return m;
    }

    // ── 辅助转换 ──

    private static String strVal(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static Long longVal(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return null;
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static Boolean boolVal(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() == 1;
        if (v == null) return null;
        String s = String.valueOf(v);
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getSchoolTier() { return schoolTier; }
    public void setSchoolTier(String schoolTier) { this.schoolTier = schoolTier; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public Boolean getIs985() { return is985; }
    public void setIs985(Boolean is985) { this.is985 = is985; }

    public Boolean getIs211() { return is211; }
    public void setIs211(Boolean is211) { this.is211 = is211; }

    public Boolean getIsDoubleFirst() { return isDoubleFirst; }
    public void setIsDoubleFirst(Boolean isDoubleFirst) { this.isDoubleFirst = isDoubleFirst; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String degreeType) { this.degreeType = degreeType; }

    public String getStudyMode() { return studyMode; }
    public void setStudyMode(String studyMode) { this.studyMode = studyMode; }

    public String getExamCombo() { return examCombo; }
    public void setExamCombo(String examCombo) { this.examCombo = examCombo; }

    public String getSubjectCodes() { return subjectCodes; }
    public void setSubjectCodes(String subjectCodes) { this.subjectCodes = subjectCodes; }

    public String getSubjectNames() { return subjectNames; }
    public void setSubjectNames(String subjectNames) { this.subjectNames = subjectNames; }

    public Integer getScoreLine() { return scoreLine; }
    public void setScoreLine(Integer scoreLine) { this.scoreLine = scoreLine; }

    public Integer getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(Integer avgAdmittedScore) { this.avgAdmittedScore = avgAdmittedScore; }

    public Integer getAdmissionLow() { return admissionLow; }
    public void setAdmissionLow(Integer admissionLow) { this.admissionLow = admissionLow; }

    public Integer getAdmissionHigh() { return admissionHigh; }
    public void setAdmissionHigh(Integer admissionHigh) { this.admissionHigh = admissionHigh; }

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

    public int getGap() { return gap; }
    public void setGap(int gap) { this.gap = gap; }

    public boolean isCanBeSafe() { return canBeSafe; }
    public void setCanBeSafe(boolean canBeSafe) { this.canBeSafe = canBeSafe; }

    public String getQuotaRisk() { return quotaRisk; }
    public void setQuotaRisk(String quotaRisk) { this.quotaRisk = quotaRisk; }

    public String getSafeBlockReason() { return safeBlockReason; }
    public void setSafeBlockReason(String safeBlockReason) { this.safeBlockReason = safeBlockReason; }
}
