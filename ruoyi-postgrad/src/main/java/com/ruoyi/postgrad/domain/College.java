package com.ruoyi.postgrad.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class College extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long schoolId;
    private String name;
    private String website;
    private String graduateUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getGraduateUrl() { return graduateUrl; }
    public void setGraduateUrl(String graduateUrl) { this.graduateUrl = graduateUrl; }
}
