package com.ruoyi.postgrad.recommend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 替换候选请求体。
 */
public class ReplaceCandidateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要移除的候选 programId */
    @NotNull
    private Long removeProgramId;

    /** 目标档位：reach / steady / safe */
    @NotBlank
    private String tier;

    /** 替换偏好：safer / higher_tier / closer_region */
    private String preference;

    // ── getters / setters ──

    public Long getRemoveProgramId() { return removeProgramId; }
    public void setRemoveProgramId(Long removeProgramId) { this.removeProgramId = removeProgramId; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getPreference() { return preference; }
    public void setPreference(String preference) { this.preference = preference; }
}
