package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class Program extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long collegeId;
    private String programCode;
    private String programName;
    private String researchDirection;
    private String studyMode;
    private String degreeType;
    private Integer is408;
    private Integer protectsFirstChoice;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCollegeId() { return collegeId; }
    public void setCollegeId(Long collegeId) { this.collegeId = collegeId; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public String getResearchDirection() { return researchDirection; }
    public void setResearchDirection(String researchDirection) { this.researchDirection = researchDirection; }
    public String getStudyMode() { return studyMode; }
    public void setStudyMode(String studyMode) { this.studyMode = studyMode; }
    public String getDegreeType() { return degreeType; }
    public void setDegreeType(String degreeType) { this.degreeType = degreeType; }
    public Integer getIs408() { return is408; }
    public void setIs408(Integer is408) { this.is408 = is408; }
    public Integer getProtectsFirstChoice() { return protectsFirstChoice; }
    public void setProtectsFirstChoice(Integer protectsFirstChoice) { this.protectsFirstChoice = protectsFirstChoice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
