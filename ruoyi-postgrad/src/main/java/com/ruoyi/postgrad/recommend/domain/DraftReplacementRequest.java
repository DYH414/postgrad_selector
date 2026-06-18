package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * Single replacement request for batch replace.
 */
public class DraftReplacementRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long removeProgramId;
    private Long addProgramId;

    public Long getRemoveProgramId() { return removeProgramId; }
    public void setRemoveProgramId(Long removeProgramId) { this.removeProgramId = removeProgramId; }
    public Long getAddProgramId() { return addProgramId; }
    public void setAddProgramId(Long addProgramId) { this.addProgramId = addProgramId; }
}
