package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.ProgramSearchMapper;
import com.ruoyi.postgrad.service.IProgramSearchService;

@Service
public class ProgramSearchServiceImpl implements IProgramSearchService
{
    private static final int MAX_RESULTS = 50;

    @Autowired
    private ProgramSearchMapper programSearchMapper;

    @Override
    public Map<String, Object> searchPrograms(String keyword, int limit)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", keyword);

        if (keyword == null || keyword.trim().isEmpty())
        {
            result.put("total", 0);
            result.put("items", new ArrayList<>());
            return result;
        }

        int clampedLimit = Math.max(1, Math.min(limit > 0 ? limit : 20, MAX_RESULTS));
        List<RowMap> rows = programSearchMapper.searchByKeyword(keyword.trim(), clampedLimit);

        List<Map<String, Object>> items = new ArrayList<>();
        for (RowMap row : rows)
        {
            items.add(normalizeSearchResult(row));
        }
        result.put("total", items.size());
        result.put("items", items);
        return result;
    }

    private Map<String, Object> normalizeSearchResult(RowMap row)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("programId", row.get("programId"));
        item.put("schoolName", row.get("schoolName"));
        item.put("collegeName", row.get("collegeName"));
        item.put("programName", row.get("programName"));
        item.put("programCode", row.get("programCode"));
        item.put("province", row.get("province"));
        item.put("examCombo", row.get("subjectCodes"));
        item.put("dataYear", row.get("dataYear"));
        item.put("scoreLine", row.get("scoreLine"));
        item.put("avgAdmittedScore", row.get("avgAdmittedScore"));
        item.put("admissionLow", row.get("admissionLow"));
        item.put("admissionHigh", row.get("admissionHigh"));
        item.put("admissionRangeLabel", buildRangeLabel(row));
        item.put("avgScoreGap", null);
        item.put("admissionLowGap", null);
        item.put("scoreLineGap", null);
        item.put("unifiedExamQuota", row.get("unifiedExamQuota"));
        item.put("planCount", row.get("planCount"));
        item.put("dataCompleteness", row.get("dataCompleteness"));
        item.put("sourceUrl", row.get("sourceUrl"));
        item.put("sourceTitle", row.get("sourceTitle"));
        item.put("sourceOwner", row.get("sourceOwner"));
        item.put("is985", row.get("is985"));
        item.put("is211", row.get("is211"));
        item.put("isDoubleFirst", row.get("isDoubleFirst"));
        item.put("schoolTier", row.get("schoolTier"));
        item.put("fitLevel", null);
        item.put("fitLevelLabel", null);
        item.put("warnings", null);
        return item;
    }

    private String buildRangeLabel(RowMap row)
    {
        Object low = row.get("admissionLow");
        Object high = row.get("admissionHigh");
        if (low == null || high == null) return "-";
        return low + "-" + high;
    }
}
