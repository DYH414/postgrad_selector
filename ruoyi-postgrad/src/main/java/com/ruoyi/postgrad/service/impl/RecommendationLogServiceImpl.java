package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.List;
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
    public List<Map<String, Object>> selectLogListByUserId(Long userId)
    {
        return new ArrayList<>(logMapper.selectLogListByUserId(userId));
    }

    @Override
    public Map<String, Object> selectLogByIdAndUserId(Long id, Long userId)
    {
        return logMapper.selectLogByIdAndUserId(id, userId);
    }
}
