package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

public interface IProgramRecommendationService
{
    Map<String, Object> recommendationOptions(Long userId);
    Map<String, Object> generateRecommendation(Long userId, Map<String, Object> request);
    Map<String, Object> loadRecommendationResult(Long userId, Long id);
    Map<String, Object> programDetail(Long programId, Integer estimatedScore);
    Map<String, Object> comparePrograms(List<Long> programIds, Integer estimatedScore);
}
