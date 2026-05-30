package com.ruoyi.postgrad.service;

import com.ruoyi.postgrad.domain.RowMap;
import java.util.List;

/**
 * Candidate pool building service for AI recommendation.
 */
public interface IAiCandidatePoolService
{
    List<RowMap> buildPool(int estimatedScore, List<String> regions);

    List<RowMap> buildAnalysisPool(int estimatedScore, List<String> regions);
}
