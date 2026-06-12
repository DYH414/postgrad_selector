package com.ruoyi.postgrad.recommend.service;

import java.util.List;

import com.ruoyi.postgrad.recommend.domain.ReportSummaryVO;
import com.ruoyi.postgrad.recommend.domain.ReportVO;

/**
 * 报告服务 —— 草稿快照生成最终报告 + 历史报告查询。
 * <p>报告存储在 DB recommendation_log (rule_version='ai-v2') 和 Redis {@code ai:v2:report:{reportId}}。</p>
 */
public interface IReportService {

    /**
     * 从当前草稿生成最终报告。
     * <p>最终报告候选集必须与生成时草稿完全一致。</p>
     *
     * @param userId 当前用户 ID
     * @return 报告详情
     * @throws IllegalArgumentException 草稿为空（至少需要 1 个可信候选）
     */
    ReportVO generateReport(Long userId);

    /**
     * 查看报告详情。
     *
     * @param userId   当前用户 ID（校验所有权）
     * @param reportId 报告 ID
     * @return 报告详情
     * @throws SecurityException 无权访问此报告
     */
    ReportVO getReport(Long userId, Long reportId);

    /**
     * 我的 v2 报告列表（按生成时间倒序）。
     *
     * @param userId 当前用户 ID
     * @return 报告摘要列表
     */
    List<ReportSummaryVO> listReports(Long userId);
}
