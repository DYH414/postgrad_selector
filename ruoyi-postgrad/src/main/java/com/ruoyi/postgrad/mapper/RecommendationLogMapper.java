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

    int insertConversationState(@Param("id") Long id, @Param("conversationId") String conversationId,
        @Param("state") String state);
    String selectConversationState(@Param("conversationId") String conversationId);
    List<RowMap> selectAiReportListByUserId(@Param("userId") Long userId);
    int updateReportResult(@Param("id") Long id, @Param("resultJson") String resultJson);
}
