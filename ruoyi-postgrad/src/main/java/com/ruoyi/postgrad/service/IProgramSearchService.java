package com.ruoyi.postgrad.service;

import java.util.List;
import java.util.Map;

public interface IProgramSearchService
{
    List<Map<String, Object>> searchPrograms(String keyword, int pageSize);
}
