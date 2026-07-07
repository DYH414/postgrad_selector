package com.ruoyi.postgrad.mapper;

import com.ruoyi.postgrad.domain.RowMap;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RagKnowledgeExportMapper {

    List<RowMap> selectProgramYearFacts(@Param("year") Integer year, @Param("limit") int limit);

    List<RowMap> selectProgramHistoryFacts(@Param("startYear") Integer startYear,
                                           @Param("endYear") Integer endYear,
                                           @Param("limit") int limit);
}
