package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class Subject extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private String subjectType;
    private String examCategory;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getExamCategory() { return examCategory; }
    public void setExamCategory(String examCategory) { this.examCategory = examCategory; }
}
