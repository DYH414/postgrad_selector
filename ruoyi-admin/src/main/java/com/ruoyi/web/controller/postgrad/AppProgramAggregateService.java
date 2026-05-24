package com.ruoyi.web.controller.postgrad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;

@Service
public class AppProgramAggregateService
{
    private static final String RULE_VERSION = "mvp-2026-05-24";
    private static final String DATA_VERSION = "nnuo-2025";

    @Autowired
    private JdbcTemplate jdbc;

    public Map<String, Object> recommendationOptions(Long userId)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultProfile", loadDefaultProfile(userId));
        data.put("examCombos", examCombos());
        data.put("regions", queryRegions());
        data.put("studyModes", Arrays.asList(
            option("any", "不限"),
            option("full_time", "全日制"),
            option("part_time", "非全日制")
        ));
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

    public Map<String, Object> generateRecommendation(Long userId, Map<String, Object> request)
    {
        int estimatedScore = intVal(request.get("estimatedScore"), 0);
        if (estimatedScore <= 0)
        {
            throw new IllegalArgumentException("请输入预计初试总分");
        }
        if (estimatedScore < 150 || estimatedScore > 500)
        {
            throw new IllegalArgumentException("预计初试总分应在 150-500 之间");
        }

        String examCombo = stringVal(request.get("examCombo"), "11408");
        String studyMode = stringVal(request.get("studyMode"), "any");
        String riskPreference = stringVal(request.get("riskPreference"), "balanced");
        boolean includeIncompleteData = boolVal(request.get("includeIncompleteData"), true);
        int pageSizePerGroup = intVal(request.get("pageSizePerGroup"), 12);
        pageSizePerGroup = Math.max(3, Math.min(pageSizePerGroup, 50));

        List<String> regions = stringList(request.get("targetRegions"));
        List<String> majorDirections = stringList(request.get("majorDirections"));

        List<Map<String, Object>> candidates = fetchCandidates(examCombo, regions, studyMode, majorDirections, estimatedScore);
        List<Map<String, Object>> normalized = candidates.stream()
            .map(row -> normalizeProgram(row, estimatedScore))
            .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        grouped.put("sprint", new ArrayList<>());
        grouped.put("balanced_sprint", new ArrayList<>());
        grouped.put("steady", new ArrayList<>());
        grouped.put("safe", new ArrayList<>());
        grouped.put("insufficient_data", new ArrayList<>());

        for (Map<String, Object> item : normalized)
        {
            String groupKey = stringVal(item.get("fitLevel"), "insufficient_data");
            if ("insufficient_data".equals(groupKey) && !includeIncompleteData)
            {
                continue;
            }
            grouped.get(groupKey).add(item);
        }

        grouped.values().forEach(list -> list.sort(recommendationComparator(riskPreference)));

        List<Map<String, Object>> groups = new ArrayList<>();
        for (String groupKey : grouped.keySet())
        {
            List<Map<String, Object>> items = grouped.get(groupKey);
            groups.add(group(groupKey, items.stream().limit(pageSizePerGroup).collect(Collectors.toList())));
        }

        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("estimatedScore", estimatedScore);
        requestSnapshot.put("examCombo", examCombo);
        requestSnapshot.put("examSubjectsLabel", subjectsLabel(examCombo));
        requestSnapshot.put("targetRegions", regions);
        requestSnapshot.put("studyMode", studyMode);
        requestSnapshot.put("majorDirections", majorDirections);
        requestSnapshot.put("riskPreference", riskPreference);

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

    public Map<String, Object> loadRecommendationResult(Long userId, Long id)
    {
        Map<String, Object> log = jdbc.queryForMap(
            "SELECT id, result_json FROM recommendation_log WHERE id = ? AND user_id = ?",
            id, userId);
        Map<String, Object> result = JSON.parseObject(String.valueOf(log.get("result_json")));
        result.put("recommendationId", id);
        return result;
    }

    public Map<String, Object> programDetail(Long programId, Integer estimatedScore)
    {
        Map<String, Object> row = fetchProgram(programId);
        int score = estimatedScore == null ? 0 : estimatedScore;
        Map<String, Object> item = normalizeProgram(row, score);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("basic", basicInfo(item));
        detail.put("recommendationOverview", recommendationOverview(item));
        detail.put("trends", fetchTrends(programId, score));
        detail.put("riskWarnings", Arrays.asList(
            "复试线只代表进入复试门槛，不代表最低录取分。",
            "推荐学校不代表只有这些学校可以报，平台结果仅是基于现有数据的候选线索。",
            "N诺数据可能遗漏、过时或错误，请以院校官方公告为准。"
        ));
        detail.put("source", sourceInfo(item));
        detail.put("dataCompleteness", dataCompletenessInfo(stringVal(item.get("dataCompleteness"), "C")));
        return detail;
    }

    public Map<String, Object> comparePrograms(List<Long> programIds, Integer estimatedScore)
    {
        int score = estimatedScore == null ? 0 : estimatedScore;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long programId : programIds)
        {
            try
            {
                items.add(normalizeProgram(fetchProgram(programId), score));
            }
            catch (Exception ignored)
            {
                // Skip deleted or inactive programs instead of failing the whole comparison.
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("estimatedScore", score);
        data.put("globalWarnings", globalWarnings());
        data.put("items", items);
        data.put("rows", compareRows());
        return data;
    }

    private List<Map<String, Object>> fetchCandidates(String examCombo, List<String> regions, String studyMode,
        List<String> majorDirections, int estimatedScore)
    {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(baseProgramSql());
        sql.append(" WHERE p.status = 'active' AND s.status = 'active' AND p.is_408 = 1");
        sql.append(" AND sc.score_line IS NOT NULL");
        sql.append(" AND subj.subject_codes = ?");
        args.add(subjectCodes(examCombo));

        if (regions != null && !regions.isEmpty())
        {
            sql.append(" AND s.province IN (").append(placeholders(regions.size())).append(")");
            args.addAll(regions);
        }
        if (studyMode != null && !"any".equals(studyMode))
        {
            sql.append(" AND p.study_mode = ?");
            args.add(studyMode);
        }
        if (majorDirections != null && !majorDirections.isEmpty())
        {
            sql.append(" AND p.program_code IN (").append(placeholders(majorDirections.size())).append(")");
            args.addAll(majorDirections);
        }

        sql.append(" ORDER BY CASE WHEN ar.min_admitted_score IS NULL THEN 1 ELSE 0 END ASC, ");
        sql.append("COALESCE(q.completeness_level, 'C') ASC, ABS(CAST(ar.min_admitted_score AS SIGNED) - ?) ASC, s.tier ASC LIMIT ?");
        args.add(estimatedScore);
        args.add(300);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private Map<String, Object> fetchProgram(Long programId)
    {
        String sql = baseProgramSql()
            + " WHERE p.id = ? AND p.status = 'active' AND s.status = 'active' AND sc.score_line IS NOT NULL";
        return jdbc.queryForMap(sql, programId);
    }

    private String baseProgramSql()
    {
        return """
            SELECT
              p.id AS programId,
              s.id AS schoolId,
              s.name AS schoolName,
              s.province AS province,
              s.city AS city,
              s.tier AS schoolTier,
              s.is_985 AS is985,
              s.is_211 AS is211,
              s.is_double_first AS isDoubleFirst,
              c.name AS collegeName,
              p.program_code AS programCode,
              p.program_name AS programName,
              p.research_direction AS researchDirection,
              p.study_mode AS studyMode,
              p.degree_type AS degreeType,
              p.score_scale AS scoreScale,
              p.protects_first_choice AS protectsFirstChoice,
              p.is_joint_program AS isJointProgram,
              subj.subject_codes AS subjectCodes,
              subj.subject_names AS subjectNames,
              sc.year AS dataYear,
              sc.score_line AS scoreLine,
              ap.total_plan AS planCount,
              ap.unified_exam_quota AS unifiedExamQuota,
              ap.retest_count AS retestCount,
              ar.admitted_count AS admittedCount,
              ar.min_admitted_score AS admissionLow,
              ar.avg_admitted_score AS avgAdmittedScore,
              ar.max_admitted_score AS admissionHigh,
              COALESCE(q.completeness_level, 'C') AS dataCompleteness,
              q.has_official_source AS officialVerified
            FROM program p
            JOIN college c ON p.college_id = c.id
            JOIN school s ON c.school_id = s.id
            LEFT JOIN (
              SELECT ps.program_id,
                     GROUP_CONCAT(sub.code ORDER BY ps.subject_order SEPARATOR ',') AS subject_codes,
                     GROUP_CONCAT(sub.name ORDER BY ps.subject_order SEPARATOR ' + ') AS subject_names
              FROM program_subject ps
              JOIN subject sub ON ps.subject_id = sub.id
              GROUP BY ps.program_id
            ) subj ON subj.program_id = p.id
            JOIN admission_score sc ON sc.program_id = p.id
              AND sc.year = (
                SELECT MAX(sc2.year)
                FROM admission_score sc2
                WHERE sc2.program_id = p.id AND sc2.score_line IS NOT NULL
              )
            LEFT JOIN admission_plan ap ON ap.program_id = p.id AND ap.year = sc.year
            LEFT JOIN admission_result ar ON ar.program_id = p.id AND ar.year = sc.year
            LEFT JOIN program_year_data_quality q ON q.program_id = p.id AND q.year = sc.year
            """;
    }

    private List<Map<String, Object>> fetchTrends(Long programId, int estimatedScore)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT sc.year AS year,
                   sc.score_line AS scoreLine,
                   ar.avg_admitted_score AS avgAdmittedScore,
                   ar.min_admitted_score AS admissionLow,
                   ar.max_admitted_score AS admissionHigh,
                   ar.admitted_count AS admittedCount
            FROM admission_score sc
            LEFT JOIN admission_result ar ON ar.program_id = sc.program_id AND ar.year = sc.year
            WHERE sc.program_id = ? AND sc.score_line IS NOT NULL
            ORDER BY sc.year DESC
            LIMIT 3
            """, programId);
        Collections.reverse(rows);
        for (Map<String, Object> row : rows)
        {
            Integer scoreLine = nullableInt(row.get("scoreLine"));
            BigDecimal avg = decimalVal(row.get("avgAdmittedScore"));
            row.put("scoreLineGap", scoreLine == null || estimatedScore <= 0 ? null : estimatedScore - scoreLine);
            Integer low = nullableInt(row.get("admissionLow"));
            row.put("admissionLowGap", low == null || estimatedScore <= 0 ? null : estimatedScore - low);
            row.put("avgScoreGap", avg == null || estimatedScore <= 0 ? null : estimatedScore - avg.intValue());
        }
        return rows;
    }

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
        Integer avgScoreGap = avgScore == null || estimatedScore <= 0 ? null : estimatedScore - avgScore.intValue();
        String fitLevel = fitLevel(admissionLowGap, completeness);

        item.put("examCombo", examCombo);
        item.put("examSubjectsLabel", subjectsLabel(examCombo));
        item.put("studyModeLabel", studyModeLabel(stringVal(row.get("studyMode"), "")));
        item.put("degreeTypeLabel", degreeTypeLabel(stringVal(row.get("degreeType"), "")));
        item.put("scoreLineGap", scoreLineGap);
        item.put("admissionLowGap", admissionLowGap);
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
        item.put("officialVerified", boolVal(row.get("officialVerified"), false));
        return item;
    }

    private Long saveRecommendationLog(Long userId, Map<String, Object> requestSnapshot, Map<String, Object> result)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO recommendation_log (user_id, profile_snapshot, result_json, rule_version, data_version, is_paid, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, 0, NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, JSON.toJSONString(requestSnapshot));
            ps.setString(3, JSON.toJSONString(result));
            ps.setString(4, RULE_VERSION);
            ps.setString(5, DATA_VERSION);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private Map<String, Object> loadDefaultProfile(Long userId)
    {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("estimatedScore", 300);
        defaults.put("examCombo", "11408");
        defaults.put("targetRegions", new ArrayList<>());
        defaults.put("studyMode", "any");
        defaults.put("riskPreference", "balanced");

        if (userId == null)
        {
            return defaults;
        }
        try
        {
            Map<String, Object> profile = jdbc.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId);
            defaults.put("estimatedScore", profile.get("estimated_score"));
            defaults.put("targetRegions", parseJsonList(profile.get("target_regions")));
            defaults.put("riskPreference", profile.get("risk_preference"));
        }
        catch (Exception ignored)
        {
        }
        return defaults;
    }

    private List<String> queryRegions()
    {
        return jdbc.queryForList(
            "SELECT DISTINCT province FROM school WHERE status = 'active' AND province IS NOT NULL AND province <> '' ORDER BY province",
            String.class);
    }

    private Map<String, Object> basicInfo(Map<String, Object> item)
    {
        Map<String, Object> basic = new LinkedHashMap<>();
        for (String key : Arrays.asList("programId", "schoolId", "schoolName", "province", "city", "collegeName",
            "programCode", "programName", "researchDirection", "studyMode", "studyModeLabel", "degreeType",
            "degreeTypeLabel", "examCombo", "examSubjectsLabel", "dataYear"))
        {
            basic.put(key, item.get(key));
        }
        return basic;
    }

    private Map<String, Object> recommendationOverview(Map<String, Object> item)
    {
        Map<String, Object> overview = new LinkedHashMap<>();
        for (String key : Arrays.asList("fitLevel", "fitLevelLabel", "scoreLine", "scoreLineGap",
            "admissionLow", "admissionLowGap", "admissionRangeLabel", "avgAdmittedScore", "avgScoreGap", "planCount", "unifiedExamQuota",
            "retestCount", "admittedCount", "retestAdmissionRatio"))
        {
            overview.put(key, item.get(key));
        }
        return overview;
    }

    private Map<String, Object> sourceInfo(Map<String, Object> item)
    {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceName", "N诺");
        source.put("sourceType", "third_party");
        source.put("dataYear", item.get("dataYear"));
        source.put("officialVerified", item.get("officialVerified"));
        return source;
    }

    private List<Map<String, Object>> compareRows()
    {
        return Arrays.asList(
            row("schoolName", "学校"),
            row("programName", "专业"),
            row("examSubjectsLabel", "考试组合"),
            row("scoreLine", "复试线"),
            row("admissionLow", "最低录取分"),
            row("admissionLowGap", "与最低录取分差距"),
            row("avgScoreGap", "与拟录取均分差距"),
            row("admissionRangeLabel", "拟录取区间（总分）"),
            row("planCount", "招生人数（含推免）"),
            row("dataCompleteness", "N诺数据完整度"),
            row("fitLevelLabel", "适配等级")
        );
    }

    private Map<String, Object> summary(Map<String, List<Map<String, Object>>> grouped, int totalCandidates)
    {
        Map<String, Object> summary = new LinkedHashMap<>();
        int insufficient = grouped.get("insufficient_data").size();
        summary.put("totalCandidates", totalCandidates);
        summary.put("mainRecommendationCount", totalCandidates - insufficient);
        summary.put("insufficientDataCount", insufficient);
        summary.put("sprintCount", grouped.get("sprint").size());
        summary.put("balancedSprintCount", grouped.get("balanced_sprint").size());
        summary.put("steadyCount", grouped.get("steady").size());
        summary.put("safeCount", grouped.get("safe").size());
        return summary;
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

    private List<String> warnings(Map<String, Object> item, String completeness)
    {
        List<String> warnings = new ArrayList<>();
        warnings.add("复试线不是最低录取分。");
        warnings.add("推荐学校不代表只有这些学校可以报。");
        if (!"A".equals(completeness))
        {
            warnings.add("N诺数据字段不完整，请重点核对院校官方公告。");
        }
        Integer quota = nullableInt(item.get("unifiedExamQuota"));
        if (quota != null && quota < 10)
        {
            warnings.add("统考名额较少，波动风险较高。");
        }
        if (!boolVal(item.get("protectsFirstChoice"), true))
        {
            warnings.add("保护一志愿信息不明确或不保护，需单独核实。");
        }
        return warnings;
    }

    private String fitLevel(Integer admissionLowGap, String completeness)
    {
        if (!"A".equals(completeness) && !"B".equals(completeness))
        {
            return "insufficient_data";
        }
        if (admissionLowGap == null)
        {
            return "insufficient_data";
        }
        if (admissionLowGap < -20)
        {
            return "sprint";
        }
        if (admissionLowGap < 5)
        {
            return "balanced_sprint";
        }
        if (admissionLowGap < 35)
        {
            return "steady";
        }
        return "safe";
    }

    private String computedCompleteness(Map<String, Object> row)
    {
        boolean hasScore = nullableInt(row.get("scoreLine")) != null;
        boolean hasRange = nullableInt(row.get("admissionLow")) != null && nullableInt(row.get("admissionHigh")) != null;
        boolean hasAverage = decimalVal(row.get("avgAdmittedScore")) != null;
        boolean hasCount = nullableInt(row.get("planCount")) != null || nullableInt(row.get("admittedCount")) != null;
        boolean hasMainExtra = hasAverage || nullableInt(row.get("admissionLow")) != null
            || nullableInt(row.get("planCount")) != null || nullableInt(row.get("unifiedExamQuota")) != null;

        if (hasScore && hasRange && hasAverage && hasCount)
        {
            return "A";
        }
        if (hasScore && hasMainExtra)
        {
            return "B";
        }
        return "C";
    }

    private Map<String, Object> group(String groupKey, List<Map<String, Object>> items)
    {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupKey", groupKey);
        group.put("groupName", fitLevelLabel(groupKey));
        group.put("description", groupDescription(groupKey));
        group.put("items", items);
        return group;
    }

    private String groupDescription(String groupKey)
    {
        switch (groupKey)
        {
            case "sprint": return "录取概率较低，但仍有机会";
            case "balanced_sprint": return "有一定机会，需合理评估";
            case "steady": return "录取概率较高，适合作为主力候选";
            case "safe": return "风险相对较低，但仍需看官方招生变化";
            default: return "字段不完整，仅作补充线索，不进入主推荐池";
        }
    }

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
            option("sprint", "冲刺"),
            option("balanced_sprint", "稳中偏冲"),
            option("steady", "稳妥候选"),
            option("safe", "保底候选"),
            option("insufficient_data", "数据不足")
        );
    }

    private List<Map<String, Object>> dataCompletenessDefinitions()
    {
        return Arrays.asList(
            dataCompletenessInfo("A"),
            dataCompletenessInfo("B"),
            dataCompletenessInfo("C")
        );
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

    private List<String> globalWarnings()
    {
        return Arrays.asList(
            "复试线不是最低录取分。",
            "推荐学校不代表只有这些学校可以报。",
            "N诺数据可能遗漏、过时或错误，最终以院校官方公告为准。"
        );
    }

    private String fitLevelLabel(String key)
    {
        switch (key)
        {
            case "sprint": return "冲刺";
            case "balanced_sprint": return "稳中偏冲";
            case "steady": return "稳妥候选";
            case "safe": return "保底候选";
            default: return "数据不足";
        }
    }

    private String subjectCodes(String examCombo)
    {
        return "22408".equals(examCombo) ? "101,204,302,408" : "101,201,301,408";
    }

    private String examComboBySubjects(String subjectCodes)
    {
        return "101,204,302,408".equals(subjectCodes) ? "22408" : "11408";
    }

    private String subjectsLabel(String examCombo)
    {
        return "22408".equals(examCombo) ? "政治 + 英语二 + 数学二 + 408" : "政治 + 英语一 + 数学一 + 408";
    }

    private String studyModeLabel(String studyMode)
    {
        return "part_time".equals(studyMode) ? "非全日制" : "全日制";
    }

    private String degreeTypeLabel(String degreeType)
    {
        return "academic".equals(degreeType) ? "学硕" : "专硕";
    }

    private String rangeLabel(Integer low, Integer high)
    {
        if (low == null && high == null) return null;
        if (Objects.equals(low, high)) return String.valueOf(low);
        return (low == null ? "-" : low) + "-" + (high == null ? "-" : high);
    }

    private BigDecimal ratio(Integer numerator, Integer denominator)
    {
        if (numerator == null || denominator == null || denominator == 0)
        {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> option(String value, String label)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        return item;
    }

    private Map<String, Object> row(String key, String label)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        return item;
    }

    private String placeholders(int count)
    {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private List<String> parseJsonList(Object value)
    {
        if (value == null)
        {
            return new ArrayList<>();
        }
        try
        {
            return JSON.parseArray(String.valueOf(value), String.class);
        }
        catch (Exception e)
        {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value)
    {
        if (value == null)
        {
            return new ArrayList<>();
        }
        if (value instanceof List)
        {
            return ((List<Object>) value).stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        }
        String text = String.valueOf(value);
        if (text.isBlank())
        {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }

    private Integer nullableInt(Object value)
    {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private int intVal(Object value, int fallback)
    {
        Integer parsed = nullableInt(value);
        return parsed == null ? fallback : parsed;
    }

    private BigDecimal decimalVal(Object value)
    {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try
        {
            return new BigDecimal(String.valueOf(value));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String stringVal(Object value, String fallback)
    {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private boolean boolVal(Object value, boolean fallback)
    {
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
}
