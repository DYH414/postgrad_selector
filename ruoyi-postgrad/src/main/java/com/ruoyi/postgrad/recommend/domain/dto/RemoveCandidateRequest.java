package com.ruoyi.postgrad.recommend.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 移除候选请求体。
 */
public class RemoveCandidateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要移除的候选 programId */
    @NotNull
    private Long programId;

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
}
