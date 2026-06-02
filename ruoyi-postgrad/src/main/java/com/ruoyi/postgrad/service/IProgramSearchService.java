package com.ruoyi.postgrad.service;

import java.util.Map;

public interface IProgramSearchService
{
    Map<String, Object> searchPrograms(String keyword, int limit);
}
