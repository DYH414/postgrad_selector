package com.ruoyi.postgrad.rag.mapper;

import com.ruoyi.postgrad.rag.trace.RagRetrievalTrace;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagRetrievalTraceMapper {
    int insert(RagRetrievalTrace trace);

    RagRetrievalTrace selectById(@Param("id") Long id);

    List<RagRetrievalTrace> selectRecent(@Param("limit") int limit);

    List<RagRetrievalTrace> selectByRunId(@Param("runId") String runId, @Param("limit") int limit);
}
