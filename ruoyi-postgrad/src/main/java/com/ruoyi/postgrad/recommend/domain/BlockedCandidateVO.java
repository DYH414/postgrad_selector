package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 被 AI 校验拦截的候选 —— 记录被拦截的 programId、学校名和拦截原因。
 * <p>用于前端展示"为什么没选 XX 学校"。</p>
 */
public class BlockedCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被拦截的候选 programId */
    private Long programId;

    /** 学校名称 */
    private String schoolName;

    /** 拦截原因 */
    private String blockReason;

    // ── getters / setters ──

    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
}
