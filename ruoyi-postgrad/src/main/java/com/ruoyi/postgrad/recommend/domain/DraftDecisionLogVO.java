package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 草稿决策日志 —— 记录系统为何选择、跳过、填充或请求确认。
 * <p>用于调试和 AI 解释。</p>
 */
public class DraftDecisionLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型 */
    private String eventType;   // generate / ai_select / remove / auto_refill / confirm_refill / manual_add / replace
    private Long programId;
    private String schoolName;
    private String tier;
    private String actor;       // system / ai_tool / user
    private String reason;
    private String toolResultJson;
    private LocalDateTime createdAt;

    // ── getters / setters ──

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getToolResultJson() { return toolResultJson; }
    public void setToolResultJson(String toolResultJson) { this.toolResultJson = toolResultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
