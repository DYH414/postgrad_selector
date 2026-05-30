package com.ruoyi.postgrad.service.impl;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.service.IAiCandidatePoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds candidate pools for AI recommendation and analysis.
 *
 * <p>Pool building is separated so the AI report consumer can use the same
 * gap-based stratification without duplicating logic across the service
 * and the consumer.</p>
 */
@Service
public class AiCandidatePoolServiceImpl implements IAiCandidatePoolService
{
    private static final int SAFE_LIMIT = 15;
    private static final int STEADY_LIMIT = 20;
    private static final int REACH_LIMIT = 15;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public List<RowMap> buildPool(int estimatedScore, List<String> regions)
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
        return new ArrayList<>(all);
    }

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
            // gap < -10: skip
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

    private List<RowMap> limitByProximity(List<RowMap> rows, int estimatedScore, int limit)
    {
        if (rows.size() <= limit) return new ArrayList<>(rows);
        rows.sort((a, b) -> Integer.compare(
            Math.abs(scoreAvg(a) - estimatedScore),
            Math.abs(scoreAvg(b) - estimatedScore)));
        return new ArrayList<>(rows.subList(0, limit));
    }

    static int scoreAvg(RowMap row)
    {
        Object avgObj = row.get("avgAdmittedScore");
        if (avgObj instanceof Number n) return n.intValue();
        return 0;
    }
}
