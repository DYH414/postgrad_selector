package com.ruoyi.postgrad.tool;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 择校推荐决策引擎 —— 纯后端计算，不依赖 AI。
 *
 * 职责：
 * 1. 对候选池所有学校计算五维评分 + weightedScore
 * 2. 按分数分档（冲刺/稳妥/保底）
 * 3. 生成 riskLevel / judgement
 * 4. 按用户偏好排序
 * 5. 输出每档 shortlist
 *
 * AI 的职责仅限于：基于本引擎的输出写 verdict / evidence / risks。
 */
public final class RecommendationDecisionEngine {

    // ---- 常量 ----
    private static final Set<String> TIER1_CITIES = Set.of("北京", "上海", "广州", "深圳");
    private static final Set<String> REMOTE_PROVINCES = Set.of(
        "西藏", "青海", "新疆", "宁夏", "内蒙古", "甘肃");

    private static final Map<String, Double> WEIGHT_FACTORS = Map.of(
        "high", 2.0, "medium", 1.0, "low", 0.3);

    private RecommendationDecisionEngine() {}

    // ========================================================================
    // 公共入口
    // ========================================================================

    /**
     * 对完整候选池打分、分档、排序。
     *
     * @param pool          候选池（每项需含 programId, avgAdmittedScore, planCount,
     *                      admittedCount, retestCount, city, province, schoolTier,
     *                      dataCompleteness 等字段）
     * @param estimatedScore 用户预估总分
     * @param userWeights    用户偏好权重 {"scoreMatch":"high", ...}，null 则全部 medium
     * @param targetRegions  用户目标地区列表
     * @return 分档结果
     */
    public static DecisionResult decide(
            List<Map<String, Object>> pool,
            int estimatedScore,
            Map<String, String> userWeights,
            List<String> targetRegions) {

        if (userWeights == null) userWeights = defaultWeights();
        if (targetRegions == null) targetRegions = Collections.emptyList();

        // 1. 对每所学校打分
        List<ScoredProgram> scored = new ArrayList<>();
        for (Map<String, Object> raw : pool) {
            ScoredProgram sp = scoreOne(raw, estimatedScore, userWeights, targetRegions);
            scored.add(sp);
        }

        // 2. 分档
        List<ScoredProgram> sprintList = new ArrayList<>();
        List<ScoredProgram> steadyList = new ArrayList<>();
        List<ScoredProgram> safeList = new ArrayList<>();
        for (ScoredProgram sp : scored) {
            switch (sp.tier) {
                case "sprint" -> sprintList.add(sp);
                case "steady" -> steadyList.add(sp);
                case "safe"   -> safeList.add(sp);
                default       -> sprintList.add(sp);
            }
        }

        // 3. 档内排序：weightedScore 降序
        Comparator<ScoredProgram> byScore = Comparator.comparingDouble((ScoredProgram s) -> s.weightedScore).reversed();
        sprintList.sort(byScore);
        steadyList.sort(byScore);
        safeList.sort(byScore);

        // 4. 组装结果
        DecisionResult result = new DecisionResult();
        result.totalCandidates = scored.size();
        result.sprintPrograms = sprintList;
        result.steadyPrograms = steadyList;
        result.safePrograms = safeList;
        result.sprintCount = sprintList.size();
        result.steadyCount = steadyList.size();
        result.safeCount = safeList.size();
        return result;
    }

    /**
     * 生成每档 shortlist（取前 N 个），返回可直接喂给 AI 的结构化数据。
     */
    public static Map<String, Object> buildAiInput(DecisionResult result, int perTier) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sprint", buildTierAiData(result.sprintPrograms, perTier));
        input.put("steady", buildTierAiData(result.steadyPrograms, perTier));
        input.put("safe",   buildTierAiData(result.safePrograms, perTier));
        return input;
    }

    // ========================================================================
    // 单校评分
    // ========================================================================

    private static ScoredProgram scoreOne(
            Map<String, Object> raw, int estimatedScore,
            Map<String, String> weights, List<String> targetRegions) {

        ScoredProgram sp = new ScoredProgram();
        sp.programId = longVal(raw.get("programId"));
        sp.schoolName = str(raw, "schoolName");
        sp.programName = str(raw, "programName");
        sp.schoolTier = str(raw, "schoolTier");
        sp.city = str(raw, "city");
        sp.province = str(raw, "province");

        // --- 原始数据快照（供 injectFullData 使用）---
        sp.rawData = new LinkedHashMap<>(raw);

        // --- 核心差距 ---
        double avg = doubleVal(raw.get("avgAdmittedScore"));
        sp.avgAdmittedScore = avg;
        sp.gap = avg > 0 ? (int) Math.round(estimatedScore - avg) : 0;

        // --- 五维评分 ---
        sp.scoreMatchScore = scoreMatch(sp.gap);
        sp.competitionScore = scoreCompetition(raw);
        sp.regionScore = scoreRegion(sp.city, sp.province, targetRegions);
        sp.schoolTierScore = scoreSchoolTier(sp.schoolTier);
        sp.programStrengthScore = 50; // 默认值，AI 后续微调
        sp.dataCompletenessScore = scoreCompleteness(str(raw, "dataCompleteness"));

        // --- 加权总分 ---
        sp.weightedScore = computeWeighted(sp, weights);

        // --- 分档 ---
        sp.tier = assignTier(sp.gap, sp.dataCompletenessScore, sp.competitionScore);
        sp.riskLevel = assignRiskLevel(sp.gap, sp.competitionScore);
        sp.judgement = assignJudgement(sp.tier, sp.riskLevel, sp.dataCompletenessScore);

        return sp;
    }

    // ========================================================================
    // 五维评分公式
    // ========================================================================

    static int scoreMatch(int gap) {
        if (gap >= 15) return 90;
        if (gap >= 5)  return 75;
        if (gap >= -10) return 55;
        return 30;
    }

    static int scoreCompetition(Map<String, Object> raw) {
        // 用 retestCount / admittedCount 作为竞争指标
        Object retestObj = raw.get("retestCount");
        Object admittedObj = raw.get("admittedCount");
        if (retestObj instanceof Number r && admittedObj instanceof Number a && a.doubleValue() > 0 && r.doubleValue() > 0) {
            double ratio = r.doubleValue() / a.doubleValue();
            if (ratio < 1.2) return 85;  // 复试≈录取，竞争温和
            if (ratio < 2.0) return 65;
            if (ratio < 3.0) return 45;
            return 30; // 高竞争
        }
        // 无数据时，用 planCount 估算
        Object planObj = raw.get("planCount");
        if (planObj instanceof Number p) {
            int plan = p.intValue();
            if (plan >= 50) return 70;  // 招生多，机会大
            if (plan >= 20) return 55;
            if (plan >= 10) return 45;
            return 35; // 招生极少
        }
        return 50; // 完全无数据
    }

    static int scoreRegion(String city, String province, List<String> targetRegions) {
        if (targetRegions.isEmpty()) {
            return TIER1_CITIES.contains(city) ? 80 : 50;
        }
        boolean isTier1 = TIER1_CITIES.contains(city);
        boolean matches = targetRegions.stream().anyMatch(r -> r.equals(province) || r.equals(city));
        boolean isRemote = REMOTE_PROVINCES.contains(province);

        if (matches && isTier1) return 90;
        if (matches) return 75;
        if (isTier1) return 65;
        if (isRemote) return 35;
        return 55;
    }

    static int scoreSchoolTier(String tier) {
        if (tier == null) return 50;
        return switch (tier) {
            case "985" -> 95;
            case "211" -> 80;
            case "DOUBLE_FIRST", "双一流" -> 70;
            default -> 50;
        };
    }

    static int scoreCompleteness(String level) {
        if (level == null) return 50;
        return switch (level) {
            case "A" -> 90;
            case "B" -> 70;
            default -> 50;
        };
    }

    // ========================================================================
    // 加权总分
    // ========================================================================

    private static double computeWeighted(ScoredProgram sp, Map<String, String> weights) {
        double total = 0;
        total += sp.scoreMatchScore        * WEIGHT_FACTORS.getOrDefault(weights.getOrDefault("scoreMatch", "medium"), 1.0);
        total += sp.competitionScore       * WEIGHT_FACTORS.getOrDefault(weights.getOrDefault("competition", "medium"), 1.0);
        total += sp.regionScore            * WEIGHT_FACTORS.getOrDefault(weights.getOrDefault("regionAdvantage", "medium"), 1.0);
        total += sp.schoolTierScore        * WEIGHT_FACTORS.getOrDefault(weights.getOrDefault("schoolTier", "medium"), 1.0);
        total += sp.programStrengthScore   * WEIGHT_FACTORS.getOrDefault(weights.getOrDefault("programStrength", "medium"), 1.0);
        total += sp.dataCompletenessScore  * 0.5; // 数据完整度权重固定偏低
        return total;
    }

    // ========================================================================
    // 分档 / 风险 / 判断
    // ========================================================================

    static String assignTier(int gap, int completenessScore, int competitionScore) {
        if (gap >= 15 && competitionScore >= 55) return "safe";
        if (gap >= 15) return "steady";
        if (gap >= 5)  return "steady";
        if (completenessScore < 50) return "sprint"; // 数据不完整 → 偏冲刺
        return "sprint";
    }

    static String assignRiskLevel(int gap, int competitionScore) {
        if (gap >= 15 && competitionScore >= 55) return "low";
        if (gap >= 5)  return "medium";
        if (gap >= -10) return "high";
        return "high";
    }

    static String assignJudgement(String tier, String riskLevel, int completenessScore) {
        if (completenessScore < 50) return "data_insufficient_pending";
        return switch (tier) {
            case "safe"   -> riskLevel.equals("low") ? "safe" : "steady";
            case "steady" -> "steady";
            default       -> riskLevel.equals("high") ? "high_risk_reach" : "small_reach";
        };
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static Map<String, String> defaultWeights() {
        Map<String, String> w = new LinkedHashMap<>();
        w.put("scoreMatch", "medium");
        w.put("competition", "medium");
        w.put("regionAdvantage", "medium");
        w.put("schoolTier", "medium");
        w.put("programStrength", "medium");
        return w;
    }

    private static List<Map<String, Object>> buildTierAiData(List<ScoredProgram> programs, int limit) {
        return programs.stream().limit(limit).map(ScoredProgram::toAiInput).collect(Collectors.toList());
    }

    private static long longVal(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static double doubleVal(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString();
    }

    // ========================================================================
    // 数据类
    // ========================================================================

    /** 单校评分结果 */
    public static class ScoredProgram {
        public long programId;
        public String schoolName;
        public String programName;
        public String schoolTier;
        public String city;
        public String province;
        public double avgAdmittedScore;
        public int gap;

        // 五维分
        public int scoreMatchScore;
        public int competitionScore;
        public int regionScore;
        public int schoolTierScore;
        public int programStrengthScore;
        public int dataCompletenessScore;
        public double weightedScore;

        // 分档
        public String tier;        // sprint / steady / safe
        public String riskLevel;   // low / medium / high
        public String judgement;   // safe / steady / small_reach / high_risk_reach / data_insufficient_pending

        // 原始数据（供 injectFullData 使用）
        public Map<String, Object> rawData;

        /** 转为传给 AI 的结构化输入 */
        Map<String, Object> toAiInput() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("programId", programId);
            m.put("schoolName", schoolName);
            m.put("programName", programName);
            m.put("tier", tier);
            m.put("gap", gap);
            m.put("scoreMatchScore", scoreMatchScore);
            m.put("competitionScore", competitionScore);
            m.put("regionScore", regionScore);
            m.put("schoolTierScore", schoolTierScore);
            m.put("programStrengthScore", programStrengthScore);
            m.put("dataCompletenessScore", dataCompletenessScore);
            m.put("riskLevel", riskLevel);
            m.put("judgement", judgement);
            return m;
        }
    }

    /** 决策结果 */
    public static class DecisionResult {
        public int totalCandidates;
        public List<ScoredProgram> sprintPrograms = Collections.emptyList();
        public List<ScoredProgram> steadyPrograms = Collections.emptyList();
        public List<ScoredProgram> safePrograms = Collections.emptyList();
        public int sprintCount;
        public int steadyCount;
        public int safeCount;

        public List<ScoredProgram> allPrograms() {
            List<ScoredProgram> all = new ArrayList<>();
            all.addAll(sprintPrograms);
            all.addAll(steadyPrograms);
            all.addAll(safePrograms);
            return all;
        }
    }
}
