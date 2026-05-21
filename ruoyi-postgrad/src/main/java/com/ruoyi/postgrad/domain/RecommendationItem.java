package com.ruoyi.postgrad.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条推荐结果
 */
public class RecommendationItem
{
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

    // 分数计算
    private int effectiveScore;
    private int scoreGap;
    private String scoreBasis; // 分数来源说明

    // 推荐档位
    private String tierLabel;  // steady/focus/reach/notRecommended/insufficient

    // 风险标记
    private List<String> warnings = new ArrayList<>();
    private String warningLevel;
    private String completenessLevel;
    private List<RecommendationItem> subPrograms = new ArrayList<>();

    // ── getters & setters ──

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

    public int getEffectiveScore() { return effectiveScore; }
    public void setEffectiveScore(int effectiveScore) { this.effectiveScore = effectiveScore; }

    public int getScoreGap() { return scoreGap; }
    public void setScoreGap(int scoreGap) { this.scoreGap = scoreGap; }

    public String getScoreBasis() { return scoreBasis; }
    public void setScoreBasis(String scoreBasis) { this.scoreBasis = scoreBasis; }

    public String getTierLabel() { return tierLabel; }
    public void setTierLabel(String tierLabel) { this.tierLabel = tierLabel; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public String getWarningLevel() { return warningLevel; }
    public void setWarningLevel(String warningLevel) { this.warningLevel = warningLevel; }

    public String getCompletenessLevel() { return completenessLevel; }
    public void setCompletenessLevel(String completenessLevel) { this.completenessLevel = completenessLevel; }

    public List<RecommendationItem> getSubPrograms() { return subPrograms; }
    public void setSubPrograms(List<RecommendationItem> subPrograms) { this.subPrograms = subPrograms; }
}
