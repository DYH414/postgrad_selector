package com.ruoyi.postgrad.service.impl;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.service.IReviewService;

/**
 * 数据审核中心实现 —— staging 审核 + 迁移到正式表。
 */
@Service
public class ReviewServiceImpl implements IReviewService
{
    @Autowired
    private JdbcTemplate jdbc;

    // ═══ List ═══

    @Override
    public Map<String, Object> list(Map<String, String> params, int pageNum, int pageSize)
    {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (has(params, "schoolName"))
        {
            where.append(" AND st.school_name LIKE ?");
            args.add("%" + params.get("schoolName") + "%");
        }
        if (has(params, "programCode"))
        {
            where.append(" AND st.program_code = ?");
            args.add(params.get("programCode"));
        }
        if (has(params, "year"))
        {
            where.append(" AND st.year = ?");
            args.add(Integer.parseInt(params.get("year")));
        }
        if (has(params, "status"))
        {
            where.append(" AND st.status = ?");
            args.add(params.get("status"));
        }
        if (has(params, "confidence"))
        {
            where.append(" AND st.confidence = ?");
            args.add(params.get("confidence"));
        }
        if (has(params, "sourceType"))
        {
            where.append(" AND st.source_type = ?");
            args.add(params.get("sourceType"));
        }

        String countSql = "SELECT COUNT(1) FROM staging st" + where;
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());

        String sql = """
            SELECT st.id, st.source_type, st.school_name, st.college_name, st.program_code,
                   st.program_name, st.year, st.score_line, st.single_politics, st.single_english,
                   st.single_math, st.single_professional, st.confidence, st.status,
                   st.source_url, st.created_at, st.updated_at, st.reviewed_at, st.review_note,
                   st.matched_program_id,
                   CONCAT(COALESCE(s.name,''), ' / ', COALESCE(c.name,''), ' / ',
                          COALESCE(p.program_code,''), ' ', COALESCE(p.program_name,'')) matched_program_label
            FROM staging st
            LEFT JOIN program p ON p.id = st.matched_program_id
            LEFT JOIN college c ON c.id = p.college_id
            LEFT JOIN school s ON s.id = c.school_id
            """ + where + " ORDER BY st.created_at DESC LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total == null ? 0L : total);
        return result;
    }

    // ═══ Detail ═══

    @Override
    public Map<String, Object> detail(Long id)
    {
        String sql = """
            SELECT st.*, CONCAT(COALESCE(s.name,''), ' / ', COALESCE(c.name,''), ' / ',
                   COALESCE(p.program_code,''), ' ', COALESCE(p.program_name,'')) matched_program_label
            FROM staging st
            LEFT JOIN program p ON p.id = st.matched_program_id
            LEFT JOIN college c ON c.id = p.college_id
            LEFT JOIN school s ON s.id = c.school_id
            WHERE st.id = ?
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    // ═══ Approve ═══

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
        updateStatus(id, "approved", note);
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

        // Migrate to admission_score (if score data)
        if (scoreLine != null)
        {
            String sql = """
                INSERT INTO admission_score (program_id, year, score_line,
                    single_politics, single_english, single_math, single_professional,
                    verify_status, source_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'MANUAL_VERIFIED', ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    score_line = VALUES(score_line),
                    single_politics = COALESCE(VALUES(single_politics), single_politics),
                    single_english = COALESCE(VALUES(single_english), single_english),
                    single_math = COALESCE(VALUES(single_math), single_math),
                    single_professional = COALESCE(VALUES(single_professional), single_professional),
                    verify_status = 'MANUAL_VERIFIED',
                    source_id = COALESCE(VALUES(source_id), source_id),
                    updated_at = NOW()
                """;
            jdbc.update(sql, programId, ((Number) year).intValue(), ((Number) scoreLine).intValue(),
                staging.get("single_politics"), staging.get("single_english"),
                staging.get("single_math"), staging.get("single_professional"),
                sourceId);
        }

        // Migrate plan_count to admission_plan
        Object planCount = staging.get("plan_count");
        if (planCount != null)
        {
            String sql = """
                INSERT INTO admission_plan (program_id, year, total_plan, unified_exam_quota,
                    retest_count, verify_status, source_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'MANUAL_VERIFIED', ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    total_plan = VALUES(total_plan),
                    unified_exam_quota = COALESCE(VALUES(unified_exam_quota), unified_exam_quota),
                    retest_count = COALESCE(VALUES(retest_count), retest_count),
                    verify_status = 'MANUAL_VERIFIED',
                    source_id = COALESCE(VALUES(source_id), source_id),
                    updated_at = NOW()
                """;
            jdbc.update(sql, programId, ((Number) year).intValue(),
                ((Number) planCount).intValue(),
                planCount, // default unified_exam_quota = total_plan
                staging.get("retest_count"),
                sourceId);
        }

        // Migrate admitted data to admission_result
        Object admittedCount = staging.get("admitted_count");
        Object minAdmitted = staging.get("min_admitted");
        if (admittedCount != null || minAdmitted != null)
        {
            String sql = """
                INSERT INTO admission_result (program_id, year, admitted_count,
                    min_admitted_score, avg_admitted_score, verify_status, source_id,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'MANUAL_VERIFIED', ?, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    admitted_count = COALESCE(VALUES(admitted_count), admitted_count),
                    min_admitted_score = COALESCE(VALUES(min_admitted_score), min_admitted_score),
                    avg_admitted_score = COALESCE(VALUES(avg_admitted_score), avg_admitted_score),
                    verify_status = 'MANUAL_VERIFIED',
                    source_id = COALESCE(VALUES(source_id), source_id),
                    updated_at = NOW()
                """;
            jdbc.update(sql, programId, ((Number) year).intValue(),
                admittedCount, minAdmitted, staging.get("avg_admitted"), sourceId);
        }
    }

    // ═══ Reject / Skip ═══

    @Override
    public void reject(Long id, String note)
    {
        updateStatus(id, "rejected", note);
    }

    @Override
    public void skip(Long id)
    {
        updateStatus(id, "skipped", "管理员跳过");
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

    // ═══ Stats ═══

    @Override
    public Map<String, Object> stats()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", countByStatus("pending"));
        result.put("approved", countByStatus("approved"));
        result.put("rejected", countByStatus("rejected"));
        result.put("skipped", countByStatus("skipped"));
        result.put("total", countByStatus(null));
        return result;
    }

    // ═══ Helpers ═══

    private void updateStatus(Long id, String status, String note)
    {
        jdbc.update("UPDATE staging SET status=?, review_note=?, reviewer_id=1, reviewed_at=NOW() WHERE id=?",
            status, note, id);
    }

    private long countByStatus(String status)
    {
        if (status == null)
            return jdbc.queryForObject("SELECT COUNT(1) FROM staging", Long.class);
        return jdbc.queryForObject("SELECT COUNT(1) FROM staging WHERE status=?", Long.class, status);
    }

    private boolean has(Map<String, String> params, String key)
    {
        String v = params.get(key);
        return v != null && !v.isEmpty();
    }
}
