package com.ruoyi.postgrad.mapper;

import com.ruoyi.postgrad.domain.RecommendationRule;

public interface RecommendationRuleMapper
{
    RecommendationRule selectActiveRuleByScope(String ruleScope);
}
