package com.ruoyi.postgrad.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单条推荐结果
 */
public class RecommendationItem
{
    // 主键ID
    private Long schoolId;
    private Long collegeId;
    private Long programId;

    // 学校信息
    private String schoolName;
    private String province;
    private String city;
    private String tier;
    private boolean is985;
    private boolean is211;
    private boolean isDoubleFirst;

    // 专业信息
    private String collegeName;
    private String programCode;
    private String programName;
    private String studyMode;
    private String degreeType;
    private boolean is408;
    private String researchDirection;

    // 分数计算
    private int effectiveScore;
    private int scoreGap;
    private String scoreBasis;

    // 展示数据
    private int scoreLine;
    private int planCount;
    private int retestCount;
    private int minAdmittedScore;
    private int avgAdmittedScore;
    private String sourceUrl;
    private String completenessLevel;

    // 历年数据
    private List<Map<String, Object>> historyScores = new ArrayList<>();

    // 推荐档位
    private String tierLabel;

    // 风险标记
    private List<String> warnings = new ArrayList<>();
    private String warningLevel;
    private List<RecommendationItem> subPrograms = new ArrayList<>();

    // ── getters & setters ──

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }

    public Long getCollegeId() { return collegeId; }
    public void setCollegeId(Long collegeId) { this.collegeId = collegeId; }

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public boolean isIs985() { return is985; }
    public void setIs985(boolean is985) { this.is985 = is985; }

    public boolean isIs211() { return is211; }
    public void setIs211(boolean is211) { this.is211 = is211; }

    public boolean isIsDoubleFirst() { return isDoubleFirst; }
    public void setIsDoubleFirst(boolean isDoubleFirst) { this.isDoubleFirst = isDoubleFirst; }

    public String getCollegeName() { return collegeName; }
    public void setCollegeName(String collegeName) { this.collegeName = collegeName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getStudyMode() { return studyMode; }
    public void setStudyMode(String studyMode) { this.studyMode = studyMode; }

    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String degreeType) { this.degreeType = degreeType; }

    public boolean isIs408() { return is408; }
    public void setIs408(boolean is408) { this.is408 = is408; }

    public String getResearchDirection() { return researchDirection; }
    public void setResearchDirection(String researchDirection) { this.researchDirection = researchDirection; }

    public int getEffectiveScore() { return effectiveScore; }
    public void setEffectiveScore(int effectiveScore) { this.effectiveScore = effectiveScore; }

    public int getScoreGap() { return scoreGap; }
    public void setScoreGap(int scoreGap) { this.scoreGap = scoreGap; }

    public String getScoreBasis() { return scoreBasis; }
    public void setScoreBasis(String scoreBasis) { this.scoreBasis = scoreBasis; }

    public int getScoreLine() { return scoreLine; }
    public void setScoreLine(int scoreLine) { this.scoreLine = scoreLine; }

    public int getPlanCount() { return planCount; }
    public void setPlanCount(int planCount) { this.planCount = planCount; }

    public int getRetestCount() { return retestCount; }
    public void setRetestCount(int retestCount) { this.retestCount = retestCount; }

    public int getMinAdmittedScore() { return minAdmittedScore; }
    public void setMinAdmittedScore(int minAdmittedScore) { this.minAdmittedScore = minAdmittedScore; }

    public int getAvgAdmittedScore() { return avgAdmittedScore; }
    public void setAvgAdmittedScore(int avgAdmittedScore) { this.avgAdmittedScore = avgAdmittedScore; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getCompletenessLevel() { return completenessLevel; }
    public void setCompletenessLevel(String completenessLevel) { this.completenessLevel = completenessLevel; }

    public String getTierLabel() { return tierLabel; }
    public void setTierLabel(String tierLabel) { this.tierLabel = tierLabel; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public String getWarningLevel() { return warningLevel; }
    public void setWarningLevel(String warningLevel) { this.warningLevel = warningLevel; }

    public List<RecommendationItem> getSubPrograms() { return subPrograms; }
    public void setSubPrograms(List<RecommendationItem> subPrograms) { this.subPrograms = subPrograms; }

    public List<Map<String, Object>> getHistoryScores() { return historyScores; }
    public void setHistoryScores(List<Map<String, Object>> historyScores) { this.historyScores = historyScores; }
}
