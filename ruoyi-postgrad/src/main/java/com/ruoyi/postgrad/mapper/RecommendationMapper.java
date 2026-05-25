package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;

public interface RecommendationMapper
{
    List<RowMap> selectCandidates(@Param("subjectCodes") String subjectCodes,
        @Param("regions") List<String> regions,
        @Param("programCodes") List<String> programCodes,
        @Param("estimatedScore") int estimatedScore);

    RowMap selectProgramForRecommendation(@Param("programId") Long programId);

    List<RowMap> selectTrends(@Param("programId") Long programId);
}
