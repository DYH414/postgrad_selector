package com.ruoyi.postgrad.service.impl;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.dto.ProgramSummaryDTO;
import com.ruoyi.postgrad.domain.vo.RecommendResultVO;
import com.ruoyi.postgrad.domain.ai.AiRecommendationSafety;
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

    @Autowired private RecommendationMapper recommendationMapper;
    @Autowired private RecommendationLogMapper logMapper;
    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private SchoolMapper schoolMapper;

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
    public RecommendResultVO generateRecommendation(Long userId, Map<String, Object> request)
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

        List<ProgramSummaryDTO> candidates = fetchCandidates(examCombo, regions, majorDirections, estimatedScore, scoreRange, studyMode);
        List<ProgramSummaryDTO> matchedItems = new ArrayList<>(candidates);

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
                    .filter(item -> !"insufficient_data".equals(item.getFitLevel()))
                    .collect(Collectors.toList());
            }
            matchedItems.sort(averageGapComparator());
        }

        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("estimatedScore", estimatedScore);
        requestSnapshot.put("examCombo", examCombo);
        requestSnapshot.put("examSubjectsLabel", subjectsLabel(examCombo));
        requestSnapshot.put("targetRegions", regions);
        requestSnapshot.put("majorDirections", majorDirections);
        requestSnapshot.put("riskPreference", riskPreference);
        requestSnapshot.put("scoreRange", scoreRange);

        RecommendResultVO.ResultGroup group = new RecommendResultVO.ResultGroup();
        group.setGroupKey("matches");
        group.setGroupName("匹配院校");
        group.setItems(matchedItems);

        RecommendResultVO result = new RecommendResultVO();
        result.setRuleVersion(RULE_VERSION);
        result.setDataVersion(DATA_VERSION);
        result.setRequest(requestSnapshot);
        result.setTotalCandidates(matchedItems.size());
        result.setGlobalWarnings(globalWarnings());
        result.setItems(matchedItems);
        result.setGroups(Collections.singletonList(group));

        Long recommendationId = saveRecommendationLog(userId, requestSnapshot, result);
        result.setRecommendationId(recommendationId);
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
        var row = recommendationMapper.selectProgramForRecommendation(programId);
        if (row == null) throw new RuntimeException("专业不存在或已停招");
        int score = estimatedScore == null ? 0 : estimatedScore;
        ProgramSummaryDTO item = ProgramSummaryDTO.fromRowMap(row, score);

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
        detail.put("dataCompleteness", dataCompletenessInfo(item.getDataCompleteness() != null ? item.getDataCompleteness() : "C"));
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
                var row = recommendationMapper.selectProgramForRecommendation(programId);
                if (row != null) items.add(compareProgramItem(ProgramSummaryDTO.fromRowMap(row, score)));
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

    // ── fetch ──

    private List<ProgramSummaryDTO> fetchCandidates(String examCombo, List<String> regions,
        List<String> majorDirections, int estimatedScore, Integer scoreRange, String studyMode)
    {
        String subjectCodes = "22408".equals(examCombo) ? "101,204,302,408" : "101,201,301,408";
        List<String> regionParam = (regions == null || regions.isEmpty()) ? null : regions;
        List<String> codesParam = (majorDirections == null || majorDirections.isEmpty()) ? null : majorDirections;
        return recommendationMapper.selectCandidates(subjectCodes, regionParam, codesParam, estimatedScore, scoreRange, studyMode)
            .stream().map(row -> ProgramSummaryDTO.fromRowMap(row, estimatedScore)).collect(Collectors.toList());
    }

    private List<Map<String, Object>> computeTrends(Long programId, int estimatedScore)
    {
        List<Map<String, Object>> rows = new ArrayList<>(recommendationMapper.selectTrends(programId));
        Collections.reverse(rows);
        for (Map<String, Object> row : rows)
        {
            Integer scoreLine = nullableInt(row.get("scoreLine"));
            BigDecimal avg = decimalVal(row.get("avgAdmittedScore"));
            int score = estimatedScore <= 0 ? 0 : estimatedScore;
            row.put("scoreLineGap", scoreLine == null || score <= 0 ? null : score - scoreLine);
            Integer low = nullableInt(row.get("admissionLow"));
            row.put("admissionLowGap", low == null || score <= 0 ? null : score - low);
            Integer high = nullableInt(row.get("admissionHigh"));
            row.put("admissionHighGap", high == null || score <= 0 ? null : score - high);
            row.put("avgScoreGap", avg == null || score <= 0 ? null : score - avg.intValue());
        }
        return rows;
    }

    // ── comparators ──

    private boolean matchesAverageDive(ProgramSummaryDTO item, int scoreRange)
    {
        return item.getAvgScoreGap() != null && item.getAvgScoreGap() >= -scoreRange;
    }

    private Comparator<ProgramSummaryDTO> averageDiveRiskComparator(int scoreRange)
    {
        return (a, b) -> {
            int gapA = a.getAvgScoreGap() != null ? a.getAvgScoreGap() : 999;
            int gapB = b.getAvgScoreGap() != null ? b.getAvgScoreGap() : 999;
            int dA = Math.abs(gapA + scoreRange), dB = Math.abs(gapB + scoreRange);
            if (dA != dB) return Integer.compare(dA, dB);
            int avgA = a.getAvgAdmittedScore() != null ? a.getAvgAdmittedScore() : 0;
            int avgB = b.getAvgAdmittedScore() != null ? b.getAvgAdmittedScore() : 0;
            if (avgA != avgB) return Integer.compare(avgB, avgA);
            return Objects.compare(a.getSchoolName(), b.getSchoolName(), String::compareTo);
        };
    }

    private Comparator<ProgramSummaryDTO> averageGapComparator()
    {
        return (a, b) -> {
            int gapA = a.getAvgScoreGap() != null ? a.getAvgScoreGap() : 999;
            int gapB = b.getAvgScoreGap() != null ? b.getAvgScoreGap() : 999;
            if (gapA != gapB) return Integer.compare(gapA, gapB);
            return Objects.compare(a.getSchoolName(), b.getSchoolName(), String::compareTo);
        };
    }

    // ── persistence ──

    private Long saveRecommendationLog(Long userId, Map<String, Object> requestSnapshot, RecommendResultVO result)
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
            if (profile != null && profile.getAcceptPartTime() != null) return profile.getAcceptPartTime() == 1;
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

    private List<String> queryRegions() { return schoolMapper.selectDistinctProvinces(); }

    // ── response assembly ──

    private Map<String, Object> basicInfo(ProgramSummaryDTO item)
    {
        Map<String, Object> basic = new LinkedHashMap<>();
        for (String key : Arrays.asList("programId", "schoolId", "schoolName", "province", "city", "collegeName",
            "programCode", "programName", "researchDirection", "degreeType", "degreeTypeLabel", "examCombo", "examSubjectsLabel", "dataYear"))
            basic.put(key, getField(item, key));
        return basic;
    }

    private Map<String, Object> recommendationOverview(ProgramSummaryDTO item)
    {
        Map<String, Object> overview = new LinkedHashMap<>();
        for (String key : Arrays.asList("fitLevel", "fitLevelLabel", "scoreLine", "scoreLineGap",
            "admissionLow", "admissionLowGap", "admissionRangeLabel", "avgAdmittedScore", "avgScoreGap",
            "admissionHigh", "admissionHighGap", "planCount", "unifiedExamQuota", "retestCount",
            "admittedCount", "retestAdmissionRatio"))
            overview.put(key, getField(item, key));
        return overview;
    }

    private Map<String, Object> sourceInfo(ProgramSummaryDTO item)
    {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceName", item.getSourceName());
        source.put("sourceType", item.getSourceType());
        source.put("dataYear", item.getDataYear());
        source.put("sourceUrl", item.getSourceUrl());
        source.put("sourceTitle", item.getSourceTitle());
        source.put("sourceOwner", item.getSourceOwner());
        source.put("officialVerified", item.getOfficialVerified());
        return source;
    }

    private Object getField(ProgramSummaryDTO item, String key)
    {
        return switch (key) {
            case "programId" -> item.getProgramId();
            case "schoolId" -> item.getSchoolId();
            case "schoolName" -> item.getSchoolName();
            case "province" -> item.getProvince();
            case "city" -> item.getCity();
            case "collegeName" -> item.getCollegeName();
            case "programCode" -> item.getProgramCode();
            case "programName" -> item.getProgramName();
            case "researchDirection" -> item.getResearchDirection();
            case "degreeType" -> item.getDegreeType();
            case "degreeTypeLabel" -> item.getDegreeTypeLabel();
            case "examCombo" -> item.getExamCombo();
            case "examSubjectsLabel" -> item.getExamSubjectsLabel();
            case "dataYear" -> item.getDataYear();
            case "fitLevel" -> item.getFitLevel();
            case "fitLevelLabel" -> item.getFitLevelLabel();
            case "scoreLine" -> item.getScoreLine();
            case "scoreLineGap" -> item.getScoreLineGap();
            case "admissionLow" -> item.getAdmissionLow();
            case "admissionLowGap" -> item.getAdmissionLowGap();
            case "admissionHigh" -> item.getAdmissionHigh();
            case "admissionHighGap" -> item.getAdmissionHighGap();
            case "avgAdmittedScore" -> item.getAvgAdmittedScore();
            case "avgScoreGap" -> item.getAvgScoreGap();
            case "admissionRangeLabel" -> item.getAdmissionRangeLabel();
            case "planCount" -> item.getPlanCount();
            case "unifiedExamQuota" -> item.getUnifiedExamQuota();
            case "retestCount" -> item.getRetestCount();
            case "admittedCount" -> item.getAdmittedCount();
            case "retestAdmissionRatio" -> item.getRetestAdmissionRatio();
            default -> null;
        };
    }

    private Map<String, Object> compareProgramItem(ProgramSummaryDTO item)
    {
        Map<String, Object> compareItem = new LinkedHashMap<>(basicInfo(item));
        compareItem.putAll(recommendationOverview(item));
        compareItem.remove("fitLevel");
        compareItem.remove("fitLevelLabel");
        compareItem.put("dataCompleteness",
            item.getDataCompleteness() != null ? item.getDataCompleteness() : "C");
        compareItem.put("sourceUrl", item.getSourceUrl());
        compareItem.put("sourceOwner", item.getSourceOwner());
        compareItem.put("sourceTitle", item.getSourceTitle());
        return compareItem;
    }

    // ── static data ──

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
        item.put("code", code); item.put("label", code);
        item.put("subjectsLabel", label); item.put("subjectCodes", subjectCodes);
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
        item.put("description", switch (level) {
            case "A" -> "含复试线、拟录取区间、人数等字段";
            case "B" -> "含主要分数字段，部分字段缺失";
            default -> "仅有复试线或基础字段";
        });
        return item;
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
            row("sourceUrl", "N诺来源"));
    }

    private Map<String, Object> option(String value, String label) { Map<String, Object> m = new LinkedHashMap<>(); m.put("value", value); m.put("label", label); return m; }
    private Map<String, Object> row(String key, String label) { Map<String, Object> m = new LinkedHashMap<>(); m.put("key", key); m.put("label", label); return m; }

    private List<String> globalWarnings()
    {
        return Arrays.asList("复试线不是最低录取分。", "推荐学校不代表只有这些学校可以报。",
            "N诺数据可能遗漏、过时或错误，最终以院校官方公告为准。");
    }

    // ── value converters (kept for RowMap access in trends / detail) ──

    private String subjectsLabel(String examCombo) { return "22408".equals(examCombo) ? "政治 + 英语二 + 数学二 + 408" : "政治 + 英语一 + 数学一 + 408"; }

    private Integer nullableInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private int intVal(Object v, int fb) { Integer p = nullableInt(v); return p == null ? fb : p; }

    private BigDecimal decimalVal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private String stringVal(Object v, String fb) { if (v == null) return fb; String s = String.valueOf(v); return s.isBlank() ? fb : s; }

    private boolean boolVal(Object v, boolean fb) {
        if (v == null) return fb;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) return ((List<Object>) value).stream().filter(Objects::nonNull)
            .map(String::valueOf).filter(s -> !s.isBlank()).collect(Collectors.toList());
        String text = String.valueOf(value);
        if (text.isBlank()) return new ArrayList<>();
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private List<String> parseJsonList(Object value) {
        if (value == null) return new ArrayList<>();
        try { return JSON.parseArray(String.valueOf(value), String.class); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}
