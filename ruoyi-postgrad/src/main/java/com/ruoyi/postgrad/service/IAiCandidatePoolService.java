package com.ruoyi.postgrad.service;

import com.ruoyi.postgrad.domain.RowMap;
import java.util.List;
import java.util.Map;

public interface IAiCandidatePoolService
{
    List<RowMap> buildPool(Map<String, Object> request, Map<String, Object> profile, int estimatedScore);

    List<RowMap> buildAnalysisPool(int estimatedScore, List<String> regions);

    List<RowMap> buildAgentPool(int estimatedScore, List<String> regions);

    List<RowMap> expandAgentPool(int estimatedScore, List<String> regions, Map<String, Object> filters, List<RowMap> existing);
}
