package com.ruoyi.postgrad.service.impl;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.domain.RecommendationItem;
import com.ruoyi.postgrad.domain.RecommendationRequest;
import com.ruoyi.postgrad.domain.RecommendationResult;
import com.ruoyi.postgrad.service.IRecommendationService;

/**
 * 推荐引擎核心实现。
 * <p>基于学校复试线 + 院校修正值 + 复录比计算 effective_score，根据 gap 分档。</p>
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
        if (candidates.isEmpty())
        {
            return new RecommendationResult();
        }

        List<RecommendationItem> items = new ArrayList<>();
        for (Map<String, Object> row : candidates)
        {
            RecommendationItem item = buildItem(row);
            calculateEffectiveScore(item, row);
            assignTier(item, req.getEstimatedScore(), req.getRiskPreference());
            generateWarnings(item, row);
            items.add(item);
        }

        List<RecommendationItem> dedupedItems = deduplicateBySchoolAndProgram(items);

        RecommendationResult result = new RecommendationResult();
        result.setTotalCandidates(dedupedItems.size());
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
                    if (req.isIncludeNotRecommended())
                    {
                        result.getNotRecommended().add(item);
                    }
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

    // ═══ SQL fetch ═══

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
                   COALESCE(ascore.score_line, 0) score_line,
                   COALESCE(ascore.score_line_type, 'national_line') score_line_type,
                   ascore.single_politics, ascore.single_english, ascore.single_math,
                   ascore.single_professional,
                   COALESCE(ssc.correction_value, 0) correction_value,
                   COALESCE(ap.total_plan, 0) total_plan,
                   COALESCE(ap.unified_exam_quota, 0) unified_exam_quota,
                   COALESCE(ap.retest_count, 0) retest_count,
                   COALESCE(ar.admitted_count, 0) admitted_count,
                   COALESCE(ar.min_admitted_score, 0) min_admitted_score,
                   COALESCE(ar.avg_admitted_score, 0) avg_admitted_score,
                   COALESCE(pydq.completeness_level, 'D') completeness_level
            FROM program p
            JOIN college c ON p.college_id = c.id
            JOIN school s ON c.school_id = s.id
            LEFT JOIN admission_score ascore ON ascore.program_id = p.id
                AND ascore.year = (SELECT MAX(year) FROM admission_score WHERE program_id = p.id)
            LEFT JOIN admission_plan ap ON ap.program_id = p.id AND ap.year = ascore.year
            LEFT JOIN admission_result ar ON ar.program_id = p.id AND ar.year = ascore.year
            LEFT JOIN school_score_correction ssc ON ssc.school_id = s.id
            LEFT JOIN program_year_data_quality pydq ON pydq.program_id = p.id AND pydq.year = ascore.year
            WHERE p.is_408 = 1 AND p.status = 'active' AND s.status = 'active'
            """);

        // province filter
        if (req.getTargetProvinces() != null && !req.getTargetProvinces().isEmpty())
        {
            sql.append(" AND s.province IN (");
            for (int i = 0; i < req.getTargetProvinces().size(); i++)
            {
                sql.append(i > 0 ? ",?" : "?");
                args.add(req.getTargetProvinces().get(i));
            }
            sql.append(")");
        }

        // program code filter
        if (!programCodes.isEmpty())
        {
            sql.append(" AND p.program_code IN (");
            for (int i = 0; i < programCodes.size(); i++)
            {
                sql.append(i > 0 ? ",?" : "?");
                args.add(programCodes.get(i));
            }
            sql.append(")");
        }

        // study mode filter
        if (!req.isAcceptPartTime())
        {
            sql.append(" AND p.study_mode = 'full_time'");
        }

        // degree type filter
        if (!req.isAcceptAcademic())
        {
            sql.append(" AND p.degree_type = 'professional'");
        }

        sql.append(" ORDER BY s.tier, s.name, p.program_code");

        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ═══ Score calculation ═══

    private void calculateEffectiveScore(RecommendationItem item, Map<String, Object> row)
    {
        String scoreType = str(row, "score_line_type");
        int scoreLine = intVal(row, "score_line");
        int correction = intVal(row, "correction_value");
        int minAdmitted = intVal(row, "min_admitted_score");
        int retestCount = intVal(row, "retest_count");
        int admittedCount = intVal(row, "admitted_count");
        int effective;

        if ("school_defined".equals(scoreType) || "unknown".equals(scoreType))
        {
            // Real school line data
            if (minAdmitted > 0)
            {
                effective = Math.max(scoreLine, minAdmitted);
            }
            else
            {
                effective = scoreLine;
            }
            // Retest ratio adjustment
            double rr = admittedCount > 0 ? (double) retestCount / admittedCount : 0;
            if (rr > 2.0) { effective += 20; }
            else if (rr > 1.5) { effective += 10; }
            item.setScoreBasis("基于历年初试复试线");
        }
        else
        {
            // national_line — use correction
            effective = scoreLine + correction;
            if (correction > 0)
                item.setScoreBasis("国家线+院校修正(+" + correction + ")");
            else
                item.setScoreBasis("国家线基准(无修正)");
        }

        item.setEffectiveScore(effective);
    }

    // ═══ Tier assignment ═══

    private void assignTier(RecommendationItem item, int estimatedScore, String riskPreference)
    {
        String completeness = item.getCompletenessLevel();
        if ("E".equals(completeness))
        {
            item.setTierLabel("insufficient");
            item.setScoreGap(estimatedScore - item.getEffectiveScore());
            return;
        }

        int gap = estimatedScore - item.getEffectiveScore();
        item.setScoreGap(gap);

        String tier;
        if (gap >= 20) tier = "steady";
        else if (gap >= 5) tier = "focus";
        else if (gap >= -10) tier = "reach";
        else tier = "notRecommended";

        // Risk adjustment: small quota → downgrade one tier
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
        String scoreType = str(row, "score_line_type");
        int unifiedQuota = intVal(row, "unified_exam_quota");
        int retestCount = intVal(row, "retest_count");
        int admittedCount = intVal(row, "admitted_count");
        int protectsFirst = intVal(row, "protects_first_choice");

        // National line estimate warning
        if ("national_line".equals(scoreType))
        {
            int correction = intVal(row, "correction_value");
            if (correction > 0)
            {
                warnings.add("⚠️ 基于国家线+院校修正估算，仅供参考");
            }
            else
            {
                warnings.add("⚠️ 数据缺失，使用国家线估值，实际可能有偏差");
            }
        }

        // Small quota
        if (unifiedQuota > 0 && unifiedQuota < 10)
        {
            warnings.add("统考名额较少（" + unifiedQuota + "人），录取不确定性高");
        }

        // High competition
        if (admittedCount > 0 && retestCount > 0)
        {
            double rr = (double) retestCount / admittedCount;
            if (rr > 2.0)
            {
                warnings.add("复试竞争激烈（复录比" + String.format("%.1f", rr) + "）");
            }
        }

        // First choice protection
        if (protectsFirst == 0)
        {
            warnings.add("该院校一志愿保护机制较弱");
        }

        if (item.getScoreGap() >= -3 && item.getScoreGap() <= 3)
        {
            item.setWarningLevel("extreme");
            warnings.add("分数与估算线持平，录取风险极高");
        }

        item.setWarnings(warnings);
    }

    // ═══ Helper ═══

    private List<RecommendationItem> deduplicateBySchoolAndProgram(List<RecommendationItem> items)
    {
        Map<String, RecommendationItem> bestItems = new LinkedHashMap<>();
        for (RecommendationItem item : items)
        {
            String key = item.getSchoolName() + "\u0000" + item.getProgramCode();
            RecommendationItem best = bestItems.get(key);
            if (best == null)
            {
                bestItems.put(key, item);
                continue;
            }

            if (item.getScoreGap() > best.getScoreGap())
            {
                item.getSubPrograms().add(best);
                item.getSubPrograms().addAll(best.getSubPrograms());
                best.setSubPrograms(new ArrayList<>());
                bestItems.put(key, item);
            }
            else
            {
                best.getSubPrograms().add(item);
            }
        }
        List<RecommendationItem> deduped = new ArrayList<>(bestItems.values());
        for (RecommendationItem item : deduped)
        {
            item.getSubPrograms().sort(EFFECTIVE_SCORE_DESC);
        }
        return deduped;
    }

    private boolean isSteadyOverflow(RecommendationItem item, int estimatedScore)
    {
        if (!"steady".equals(item.getTierLabel()))
        {
            return false;
        }
        if (estimatedScore >= 380)
        {
            return item.getEffectiveScore() < 300;
        }
        if (estimatedScore >= 350)
        {
            return item.getEffectiveScore() < 270;
        }
        return false;
    }

    private void trimAndSort(List<RecommendationItem> tierItems, RecommendationResult result, RecommendationRequest req)
    {
        tierItems.sort(EFFECTIVE_SCORE_DESC);
        if (tierItems.size() <= TIER_LIMIT)
        {
            return;
        }
        if (req.isIncludeOverflow())
        {
            result.getOverflow().addAll(new ArrayList<>(tierItems.subList(TIER_LIMIT, tierItems.size())));
        }
        tierItems.subList(TIER_LIMIT, tierItems.size()).clear();
    }

    private void addOverflow(RecommendationResult result, RecommendationItem item, RecommendationRequest req)
    {
        if (req.isIncludeOverflow())
        {
            result.getOverflow().add(item);
        }
    }

    private RecommendationItem buildItem(Map<String, Object> row)
    {
        RecommendationItem item = new RecommendationItem();
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
        item.setCompletenessLevel(str(row, "completeness_level"));
        return item;
    }

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
        {
            for (String code : req.getProgramCodes())
            {
                if (code != null && !code.isBlank())
                {
                    codes.add(code.trim());
                }
            }
        }
        if (req.getDirectionKeys() != null)
        {
            for (String directionKey : req.getDirectionKeys())
            {
                List<String> mappedCodes = PROGRAM_DIRECTION_MAP.get(directionKey);
                if (mappedCodes != null)
                {
                    codes.addAll(mappedCodes);
                }
            }
        }
        return new ArrayList<>(codes);
    }

    private static Map<String, List<String>> buildProgramDirectionMap()
    {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("计算机技术（专硕）", List.of("085404"));
        map.put("电子信息（专硕）", List.of(
                "085400", "085401", "085402", "085403", "085404", "085405", "085406",
                "085407", "085408", "085409", "085410", "085411", "085412"));
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
        {
            try { return Integer.parseInt((String) v); }
            catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
