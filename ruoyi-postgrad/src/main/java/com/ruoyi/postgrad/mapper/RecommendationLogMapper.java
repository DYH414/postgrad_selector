package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;

public interface RecommendationLogMapper
{
    List<RowMap> selectLogListByUserId(@Param("userId") Long userId);
    RowMap selectLogByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    int insertRecommendationLog(RecommendationLog log);
}
