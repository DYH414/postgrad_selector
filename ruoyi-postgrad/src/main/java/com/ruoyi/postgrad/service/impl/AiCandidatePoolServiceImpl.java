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
            return limit(recommendationMapper.selectProgramsByIds(candidateIds, estimatedScore));
        }

        List<String> regions = parseTargetRegions(profile == null ? null : profile.get("targetRegions"));
        List<RowMap> pool = queryAllExamCombos(regions, estimatedScore);
        if (pool.isEmpty() && !regions.isEmpty())
        {
            pool = queryAllExamCombos(Collections.emptyList(), estimatedScore);
        }
        return limit(pool);
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

    private List<RowMap> limit(List<RowMap> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return Collections.emptyList();
        }
        if (rows.size() <= DEFAULT_POOL_LIMIT)
        {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, DEFAULT_POOL_LIMIT));
    }
}
