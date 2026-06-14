package com.ruoyi.postgrad.recommend.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ReportSummaryVO;
import com.ruoyi.postgrad.recommend.domain.ReportVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.IReportService;

/**
 * 报告服务实现 —— 草稿快照生成最终报告 + 历史报告查询。
 * <p>最终报告的候选集与生成时草稿完全一致，不新增、不删除、不替换。</p>
 */
@Service
public class ReportServiceImpl implements IReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final String REPORT_KEY_PREFIX = "ai:v2:report:";
    private static final String RULE_VERSION = "ai-v2";
    private static final long TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RecommendationLogMapper logMapper;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public ReportVO generateReport(Long userId) {
        // 1. 从 Redis 读取当前草稿
        String draftJson = redisTemplate.opsForValue().get(DraftServiceImpl.DRAFT_KEY_PREFIX + userId);
        if (draftJson == null || draftJson.isBlank()) {
            throw new IllegalArgumentException("草稿不存在，请先生成 AI 推荐草稿");
        }

        DraftVO draft;
        try {
            draft = JSON.parseObject(draftJson, DraftVO.class);
        } catch (Exception e) {
            throw new IllegalStateException("草稿数据解析失败，请重新生成草稿");
        }

        // 2. 校验：至少 1 个可信候选
        int totalCount = 0;
        if (draft.getTiers() != null) {
            for (TierCandidates t : draft.getTiers()) {
                if (t.getCandidates() != null) totalCount += t.getCandidates().size();
            }
        }
        if (totalCount == 0) {
            throw new IllegalArgumentException("草稿中没有可信候选，无法生成报告。请先生成 AI 推荐草稿");
        }

        // 3. 水合完整学校数据（深拷贝，不受后续草稿修改影响）
        List<TierCandidates> snapshotTiers = deepCopyTiers(draft.getTiers(), userId);

        // 4. 生成摘要
        String summary = buildSummary(snapshotTiers);

        // 5. 构建 ReportVO
        ReportVO report = new ReportVO();
        report.setTiers(snapshotTiers);
        report.setSummary(summary);
        report.setProfileBasis(draft.getProfileBasis());
        report.setStatus("COMPLETED");
        report.setCreatedAt(LocalDateTime.now());

        // 6. 写入 recommendation_log
        RecommendationLog recLog = new RecommendationLog();
        recLog.setUserId(userId);
        recLog.setProfileSnapshot(draft.getProfileBasis() != null
            ? JSON.toJSONString(draft.getProfileBasis()) : "{}");
        recLog.setResultJson(JSON.toJSONString(report));
        recLog.setRuleVersion(RULE_VERSION);
        recLog.setDataVersion("1.0");
        recLog.setIsPaid(0);
        logMapper.insertRecommendationLog(recLog);

        Long reportId = recLog.getId();
        report.setReportId(reportId);

        // 7. 缓存 Redis
        redisTemplate.opsForValue().set(
            REPORT_KEY_PREFIX + reportId,
            JSON.toJSONString(report),
            Duration.ofDays(TTL_DAYS));

        log.info("[Report] Generated reportId={} for userId={}, {} schools", reportId, userId, totalCount);
        return report;
    }

    @Override
    public ReportVO getReport(Long userId, Long reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId 不能为空");
        }

        // 1. 先查 Redis 缓存
        String cached = redisTemplate.opsForValue().get(REPORT_KEY_PREFIX + reportId);
        if (cached != null && !cached.isBlank()) {
            try {
                ReportVO report = JSON.parseObject(cached, ReportVO.class);
                if (report != null) {
                    report.setReportId(reportId);
                    report.setStatus("COMPLETED");
                    return report;
                }
            } catch (Exception e) {
                log.warn("[Report] Failed to parse cached report {}, falling back to DB", reportId);
            }
        }

        // 2. 查 DB
        RowMap row = logMapper.selectLogByIdAndUserId(reportId, userId);
        if (row == null) {
            throw new IllegalArgumentException("报告不存在或无权访问");
        }

        String resultJson = (String) row.get("result_json");
        ReportVO report;
        try {
            report = JSON.parseObject(resultJson, ReportVO.class);
        } catch (Exception e) {
            log.warn("[Report] Failed to parse report {} from DB: {}", reportId, e.getMessage());
            throw new IllegalStateException("报告数据解析失败");
        }

        report.setReportId(reportId);
        report.setStatus("COMPLETED");
        Object createdAt = row.get("created_at");
        if (createdAt != null && report.getCreatedAt() == null) {
            report.setCreatedAt(createdAt instanceof java.util.Date d
                ? d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.parse(createdAt.toString()));
        }

        // 3. 回写 Redis 缓存
        redisTemplate.opsForValue().set(
            REPORT_KEY_PREFIX + reportId,
            JSON.toJSONString(report),
            Duration.ofDays(TTL_DAYS));

        return report;
    }

    @Override
    public List<ReportSummaryVO> listReports(Long userId) {
        List<RowMap> rows = logMapper.selectReportListByRuleVersion(userId, RULE_VERSION);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<ReportSummaryVO> result = new ArrayList<>(rows.size());
        for (RowMap row : rows) {
            ReportSummaryVO vo = new ReportSummaryVO();
            Object idObj = row.get("id");
            if (idObj instanceof Number n) vo.setReportId(n.longValue());

            // 从 result_json 提取 summary
            String resultJson = (String) row.get("result_json");
            if (resultJson != null && !resultJson.isBlank()) {
                try {
                    ReportVO report = JSON.parseObject(resultJson, ReportVO.class);
                    if (report != null && report.getSummary() != null) {
                        vo.setSummary(report.getSummary());
                    }
                } catch (Exception e) {
                    vo.setSummary("报告数据异常");
                }
            }

            Object createdAt = row.get("created_at");
            if (createdAt instanceof java.util.Date d) {
                vo.setCreatedAt(d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            } else if (createdAt != null) {
                try {
                    vo.setCreatedAt(LocalDateTime.parse(createdAt.toString()));
                } catch (Exception e) { /* ignore */ }
            }

            result.add(vo);
        }
        return result;
    }

    // ── private helpers ──

    /**
     * 深拷贝草稿 tiers，并通过 DB 水合每个候选的完整 SchoolFact。
     * <p>快照独立于草稿 Redis 数据，后续草稿修改不影响已生成的报告。</p>
     */
    private List<TierCandidates> deepCopyTiers(List<TierCandidates> source, Long userId) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        // 收集所有 programId → 批量查询 DB
        List<Long> allIds = new ArrayList<>();
        for (TierCandidates t : source) {
            if (t.getCandidates() != null) {
                for (CandidateCardVO c : t.getCandidates()) {
                    if (c.getFact() != null && c.getFact().getProgramId() != null) {
                        allIds.add(c.getFact().getProgramId());
                    }
                }
            }
        }

        // 批量水合（使用默认分数 300，不影响已计算的 gap）
        Map<Long, SchoolFact> hydratedMap = new LinkedHashMap<>();
        if (!allIds.isEmpty()) {
            List<RowMap> rows = recommendationMapper.selectProgramsByIds(allIds, 300);
            for (RowMap row : rows) {
                Object pidObj = row.get("programId");
                if (pidObj instanceof Number n) {
                    SchoolFact fact = SchoolFact.fromRow(row);
                    // 保留原草稿中的 gap/canBeSafe 等计算字段
                    hydratedMap.put(n.longValue(), fact);
                }
            }
        }

        // 构建快照
        List<TierCandidates> snapshot = new ArrayList<>(source.size());
        for (TierCandidates srcTier : source) {
            TierCandidates snapTier = new TierCandidates();
            snapTier.setLevel(srcTier.getLevel());
            snapTier.setLabel(srcTier.getLabel());
            snapTier.setTargetCount(srcTier.getTargetCount());

            List<CandidateCardVO> snapCards = new ArrayList<>();
            if (srcTier.getCandidates() != null) {
                for (CandidateCardVO c : srcTier.getCandidates()) {
                    CandidateCardVO snap = new CandidateCardVO();
                    // 使用水合后的 fact，保留原 opinion
                    SchoolFact hydrated = hydratedMap.get(c.getFact().getProgramId());
                    if (hydrated != null) {
                        // 保留草稿中的计算字段
                        hydrated.setScoreGap(c.getFact().getScoreGap());
                        hydrated.setGapLabel(c.getFact().getGapLabel());
                        hydrated.setQuotaLabel(c.getFact().getQuotaLabel());
                        hydrated.setQuotaRisk(c.getFact().getQuotaRisk());
                        hydrated.setCanBeSafe(c.getFact().getCanBeSafe());
                        hydrated.setSafeBlockReason(c.getFact().getSafeBlockReason());
                        snap.setFact(hydrated);
                    } else {
                        snap.setFact(c.getFact()); // DB 中查不到，用草稿原数据
                    }
                    snap.setOpinion(c.getOpinion());
                    snap.setFinalJudgement(c.getFinalJudgement());
                    snap.setAdjusted(c.getAdjusted());
                    snap.setAdjustReason(c.getAdjustReason());
                    snap.setStatus("selected");
                    snapCards.add(snap);
                }
            }
            snapTier.setCandidates(snapCards);
            snapTier.setInsufficient(srcTier.isInsufficient());
            snapTier.setInsufficientReason(srcTier.getInsufficientReason());
            snapshot.add(snapTier);
        }
        return snapshot;
    }

    /**
     * 生成报告摘要。
     */
    private String buildSummary(List<TierCandidates> tiers) {
        int reach = 0, steady = 0, safe = 0;
        List<String> notes = new ArrayList<>();
        for (TierCandidates t : tiers) {
            int count = t.getCandidates() != null ? t.getCandidates().size() : 0;
            switch (t.getLevel()) {
                case "reach" -> reach = count;
                case "steady" -> steady = count;
                case "safe" -> safe = count;
            }
            if (t.isInsufficient() && t.getInsufficientReason() != null) {
                notes.add(t.getInsufficientReason());
            }
        }
        int total = reach + steady + safe;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("基于你的画像，在候选池中选出冲刺 %d 所、稳妥 %d 所、保底 %d 所，共 %d 所学校。",
            reach, steady, safe, total));
        if (!notes.isEmpty()) {
            sb.append(" 注意：").append(String.join("；", notes));
        }
        return sb.toString();
    }

}
