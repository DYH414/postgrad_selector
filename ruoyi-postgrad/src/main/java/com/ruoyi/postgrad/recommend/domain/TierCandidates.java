package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 单档候选集合 —— 档位标签 + 候选列表 + 是否不足。
 * <p>用于候选池分档结果和草稿中的每档展示。</p>
 */
public class TierCandidates implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档位标识：reach / steady / safe */
    private String level;

    /** 档位中文标签：冲刺档 / 稳妥档 / 保底档 */
    private String label;

    /** 目标候选数量 */
    private int targetCount;

    /** 当前档位的候选列表 */
    private List<CandidateCardVO> candidates;

    /** 是否候选不足 */
    private boolean insufficient;

    /** 不足原因（仅 insufficient=true 时有值） */
    private String insufficientReason;

    // ── getters / setters ──

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getTargetCount() { return targetCount; }
    public void setTargetCount(int targetCount) { this.targetCount = targetCount; }

    public List<CandidateCardVO> getCandidates() { return candidates; }
    public void setCandidates(List<CandidateCardVO> candidates) { this.candidates = candidates; }

    public boolean isInsufficient() { return insufficient; }
    public void setInsufficient(boolean insufficient) { this.insufficient = insufficient; }

    public String getInsufficientReason() { return insufficientReason; }
    public void setInsufficientReason(String insufficientReason) { this.insufficientReason = insufficientReason; }
}
