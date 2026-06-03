package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.service.IAiCandidatePoolService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiCandidatePoolServiceImpl implements IAiCandidatePoolService
{
    private static final int DEFAULT_SCORE_RANGE = 30;
    private static final int DEFAULT_POOL_LIMIT = 50;
    private static final int AGENT_INITIAL_LIMIT = 300;
    private static final int AGENT_MAX_LIMIT = 500;

    /**
     * Full subject-code strings for the two 408 exam combos, matching what
     * {@code ProgramRecommendationServiceImpl#subjectCodes} produces and how
     * the database stores {@code program_subject.group_concat} results.
     */
    private static final List<String> EXAM_408_SUBJECT_CODES = List.of(
        "101,204,302,408", // 22408: 政治 + 英语二 + 数学二 + 408
        "101,201,301,408"  // 11408: 政治 + 英语一 + 数学一 + 408
    );

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public List<RowMap> buildPool(Map<String, Object> request, Map<String, Object> profile, int estimatedScore)
    {
        List<Long> candidateIds = parseCandidateIds(request == null ? null : request.get("candidateIds"));
        if (!candidateIds.isEmpty())
        {
            return sortAndLimit(recommendationMapper.selectProgramsByIds(candidateIds, estimatedScore), estimatedScore);
        }

        List<String> regions = parseTargetRegions(profile == null ? null : profile.get("targetRegions"));
        List<RowMap> pool = queryAllExamCombos(regions, estimatedScore);
        if (pool.isEmpty() && !regions.isEmpty())
        {
            pool = queryAllExamCombos(Collections.emptyList(), estimatedScore);
        }
        return sortAndLimit(pool, estimatedScore);
    }

    /** Build a diverse pool: sort by proximity to estimatedScore, then sample evenly
     *  from three strata (保底/稳妥/冲刺) so every admission-difficulty tier is represented. */
    private List<RowMap> sortAndLimit(List<RowMap> rows, int estimatedScore)
    {
        if (rows == null || rows.isEmpty())
        {
            return Collections.emptyList();
        }
        if (rows.size() <= DEFAULT_POOL_LIMIT)
        {
            rows.sort((a, b) -> Integer.compare(scoreDistance(a, estimatedScore), scoreDistance(b, estimatedScore)));
            return rows;
        }

        // Stratify into 4 tiers: 保底(gap>=15), 稳妥(gap 5-14), 可冲(gap -10..4), 高难(gap<-10)
        List<RowMap> safe = new ArrayList<>();    // gap >= 15
        List<RowMap> steady = new ArrayList<>();  // gap 5-14
        List<RowMap> reach = new ArrayList<>();   // gap -10..4
        List<RowMap> hard = new ArrayList<>();    // gap < -10
        for (RowMap row : rows) {
            int gap = estimatedScore - scoreAvg(row);
            if (gap >= 15) safe.add(row);
            else if (gap >= 5) steady.add(row);
            else if (gap >= -10) reach.add(row);
            else hard.add(row);
        }

        // 保底档取高 gap 优先；稳妥档取接近分数优先
        safe.sort((a, b) -> Integer.compare(
            scoreDistance(b, estimatedScore), scoreDistance(a, estimatedScore))); // gap 大的在前
        steady.sort((a, b) -> Integer.compare(
            scoreDistance(a, estimatedScore), scoreDistance(b, estimatedScore)));

        int perStrata = DEFAULT_POOL_LIMIT / 4; // 12 each
        List<RowMap> result = new ArrayList<>(DEFAULT_POOL_LIMIT);
        // 保底留足 8 个名额（不够从稳妥补）
        int safeQuota = Math.max(8, Math.min(perStrata, safe.size()));
        result.addAll(takeUpTo(safe, safeQuota));
        result.addAll(takeUpTo(steady, perStrata + Math.max(0, perStrata - safe.size())));
        result.addAll(takeUpTo(reach, perStrata));
        result.addAll(takeUpTo(hard, Math.min(perStrata, hard.size())));

        // Fill remaining quota from whichever strata have more, sorted by proximity
        int remaining = DEFAULT_POOL_LIMIT - result.size();
        if (remaining > 0)
        {
            for (RowMap row : rows)
            {
                if (remaining <= 0) break;
                // Check if not already included
                if (containsById(result, row))
                {
                    continue;
                }
                result.add(row);
                remaining--;
            }
        }

        // Final sort by proximity so the summary has a natural order
        result.sort((a, b) -> Integer.compare(scoreDistance(a, estimatedScore), scoreDistance(b, estimatedScore)));
        return result;
    }

    private static List<RowMap> takeUpTo(List<RowMap> source, int max)
    {
        return new ArrayList<>(source.subList(0, Math.min(source.size(), max)));
    }

    private static boolean containsById(List<RowMap> rows, RowMap target)
    {
        Object targetId = target.get("programId");
        for (RowMap row : rows)
        {
            Object id = row.get("programId");
            if (id != null && id.equals(targetId)) return true;
        }
        return false;
    }

    private static final int SAFE_LIMIT = 15;
    private static final int STEADY_LIMIT = 20;
    private static final int REACH_LIMIT = 15;

    @Override
    public List<RowMap> buildAnalysisPool(int estimatedScore, List<String> regions)
    {
        int minScore = estimatedScore - 20;
        int maxScore = estimatedScore + 20;
        if (regions == null) regions = Collections.emptyList();

        List<RowMap> all = recommendationMapper.selectForAnalysis(
            estimatedScore, regions, minScore, maxScore);
        if (all == null || all.isEmpty())
        {
            return Collections.emptyList();
        }

        List<RowMap> safe = new ArrayList<>();
        List<RowMap> steady = new ArrayList<>();
        List<RowMap> reach = new ArrayList<>();

        for (RowMap row : all)
        {
            int gap = estimatedScore - scoreAvg(row);
            if (gap >= 15) safe.add(row);
            else if (gap >= 5) steady.add(row);
            else if (gap >= -10) reach.add(row);
        }

        List<RowMap> result = new ArrayList<>(SAFE_LIMIT + STEADY_LIMIT + REACH_LIMIT);
        result.addAll(limitByProximity(safe, estimatedScore, SAFE_LIMIT));
        result.addAll(limitByProximity(steady, estimatedScore, STEADY_LIMIT));
        result.addAll(limitByProximity(reach, estimatedScore, REACH_LIMIT));

        result.sort((a, b) -> Integer.compare(
            Math.abs(scoreAvg(a) - estimatedScore),
            Math.abs(scoreAvg(b) - estimatedScore)));
        return result;
    }

    @Override
    public List<RowMap> buildAgentPool(int estimatedScore, List<String> regions)
    {
        List<String> safeRegions = regions == null ? Collections.emptyList() : regions;
        int minScore = Math.max(0, estimatedScore - 80);
        int maxScore = estimatedScore + 40;
        List<RowMap> rows = recommendationMapper.selectForAgentPool(
            estimatedScore, safeRegions, minScore, maxScore, AGENT_MAX_LIMIT);
        return normalizeAgentPool(rows, estimatedScore, AGENT_INITIAL_LIMIT);
    }

    @Override
    public List<RowMap> expandAgentPool(int estimatedScore, List<String> regions,
        Map<String, Object> filters, List<RowMap> existing)
    {
        List<RowMap> expanded = buildAgentPool(estimatedScore, regions);
        List<RowMap> merged = new ArrayList<>();
        if (existing != null)
        {
            merged.addAll(existing);
        }
        merged.addAll(expanded);
        List<RowMap> deduped = dedupeAgentPool(merged);
        return new ArrayList<>(deduped.subList(0, Math.min(deduped.size(), AGENT_MAX_LIMIT)));
    }

    private List<RowMap> normalizeAgentPool(List<RowMap> rows, int estimatedScore, int limit)
    {
        if (rows == null || rows.isEmpty())
        {
            return Collections.emptyList();
        }
        List<RowMap> normalized = dedupeAgentPool(rows);
        for (RowMap row : normalized)
        {
            Object avg = row.get("avgAdmittedScore");
            if (avg instanceof Number n)
            {
                row.put("gap", estimatedScore - n.intValue());
                row.put("verificationStatus", "local_data_only");
            }
            else
            {
                row.put("gap", null);
                row.put("verificationStatus", "pending");
            }
        }
        normalized.sort((a, b) -> Integer.compare(agentRank(a, estimatedScore), agentRank(b, estimatedScore)));
        return new ArrayList<>(normalized.subList(0, Math.min(normalized.size(), limit)));
    }

    private List<RowMap> dedupeAgentPool(List<RowMap> rows)
    {
        List<RowMap> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (RowMap row : rows)
        {
            String key = dedupeKey(row);
            if (seen.add(key))
            {
                result.add(row);
            }
        }
        return result;
    }

    private String dedupeKey(RowMap row)
    {
        Object id = row.get("programId");
        if (id != null)
        {
            return "program:" + id;
        }
        return "fallback:" + row.get("schoolName") + ":" + row.get("collegeName") + ":"
            + row.get("programCode") + ":" + row.get("programName") + ":" + row.get("dataYear");
    }

    private int agentRank(RowMap row, int estimatedScore)
    {
        Object avg = row.get("avgAdmittedScore");
        int distance = avg instanceof Number n ? Math.abs(n.intValue() - estimatedScore) : 999;
        int completeness = "A".equals(row.get("dataCompleteness")) ? 0 : "B".equals(row.get("dataCompleteness")) ? 1 : 2;
        return completeness * 1000 + distance;
    }

    private List<RowMap> limitByProximity(List<RowMap> rows, int estimatedScore, int limit)
    {
        if (rows.size() <= limit) return new ArrayList<>(rows);
        rows.sort((a, b) -> Integer.compare(
            Math.abs(scoreAvg(a) - estimatedScore),
            Math.abs(scoreAvg(b) - estimatedScore)));
        return new ArrayList<>(rows.subList(0, limit));
    }

    private static int scoreAvg(RowMap row)
    {
        Object avgObj = row.get("avgAdmittedScore");
        if (avgObj instanceof Number n) return n.intValue();
        return 0;
    }

    private static int scoreDistance(RowMap row, int estimatedScore)
    {
        Object avgObj = row.get("avgAdmittedScore");
        if (avgObj instanceof Number n)
        {
            return Math.abs(n.intValue() - estimatedScore);
        }
        return 999;
    }

    /** Query both 408 exam combos and merge, keeping the first occurrence of each program. */
    private List<RowMap> queryAllExamCombos(List<String> regions, int estimatedScore)
    {
        List<RowMap> merged = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (String subjectCodes : EXAM_408_SUBJECT_CODES)
        {
            List<RowMap> rows = recommendationMapper.selectCandidates(
                subjectCodes, regions, null, estimatedScore, DEFAULT_SCORE_RANGE, "full_time");
            if (rows != null)
            {
                for (RowMap row : rows)
                {
                    Object pid = row.get("programId");
                    if (pid instanceof Number id && seen.add(id.longValue()))
                    {
                        merged.add(row);
                    }
                }
            }
        }
        return merged;
    }

    private List<Long> parseCandidateIds(Object value)
    {
        List<Long> ids = new ArrayList<>();
        for (Object item : flattenValue(value))
        {
            Long id = parsePositiveLong(item);
            if (id != null)
            {
                ids.add(id);
            }
        }
        return ids;
    }

    private List<String> parseTargetRegions(Object value)
    {
        if (value == null)
        {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection)
        {
            return cleanRegions(collection);
        }
        if (value.getClass().isArray())
        {
            return cleanRegions(flattenValue(value));
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "[]".equals(text) || "不限".equals(text))
        {
            return Collections.emptyList();
        }

        if (text.startsWith("[") && text.endsWith("]"))
        {
            try
            {
                return cleanRegions(JSON.parseArray(text, Object.class));
            }
            catch (RuntimeException ignored)
            {
                text = text.substring(1, text.length() - 1);
            }
        }

        String[] parts = text.split("[,，、]");
        List<String> regions = new ArrayList<>();
        for (String part : parts)
        {
            String region = part.trim();
            if (!region.isEmpty() && !"不限".equals(region))
            {
                regions.add(region);
            }
        }
        return regions;
    }

    private List<String> cleanRegions(Collection<?> values)
    {
        if (values == null)
        {
            return Collections.emptyList();
        }

        List<String> regions = new ArrayList<>();
        for (Object value : values)
        {
            if (value == null)
            {
                continue;
            }
            String region = String.valueOf(value).trim();
            if (!region.isEmpty() && !"不限".equals(region))
            {
                regions.add(region);
            }
        }
        return regions;
    }

    private List<Object> flattenValue(Object value)
    {
        if (value == null)
        {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection)
        {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray())
        {
            List<Object> items = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++)
            {
                items.add(Array.get(value, i));
            }
            return items;
        }
        return Collections.singletonList(value);
    }

    private Long parsePositiveLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return parseIntegralNumber(number);
        }
        try
        {
            long id = Long.parseLong(String.valueOf(value).trim());
            return id > 0 ? id : null;
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private Long parseIntegralNumber(Number number)
    {
        BigDecimal decimal;
        try
        {
            decimal = new BigDecimal(number.toString());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
        try
        {
            BigInteger integer = decimal.toBigIntegerExact();
            if (integer.signum() <= 0 || integer.bitLength() >= Long.SIZE)
            {
                return null;
            }
            return integer.longValue();
        }
        catch (ArithmeticException ex)
        {
            return null;
        }
    }

}
