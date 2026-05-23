package com.ruoyi.postgrad.service;

import com.ruoyi.postgrad.domain.RecommendationRequest;
import com.ruoyi.postgrad.domain.RecommendationResult;

/**
 * 推荐引擎Service接口
 */
public interface IRecommendationService
{
    RecommendationResult generate(RecommendationRequest request);

    RecommendationResult filter(RecommendationRequest request);
}
