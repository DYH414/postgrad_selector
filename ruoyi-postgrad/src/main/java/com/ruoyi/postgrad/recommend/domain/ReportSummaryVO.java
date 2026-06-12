package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告列表项 —— 只包含摘要信息，点击后通过 reportId 查询完整报告。
 */
public class ReportSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long reportId;
    private String summary;
    private LocalDateTime createdAt;

    // ── getters / setters ──

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
