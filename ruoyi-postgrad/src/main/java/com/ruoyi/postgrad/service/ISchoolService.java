package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.postgrad.domain.School;

/**
 * 学校基础信息Service接口
 *
 * @author ruoyi
 */
public interface ISchoolService
{
    public School selectSchoolById(Long id);

    public List<School> selectSchoolList(School school);

    public List<School> selectSchoolAll();

    public Map<String, Object> selectSchoolOverview(Long id);

    public boolean checkSchoolNameUnique(School school);

    public int insertSchool(School school);

    public int updateSchool(School school);

    public int deleteSchoolByIds(Long[] ids);

    public int deleteSchoolById(Long id);
}
