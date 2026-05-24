package com.ruoyi.postgrad.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

public class DataSource extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sourceType;
    private String url;
    private String title;
    private String sourceOwner;
    private Date publishDate;
    private String localFilePath;
    private String fileHash;
    private String pageHash;
    private String fetchedAt;
    private Integer robotsChecked;
    private Integer robotsAllowed;
    private Integer termsChecked;
    private String commercialUseRisk;
    private String copyrightRisk;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceOwner() { return sourceOwner; }
    public void setSourceOwner(String sourceOwner) { this.sourceOwner = sourceOwner; }
    public Date getPublishDate() { return publishDate; }
    public void setPublishDate(Date publishDate) { this.publishDate = publishDate; }
    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getPageHash() { return pageHash; }
    public void setPageHash(String pageHash) { this.pageHash = pageHash; }
    public String getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(String fetchedAt) { this.fetchedAt = fetchedAt; }
    public Integer getRobotsChecked() { return robotsChecked; }
    public void setRobotsChecked(Integer robotsChecked) { this.robotsChecked = robotsChecked; }
    public Integer getRobotsAllowed() { return robotsAllowed; }
    public void setRobotsAllowed(Integer robotsAllowed) { this.robotsAllowed = robotsAllowed; }
    public Integer getTermsChecked() { return termsChecked; }
    public void setTermsChecked(Integer termsChecked) { this.termsChecked = termsChecked; }
    public String getCommercialUseRisk() { return commercialUseRisk; }
    public void setCommercialUseRisk(String commercialUseRisk) { this.commercialUseRisk = commercialUseRisk; }
    public String getCopyrightRisk() { return copyrightRisk; }
    public void setCopyrightRisk(String copyrightRisk) { this.copyrightRisk = copyrightRisk; }
}
