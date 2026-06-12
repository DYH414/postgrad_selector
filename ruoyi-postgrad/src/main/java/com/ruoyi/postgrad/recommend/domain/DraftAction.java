package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * AI 对话解析出的草稿操作指令。
 * <p>对话不直接改草稿——前端读取 DraftAction 后调用 DraftService API 执行。</p>
 */
public class DraftAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型：remove | replace | analyze | none */
    private String type;

    /** 操作目标 programId */
    private Long programId;

    /** 替换时指定目标档位 */
    private String tier;

    /** 替换偏好：safer | higher_tier | closer_region */
    private String preference;

    /** AI 解释操作原因 */
    private String reason;

    // ── getters / setters ──

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getPreference() { return preference; }
    public void setPreference(String preference) { this.preference = preference; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
