package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.School;

/**
 * 学校基础信息Mapper接口
 *
 * @author ruoyi
 */
public interface SchoolMapper
{
    public School selectSchoolById(@Param("id") Long id);

    public List<School> selectSchoolList(School school);

    public List<School> selectSchoolAll();

    public RowMap selectSchoolOverviewStats(@Param("id") Long id);

    public List<RowMap> selectSchoolOverviewColleges(@Param("id") Long id);

    public List<RowMap> selectSchoolOverviewPrograms(@Param("id") Long id);

    public int insertSchool(School school);

    public int updateSchool(School school);

    public int deleteSchoolById(@Param("id") Long id);

    public int deleteSchoolByIds(Long[] ids);

    public School checkSchoolNameUnique(String name);

    public List<String> selectDistinctProvinces();
}
