package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.postgrad.domain.RowMap;

public interface IWorkspaceService
{
    RowMap selectWorkspaceStats(Map<String, String> params);

    List<RowMap> selectWorkspaceSchools(Map<String, String> params);

    Map<String, Object> selectSchoolWorkspace(Long schoolId, Map<String, String> params);
}
