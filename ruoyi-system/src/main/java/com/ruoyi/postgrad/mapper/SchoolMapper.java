package com.ruoyi.postgrad.mapper;

import java.util.List;
import com.ruoyi.postgrad.domain.School;

/**
 * 学校基础信息Mapper接口
 *
 * @author ruoyi
 */
public interface SchoolMapper
{
    public School selectSchoolById(Long id);

    public List<School> selectSchoolList(School school);

    public List<School> selectSchoolAll();

    public int insertSchool(School school);

    public int updateSchool(School school);

    public int deleteSchoolById(Long id);

    public int deleteSchoolByIds(Long[] ids);

    public School checkSchoolNameUnique(String name);
}
