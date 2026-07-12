package com.ruoyi.postgrad.observe.tool.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiToolCallTraceMapper {
    int insert(AiToolCallTrace trace);

    int complete(@Param("callId") String callId, @Param("status") String status,
        @Param("resultCode") String resultCode, @Param("retryable") boolean retryable,
        @Param("resultSummary") String resultSummary,
        @Param("resultTokenEstimate") int resultTokenEstimate,
        @Param("durationMs") long durationMs,
        @Param("confirmationExpiresAt") LocalDateTime confirmationExpiresAt);

    int updateStatus(@Param("callId") String callId, @Param("status") String status);

    List<AiToolCallTrace> selectRecent(@Param("limit") int limit);

    List<AiToolCallTrace> selectByRunId(@Param("runId") String runId, @Param("limit") int limit);
}
