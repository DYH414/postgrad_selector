package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;

public interface ProgramSearchMapper
{
    List<RowMap> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);
}
