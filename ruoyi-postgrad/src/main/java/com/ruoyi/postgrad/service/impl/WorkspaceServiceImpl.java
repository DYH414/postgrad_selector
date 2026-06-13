package com.ruoyi.postgrad.service.impl;

import java.time.Year;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.mapper.WorkspaceMapper;
import com.ruoyi.postgrad.service.IWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceServiceImpl implements IWorkspaceService
{
    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Autowired
    private SchoolMapper schoolMapper;

    @Override
    public RowMap selectWorkspaceStats(Map<String, String> params)
    {
        return workspaceMapper.selectWorkspaceStats(
            clean(params.get("keyword")),
            clean(params.get("province")),
            clean(params.get("tier")),
            parseYear(params.get("year")),
            clean(params.get("is408")),
            clean(params.get("completeness"))
        );
    }

    @Override
    public List<RowMap> selectWorkspaceSchools(Map<String, String> params)
    {
        return workspaceMapper.selectWorkspaceSchools(
            clean(params.get("keyword")),
            clean(params.get("province")),
            clean(params.get("tier")),
            parseYear(params.get("year")),
            clean(params.get("is408")),
            clean(params.get("completeness"))
        );
    }

    @Override
    public Map<String, Object> selectSchoolWorkspace(Long schoolId, Map<String, String> params)
    {
        Integer year = parseYear(params.get("year"));
        List<Integer> years = workspaceYears(year);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("school", schoolMapper.selectSchoolById(schoolId));
        result.put("stats", workspaceMapper.selectSchoolWorkspaceStats(schoolId, year));
        result.put("colleges", workspaceMapper.selectSchoolColleges(schoolId));
        result.put("programs", workspaceMapper.selectSchoolPrograms(
            schoolId,
            year,
            clean(params.get("is408")),
            clean(params.get("completeness"))
        ));
        result.put("programYears", workspaceMapper.selectSchoolProgramYears(schoolId, years));
        result.put("years", years);
        return result;
    }

    private static String clean(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static Integer parseYear(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return Year.now().getValue();
        }
        try
        {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException ignored)
        {
            return Year.now().getValue();
        }
    }

    private static List<Integer> workspaceYears(Integer year)
    {
        int current = year == null ? Year.now().getValue() : year;
        return Arrays.asList(current - 2, current - 1, current);
    }
}
