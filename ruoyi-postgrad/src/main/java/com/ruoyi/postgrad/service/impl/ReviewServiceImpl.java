package com.ruoyi.postgrad.service.impl;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.Staging;
import com.ruoyi.postgrad.mapper.StagingMapper;
import com.ruoyi.postgrad.service.IReviewService;

@Service
public class ReviewServiceImpl implements IReviewService
{
    @Autowired
    private StagingMapper stagingMapper;

    @Override
    public Map<String, Object> list(Map<String, String> params, int pageNum, int pageSize)
    {
        Staging staging = toStaging(params);

        Long total = stagingMapper.selectStagingCount(staging);
        int offset = (pageNum - 1) * pageSize;
        List<RowMap> rows = stagingMapper.selectStagingList(staging);

        // Manual paging since XML doesn't support LIMIT/OFFSET in this context
        int fromIndex = Math.min(offset, rows.size());
        int toIndex = Math.min(offset + pageSize, rows.size());
        List<RowMap> paged = rows.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", paged);
        result.put("total", total == null ? 0L : total);
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id)
    {
        Map<String, Object> row = stagingMapper.selectStagingById(id);
        return row == null ? Collections.emptyMap() : row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String note)
    {
        Map<String, Object> staging = detail(id);
        if (staging.isEmpty())
        {
            throw new ServiceException("记录不存在");
        }
        String status = (String) staging.get("status");
        if ("approved".equals(status))
        {
            throw new ServiceException("已通过，无需重复审核");
        }

        migrate(staging);
        stagingMapper.updateStagingStatus(id, "approved", note, 1L);
    }

    private void migrate(Map<String, Object> staging)
    {
        Object programId = staging.get("matched_program_id");
        Object year = staging.get("year");
        Object scoreLine = staging.get("score_line");
        Object sourceId = staging.get("source_id");

        if (programId == null || year == null)
        {
            throw new ServiceException("缺少专业或年份，无法迁移");
        }

        Long pid = ((Number) programId).longValue();
        int y = ((Number) year).intValue();
        Long srcId = sourceId instanceof Number ? ((Number) sourceId).longValue() : null;

        if (scoreLine instanceof Number)
        {
            stagingMapper.migrateAdmissionScore(pid, y, ((Number) scoreLine).intValue(),
                intOrNull(staging.get("single_politics")),
                intOrNull(staging.get("single_english")),
                intOrNull(staging.get("single_math")),
                intOrNull(staging.get("single_professional")),
                srcId);
        }

        Object planCount = staging.get("plan_count");
        if (planCount instanceof Number)
        {
            stagingMapper.migrateAdmissionPlan(pid, y, ((Number) planCount).intValue(),
                intOrNull(staging.get("retest_count")), srcId);
        }

        Object admittedCount = staging.get("admitted_count");
        Object minAdmitted = staging.get("min_admitted");
        Object maxAdmitted = staging.get("max_admitted");
        if (admittedCount instanceof Number || minAdmitted instanceof Number || maxAdmitted instanceof Number)
        {
            stagingMapper.migrateAdmissionResult(pid, y,
                intOrNull(admittedCount),
                intOrNull(minAdmitted),
                decimalOrNull(staging.get("avg_admitted")),
                intOrNull(maxAdmitted),
                srcId);
        }
    }

    @Override
    public void reject(Long id, String note)
    {
        stagingMapper.updateStagingStatus(id, "rejected", note, 1L);
    }

    @Override
    public void skip(Long id)
    {
        stagingMapper.updateStagingStatus(id, "skipped", "管理员跳过", 1L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchApprove(List<Integer> ids)
    {
        for (Integer id : ids)
        {
            approve(id.longValue(), "批量通过");
        }
    }

    @Override
    public Map<String, Object> stats()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", stagingMapper.countByStatus("pending"));
        result.put("approved", stagingMapper.countByStatus("approved"));
        result.put("rejected", stagingMapper.countByStatus("rejected"));
        result.put("skipped", stagingMapper.countByStatus("skipped"));
        result.put("total", stagingMapper.countByStatus(null));
        return result;
    }

    @Override
    public int autoApproveDirectory()
    {
        return stagingMapper.autoApproveDirectory("学校/专业目录数据自动通过", 1L);
    }

    private Staging toStaging(Map<String, String> params)
    {
        Staging staging = new Staging();
        if (has(params, "schoolName")) staging.setSchoolName(params.get("schoolName"));
        if (has(params, "programCode")) staging.setProgramCode(params.get("programCode"));
        if (has(params, "year")) staging.setYear(Integer.parseInt(params.get("year")));
        if (has(params, "status")) staging.setStatus(params.get("status"));
        if (has(params, "confidence")) staging.setConfidence(params.get("confidence"));
        if (has(params, "sourceType")) staging.setSourceType(params.get("sourceType"));
        if (has(params, "matchStatus")) staging.getParams().put("matchStatus", params.get("matchStatus"));
        if (has(params, "is408")) staging.getParams().put("is408", true);
        return staging;
    }

    private boolean has(Map<String, String> params, String key)
    {
        String v = params.get(key);
        return v != null && !v.isEmpty();
    }

    private Integer intOrNull(Object val)
    {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private java.math.BigDecimal decimalOrNull(Object val)
    {
        if (val == null) return null;
        if (val instanceof java.math.BigDecimal) return (java.math.BigDecimal) val;
        if (val instanceof Number) return java.math.BigDecimal.valueOf(((Number) val).doubleValue());
        try { return new java.math.BigDecimal(String.valueOf(val)); } catch (Exception e) { return null; }
    }
}
