package com.ruoyi.postgrad.service.impl;

import java.util.List;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.postgrad.domain.School;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.service.ISchoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 学校基础信息Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class SchoolServiceImpl implements ISchoolService
{
    @Autowired
    private SchoolMapper schoolMapper;

    @Override
    public School selectSchoolById(Long id)
    {
        return schoolMapper.selectSchoolById(id);
    }

    @Override
    public List<School> selectSchoolList(School school)
    {
        return schoolMapper.selectSchoolList(school);
    }

    @Override
    public List<School> selectSchoolAll()
    {
        return schoolMapper.selectSchoolAll();
    }

    @Override
    public boolean checkSchoolNameUnique(School school)
    {
        Long id = StringUtils.isNull(school.getId()) ? -1L : school.getId();
        School info = schoolMapper.checkSchoolNameUnique(school.getName());
        if (StringUtils.isNotNull(info) && info.getId().longValue() != id.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    @Override
    public int insertSchool(School school)
    {
        return schoolMapper.insertSchool(school);
    }

    @Override
    public int updateSchool(School school)
    {
        return schoolMapper.updateSchool(school);
    }

    @Override
    public int deleteSchoolByIds(Long[] ids)
    {
        return schoolMapper.deleteSchoolByIds(ids);
    }

    @Override
    public int deleteSchoolById(Long id)
    {
        return schoolMapper.deleteSchoolById(id);
    }
}
