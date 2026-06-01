package com.ruoyi.postgrad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.IProgramRecommendationService;

@Service
public class ProgramRecommendationServiceImpl implements IProgramRecommendationService
{
    private static final String RULE_VERSION = "mvp-2026-05-24";
    private static final String DATA_VERSION = "nnuo-2025";

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private RecommendationLogMapper logMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private SchoolMapper schoolMapper;

    @Override
    public Map<String, Object> recommendationOptions(Long userId)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultProfile", loadDefaultProfile(userId));
        data.put("examCombos", examCombos());
        data.put("regions", queryRegions());
        data.put("riskPreferences", Arrays.asList(
            option("conservative", "稳妥优先"),
            option("balanced", "平衡兼顾"),
            option("aggressive", "冲刺优先")
        ));
        data.put("fitLevels", fitLevelDefinitions());
        data.put("dataCompletenessDefs", dataCompletenessDefinitions());
        data.put("globalWarnings", globalWarnings());
        return data;
    }

    @Override
    public Map<String, Object> generateRecommendation(Long userId, Map<String, Object> request)
    {
        int estimatedScore = intVal(request.get("estimatedScore"), 0);
        if (estimatedScore <= 0) throw new IllegalArgumentException("请输入预计初试总分");
        if (estimatedScore < 150 || estimatedScore > 500)
            throw new IllegalArgumentException("预计初试总分应在 150-500 之间");

        String examCombo = stringVal(request.get("examCombo"), "11408");
        String riskPreference = stringVal(request.get("riskPreference"), "balanced");
        Integer scoreRange = nullableInt(request.get("scoreRange"));
        boolean includeIncompleteData = boolVal(request.get("includeIncompleteData"), true);
        int pageSizePerGroup = Math.max(3, Math.min(intVal(request.get("pageSizePerGroup"), 12), 50));

        boolean acceptPartTime = readAcceptPartTime(userId);
        String studyMode = acceptPartTime ? null : "full_time";

        List<String> regions = stringList(request.get("targetRegions"));
        List<String> majorDirections = stringList(request.get("majorDirections"));

        List<Map<String, Object>> candidates = fetchCandidates(examCombo, regions, majorDirections, estimatedScore, scoreRange, studyMode);
        List<Map<String, Object>> normalized = candidates.stream()
            .map(row -> normalizeProgram(row, estimatedScore))
            .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        List<Map<String, Object>> matchedItems = new ArrayList<>(normalized);
        if (scoreRange != null)
        {
            matchedItems = matchedItems.stream()
                .filter(item -> matchesAverageDive(item, scoreRange))
                .collect(Collectors.toList());
            matchedItems.sort(averageDiveRiskComparator(scoreRange));
        }
        else
        {
            if (!includeIncompleteData)
            {
                matchedItems = matchedItems.stream()
                    .filter(item -> !"insufficient_data".equals(stringVal(item.get("fitLevel"), "insufficient_data")))
                    .collect(Collectors.toList());
            }
            matchedItems.sort(averageGapComparator());
        }
        grouped.put("matches", matchedItems);

        List<Map<String, Object>> groups = new ArrayList<>();
        for (String groupKey : grouped.keySet())
        {
            List<Map<String, Object>> items = grouped.get(groupKey);
            List<Map<String, Object>> visibleItems = scoreRange == null
                ? items.stream().limit(pageSizePerGroup).collect(Collectors.toList())
                : items;
            groups.add(group(groupKey, visibleItems));
        }

        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("estimatedScore", estimatedScore);
        requestSnapshot.put("examCombo", examCombo);
        requestSnapshot.put("examSubjectsLabel", subjectsLabel(examCombo));
        requestSnapshot.put("targetRegions", regions);
        requestSnapshot.put("majorDirections", majorDirections);
        requestSnapshot.put("riskPreference", riskPreference);
        requestSnapshot.put("scoreRange", scoreRange);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleVersion", RULE_VERSION);
        result.put("dataVersion", DATA_VERSION);
        result.put("request", requestSnapshot);
        result.put("summary", summary(grouped, normalized.size()));
        result.put("globalWarnings", globalWarnings());
        result.put("groups", groups);

        Long recommendationId = saveRecommendationLog(userId, requestSnapshot, result);
        result.put("recommendationId", recommendationId);
        return result;
    }

    @Override
    public Map<String, Object> loadRecommendationResult(Long userId, Long id)
    {
        Map<String, Object> log = logMapper.selectLogByIdAndUserId(id, userId);
        if (log == null) throw new RuntimeException("记录不存在");
        Map<String, Object> result = JSON.parseObject(String.valueOf(log.get("result_json")));
        result.put("recommendationId", id);
        return result;
    }

    @Override
    public Map<String, Object> programDetail(Long programId, Integer estimatedScore)
    {
        Map<String, Object> row = recommendationMapper.selectProgramForRecommendation(programId);
        if (row == null) throw new RuntimeException("专业不存在或已停招");
        int score = estimatedScore == null ? 0 : estimatedScore;
        Map<String, Object> item = normalizeProgram(row, score);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("basic", basicInfo(item));
        detail.put("recommendationOverview", recommendationOverview(item));
        detail.put("trends", computeTrends(programId, score));
        detail.put("riskWarnings", Arrays.asList(
            "复试线只代表进入复试门槛，不代表最低录取分。",
            "筛选学校不代表只有这些学校可以报，平台结果仅是基于现有数据的候选线索。",
            "N诺数据可能遗漏、过时或错误，请以院校官方公告为准。"
        ));
        detail.put("source", sourceInfo(item));
        detail.put("dataCompleteness", dataCompletenessInfo(stringVal(item.get("dataCompleteness"), "C")));
        return detail;
    }

    @Override
    public Map<String, Object> comparePrograms(List<Long> programIds, Integer estimatedScore)
    {
        int score = estimatedScore == null ? 0 : estimatedScore;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long programId : programIds)
        {
            try
            {
                Map<String, Object> row = recommendationMapper.selectProgramForRecommendation(programId);
                if (row != null) items.add(normalizeProgram(row, score));
            }
            catch (Exception ignored) {}
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("estimatedScore", score);
        data.put("globalWarnings", globalWarnings());
        data.put("items", items);
        data.put("rows", compareRows());
        return data;
    }

    // ---- fetch helpers ----

    private List<Map<String, Object>> fetchCandidates(String examCombo, List<String> regions,
        List<String> majorDirections, int estimatedScore, Integer scoreRange, String studyMode)
    {
        String subjectCodes = subjectCodes(examCombo);
        List<String> regionParam = (regions == null || regions.isEmpty()) ? null : regions;
        List<String> codesParam = (majorDirections == null || majorDirections.isEmpty()) ? null : majorDirections;
        return new ArrayList<>(recommendationMapper.selectCandidates(subjectCodes, regionParam, codesParam, estimatedScore, scoreRange, studyMode));
    }

    private List<Map<String, Object>> computeTrends(Long programId, int estimatedScore)
    {
        List<Map<String, Object>> rows = new ArrayList<>(recommendationMapper.selectTrends(programId));
        Collections.reverse(rows);
        for (Map<String, Object> row : rows)
        {
            Integer scoreLine = nullableInt(row.get("scoreLine"));
            BigDecimal avg = decimalVal(row.get("avgAdmittedScore"));
            row.put("scoreLineGap", scoreLine == null || estimatedScore <= 0 ? null : estimatedScore - scoreLine);
            Integer low = nullableInt(row.get("admissionLow"));
            row.put("admissionLowGap", low == null || estimatedScore <= 0 ? null : estimatedScore - low);
            Integer high = nullableInt(row.get("admissionHigh"));
            row.put("admissionHighGap", high == null || estimatedScore <= 0 ? null : estimatedScore - high);
            row.put("avgScoreGap", avg == null || estimatedScore <= 0 ? null : estimatedScore - avg.intValue());
        }
        return rows;
    }

    private Long saveRecommendationLog(Long userId, Map<String, Object> requestSnapshot, Map<String, Object> result)
    {
        RecommendationLog log = new RecommendationLog();
        log.setUserId(userId);
        log.setProfileSnapshot(JSON.toJSONString(requestSnapshot));
        log.setResultJson(JSON.toJSONString(result));
        log.setRuleVersion(RULE_VERSION);
        log.setDataVersion(DATA_VERSION);
        log.setIsPaid(0);
        logMapper.insertRecommendationLog(log);
        return log.getId();
    }

    private boolean readAcceptPartTime(Long userId)
    {
        if (userId == null) return false;
        try
        {
            var profile = userProfileMapper.selectUserProfileByUserId(userId);
            if (profile != null && profile.getAcceptPartTime() != null)
                return profile.getAcceptPartTime() == 1;
        }
        catch (Exception ignored) {}
        return false;
    }

    private Map<String, Object> loadDefaultProfile(Long userId)
    {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("estimatedScore", 300);
        defaults.put("examCombo", "11408");
        defaults.put("targetRegions", new ArrayList<>());
        defaults.put("riskPreference", "balanced");

        if (userId == null) return defaults;
        try
        {
            var profile = userProfileMapper.selectUserProfileByUserId(userId);
            if (profile != null)
            {
                defaults.put("estimatedScore", profile.getEstimatedScore());
                defaults.put("targetRegions", parseJsonList(profile.getTargetRegions()));
                defaults.put("riskPreference", profile.getRiskPreference());
            }
        }
        catch (Exception ignored) {}
        return defaults;
    }

    private List<String> queryRegions()
    {
        return schoolMapper.selectDistinctProvinces();
    }

    // ---- normalization ----

    private Map<String, Object> normalizeProgram(Map<String, Object> row, int estimatedScore)
    {
        Map<String, Object> item = new LinkedHashMap<>(row);
        Integer scoreLine = nullableInt(row.get("scoreLine"));
        BigDecimal avgScore = decimalVal(row.get("avgAdmittedScore"));
        Integer low = nullableInt(row.get("admissionLow"));
        Integer high = nullableInt(row.get("admissionHigh"));
        Integer admittedCount = nullableInt(row.get("admittedCount"));
        Integer retestCount = nullableInt(row.get("retestCount"));

        String subjectCodes = stringVal(row.get("subjectCodes"), "");
        String examCombo = examComboBySubjects(subjectCodes);
        String completeness = computedCompleteness(row);

        Integer scoreLineGap = scoreLine == null || estimatedScore <= 0 ? null : estimatedScore - scoreLine;
        Integer admissionLowGap = low == null || estimatedScore <= 0 ? null : estimatedScore - low;
        Integer admissionHighGap = high == null || estimatedScore <= 0 ? null : estimatedScore - high;
        Integer avgScoreGap = avgScore == null || estimatedScore <= 0 ? null : estimatedScore - avgScore.intValue();
        String fitLevel = fitLevel(avgScoreGap, completeness);

        item.put("examCombo", examCombo);
        item.put("examSubjectsLabel", subjectsLabel(examCombo));
        item.put("degreeTypeLabel", degreeTypeLabel(stringVal(row.get("degreeType"), "")));
        item.put("scoreLineGap", scoreLineGap);
        item.put("admissionLowGap", admissionLowGap);
        item.put("admissionHighGap", admissionHighGap);
        item.put("avgScoreGap", avgScoreGap);
        item.put("admissionRangeLabel", rangeLabel(low, high));
        item.put("retestAdmissionRatio", ratio(retestCount, admittedCount));
        item.put("dataCompleteness", completeness);
        item.put("dataCompletenessText", dataCompletenessDescription(completeness));
        item.put("fitLevel", fitLevel);
        item.put("fitLevelLabel", fitLevelLabel(fitLevel));
        item.put("warnings", warnings(item, completeness));
        item.put("sourceName", "N诺");
        item.put("sourceType", "third_party");
        item.put("sourceUrl", row.get("sourceUrl"));
        item.put("sourceTitle", row.get("sourceTitle"));
        item.put("sourceOwner", row.get("sourceOwner"));
        item.put("officialVerified", boolVal(row.get("officialVerified"), false));
        return item;
    }

    private String computedCompleteness(Map<String, Object> row)
    {
        boolean hasScore = nullableInt(row.get("scoreLine")) != null;
        boolean hasRange = nullableInt(row.get("admissionLow")) != null && nullableInt(row.get("admissionHigh")) != null;
        boolean hasAverage = decimalVal(row.get("avgAdmittedScore")) != null;
        boolean hasCount = nullableInt(row.get("planCount")) != null || nullableInt(row.get("admittedCount")) != null;
        boolean hasMainExtra = hasAverage || nullableInt(row.get("admissionLow")) != null
            || nullableInt(row.get("planCount")) != null || nullableInt(row.get("unifiedExamQuota")) != null;
        if (hasScore && hasRange && hasAverage && hasCount) return "A";
        if (hasScore && hasMainExtra) return "B";
        return "C";
    }

    // ---- business logic helpers (unchanged from original) ----

    private Map<String, Object> group(String groupKey, List<Map<String, Object>> items)
    {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupKey", groupKey);
        group.put("groupName", fitLevelLabel(groupKey));
        group.put("description", groupDescription(groupKey));
        group.put("items", items);
        return group;
    }

    private Comparator<Map<String, Object>> recommendationComparator(String riskPreference)
    {
        return (a, b) -> {
            int gapA = Math.abs(intVal(a.get("admissionLowGap"), 999));
            int gapB = Math.abs(intVal(b.get("admissionLowGap"), 999));
            if ("conservative".equals(riskPreference))
            {
                gapA = -intVal(a.get("admissionLowGap"), -999);
                gapB = -intVal(b.get("admissionLowGap"), -999);
            }
            return Integer.compare(gapA, gapB);
        };
    }

    private boolean matchesAverageDive(Map<String, Object> item, int scoreRange)
    {
        Integer avgScoreGap = nullableInt(item.get("avgScoreGap"));
        return avgScoreGap != null && avgScoreGap >= -scoreRange;
    }

    private Comparator<Map<String, Object>> averageDiveRiskComparator(int scoreRange)
    {
        return (a, b) -> {
            int gapA = intVal(a.get("avgScoreGap"), 999);
            int gapB = intVal(b.get("avgScoreGap"), 999);
            int distanceA = Math.abs(gapA + scoreRange);
            int distanceB = Math.abs(gapB + scoreRange);
            if (distanceA != distanceB) return Integer.compare(distanceA, distanceB);
            int avgA = intVal(a.get("avgAdmittedScore"), 0);
            int avgB = intVal(b.get("avgAdmittedScore"), 0);
            if (avgA != avgB) return Integer.compare(avgB, avgA);
            return stringVal(a.get("schoolName"), "").compareTo(stringVal(b.get("schoolName"), ""));
        };
    }

    private Comparator<Map<String, Object>> averageGapComparator()
    {
        return (a, b) -> {
            int gapA = intVal(a.get("avgScoreGap"), 999);
            int gapB = intVal(b.get("avgScoreGap"), 999);
            if (gapA != gapB) return Integer.compare(gapA, gapB);
            return stringVal(a.get("schoolName"), "").compareTo(stringVal(b.get("schoolName"), ""));
        };
    }

    private Map<String, Object> summary(Map<String, List<Map<String, Object>>> grouped, int totalCandidates)
    {
        Map<String, Object> summary = new LinkedHashMap<>();
        int insufficient = grouped.getOrDefault("insufficient_data", Collections.emptyList()).size();
        summary.put("totalCandidates", totalCandidates);
        summary.put("mainRecommendationCount", totalCandidates - insufficient);
        summary.put("insufficientDataCount", insufficient);
        summary.put("sprintCount", grouped.getOrDefault("sprint", Collections.emptyList()).size());
        summary.put("balancedSprintCount", grouped.getOrDefault("balanced_sprint", Collections.emptyList()).size());
        summary.put("steadyCount", grouped.getOrDefault("steady", Collections.emptyList()).size());
        summary.put("safeCount", grouped.getOrDefault("safe", Collections.emptyList()).size());
        return summary;
    }

    private List<String> warnings(Map<String, Object> item, String completeness)
    {
        List<String> warnings = new ArrayList<>();
        warnings.add("复试线不是最低录取分。");
        warnings.add("筛选学校不代表只有这些学校可以报。");
        if (!"A".equals(completeness)) warnings.add("N诺数据字段不完整，请重点核对院校官方公告。");
        Integer quota = nullableInt(item.get("unifiedExamQuota"));
        if (quota != null && quota < 10) warnings.add("统考名额较少，波动风险较高。");
        if (!boolVal(item.get("protectsFirstChoice"), true))
            warnings.add("保护一志愿信息不明确或不保护，需单独核实。");
        return warnings;
    }

    private String fitLevel(Integer avgScoreGap, String completeness)
    {
        if (!"A".equals(completeness) && !"B".equals(completeness)) return "insufficient_data";
        if (avgScoreGap == null) return "insufficient_data";
        if (avgScoreGap < -20) return "sprint";
        if (avgScoreGap < -5) return "balanced_sprint";
        if (avgScoreGap >= 15) return "safe";
        return "steady";
    }

    private Map<String, Object> basicInfo(Map<String, Object> item)
    {
        Map<String, Object> basic = new LinkedHashMap<>();
        for (String key : Arrays.asList("programId", "schoolId", "schoolName", "province", "city", "collegeName",
            "programCode", "programName", "researchDirection", "degreeType", "degreeTypeLabel", "examCombo", "examSubjectsLabel", "dataYear"))
            basic.put(key, item.get(key));
        return basic;
    }

    private Map<String, Object> recommendationOverview(Map<String, Object> item)
    {
        Map<String, Object> overview = new LinkedHashMap<>();
        for (String key : Arrays.asList("fitLevel", "fitLevelLabel", "scoreLine", "scoreLineGap",
            "admissionLow", "admissionLowGap", "admissionRangeLabel", "avgAdmittedScore", "avgScoreGap",
            "admissionHigh", "admissionHighGap", "planCount", "unifiedExamQuota", "retestCount",
            "admittedCount", "retestAdmissionRatio"))
            overview.put(key, item.get(key));
        return overview;
    }

    private Map<String, Object> sourceInfo(Map<String, Object> item)
    {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceName", "N诺");
        source.put("sourceType", "third_party");
        source.put("dataYear", item.get("dataYear"));
        source.put("sourceUrl", item.get("sourceUrl"));
        source.put("sourceTitle", item.get("sourceTitle"));
        source.put("sourceOwner", item.get("sourceOwner"));
        source.put("officialVerified", item.get("officialVerified"));
        return source;
    }

    // ---- label / display helpers ----

    private String fitLevelLabel(String key)
    {
        switch (key)
        {
            case "matches": return "匹配院校";
            case "sprint": return "冲刺";
            case "balanced_sprint": return "稳中偏冲";
            case "steady": return "稳妥候选";
            case "safe": return "保底候选";
            default: return "数据不足";
        }
    }

    private String groupDescription(String groupKey)
    {
        switch (groupKey)
        {
            case "matches": return "符合筛选范围的院校";
            case "sprint": return "录取概率较低，但仍有机会";
            case "balanced_sprint": return "有一定机会，需合理评估";
            case "steady": return "录取概率较高，适合作为主力候选";
            case "safe": return "预计分数高于历史最高录取分，但仍需看官方招生变化";
            default: return "字段不完整，仅作补充线索，不进入主推荐池";
        }
    }

    // ---- static data ----

    private List<Map<String, Object>> examCombos()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(examCombo("11408", "政治 + 英语一 + 数学一 + 408", Arrays.asList("101", "201", "301", "408")));
        list.add(examCombo("22408", "政治 + 英语二 + 数学二 + 408", Arrays.asList("101", "204", "302", "408")));
        return list;
    }

    private Map<String, Object> examCombo(String code, String label, List<String> subjectCodes)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", code);
        item.put("subjectsLabel", label);
        item.put("subjectCodes", subjectCodes);
        return item;
    }

    private List<Map<String, Object>> fitLevelDefinitions()
    {
        return Arrays.asList(
            option("sprint", "冲刺"), option("balanced_sprint", "稳中偏冲"),
            option("steady", "稳妥候选"), option("safe", "保底候选"),
            option("insufficient_data", "数据不足"));
    }

    private List<Map<String, Object>> dataCompletenessDefinitions()
    {
        return Arrays.asList(dataCompletenessInfo("A"), dataCompletenessInfo("B"), dataCompletenessInfo("C"));
    }

    private Map<String, Object> dataCompletenessInfo(String level)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("label", "N诺数据完整度 " + level);
        item.put("description", dataCompletenessDescription(level));
        return item;
    }

    private String dataCompletenessDescription(String level)
    {
        switch (level)
        {
            case "A": return "含复试线、拟录取区间、人数等字段";
            case "B": return "含主要分数字段，部分字段缺失";
            default: return "仅有复试线或基础字段";
        }
    }

    private List<Map<String, Object>> compareRows()
    {
        return Arrays.asList(
            row("schoolName", "学校"), row("programName", "专业"),
            row("examSubjectsLabel", "考试组合"), row("scoreLine", "复试线"),
            row("admissionLow", "最低录取分"), row("admissionLowGap", "与最低录取分差距"),
            row("admissionHigh", "最高录取分"), row("admissionHighGap", "与最高录取分差距"),
            row("avgScoreGap", "与拟录取均分差距"), row("admissionRangeLabel", "拟录取区间（总分）"),
            row("planCount", "招生人数（含推免）"), row("dataCompleteness", "N诺数据完整度"),
            row("sourceUrl", "N诺来源"), row("fitLevelLabel", "适配等级"));
    }

    private Map<String, Object> option(String value, String label)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value); item.put("label", label);
        return item;
    }

    private Map<String, Object> row(String key, String label)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key); item.put("label", label);
        return item;
    }

    private List<String> globalWarnings()
    {
        return Arrays.asList("复试线不是最低录取分。", "推荐学校不代表只有这些学校可以报。",
            "N诺数据可能遗漏、过时或错误，最终以院校官方公告为准。");
    }

    // ---- simple value converters ----

    private String subjectCodes(String examCombo) { return "22408".equals(examCombo) ? "101,204,302,408" : "101,201,301,408"; }
    private String examComboBySubjects(String subjectCodes) { return "101,204,302,408".equals(subjectCodes) ? "22408" : "11408"; }

    private String subjectsLabel(String examCombo) { return "22408".equals(examCombo) ? "政治 + 英语二 + 数学二 + 408" : "政治 + 英语一 + 数学一 + 408"; }
    private String degreeTypeLabel(String s) { return "academic".equals(s) ? "学硕" : "专硕"; }

    private String rangeLabel(Integer low, Integer high)
    {
        if (low == null && high == null) return null;
        if (Objects.equals(low, high)) return String.valueOf(low);
        return (low == null ? "-" : low) + "-" + (high == null ? "-" : high);
    }

    private BigDecimal ratio(Integer num, Integer den)
    {
        if (num == null || den == null || den == 0) return null;
        return BigDecimal.valueOf(num).divide(BigDecimal.valueOf(den), 2, RoundingMode.HALF_UP);
    }

    private Integer nullableInt(Object v)
    {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private int intVal(Object v, int fb) { Integer p = nullableInt(v); return p == null ? fb : p; }
    private BigDecimal decimalVal(Object v)
    {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private String stringVal(Object v, String fb) { if (v == null) return fb; String s = String.valueOf(v); return s.isBlank() ? fb : s; }

    private boolean boolVal(Object v, boolean fb)
    {
        if (v == null) return fb;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value)
    {
        if (value == null) return new ArrayList<>();
        if (value instanceof List)
            return ((List<Object>) value).stream().filter(Objects::nonNull)
                .map(String::valueOf).filter(s -> !s.isBlank()).collect(Collectors.toList());
        String text = String.valueOf(value);
        if (text.isBlank()) return new ArrayList<>();
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private List<String> parseJsonList(Object value)
    {
        if (value == null) return new ArrayList<>();
        try { return JSON.parseArray(String.valueOf(value), String.class); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}
