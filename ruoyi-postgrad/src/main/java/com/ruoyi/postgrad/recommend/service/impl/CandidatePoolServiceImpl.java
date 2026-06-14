package com.ruoyi.postgrad.recommend.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.ICandidatePoolService;

/**
 * 候选池服务实现 —— DB 查询 → 粗筛 → 规则分档。
 * <p>纯计算，不涉及 AI 调用。分档规则是确定性的（按 gap 硬分三档）。</p>
 */
@Service
public class CandidatePoolServiceImpl implements ICandidatePoolService {

    private static final Logger log = LoggerFactory.getLogger(CandidatePoolServiceImpl.class);

    /** 分数搜索范围（预估分 ± 此值） */
    private static final int SCORE_RANGE = 30;

    /** 每档 AI 选择前的上限（粗筛后给 AI 的候选数） */
    private static final int PER_TIER_LIMIT = 15;

    /** 408 考试科目组合 */
    private static final List<String> EXAM_408_SUBJECT_CODES = List.of(
        "101,204,302,408", // 22408: 政治 + 英语二 + 数学二 + 408
        "101,201,301,408"  // 11408: 政治 + 英语一 + 数学一 + 408
    );

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public List<TierCandidates> buildPool(int estimatedScore, List<String> targetRegions,
                                           String schoolTierPreference) {
        if (targetRegions == null) {
            targetRegions = Collections.emptyList();
        }

        // 1. 查询两个 408 exam combo → 合并去重
        List<RowMap> allRows = queryAllExamCombos(targetRegions, estimatedScore);
        log.info("[CandidatePool] Queried {} raw candidates for score={}, regions={}",
            allRows.size(), estimatedScore, targetRegions.isEmpty() ? "不限" : targetRegions);

        if (allRows.isEmpty()) {
            return buildEmptyTiers();
        }

        // 2. 计算 gap + canBeSafe，转为 SchoolFact
        List<SchoolFact> facts = new ArrayList<>(allRows.size());
        for (RowMap row : allRows) {
            SchoolFact fact = toSchoolFact(row, estimatedScore);
            if (fact != null) {
                facts.add(fact);
            }
        }

        // 3. 规则分档（统一使用 SchoolFact.classifyTier，系统唯一真相来源）
        List<SchoolFact> reach = new ArrayList<>();
        List<SchoolFact> steady = new ArrayList<>();
        List<SchoolFact> safe = new ArrayList<>();

        for (SchoolFact f : facts) {
            int gap = f.getScoreGap() != null ? f.getScoreGap() : 0;
            String tier = SchoolFact.classifyTier(gap, f.getCanBeSafe());
            if (tier == null) {
                // gap < -15，不入档
                continue;
            }
            switch (tier) {
                case "reach" -> reach.add(f);
                case "steady" -> steady.add(f);
                case "safe" -> safe.add(f);
            }
        }

        log.info("[CandidatePool] Tiers: reach={}, steady={}, safe={}", reach.size(), steady.size(), safe.size());

        // 4. 每档按综合得分排序，取 top
        List<TierCandidates> result = new ArrayList<>(3);
        result.add(buildTier("reach", "冲刺档", 3, reach, estimatedScore, schoolTierPreference));
        result.add(buildTier("steady", "稳妥档", 4, steady, estimatedScore, schoolTierPreference));
        result.add(buildTier("safe", "保底档", 3, safe, estimatedScore, schoolTierPreference));
        return result;
    }

    // ── private helpers ──

    /**
     * 查询两个 408 exam combo 的候选，按 programId 去重。
     */
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

    /**
     * 将 MyBatis RowMap 转为 SchoolFact（DB 字段 + 计算字段）。
     */
    private SchoolFact toSchoolFact(RowMap row, int estimatedScore) {
        SchoolFact f = SchoolFact.fromRow(row);

        // ── 后端计算字段 ──
        Integer avg = f.getAvgAdmittedScore();
        int gap = avg != null ? estimatedScore - avg : 0;
        f.setScoreGap(gap);
        f.setGapLabel(gapLabel(gap));

        int quota = f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota()
            : (f.getPlanCount() != null ? f.getPlanCount() : 0);
        f.setQuotaLabel(quotaLabel(quota));
        f.setQuotaRisk(quotaRisk(quota));

        boolean safe = canBeSafe(quota, gap, f.getDataCompleteness(), f.getAdmissionLow(), f.getAdmissionHigh());
        f.setCanBeSafe(safe);
        if (!safe) {
            f.setSafeBlockReason(buildSafeBlockReason(quota, gap, f.getDataCompleteness()));
        }

        return f;
    }

    /**
     * 构建单档 TierCandidates。
     */
    private TierCandidates buildTier(String level, String label, int targetCount,
                                      List<SchoolFact> facts, int estimatedScore,
                                      String schoolTierPreference) {
        // 按综合得分排序
        facts.sort((a, b) -> Integer.compare(
            compositeScore(b, level, schoolTierPreference),
            compositeScore(a, level, schoolTierPreference)));

        // 取 top PER_TIER_LIMIT
        List<SchoolFact> top = facts.size() > PER_TIER_LIMIT
            ? new ArrayList<>(facts.subList(0, PER_TIER_LIMIT))
            : new ArrayList<>(facts);

        List<CandidateCardVO> cards = new ArrayList<>(top.size());
        for (SchoolFact f : top) {
            cards.add(CandidateCardVO.fromFact(f));
        }

        TierCandidates tier = new TierCandidates();
        tier.setLevel(level);
        tier.setLabel(label);
        tier.setTargetCount(targetCount);
        tier.setCandidates(cards);
        tier.setInsufficient(cards.size() < targetCount);
        if (cards.size() < targetCount) {
            tier.setInsufficientReason(String.format(
                "当前分数下%s候选不足，仅找到 %d 所可信候选。", label, cards.size()));
        }
        return tier;
    }

    // ── 计算规则 ──

    /**
     * 判断是否满足严格保底条件。
     * <p>名额 &gt; 3 且（名额 ≥ 10 或（数据完整度非 C 且有录取区间））。</p>
     */
    private boolean canBeSafe(int quota, int gap, String completeness,
                               Integer admissionLow, Integer admissionHigh) {
        if (quota <= 3) return false;
        if (quota < 10) {
            boolean hasRange = admissionLow != null || admissionHigh != null;
            if ("C".equalsIgnoreCase(completeness) || !hasRange) return false;
        }
        return true;
    }

    /**
     * 名额风险等级。
     */
    private String quotaRisk(int quota) {
        if (quota <= 0) return "unknown";
        if (quota <= 3) return "very_high";
        if (quota < 10) return "high";
        if (quota < 20) return "medium";
        return "normal";
    }

    /**
     * 名额标签。
     */
    private String quotaLabel(int quota) {
        if (quota <= 0) return "名额未知";
        if (quota <= 3) return "名额极少";
        if (quota < 10) return "名额偏少";
        if (quota < 20) return "名额正常";
        return "名额充裕";
    }

    /**
     * 差距标签。
     */
    private String gapLabel(int gap) {
        if (gap >= 0) return "+" + gap;
        return String.valueOf(gap);
    }

    /**
     * 保底阻止原因。
     */
    private String buildSafeBlockReason(int quota, int gap, String completeness) {
        if (quota <= 3) return "统考名额仅" + quota + "人，录取波动极大，不能作为保底";
        if (quota < 10) return "统考名额仅" + quota + "人，数据不足以支撑保底判断";
        if ("C".equalsIgnoreCase(completeness)) return "数据完整度较低，不能作为保底";
        return "不满足保底条件";
    }

    /**
     * 综合得分：数据完整度(30) + 名额风险(30) + 学校层次(25) + gap适配度(15)。
     * <p>不同档位对 gap 的"理想值"不同：reach 理想 gap=0，steady 理想 gap=10，safe 理想 gap=20。</p>
     */
    private int compositeScore(SchoolFact f, String tier, String schoolTierPreference) {
        int score = 0;

        // 数据完整度：A=30, B=20, C/其他=10
        String comp = f.getDataCompleteness();
        score += "A".equals(comp) ? 30 : "B".equals(comp) ? 20 : 10;

        // 名额风险：normal=30, medium=20, high/very_high/unknown=10
        String risk = f.getQuotaRisk();
        score += "normal".equals(risk) ? 30 : "medium".equals(risk) ? 20 : 10;

        // 学校层次：985=25, 211/双一流=18, 其他=10（如偏好高则 985 加权）
        String tierLabel = f.getSchoolTier();
        int baseTier = "985".equals(tierLabel) ? 25
            : ("211".equals(tierLabel) || "双一流".equals(tierLabel)) ? 18 : 10;
        if ("must_211_or_better".equals(schoolTierPreference)
            || "prefer_211_or_better".equals(schoolTierPreference)) {
            baseTier = "985".equals(tierLabel) ? 30 : ("211".equals(tierLabel) || "双一流".equals(tierLabel)) ? 22 : 5;
        }
        score += baseTier;

        // gap 适配度：接近该档理想值的位置加分
        int gap = f.getScoreGap() != null ? f.getScoreGap() : 0;
        int idealGap = switch (tier) {
            case "reach" -> 0;
            case "safe" -> 20;
            default -> 10; // steady
        };
        score += Math.max(0, 15 - Math.abs(gap - idealGap));

        return score;
    }

    /**
     * 空候选池兜底。
     */
    private List<TierCandidates> buildEmptyTiers() {
        List<TierCandidates> result = new ArrayList<>(3);
        result.add(emptyTier("reach", "冲刺档", 3));
        result.add(emptyTier("steady", "稳妥档", 4));
        result.add(emptyTier("safe", "保底档", 3));
        return result;
    }

    private TierCandidates emptyTier(String level, String label, int target) {
        TierCandidates t = new TierCandidates();
        t.setLevel(level);
        t.setLabel(label);
        t.setTargetCount(target);
        t.setCandidates(Collections.emptyList());
        t.setInsufficient(true);
        t.setInsufficientReason("当前条件下没有可信候选。");
        return t;
    }

}
