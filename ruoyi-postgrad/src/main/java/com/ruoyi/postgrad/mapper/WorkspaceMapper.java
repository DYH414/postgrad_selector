package com.ruoyi.postgrad.mapper;

import java.util.List;
import com.ruoyi.postgrad.domain.RowMap;
import org.apache.ibatis.annotations.Param;

public interface WorkspaceMapper
{
    RowMap selectWorkspaceStats(@Param("keyword") String keyword,
                                @Param("province") String province,
                                @Param("tier") String tier,
                                @Param("year") Integer year,
                                @Param("is408") String is408,
                                @Param("completeness") String completeness);

    List<RowMap> selectWorkspaceSchools(@Param("keyword") String keyword,
                                        @Param("province") String province,
                                        @Param("tier") String tier,
                                        @Param("year") Integer year,
                                        @Param("is408") String is408,
                                        @Param("completeness") String completeness);

    RowMap selectSchoolWorkspaceStats(@Param("schoolId") Long schoolId,
                                      @Param("year") Integer year);

    List<RowMap> selectSchoolColleges(@Param("schoolId") Long schoolId);

    List<RowMap> selectSchoolPrograms(@Param("schoolId") Long schoolId,
                                      @Param("year") Integer year,
                                      @Param("is408") String is408,
                                      @Param("completeness") String completeness);

    List<RowMap> selectSchoolProgramYears(@Param("schoolId") Long schoolId,
                                          @Param("years") List<Integer> years);
}
