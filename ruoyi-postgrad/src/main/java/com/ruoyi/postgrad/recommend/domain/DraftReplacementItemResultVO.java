package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * Result for a single replacement in batchReplace.
 */
public class DraftReplacementItemResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean ok;
    private Long removeProgramId;
    private Long addProgramId;
    private String tier;
    private String removedSchoolName;
    private String addedSchoolName;
    private String error;
    private String message;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public Long getRemoveProgramId() { return removeProgramId; }
    public void setRemoveProgramId(Long removeProgramId) { this.removeProgramId = removeProgramId; }
    public Long getAddProgramId() { return addProgramId; }
    public void setAddProgramId(Long addProgramId) { this.addProgramId = addProgramId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getRemovedSchoolName() { return removedSchoolName; }
    public void setRemovedSchoolName(String removedSchoolName) { this.removedSchoolName = removedSchoolName; }
    public String getAddedSchoolName() { return addedSchoolName; }
    public void setAddedSchoolName(String addedSchoolName) { this.addedSchoolName = addedSchoolName; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
