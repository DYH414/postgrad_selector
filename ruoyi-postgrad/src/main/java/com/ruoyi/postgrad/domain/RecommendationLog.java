package com.ruoyi.postgrad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class RecommendationLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String profileSnapshot;
    private String resultJson;
    private String ruleVersion;
    private String dataVersion;
    private Integer isPaid;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProfileSnapshot() { return profileSnapshot; }
    public void setProfileSnapshot(String profileSnapshot) { this.profileSnapshot = profileSnapshot; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public String getDataVersion() { return dataVersion; }
    public void setDataVersion(String dataVersion) { this.dataVersion = dataVersion; }
    public Integer getIsPaid() { return isPaid; }
    public void setIsPaid(Integer isPaid) { this.isPaid = isPaid; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("ruleVersion", getRuleVersion())
            .append("dataVersion", getDataVersion())
            .append("isPaid", getIsPaid())
            .append("createdAt", getCreatedAt())
            .toString();
    }
}
