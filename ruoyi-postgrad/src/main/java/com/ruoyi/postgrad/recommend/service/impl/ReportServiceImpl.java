package com.ruoyi.postgrad.recommend.service.impl;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.recommend.domain.ReportSummaryVO;
import com.ruoyi.postgrad.recommend.domain.ReportVO;
import com.ruoyi.postgrad.recommend.service.IReportService;

/**
 * 报告服务实现。
 *
 * <p>TODO: 实现 generateReport / getReport / listReports</p>
 */
@Service
public class ReportServiceImpl implements IReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final String REPORT_KEY_PREFIX = "ai:v2:report:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RecommendationLogMapper logMapper;

    // TODO: 注入 RecommendationMapper（水合完整数据用）

    @Override
    public ReportVO generateReport(Long userId) {
        // TODO: 从 Redis 读取当前草稿 → 校验 → 水合完整数据 → 插入 recommendation_log → 缓存 Redis → 返回 ReportVO
        throw new UnsupportedOperationException("TODO: implement generateReport");
    }

    @Override
    public ReportVO getReport(Long userId, Long reportId) {
        // TODO: 先查 Redis 缓存 → 未命中则查 DB → 校验所有权 → 返回 ReportVO
        throw new UnsupportedOperationException("TODO: implement getReport");
    }

    @Override
    public List<ReportSummaryVO> listReports(Long userId) {
        // TODO: 查询 recommendation_log where rule_version='ai-v2' → 解析摘要字段 → 返回列表
        throw new UnsupportedOperationException("TODO: implement listReports");
    }

    // ── private helpers (to be added) ──

    private String reportKey(Long reportId) {
        return REPORT_KEY_PREFIX + reportId;
    }
}
