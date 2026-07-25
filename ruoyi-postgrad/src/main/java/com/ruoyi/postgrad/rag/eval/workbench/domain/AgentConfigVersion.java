package com.ruoyi.postgrad.rag.eval.workbench.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不可变的评测 Agent 配置版本。创建后文本不可改，改 Prompt 须建新版本。 */
public class AgentConfigVersion {
    private Long id;
    private String versionName;
    private String businessPromptText;
    private String businessPromptChecksum;
    private String routingPromptText;
    private String routingPromptChecksum;
    private String model;
    private BigDecimal temperature;
    private String toolCatalogChecksum;
    private String ragConfigSnapshotJson;
    private String gitCommit;
    private String status; // ACTIVE / ARCHIVED
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getBusinessPromptText() { return businessPromptText; }
    public void setBusinessPromptText(String v) { this.businessPromptText = v; }
    public String getBusinessPromptChecksum() { return businessPromptChecksum; }
    public void setBusinessPromptChecksum(String v) { this.businessPromptChecksum = v; }
    public String getRoutingPromptText() { return routingPromptText; }
    public void setRoutingPromptText(String v) { this.routingPromptText = v; }
    public String getRoutingPromptChecksum() { return routingPromptChecksum; }
    public void setRoutingPromptChecksum(String v) { this.routingPromptChecksum = v; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public String getToolCatalogChecksum() { return toolCatalogChecksum; }
    public void setToolCatalogChecksum(String v) { this.toolCatalogChecksum = v; }
    public String getRagConfigSnapshotJson() { return ragConfigSnapshotJson; }
    public void setRagConfigSnapshotJson(String v) { this.ragConfigSnapshotJson = v; }
    public String getGitCommit() { return gitCommit; }
    public void setGitCommit(String gitCommit) { this.gitCommit = gitCommit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
