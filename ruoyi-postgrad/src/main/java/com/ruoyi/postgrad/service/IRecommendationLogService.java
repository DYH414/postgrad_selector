package com.ruoyi.postgrad.service;

import java.util.Map;

public interface IRecommendationLogService
{
    Map<String, Object> selectLogByIdAndUserId(Long id, Long userId);
}
