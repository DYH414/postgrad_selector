package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作集中单个档位的候选列表。
 */
public class WorkspaceTierVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String level;       // reach / steady / safe
    private String label;       // 冲刺档 / 稳妥档 / 保底档
    private int targetCount;    // 目标数量（30）
    private List<CandidateCardVO> candidates = new ArrayList<>();
    private boolean insufficient;
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
