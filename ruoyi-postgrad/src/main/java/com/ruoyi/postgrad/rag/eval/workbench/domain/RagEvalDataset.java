package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.time.LocalDateTime;

public class RagEvalDataset {
    private Long id;
    private String datasetKey;
    private String name;
    private String version;
    private String datasetType;
    private String lifecycleState;
    private String description;
    private Long sourceDatasetId;
    private String checksumSha256;
    private Integer caseCount;
    private Integer lockVersion;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDatasetType() { return datasetType; }
    public void setDatasetType(String datasetType) { this.datasetType = datasetType; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getSourceDatasetId() { return sourceDatasetId; }
    public void setSourceDatasetId(Long sourceDatasetId) { this.sourceDatasetId = sourceDatasetId; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public Integer getCaseCount() { return caseCount; }
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
