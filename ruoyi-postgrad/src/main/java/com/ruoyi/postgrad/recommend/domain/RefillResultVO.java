package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 填充结果 —— 单次移除/替换后的填充响应。
 */
public class RefillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 填充策略：auto / confirm / none */
    private String policy;

    /** 自动填充的候选（policy=auto 时有值） */
    private CandidateCardVO filled;

    /** 待确认的候选列表（policy=confirm 时有值，通常 3 个） */
    private List<RefillCandidateVO> candidates = new ArrayList<>();

    /** 无法填充的原因（policy=none 时有值） */
    private String reason;

    // ── getters / setters ──

    public String getPolicy() { return policy; }
    public void setPolicy(String policy) { this.policy = policy; }

    public CandidateCardVO getFilled() { return filled; }
    public void setFilled(CandidateCardVO filled) { this.filled = filled; }

    public List<RefillCandidateVO> getCandidates() { return candidates; }
    public void setCandidates(List<RefillCandidateVO> candidates) { this.candidates = candidates; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
