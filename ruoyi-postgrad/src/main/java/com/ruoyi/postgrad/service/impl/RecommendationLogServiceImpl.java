package com.ruoyi.postgrad.service.impl;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.service.IRecommendationLogService;

@Service
public class RecommendationLogServiceImpl implements IRecommendationLogService
{
    @Autowired
    private RecommendationLogMapper logMapper;

    @Override
    public Map<String, Object> selectLogByIdAndUserId(Long id, Long userId)
    {
        return logMapper.selectLogByIdAndUserId(id, userId);
    }
}
