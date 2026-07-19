package com.ruoyi.postgrad.observe.llm.mapper;

import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmTraceAggregate;
import com.ruoyi.postgrad.observe.domain.AiObserveGroupVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiLlmCallTraceMapper {
    int insert(AiLlmCallTrace trace);
    AiLlmTraceAggregate selectAggregate(@Param("start") LocalDateTime start);
    List<AiObserveGroupVO> selectModelGroups(@Param("start") LocalDateTime start);
    List<AiObserveGroupVO> selectOperationGroups(@Param("start") LocalDateTime start);
    AiLlmCallTrace selectLatest(@Param("start") LocalDateTime start);
    List<AiLlmCallTrace> selectByRunId(@Param("runId") String runId, @Param("limit") int limit);
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
