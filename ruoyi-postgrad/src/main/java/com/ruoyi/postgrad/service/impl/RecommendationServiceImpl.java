package com.ruoyi.postgrad.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.domain.RecommendationItem;
import com.ruoyi.postgrad.domain.RecommendationRequest;
import com.ruoyi.postgrad.domain.RecommendationResult;
import com.ruoyi.postgrad.service.IRecommendationService;

/**
 * 推荐引擎核心实现。
 *
 * 数据原则：
 *  1. 只使用 verify_status IN ('OFFICIAL_VERIFIED','MANUAL_VERIFIED') 的已审核数据
 *  2. 不使用国家线回退，无已审核数据则排除
 *  3. 展示复试线（门槛）+ 录取均分（正常难度）双指标
 */
@Service
public class RecommendationServiceImpl implements IRecommendationService
{
    private static final int TIER_LIMIT = 15;
    private static final Map<String, List<String>> PROGRAM_DIRECTION_MAP = buildProgramDirectionMap();
    private static final Comparator<RecommendationItem> EFFECTIVE_SCORE_DESC =
            Comparator.comparingInt(RecommendationItem::getEffectiveScore).reversed()
                    .thenComparing(Comparator.comparingInt(RecommendationItem::getScoreGap).reversed())
                    .thenComparing(RecommendationItem::getSchoolName)
                    .thenComparing(RecommendationItem::getCollegeName);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public RecommendationResult generate(RecommendationRequest req)
    {
        List<Map<String, Object>> candidates = fetchCandidates(req);
        if (candidates.isEmpty()) return new RecommendationResult();

        List<RecommendationItem> items = new ArrayList<>();
        int insufficientCount = 0;
        for (Map<String, Object> row : candidates)
        {
            RecommendationItem item = buildItem(row);
            boolean usable = calculateEffectiveScore(item, row);
            if (!usable) { insufficientCount++; continue; }
            assignTier(item, req.getEstimatedScore(), req.getRiskPreference());
            generateWarnings(item, row);
            items.add(item);
        }

        List<RecommendationItem> dedupedItems = deduplicateBySchoolAndProgram(items);

        RecommendationResult result = new RecommendationResult();
        result.setTotalCandidates(dedupedItems.size() + insufficientCount);
        for (RecommendationItem item : dedupedItems)
        {
            if (isSteadyOverflow(item, req.getEstimatedScore()))
            {
                addOverflow(result, item, req);
                continue;
            }
            switch (item.getTierLabel())
            {
                case "steady": result.getSteady().add(item); break;
                case "focus": result.getFocus().add(item); break;
                case "reach": result.getReach().add(item); break;
                case "notRecommended":
                    if (req.isIncludeNotRecommended()) result.getNotRecommended().add(item);
                    break;
                default: result.getInsufficient().add(item); break;
            }
        }
        trimAndSort(result.getSteady(), result, req);
        trimAndSort(result.getFocus(), result, req);
        trimAndSort(result.getReach(), result, req);
        trimAndSort(result.getNotRecommended(), result, req);
        trimAndSort(result.getInsufficient(), result, req);
        result.getOverflow().sort(EFFECTIVE_SCORE_DESC);
        return result;
    }

    // ═══ SQL — 只查已审核数据 ═══

    private List<Map<String, Object>> fetchCandidates(RecommendationRequest req)
    {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        List<String> programCodes = resolveProgramCodes(req);

        sql.append("""
            SELECT p.id program_id, p.program_code, p.program_name, p.study_mode, p.degree_type,
                   p.protects_first_choice, p.is_joint_program,
                   c.name college_name,
                   s.id school_id, s.name school_name, s.province, s.city, s.tier,
                   s.is_985, s.is_211, s.is_double_first,
                   ascore.score_line,
                   ascore.verify_status score_verify,
                   ap.total_plan, ap.unified_exam_quota, ap.retest_count,
                   ap.verify_status plan_verify,
                   ar.admitted_count, ar.min_admitted_score, ar.avg_admitted_score,
                   ar.verify_status result_verify
            FROM program p
            JOIN college c ON p.college_id = c.id
            JOIN school s ON c.school_id = s.id
            LEFT JOIN admission_score ascore ON ascore.program_id = p.id
                AND ascore.year = (SELECT MAX(year) FROM admission_score WHERE program_id = p.id)
                AND ascore.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            LEFT JOIN admission_plan ap ON ap.program_id = p.id AND ap.year = ascore.year
                AND ap.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            LEFT JOIN admission_result ar ON ar.program_id = p.id AND ar.year = ascore.year
                AND ar.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            WHERE p.is_408 = 1 AND p.status = 'active' AND s.status = 'active'
            """);

        if (req.getTargetProvinces() != null && !req.getTargetProvinces().isEmpty())
        {
            sql.append(" AND s.province IN (");
            for (int i = 0; i < req.getTargetProvinces().size(); i++)
            { sql.append(i > 0 ? ",?" : "?"); args.add(req.getTargetProvinces().get(i)); }
            sql.append(")");
        }
        if (!programCodes.isEmpty())
        {
            sql.append(" AND p.program_code IN (");
            for (int i = 0; i < programCodes.size(); i++)
            { sql.append(i > 0 ? ",?" : "?"); args.add(programCodes.get(i)); }
            sql.append(")");
        }
        if (!req.isAcceptPartTime()) sql.append(" AND p.study_mode = 'full_time'");
        if (!req.isAcceptAcademic()) sql.append(" AND p.degree_type = 'professional'");

        sql.append(" ORDER BY s.tier, s.name, p.program_code");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ═══ Score calculation ═══

    private boolean calculateEffectiveScore(RecommendationItem item, Map<String, Object> row)
    {
        Integer scoreLine = intOrNull(row, "score_line");
        String scoreVerify = str(row, "score_verify");
        Integer minAdmitted = intOrNull(row, "min_admitted_score");
        Integer avgAdmitted = intOrNull(row, "avg_admitted_score");
        String resultVerify = str(row, "result_verify");
        Integer retestCount = intOrNull(row, "retest_count");
        Integer admittedCount = intOrNull(row, "admitted_count");
        String planVerify = str(row, "plan_verify");

        // 必须有已审核的复试线
        if (scoreLine == null || scoreLine <= 0 || scoreVerify.isEmpty())
            return false;

        int effective;
        StringBuilder basis = new StringBuilder();

        // ══ 展示数据：复试线(门槛) + 录取分数(真实难度) ══
        item.setScoreLine(scoreLine);

        Integer totalPlan = intOrNull(row, "total_plan");
        if (totalPlan != null && totalPlan > 0)
            item.setPlanCount(totalPlan);

        if (minAdmitted != null && minAdmitted > 0 && !resultVerify.isEmpty())
        {
            item.setMinAdmittedScore(minAdmitted);
            effective = Math.max(scoreLine, minAdmitted);
            basis.append("录取最低分").append(effective).append("(复试线").append(scoreLine).append(")");
        }
        else
        {
            effective = scoreLine;
            basis.append("复试线").append(scoreLine).append("(暂无录取分数据)");
        }

        if (avgAdmitted != null && avgAdmitted > 0 && !resultVerify.isEmpty())
            item.setAvgAdmittedScore(avgAdmitted);

        // 复录比修正（仅已审核数据）
        if (retestCount != null && retestCount > 0
                && admittedCount != null && admittedCount > 0
                && !planVerify.isEmpty() && !resultVerify.isEmpty())
        {
            double rr = (double) retestCount / admittedCount;
            if (rr > 2.0) effective += 20;
            else if (rr > 1.5) effective += 10;
        }

        item.setEffectiveScore(effective);
        item.setScoreBasis(basis.toString());
        return true;
    }

    // ═══ Tier assignment ═══

    private void assignTier(RecommendationItem item, int estimatedScore, String riskPreference)
    {
        int gap = estimatedScore - item.getEffectiveScore();
        item.setScoreGap(gap);
        String tier;
        if (gap >= 20) tier = "steady";
        else if (gap >= 5) tier = "focus";
        else if (gap >= -10) tier = "reach";
        else tier = "notRecommended";
        if (item.getWarnings().contains("统考名额较少"))
        {
            if ("steady".equals(tier)) tier = "focus";
            else if ("focus".equals(tier)) tier = "reach";
            else if ("reach".equals(tier)) tier = "notRecommended";
        }
        item.setTierLabel(tier);
    }

    // ═══ Warnings ═══

    private void generateWarnings(RecommendationItem item, Map<String, Object> row)
    {
        List<String> warnings = new ArrayList<>();
        String resultVerify = str(row, "result_verify");
        String planVerify = str(row, "plan_verify");
        Integer unifiedQuota = intOrNull(row, "unified_exam_quota");
        Integer retestCount = intOrNull(row, "retest_count");
        Integer admittedCount = intOrNull(row, "admitted_count");
        int protectsFirst = intVal(row, "protects_first_choice");

        if (item.getAvgAdmittedScore() <= 0)
            warnings.add("暂无已审核录取分数据，基于复试线评估");

        if (unifiedQuota != null && unifiedQuota > 0 && unifiedQuota < 10)
            warnings.add("统考名额较少（" + unifiedQuota + "人），录取不确定性高");

        if (admittedCount != null && admittedCount > 0
                && retestCount != null && retestCount > 0
                && !planVerify.isEmpty() && !resultVerify.isEmpty())
        {
            double rr = (double) retestCount / admittedCount;
            if (rr > 2.0) warnings.add("复试竞争激烈（复录比" + String.format("%.1f", rr) + "）");
        }

        if (protectsFirst == 0) warnings.add("该院校一志愿保护机制较弱");

        if (item.getScoreGap() >= -3 && item.getScoreGap() <= 3)
        {
            item.setWarningLevel("extreme");
            warnings.add("分数与估算线持平，录取风险极高");
        }
        item.setWarnings(warnings);
    }

    // ═══ Dedup & overflow ═══

    private List<RecommendationItem> deduplicateBySchoolAndProgram(List<RecommendationItem> items)
    {
        Map<String, RecommendationItem> best = new LinkedHashMap<>();
        for (RecommendationItem item : items)
        {
            String key = item.getSchoolName() + "\0" + item.getProgramCode();
            RecommendationItem existing = best.get(key);
            if (existing == null) { best.put(key, item); continue; }
            if (item.getScoreGap() > existing.getScoreGap())
            {
                item.getSubPrograms().add(existing);
                item.getSubPrograms().addAll(existing.getSubPrograms());
                existing.setSubPrograms(new ArrayList<>());
                best.put(key, item);
            }
            else { existing.getSubPrograms().add(item); }
        }
        List<RecommendationItem> deduped = new ArrayList<>(best.values());
        for (RecommendationItem item : deduped)
            item.getSubPrograms().sort(EFFECTIVE_SCORE_DESC);
        return deduped;
    }

    private boolean isSteadyOverflow(RecommendationItem item, int estimatedScore)
    {
        if (!"steady".equals(item.getTierLabel())) return false;
        if (estimatedScore >= 380) return item.getEffectiveScore() < 300;
        if (estimatedScore >= 350) return item.getEffectiveScore() < 270;
        return false;
    }

    private void trimAndSort(List<RecommendationItem> tierItems, RecommendationResult result, RecommendationRequest req)
    {
        tierItems.sort(EFFECTIVE_SCORE_DESC);
        if (tierItems.size() <= TIER_LIMIT) return;
        if (req.isIncludeOverflow())
            result.getOverflow().addAll(new ArrayList<>(tierItems.subList(TIER_LIMIT, tierItems.size())));
        tierItems.subList(TIER_LIMIT, tierItems.size()).clear();
    }

    private void addOverflow(RecommendationResult result, RecommendationItem item, RecommendationRequest req)
    {
        if (req.isIncludeOverflow()) result.getOverflow().add(item);
    }

    // ═══ Filter mode — 纯筛选，不做推荐分档 ═══

    private static final int FILTER_YEAR = 2025;

    @Override
    public RecommendationResult filter(RecommendationRequest req)
    {
        // 1. 用 2025 年数据筛选
        List<Map<String, Object>> candidates = fetchCandidatesFilter(req);

        // 2. 收集 program_id
        List<Long> programIds = new ArrayList<>();
        for (Map<String, Object> row : candidates)
        {
            Number pidObj = (Number) row.get("program_id");
            if (pidObj != null) programIds.add(pidObj.longValue());
        }

        // 3. 批量查历年数据
        Map<Long, List<Map<String, Object>>> historyMap = fetchHistory(programIds);

        // 4. 构建结果
        List<RecommendationItem> items = new ArrayList<>();
        for (Map<String, Object> row : candidates)
        {
            RecommendationItem item = buildItem(row);
            Number pidObj = (Number) row.get("program_id");
            Long pid = pidObj != null ? pidObj.longValue() : null;

            Integer scoreLine = intOrNull(row, "score_line");
            Integer minAdmitted = intOrNull(row, "min_admitted_score");
            Integer avgAdmitted = intOrNull(row, "avg_admitted_score");
            Integer totalPlan = intOrNull(row, "total_plan");

            item.setScoreLine(scoreLine != null ? scoreLine : 0);
            item.setMinAdmittedScore(minAdmitted != null ? minAdmitted : 0);
            item.setAvgAdmittedScore(avgAdmitted != null ? avgAdmitted : 0);
            item.setPlanCount(totalPlan != null ? totalPlan : 0);
            Integer rc = intOrNull(row, "retest_count");
            item.setRetestCount(rc != null ? rc : 0);
            item.setSourceUrl(str(row, "source_url"));

            if (minAdmitted != null && minAdmitted > 0)
                item.setScoreGap(req.getEstimatedScore() - minAdmitted);
            else if (scoreLine != null && scoreLine > 0)
                item.setScoreGap(req.getEstimatedScore() - scoreLine);

            // 附加历年数据
            if (pid != null && historyMap.containsKey(pid))
                item.setHistoryScores(historyMap.get(pid));

            item.setTierLabel("all");
            items.add(item);
        }

        // 按省份 + 拟录取最低分排序
        items.sort(Comparator
                .comparing(RecommendationItem::getProvince)
                .thenComparingInt(RecommendationItem::getMinAdmittedScore)
                .thenComparing(RecommendationItem::getSchoolName));

        RecommendationResult result = new RecommendationResult();
        result.setTotalCandidates(items.size());
        result.getSteady().addAll(items);
        return result;
    }

    private Map<Long, List<Map<String, Object>>> fetchHistory(List<Long> programIds)
    {
        if (programIds.isEmpty()) return Collections.emptyMap();

        String inClause = programIds.stream()
                .map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("""
            SELECT program_id, year, score_line, min_admitted_score, avg_admitted_score,
                   unified_exam_quota, retest_count
            FROM (
                SELECT sc.program_id, sc.year, sc.score_line,
                       ar.min_admitted_score, ar.avg_admitted_score,
                       ap.unified_exam_quota, ap.retest_count,
                       ROW_NUMBER() OVER (PARTITION BY sc.program_id, sc.year ORDER BY sc.id) rn
                FROM admission_score sc
                LEFT JOIN admission_plan ap ON ap.program_id = sc.program_id AND ap.year = sc.year
                  AND ap.verify_status IN ('OFFICIAL_VERIFIED','MANUAL_VERIFIED')
                LEFT JOIN admission_result ar ON ar.program_id = sc.program_id AND ar.year = sc.year
                  AND ar.verify_status IN ('OFFICIAL_VERIFIED','MANUAL_VERIFIED')
                WHERE sc.program_id IN (%s)
                  AND sc.verify_status IN ('OFFICIAL_VERIFIED','MANUAL_VERIFIED')
            ) t WHERE rn = 1 ORDER BY program_id, year DESC
            """, inClause);

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            Number pid = (Number) row.get("program_id");
            result.computeIfAbsent(pid.longValue(), k -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private List<Map<String, Object>> fetchCandidatesFilter(RecommendationRequest req)
    {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        List<String> programCodes = resolveProgramCodes(req);
        int scoreRange = req.getScoreRange() > 0 ? req.getScoreRange() : 20;
        int scoreHigh = req.getEstimatedScore() + scoreRange;

        sql.append("""
            SELECT p.id program_id, p.program_code, p.program_name, p.study_mode, p.degree_type,
                   c.name college_name,
                   s.id school_id, s.name school_name, s.province, s.city, s.tier,
                   s.is_985, s.is_211, s.is_double_first,
                   ascore.score_line,
                   ap.total_plan, ap.unified_exam_quota, ap.retest_count,
                   ar.admitted_count, ar.min_admitted_score, ar.avg_admitted_score,
                   ds.url source_url
            FROM program p
            JOIN college c ON p.college_id = c.id
            JOIN school s ON c.school_id = s.id
            LEFT JOIN admission_score ascore ON ascore.program_id = p.id
                AND ascore.year = (SELECT MAX(year) FROM admission_score WHERE program_id = p.id)
                AND ascore.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            LEFT JOIN admission_plan ap ON ap.program_id = p.id AND ap.year = ascore.year
                AND ap.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            LEFT JOIN admission_result ar ON ar.program_id = p.id AND ar.year = ascore.year
                AND ar.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')
            LEFT JOIN data_source ds ON ds.id = ascore.source_id
            WHERE p.is_408 = 1 AND p.status = 'active' AND s.status = 'active'
            """);

        // 分数筛选：拟录取最低分 ≤ 预估分 + N（单向，低分不排除）
        sql.append(" AND (");
        sql.append("  (ar.min_admitted_score IS NOT NULL AND ar.min_admitted_score <= ?)");
        sql.append("  OR (ar.min_admitted_score IS NULL AND ascore.score_line IS NOT NULL AND ascore.score_line <= ?)");
        sql.append(")");
        args.add(scoreHigh);
        args.add(scoreHigh);

        // 省份筛选
        if (req.getTargetProvinces() != null && !req.getTargetProvinces().isEmpty())
        {
            sql.append(" AND s.province IN (");
            for (int i = 0; i < req.getTargetProvinces().size(); i++)
            { sql.append(i > 0 ? ",?" : "?"); args.add(req.getTargetProvinces().get(i)); }
            sql.append(")");
        }
        // 专业代码筛选
        if (!programCodes.isEmpty())
        {
            sql.append(" AND p.program_code IN (");
            for (int i = 0; i < programCodes.size(); i++)
            { sql.append(i > 0 ? ",?" : "?"); args.add(programCodes.get(i)); }
            sql.append(")");
        }
        if (!req.isAcceptPartTime()) sql.append(" AND p.study_mode = 'full_time'");
        if (!req.isAcceptAcademic()) sql.append(" AND p.degree_type = 'professional'");

        sql.append(" ORDER BY s.province, ar.min_admitted_score ASC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ═══ Build item ═══

    private RecommendationItem buildItem(Map<String, Object> row)
    {
        RecommendationItem item = new RecommendationItem();
        item.setSchoolId(((Number) row.get("school_id")).longValue());
        item.setCollegeId((long) 0); // not used directly
        item.setSchoolName(str(row, "school_name"));
        item.setProvince(str(row, "province"));
        item.setCity(str(row, "city"));
        item.setTier(normalizeTier(str(row, "tier")));
        item.setIs985(intVal(row, "is_985") == 1);
        item.setIs211(intVal(row, "is_211") == 1);
        item.setIsDoubleFirst(intVal(row, "is_double_first") == 1);
        item.setCollegeName(str(row, "college_name"));
        item.setProgramCode(str(row, "program_code"));
        item.setProgramName(str(row, "program_name"));
        item.setStudyMode(str(row, "study_mode"));
        item.setDegreeType(str(row, "degree_type"));
        item.setIs408(true);
        return item;
    }

    // ═══ Helpers ═══

    private static String normalizeTier(String tier)
    {
        switch (tier)
        {
            case "985": return "985";
            case "211": return "211";
            case "DOUBLE_FIRST": return "双一流";
            case "PUBLIC_REGULAR": return "普通本科";
            default: return "其他";
        }
    }

    private List<String> resolveProgramCodes(RecommendationRequest req)
    {
        Set<String> codes = new LinkedHashSet<>();
        if (req.getProgramCodes() != null)
            for (String c : req.getProgramCodes())
                if (c != null && !c.isBlank()) codes.add(c.trim());
        if (req.getDirectionKeys() != null)
            for (String k : req.getDirectionKeys())
            { List<String> m = PROGRAM_DIRECTION_MAP.get(k); if (m != null) codes.addAll(m); }
        return new ArrayList<>(codes);
    }

    private static Map<String, List<String>> buildProgramDirectionMap()
    {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("计算机技术（专硕）", List.of("085404"));
        map.put("电子信息（专硕）", List.of("085400", "085401", "085402", "085403", "085404", "085405", "085406", "085407", "085408", "085409", "085410", "085411", "085412"));
        map.put("计算机科学（学硕）", List.of("081200"));
        map.put("人工智能（学硕）", List.of("083900"));
        map.put("软件工程（学硕）", List.of("083500"));
        map.put("网络空间安全", List.of("083900", "085412"));
        return Collections.unmodifiableMap(map);
    }

    private static String str(Map<String, Object> row, String key)
    {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }

    private static int intVal(Map<String, Object> row, String key)
    {
        Object v = row.get(key);
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String && !((String) v).isEmpty())
        { try { return Integer.parseInt((String) v); } catch (NumberFormatException e) { return 0; } }
        return 0;
    }

    private static Integer intOrNull(Map<String, Object> row, String key)
    {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof Number) { int val = ((Number) v).intValue(); return val > 0 ? val : null; }
        if (v instanceof String && !((String) v).isEmpty())
        { try { int val = Integer.parseInt((String) v); return val > 0 ? val : null; } catch (NumberFormatException e) { return null; } }
        return null;
    }
}
