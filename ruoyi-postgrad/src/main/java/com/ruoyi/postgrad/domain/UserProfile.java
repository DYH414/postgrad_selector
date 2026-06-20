package com.ruoyi.postgrad.domain;

import java.math.BigDecimal;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class UserProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Integer estimatedScore;
    private String examCombo;
    private String targetRegions;
    private Integer acceptPartTime;
    private Integer acceptTransfer;
    private Integer acceptAcademic;
    private Integer acceptJoint;
    private String riskPreference;
    private String schoolTierPreference;
    private String regionStrategy;
    private String undergradTier;
    private String undergraduateMajor;
    private Integer isCrossMajor;
    private String mathLevel;
    private String englishLevel;
    private String csLevel;
    private BigDecimal dailyStudyHours;
    private String reviewProgress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getEstimatedScore() { return estimatedScore; }
    public void setEstimatedScore(Integer estimatedScore) { this.estimatedScore = estimatedScore; }
    public String getExamCombo() { return examCombo; }
    public void setExamCombo(String examCombo) { this.examCombo = examCombo; }
    public String getTargetRegions() { return targetRegions; }
    public void setTargetRegions(String targetRegions) { this.targetRegions = targetRegions; }
    public Integer getAcceptPartTime() { return acceptPartTime; }
    public void setAcceptPartTime(Integer acceptPartTime) { this.acceptPartTime = acceptPartTime; }
    public Integer getAcceptTransfer() { return acceptTransfer; }
    public void setAcceptTransfer(Integer acceptTransfer) { this.acceptTransfer = acceptTransfer; }
    public Integer getAcceptAcademic() { return acceptAcademic; }
    public void setAcceptAcademic(Integer acceptAcademic) { this.acceptAcademic = acceptAcademic; }
    public Integer getAcceptJoint() { return acceptJoint; }
    public void setAcceptJoint(Integer acceptJoint) { this.acceptJoint = acceptJoint; }
    public String getRiskPreference() { return riskPreference; }
    public void setRiskPreference(String riskPreference) { this.riskPreference = riskPreference; }
    public String getSchoolTierPreference() { return schoolTierPreference; }
    public void setSchoolTierPreference(String schoolTierPreference) { this.schoolTierPreference = schoolTierPreference; }
    public String getRegionStrategy() { return regionStrategy; }
    public void setRegionStrategy(String regionStrategy) { this.regionStrategy = regionStrategy; }
    public String getUndergradTier() { return undergradTier; }
    public void setUndergradTier(String undergradTier) { this.undergradTier = undergradTier; }
    public String getUndergraduateMajor() { return undergraduateMajor; }
    public void setUndergraduateMajor(String undergraduateMajor) { this.undergraduateMajor = undergraduateMajor; }
    public Integer getIsCrossMajor() { return isCrossMajor; }
    public void setIsCrossMajor(Integer isCrossMajor) { this.isCrossMajor = isCrossMajor; }
    public String getMathLevel() { return mathLevel; }
    public void setMathLevel(String mathLevel) { this.mathLevel = mathLevel; }
    public String getEnglishLevel() { return englishLevel; }
    public void setEnglishLevel(String englishLevel) { this.englishLevel = englishLevel; }
    public String getCsLevel() { return csLevel; }
    public void setCsLevel(String csLevel) { this.csLevel = csLevel; }
    public BigDecimal getDailyStudyHours() { return dailyStudyHours; }
    public void setDailyStudyHours(BigDecimal dailyStudyHours) { this.dailyStudyHours = dailyStudyHours; }
    public String getReviewProgress() { return reviewProgress; }
    public void setReviewProgress(String reviewProgress) { this.reviewProgress = reviewProgress; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("estimatedScore", getEstimatedScore())
            .append("targetRegions", getTargetRegions())
            .append("riskPreference", getRiskPreference())
            .append("schoolTierPreference", getSchoolTierPreference())
            .append("regionStrategy", getRegionStrategy())
            .append("undergradTier", getUndergradTier())
            .append("undergraduateMajor", getUndergraduateMajor())
            .append("isCrossMajor", getIsCrossMajor())
            .toString();
    }
}
