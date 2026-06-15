package com.ruoyi.postgrad.recommend.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.ICandidateWorkspaceService;

/**
 * 候选工作集服务实现 —— 从 Universe 构建分层活动候选集。
 * <p>每档最多 30 个，按策略排序，包含多样性约束。</p>
 */
@Service
public class CandidateWorkspaceServiceImpl implements ICandidateWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(CandidateWorkspaceServiceImpl.class);

    @Override
    public CandidateWorkspaceVO buildWorkspace(CandidateUniverseVO universe, String schoolTierPref,
                                                String regionStrategy) {
        CandidateWorkspaceVO workspace = new CandidateWorkspaceVO();
        workspace.setWorkspaceId(java.util.UUID.randomUUID().toString());
        workspace.setUserId(universe.getUserId());
        workspace.setUniverseId(universe.getUniverseId());

        List<SchoolFact> all = universe.getCandidates();
        if (all == null || all.isEmpty()) {
            workspace.setTiers(buildEmptyTiers());
            return workspace;
        }

        // 1. 按 gap 分三档（使用 SchoolFact.classifyTier 唯一规则）
        List<SchoolFact> reach = new ArrayList<>();
        List<SchoolFact> steady = new ArrayList<>();
        List<SchoolFact> safe = new ArrayList<>();
        for (SchoolFact f : all) {
            int gap = f.getScoreGap() != null ? f.getScoreGap() : 0;
            String tier = SchoolFact.classifyTier(gap, f.getCanBeSafe());
            if (tier == null) continue;
            switch (tier) {
                case "reach" -> reach.add(f);
                case "steady" -> steady.add(f);
                case "safe" -> safe.add(f);
            }
        }

        // 2. 每档按策略排序 + 多样性修剪 + 截断到 DEFAULT_TIER_LIMIT
        List<WorkspaceTierVO> tiers = new ArrayList<>(3);
        tiers.add(buildTier("reach", "冲刺档", 3, reach, schoolTierPref, regionStrategy));
        tiers.add(buildTier("steady", "稳妥档", 4, steady, schoolTierPref, regionStrategy));
        tiers.add(buildTier("safe", "保底档", 3, safe, schoolTierPref, regionStrategy));
        workspace.setTiers(tiers);

        // 3. 元数据
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("policyVersion", "v2-hybrid-1");
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("reach", DEFAULT_TIER_LIMIT);
        limits.put("steady", DEFAULT_TIER_LIMIT);
        limits.put("safe", DEFAULT_TIER_LIMIT);
        meta.put("limits", limits);
        workspace.setMetadata(meta);

        log.info("[Workspace] userId={} reach={} steady={} safe={}",
            universe.getUserId(),
            tiers.get(0).getCandidates().size(),
            tiers.get(1).getCandidates().size(),
            tiers.get(2).getCandidates().size());
        return workspace;
    }

    private WorkspaceTierVO buildTier(String level, String label, int draftTarget,
                                       List<SchoolFact> facts, String schoolTierPref,
                                       String regionStrategy) {
        // 按策略得分排序（含地区策略权重）
        facts.sort(Comparator.comparingInt(
            (SchoolFact f) -> policyScore(f, level, schoolTierPref, regionStrategy)).reversed());

        // 多样性修剪：同学校只保留最高分的一个专业方向
        List<SchoolFact> diverse = diversityTrim(facts);

        // 截断
        List<SchoolFact> top = diverse.size() > DEFAULT_TIER_LIMIT
            ? new ArrayList<>(diverse.subList(0, DEFAULT_TIER_LIMIT))
            : new ArrayList<>(diverse);

        List<CandidateCardVO> cards = new ArrayList<>(top.size());
        for (SchoolFact f : top) {
            cards.add(CandidateCardVO.fromFact(f));
        }

        WorkspaceTierVO tier = new WorkspaceTierVO();
        tier.setLevel(level);
        tier.setLabel(label);
        tier.setTargetCount(DEFAULT_TIER_LIMIT);
        tier.setCandidates(cards);
        tier.setInsufficient(cards.size() < draftTarget);
        if (cards.size() < draftTarget) {
            tier.setInsufficientReason(String.format(
                "%s候选不足，工作集中仅 %d 所。", label, cards.size()));
        }
        return tier;
    }

    /** 发达地区城市列表 */
    private static final java.util.Set<String> DEVELOPED_CITIES = java.util.Set.of(
        "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "武汉", "成都",
        "重庆", "天津", "西安", "长沙", "青岛", "宁波", "东莞", "佛山", "无锡",
        "合肥", "郑州", "厦门", "福州", "济南", "大连"
    );

    /**
     * 策略得分：gap 适配 + 名额风险 + 学校层次 + 数据完整度 + 地区策略。
     */
    private int policyScore(SchoolFact f, String tier, String schoolTierPref, String regionStrategy) {
        int score = 0;
        // 数据完整度：A=30, B=20, 其他=10
        String comp = f.getDataCompleteness();
        score += "A".equals(comp) ? 30 : "B".equals(comp) ? 20 : 10;
        // 名额风险：normal=30, medium=20, 其他=10
        String risk = f.getQuotaRisk();
        score += "normal".equals(risk) ? 30 : "medium".equals(risk) ? 20 : 10;
        // 学校层次
        String tl = f.getSchoolTier();
        int base = "985".equals(tl) ? 25 : ("211".equals(tl) || "双一流".equals(tl)) ? 18 : 10;
        if ("must_211_or_better".equals(schoolTierPref) || "prefer_211_or_better".equals(schoolTierPref)) {
            base = "985".equals(tl) ? 30 : ("211".equals(tl) || "双一流".equals(tl)) ? 22 : 5;
        }
        score += base;
        // gap 适配度
        int gap = f.getScoreGap() != null ? f.getScoreGap() : 0;
        int ideal = switch (tier) { case "reach" -> 0; case "safe" -> 20; default -> 10; };
        score += Math.max(0, 15 - Math.abs(gap - ideal));
        // 地区策略：developed_priority 时发达地区 +10 分
        if ("developed_priority".equals(regionStrategy)) {
            String city = f.getCity();
            if (city != null && DEVELOPED_CITIES.contains(city)) {
                score += 10;
            }
        }
        return score;
    }

    /**
     * 多样性修剪：同学校只保留得分最高的一个。
     */
    private List<SchoolFact> diversityTrim(List<SchoolFact> sorted) {
        Set<Long> seenSchools = new java.util.HashSet<>();
        List<SchoolFact> result = new ArrayList<>();
        for (SchoolFact f : sorted) {
            if (f.getSchoolId() == null || seenSchools.add(f.getSchoolId())) {
                result.add(f);
            }
        }
        return result;
    }

    private List<WorkspaceTierVO> buildEmptyTiers() {
        List<WorkspaceTierVO> tiers = new ArrayList<>(3);
        for (String[] t : new String[][]{{"reach", "冲刺档"}, {"steady", "稳妥档"}, {"safe", "保底档"}}) {
            WorkspaceTierVO tier = new WorkspaceTierVO();
            tier.setLevel(t[0]); tier.setLabel(t[1]); tier.setTargetCount(DEFAULT_TIER_LIMIT);
            tier.setCandidates(List.of());
            tier.setInsufficient(true);
            tier.setInsufficientReason("候选宇宙中没有可信候选。");
            tiers.add(tier);
        }
        return tiers;
    }
}
