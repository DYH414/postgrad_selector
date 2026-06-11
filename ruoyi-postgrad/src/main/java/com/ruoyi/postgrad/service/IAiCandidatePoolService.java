package com.ruoyi.postgrad.service;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.dto.CandidateProgramDTO;
import java.util.List;
import java.util.Map;

public interface IAiCandidatePoolService
{
    List<CandidateProgramDTO> buildPool(Map<String, Object> request, Map<String, Object> profile, int estimatedScore);

    List<CandidateProgramDTO> buildAnalysisPool(int estimatedScore, List<String> regions);

    List<CandidateProgramDTO> buildAgentPool(int estimatedScore, List<String> regions);

    List<CandidateProgramDTO> expandAgentPool(int estimatedScore, List<String> regions, Map<String, Object> filters, List<RowMap> existing);
}
