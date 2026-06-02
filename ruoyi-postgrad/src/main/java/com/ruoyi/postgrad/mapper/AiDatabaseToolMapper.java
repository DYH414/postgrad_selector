package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;

/**
 * AI Tool 专用：直接查询数据库院校数据，不受候选池限制。
 */
public interface AiDatabaseToolMapper
{
    /** 按条件灵活查询，最多返回 limit 条 */
    List<RowMap> querySchools(@Param("keyword") String keyword,
                              @Param("tier") String tier,
                              @Param("province") String province,
                              @Param("minScore") Integer minScore,
                              @Param("maxScore") Integer maxScore,
                              @Param("limit") int limit);
}
