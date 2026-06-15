package com.ruoyi.postgrad.recommend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.service.ICandidateUniverseService;

/**
 * 候选宇宙服务实现 —— 从 DB 广泛查询，保留尽可能多的候选。
 * <p>与旧 CandidatePoolServiceImpl 的区别：不在这里做分档和截断，
 * 只做硬性过滤（gap 过低的丢弃），其余全部保留。</p>
 */
@Service
public class CandidateUniverseServiceImpl implements ICandidateUniverseService {

    private static final Logger log = LoggerFactory.getLogger(CandidateUniverseServiceImpl.class);

    private static final int SCORE_RANGE = 30;
    private static final String POLICY_VERSION = "v2-hybrid-1";

    private static final List<String> EXAM_408_SUBJECT_CODES = List.of(
        "101,204,302,408",
        "101,201,301,408"
    );

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public CandidateUniverseVO buildUniverse(Long userId, ProfileBasisVO profile,
                                              int estimatedScore, List<String> targetRegions,
                                              String schoolTierPref) {
        CandidateUniverseVO universe = new CandidateUniverseVO();
        universe.setUniverseId(UUID.randomUUID().toString());
        universe.setUserId(userId);
        universe.setProfileSnapshot(profile);
        universe.setGeneratedAt(LocalDateTime.now());
        universe.setPolicyVersion(POLICY_VERSION);

        // 1. 双考试组合 + programId 去重
        List<RowMap> allRows = queryAllExamCombos(targetRegions, estimatedScore);
        int rawCount = allRows.size();

        // 2. RowMap → SchoolFact + 计算字段
        List<SchoolFact> facts = new ArrayList<>(allRows.size());
        for (RowMap row : allRows) {
            SchoolFact fact = SchoolFact.fromRow(row);
            // 计算 gap + 派生字段
            Integer avg = fact.getAvgAdmittedScore();
            int gap = avg != null ? estimatedScore - avg : 0;
            fact.setScoreGap(gap);
            if (gap < -30) continue; // 差距超过 30 分，完全无意义
            // 名额计算
            int quota = fact.getUnifiedExamQuota() != null ? fact.getUnifiedExamQuota()
                : (fact.getPlanCount() != null ? fact.getPlanCount() : 0);
            fact.setQuotaLabel(quotaLabel(quota));
            fact.setQuotaRisk(quotaRisk(quota));
            // 差距标签
            fact.setGapLabel(gap >= 0 ? "+" + gap : String.valueOf(gap));
            // 保底条件
            boolean safe = canBeSafe(quota, fact.getDataCompleteness(),
                fact.getAdmissionLow(), fact.getAdmissionHigh());
            fact.setCanBeSafe(safe);
            if (!safe) {
                fact.setSafeBlockReason(buildSafeBlockReason(quota, fact.getDataCompleteness()));
            }
            facts.add(fact);
        }

        universe.setCandidates(facts);

        // 3. 来源摘要
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rawCount", rawCount);
        summary.put("filteredCount", facts.size());
        summary.put("scoreRange", SCORE_RANGE);
        summary.put("examCombos", EXAM_408_SUBJECT_CODES);
        summary.put("regions", targetRegions != null ? targetRegions : List.of());
        universe.setSourceSummary(summary);

        log.info("[Universe] userId={} raw={} filtered={}", userId, rawCount, facts.size());
        return universe;
    }

    // ── 计算规则（与 CandidatePoolServiceImpl 一致）──

    private String quotaLabel(int quota) {
        if (quota <= 0) return "名额未知";
        if (quota <= 3) return "名额极少";
        if (quota < 10) return "名额偏少";
        if (quota < 20) return "名额正常";
        return "名额充裕";
    }

    private String quotaRisk(int quota) {
        if (quota <= 0) return "unknown";
        if (quota <= 3) return "very_high";
        if (quota < 10) return "high";
        if (quota < 20) return "medium";
        return "normal";
    }

    private boolean canBeSafe(int quota, String completeness,
                               Integer admissionLow, Integer admissionHigh) {
        if (quota <= 3) return false;
        if (quota < 10) {
            boolean hasRange = admissionLow != null || admissionHigh != null;
            if ("C".equalsIgnoreCase(completeness) || !hasRange) return false;
        }
        return true;
    }

    private String buildSafeBlockReason(int quota, String completeness) {
        if (quota <= 3) return "统考名额仅" + quota + "人，录取波动极大，不能作为保底";
        if (quota < 10) return "统考名额仅" + quota + "人，数据不足以支撑保底判断";
        if ("C".equalsIgnoreCase(completeness)) return "数据完整度较低，不能作为保底";
        return "不满足保底条件";
    }

    private List<RowMap> queryAllExamCombos(List<String> regions, int estimatedScore) {
        List<RowMap> merged = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (String subjectCodes : EXAM_408_SUBJECT_CODES) {
            List<RowMap> rows = recommendationMapper.selectCandidates(
                subjectCodes, regions, null, estimatedScore, SCORE_RANGE, "full_time");
            if (rows != null) {
                for (RowMap row : rows) {
                    Object pid = row.get("programId");
                    if (pid instanceof Number n && seen.add(n.longValue())) {
                        merged.add(row);
                    }
                }
            }
        }
        return merged;
    }
}
