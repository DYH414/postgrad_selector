package com.ruoyi.postgrad.domain;

import java.util.List;

/**
 * 推荐引擎请求参数
 */
public class RecommendationRequest
{
    /** 用户预估初试总分 */
    private int estimatedScore;

    /** 目标省份列表 */
    private List<String> targetProvinces;

    /** 目标专业代码列表（如 085404, 081200） */
    private List<String> programCodes;

    /** 目标专业方向 key 列表，由后端展开为 programCodes */
    private List<String> directionKeys;

    /** 是否接受非全日制 */
    private boolean acceptPartTime;

    /** 是否接受学术学位 */
    private boolean acceptAcademic;

    /** 风险偏好: conservative/balanced/aggressive */
    private String riskPreference = "balanced";

    /** 是否返回不建议档 */
    private boolean includeNotRecommended;

    /** 是否返回溢出结果 */
    private boolean includeOverflow;

    /** 筛选模式：拟录取分 ±N */
    private int scoreRange = 20;

    // ── getters & setters ──

    public int getEstimatedScore() { return estimatedScore; }
    public void setEstimatedScore(int estimatedScore) { this.estimatedScore = estimatedScore; }

    public List<String> getTargetProvinces() { return targetProvinces; }
    public void setTargetProvinces(List<String> targetProvinces) { this.targetProvinces = targetProvinces; }

    public List<String> getProgramCodes() { return programCodes; }
    public void setProgramCodes(List<String> programCodes) { this.programCodes = programCodes; }

    public List<String> getDirectionKeys() { return directionKeys; }
    public void setDirectionKeys(List<String> directionKeys) { this.directionKeys = directionKeys; }

    public boolean isAcceptPartTime() { return acceptPartTime; }
    public void setAcceptPartTime(boolean acceptPartTime) { this.acceptPartTime = acceptPartTime; }

    public boolean isAcceptAcademic() { return acceptAcademic; }
    public void setAcceptAcademic(boolean acceptAcademic) { this.acceptAcademic = acceptAcademic; }

    public String getRiskPreference() { return riskPreference; }
    public void setRiskPreference(String riskPreference) { this.riskPreference = riskPreference; }

    public boolean isIncludeNotRecommended() { return includeNotRecommended; }
    public void setIncludeNotRecommended(boolean includeNotRecommended) { this.includeNotRecommended = includeNotRecommended; }

    public boolean isIncludeOverflow() { return includeOverflow; }
    public void setIncludeOverflow(boolean includeOverflow) { this.includeOverflow = includeOverflow; }

    public int getScoreRange() { return scoreRange; }
    public void setScoreRange(int scoreRange) { this.scoreRange = scoreRange; }
}
