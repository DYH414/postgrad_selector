package com.ruoyi.postgrad.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RecommendationRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ruleScope;
    private Integer steadyGapMin;
    private Integer focusGapMin;
    private Integer reachGapMin;
    private Integer smallPlanThreshold;
    private Integer highScoreGapThreshold;
    private Integer waveThreshold;
    private BigDecimal retestRatioWarning;
    private Integer isActive;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleScope() { return ruleScope; }
    public void setRuleScope(String ruleScope) { this.ruleScope = ruleScope; }
    public Integer getSteadyGapMin() { return steadyGapMin; }
    public void setSteadyGapMin(Integer steadyGapMin) { this.steadyGapMin = steadyGapMin; }
    public Integer getFocusGapMin() { return focusGapMin; }
    public void setFocusGapMin(Integer focusGapMin) { this.focusGapMin = focusGapMin; }
    public Integer getReachGapMin() { return reachGapMin; }
    public void setReachGapMin(Integer reachGapMin) { this.reachGapMin = reachGapMin; }
    public Integer getSmallPlanThreshold() { return smallPlanThreshold; }
    public void setSmallPlanThreshold(Integer smallPlanThreshold) { this.smallPlanThreshold = smallPlanThreshold; }
    public Integer getHighScoreGapThreshold() { return highScoreGapThreshold; }
    public void setHighScoreGapThreshold(Integer highScoreGapThreshold) { this.highScoreGapThreshold = highScoreGapThreshold; }
    public Integer getWaveThreshold() { return waveThreshold; }
    public void setWaveThreshold(Integer waveThreshold) { this.waveThreshold = waveThreshold; }
    public BigDecimal getRetestRatioWarning() { return retestRatioWarning; }
    public void setRetestRatioWarning(BigDecimal retestRatioWarning) { this.retestRatioWarning = retestRatioWarning; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("ruleScope", getRuleScope())
            .append("steadyGapMin", getSteadyGapMin())
            .append("focusGapMin", getFocusGapMin())
            .append("reachGapMin", getReachGapMin())
            .append("isActive", getIsActive())
            .toString();
    }
}
