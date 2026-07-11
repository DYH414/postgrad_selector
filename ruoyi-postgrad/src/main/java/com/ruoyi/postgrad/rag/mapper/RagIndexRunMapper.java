package com.ruoyi.postgrad.rag.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.rag.domain.RagIndexRun;

@Mapper
public interface RagIndexRunMapper {

    int insert(RagIndexRun run);

    int updateStatus(@Param("runId") String runId,
                     @Param("status") String status,
                     @Param("indexedChunkCount") Integer indexedChunkCount,
                     @Param("progressStage") String progressStage,
                     @Param("progressPercent") Integer progressPercent,
                     @Param("errorCode") String errorCode,
                     @Param("errorSummary") String errorSummary);

    int updateCandidateCollection(@Param("runId") String runId,
                                  @Param("candidateCollection") String candidateCollection);

    int switchToSucceeded(@Param("runId") String runId,
                          @Param("previousCollection") String previousCollection);

    RagIndexRun selectByRunId(@Param("runId") String runId);

    RagIndexRun selectLatest();

    RagIndexRun selectActive();

    int markInterrupted(@Param("statuses") List<String> statuses);
}
