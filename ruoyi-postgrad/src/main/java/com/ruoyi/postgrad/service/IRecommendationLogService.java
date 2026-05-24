package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

public interface IRecommendationLogService
{
    List<Map<String, Object>> selectLogListByUserId(Long userId);
    Map<String, Object> selectLogByIdAndUserId(Long id, Long userId);
}
