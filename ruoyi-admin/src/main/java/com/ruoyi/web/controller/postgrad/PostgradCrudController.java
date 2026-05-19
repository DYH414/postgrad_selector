package com.ruoyi.web.controller.postgrad;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考研择校后台通用CRUD。
 *
 * <p>业务表由白名单元数据驱动，避免为每张维护表重复生成大段样板代码。</p>
 */
@RestController
@RequestMapping("/postgrad/{module}")
public class PostgradCrudController extends BaseController
{
    private static final Map<String, ModuleMeta> MODULES = buildModules();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':list')")
    @GetMapping("/list")
    public TableDataInfo list(@PathVariable String module, @RequestParam Map<String, String> params)
    {
        ModuleMeta meta = getMeta(module);
        int pageNum = parsePositiveInt(params.get("pageNum"), 1);
        int pageSize = parsePositiveInt(params.get("pageSize"), 10);
        WhereSql where = buildWhere(meta, params);

        Long total = jdbcTemplate.queryForObject("select count(1) from " + meta.fromSql + where.sql, Long.class, where.args.toArray());
        List<Object> args = new ArrayList<>(where.args);
        args.add(pageSize);
        args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(meta.selectSql + where.sql + " " + meta.orderSql + " limit ? offset ?", args.toArray())
            .stream()
            .map(row -> toCamelRow(meta, row))
            .collect(Collectors.toList());

        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        rspData.setRows(rows);
        rspData.setTotal(total == null ? 0 : total);
        return rspData;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':export')")
    @Log(title = "考研择校数据导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@PathVariable String module, @RequestParam Map<String, String> params, HttpServletResponse response) throws IOException
    {
        ModuleMeta meta = getMeta(module);
        WhereSql where = buildWhere(meta, params);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(meta.selectSql + where.sql + " " + meta.orderSql, where.args.toArray())
            .stream()
            .map(row -> toCamelRow(meta, row))
            .collect(Collectors.toList());

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + module + ".csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println(meta.exportProps.stream().map(meta::labelOf).collect(Collectors.joining(",")));
        for (Map<String, Object> row : rows)
        {
            writer.println(meta.exportProps.stream().map(prop -> csv(row.get(prop))).collect(Collectors.joining(",")));
        }
        writer.flush();
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable String module, @PathVariable String id)
    {
        ModuleMeta meta = getMeta(module);
        KeySql keySql = buildKeyWhere(meta, id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(meta.selectSql + " where " + keySql.sql, keySql.args.toArray());
        if (rows.isEmpty())
        {
            return success(null);
        }
        return success(toCamelRow(meta, rows.get(0)));
    }

    @GetMapping("/optionselect")
    public AjaxResult optionselect(@PathVariable String module, @RequestParam Map<String, String> params)
    {
        ModuleMeta meta = getMeta(module);
        if (!StringUtils.hasText(meta.optionSql))
        {
            return success(Collections.emptyList());
        }
        return success(jdbcTemplate.queryForList(meta.optionSql, optionArgs(meta, params).toArray()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':add')")
    @Log(title = "考研择校数据新增", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@PathVariable String module, @RequestBody Map<String, Object> body)
    {
        ModuleMeta meta = getMeta(module);
        validateRequired(meta, body, true);
        List<ColumnMeta> columns = meta.columns.stream()
            .filter(column -> !column.readonly && body.containsKey(column.prop))
            .collect(Collectors.toList());
        if (columns.isEmpty())
        {
            return error("没有可保存的字段");
        }

        String fields = columns.stream().map(column -> column.column).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        Object[] args = columns.stream().map(column -> normalize(body.get(column.prop))).toArray();
        try
        {
            return toAjax(jdbcTemplate.update("insert into " + meta.tableName + " (" + fields + ") values (" + placeholders + ")", args));
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("新增失败，请检查是否重复或关联数据不存在");
        }
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':edit')")
    @Log(title = "考研择校数据修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@PathVariable String module, @RequestBody Map<String, Object> body)
    {
        ModuleMeta meta = getMeta(module);
        validateRequired(meta, body, false);
        String encodedKey = Objects.toString(body.get("__key"), "");
        if (!StringUtils.hasText(encodedKey))
        {
            encodedKey = meta.keyColumns.stream().map(column -> Objects.toString(body.get(column.prop), "")).collect(Collectors.joining("_"));
        }
        KeySql keySql = buildKeyWhere(meta, encodedKey);
        List<ColumnMeta> columns = meta.columns.stream()
            .filter(column -> !column.readonly && !column.key && body.containsKey(column.prop))
            .collect(Collectors.toList());
        if (columns.isEmpty())
        {
            return error("没有可更新的字段");
        }

        List<Object> args = columns.stream().map(column -> normalize(body.get(column.prop))).collect(Collectors.toList());
        args.addAll(keySql.args);
        String sets = columns.stream().map(column -> column.column + " = ?").collect(Collectors.joining(", "));
        try
        {
            return toAjax(jdbcTemplate.update("update " + meta.tableName + " set " + sets + " where " + keySql.sql, args.toArray()));
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("修改失败，请检查是否重复或关联数据不存在");
        }
    }

    @PreAuthorize("@ss.hasPermi('postgrad:' + #module + ':remove')")
    @Log(title = "考研择校数据删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable String module, @PathVariable String ids)
    {
        ModuleMeta meta = getMeta(module);
        int count = 0;
        try
        {
            for (String id : ids.split(","))
            {
                KeySql keySql = buildKeyWhere(meta, id);
                count += jdbcTemplate.update("delete from " + meta.tableName + " where " + keySql.sql, keySql.args.toArray());
            }
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("删除失败，该数据已被其他业务数据引用");
        }
        return toAjax(count);
    }

    private static Map<String, ModuleMeta> buildModules()
    {
        Map<String, ModuleMeta> modules = new LinkedHashMap<>();

        modules.put("college", ModuleMeta.of("college", "college c left join school s on s.id = c.school_id",
            "select c.id, c.school_id, s.name school_name, c.name, c.website, c.graduate_url, c.created_at, c.updated_at from college c left join school s on s.id = c.school_id",
            "order by s.name, c.name", option("college", "c.id", "concat(s.name, ' / ', c.name)", "college c left join school s on s.id = c.school_id", "s.name, c.name"))
            .keyAlias("c").key("id", "id", "ID").col("schoolId", "school_id", "学校", true).view("schoolName", "学校名称")
            .col("name", "name", "学院名称", true).col("website", "website", "学院官网").col("graduateUrl", "graduate_url", "研招信息页")
            .time("createdAt", "创建时间").time("updatedAt", "更新时间").searchLike("name", "c.name").searchExact("schoolId", "c.school_id"));

        modules.put("subject", ModuleMeta.of("subject", "subject",
            "select id, code, name, subject_type, exam_category, created_at from subject",
            "order by code", option("subject", "id", "concat(code, ' ', name)", "subject", "code"))
            .key("id", "id", "ID").col("code", "code", "科目代码", true).col("name", "name", "科目名称", true)
            .col("subjectType", "subject_type", "科目类型", true).col("examCategory", "exam_category", "考试类别")
            .time("createdAt", "创建时间").searchLike("code", "code").searchLike("name", "name").searchExact("subjectType", "subject_type"));

        modules.put("program", ModuleMeta.of("program", "program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "select p.id, p.college_id, c.name college_name, s.name school_name, p.program_code, p.program_name, p.research_direction, p.discipline_category, p.first_discipline, p.study_mode, p.degree_type, p.exam_type, p.score_scale, p.retest_subjects, p.is_408, p.protects_first_choice, p.is_joint_program, p.status, p.created_at, p.updated_at from program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "order by s.name, c.name, p.program_code", option("program", "p.id", "concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name)", "program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id", "s.name, c.name, p.program_code"))
            .keyAlias("p").key("id", "id", "ID").col("collegeId", "college_id", "学院", true).view("schoolName", "学校").view("collegeName", "学院")
            .col("programCode", "program_code", "专业代码", true).col("programName", "program_name", "专业名称", true)
            .col("researchDirection", "research_direction", "研究方向").col("disciplineCategory", "discipline_category", "学科门类").col("firstDiscipline", "first_discipline", "一级学科")
            .col("studyMode", "study_mode", "学习方式", true).col("degreeType", "degree_type", "学位类型", true).col("examType", "exam_type", "考试类型", true)
            .col("scoreScale", "score_scale", "满分", true).col("retestSubjects", "retest_subjects", "复试科目").col("is408", "is_408", "408").col("protectsFirstChoice", "protects_first_choice", "保护一志愿")
            .col("isJointProgram", "is_joint_program", "联培").col("status", "status", "状态", true).time("createdAt", "创建时间").time("updatedAt", "更新时间")
            .searchLike("programCode", "p.program_code").searchLike("programName", "p.program_name").searchExact("collegeId", "p.college_id").searchExact("is408", "p.is_408").searchExact("status", "p.status"));

        modules.put("programSubject", ModuleMeta.of("program_subject", "program_subject ps left join program p on p.id = ps.program_id left join subject sub on sub.id = ps.subject_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "select ps.program_id, ps.subject_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, concat(sub.code, ' ', sub.name) subject_label, ps.subject_order from program_subject ps left join program p on p.id = ps.program_id left join subject sub on sub.id = ps.subject_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "order by s.name, c.name, p.program_code, ps.subject_order", null)
            .keyAlias("ps").compositeKey("programId", "program_id", "专业", "subjectId", "subject_id", "科目").view("programLabel", "专业方向").view("subjectLabel", "考试科目")
            .col("subjectOrder", "subject_order", "科目顺序", true).searchExact("programId", "ps.program_id").searchExact("subjectId", "ps.subject_id"));

        modules.put("dataSource", ModuleMeta.of("data_source", "data_source",
            "select id, source_type, url, title, source_owner, publish_date, local_file_path, file_hash, page_hash, fetched_at, robots_checked, robots_allowed, terms_checked, commercial_use_risk, copyright_risk, created_at, updated_at from data_source",
            "order by created_at desc", option("dataSource", "id", "coalesce(title, url, concat('来源#', id))", "data_source", "created_at desc"))
            .key("id", "id", "ID").col("sourceType", "source_type", "来源类型", true).col("url", "url", "来源URL").col("title", "title", "来源标题").col("sourceOwner", "source_owner", "来源主体")
            .col("publishDate", "publish_date", "发布日期").col("localFilePath", "local_file_path", "本地文件").col("fileHash", "file_hash", "文件Hash").col("pageHash", "page_hash", "页面Hash")
            .col("fetchedAt", "fetched_at", "抓取时间").col("robotsChecked", "robots_checked", "已查robots").col("robotsAllowed", "robots_allowed", "robots允许").col("termsChecked", "terms_checked", "已查条款")
            .col("commercialUseRisk", "commercial_use_risk", "商用风险").col("copyrightRisk", "copyright_risk", "版权风险").time("createdAt", "创建时间").time("updatedAt", "更新时间")
            .searchExact("sourceType", "source_type").searchLike("title", "title").searchLike("sourceOwner", "source_owner"));

        addAdmission(modules, "admissionScore", "admission_score", "历年复试线", "score_line, single_math, single_english, single_politics, single_professional");
        addAdmission(modules, "admissionPlan", "admission_plan", "历年招生计划", "total_plan, recommended_exemption_plan, unified_exam_quota, retest_count");
        addAdmission(modules, "admissionResult", "admission_result", "历年拟录取", "admitted_count, first_choice_admitted_count, min_admitted_score, avg_admitted_score, max_admitted_score, has_transfer, transfer_count");

        modules.put("programYearDataQuality", ModuleMeta.of("program_year_data_quality", "program_year_data_quality q left join program p on p.id = q.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "select q.id, q.program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, q.year, q.has_score, q.has_plan, q.has_result, q.has_official_source, q.has_conflict, q.completeness_level, q.missing_fields, q.last_checked_at, q.created_at, q.updated_at from program_year_data_quality q left join program p on p.id = q.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "order by q.year desc, q.completeness_level, q.id desc", null)
            .keyAlias("q").key("id", "id", "ID").col("programId", "program_id", "专业", true).view("programLabel", "专业方向").col("year", "year", "年份", true)
            .col("hasScore", "has_score", "复试线").col("hasPlan", "has_plan", "招生计划").col("hasResult", "has_result", "录取结果").col("hasOfficialSource", "has_official_source", "官网来源").col("hasConflict", "has_conflict", "存在冲突")
            .col("completenessLevel", "completeness_level", "完整度", true).col("missingFields", "missing_fields", "缺失字段").col("lastCheckedAt", "last_checked_at", "检查时间").time("createdAt", "创建时间").time("updatedAt", "更新时间")
            .searchExact("programId", "q.program_id").searchExact("year", "q.year").searchExact("completenessLevel", "q.completeness_level"));

        modules.put("dataCollectionTask", ModuleMeta.of("data_collection_task", "data_collection_task t left join program p on p.id = t.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "select t.id, t.program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, t.task_type, t.target_year, t.priority, t.status, t.source_hint_url, t.failure_reason, t.created_by, t.assigned_to, t.started_at, t.finished_at, t.created_at, t.updated_at from data_collection_task t left join program p on p.id = t.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "order by t.status, t.priority desc, t.created_at desc", null)
            .keyAlias("t").key("id", "id", "ID").col("programId", "program_id", "专业", true).view("programLabel", "专业方向").col("taskType", "task_type", "任务类型", true).col("targetYear", "target_year", "目标年份", true)
            .col("priority", "priority", "优先级").col("status", "status", "状态", true).col("sourceHintUrl", "source_hint_url", "来源提示").col("failureReason", "failure_reason", "失败原因")
            .col("createdBy", "created_by", "创建来源").col("assignedTo", "assigned_to", "处理人").col("startedAt", "started_at", "开始时间").col("finishedAt", "finished_at", "完成时间").time("createdAt", "创建时间").time("updatedAt", "更新时间")
            .searchExact("programId", "t.program_id").searchExact("targetYear", "t.target_year").searchExact("taskType", "t.task_type").searchExact("status", "t.status"));

        modules.put("staging", ModuleMeta.of("staging", "staging st left join data_collection_task t on t.id = st.task_id left join data_source ds on ds.id = st.source_id left join program p on p.id = st.matched_program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "select st.id, st.task_id, st.source_id, st.matched_program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) matched_program_label, st.source_type, st.school_name, st.college_name, st.city, st.program_code, st.program_name, st.exam_subjects, st.year, st.score_line, st.single_math, st.single_english, st.single_politics, st.single_professional, st.min_admitted, st.avg_admitted, st.plan_count, st.retest_count, st.admitted_count, st.confidence, st.source_url, st.raw_text, st.extract_json, st.status, st.error_message, st.reviewer_id, st.reviewed_at, st.review_note, st.created_at, st.updated_at from staging st left join data_collection_task t on t.id = st.task_id left join data_source ds on ds.id = st.source_id left join program p on p.id = st.matched_program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
            "order by st.created_at desc", null)
            .keyAlias("st").key("id", "id", "ID").col("taskId", "task_id", "任务").col("sourceId", "source_id", "来源").col("matchedProgramId", "matched_program_id", "匹配专业").view("matchedProgramLabel", "匹配专业方向")
            .col("sourceType", "source_type", "采集来源", true).col("schoolName", "school_name", "学校名称", true).col("collegeName", "college_name", "学院名称").col("city", "city", "城市")
            .col("programCode", "program_code", "专业代码").col("programName", "program_name", "专业名称").col("examSubjects", "exam_subjects", "初试科目").col("year", "year", "年份")
            .col("scoreLine", "score_line", "复试线").col("singleMath", "single_math", "数学线").col("singleEnglish", "single_english", "英语线").col("singlePolitics", "single_politics", "政治线").col("singleProfessional", "single_professional", "专业课线")
            .col("minAdmitted", "min_admitted", "最低录取").col("avgAdmitted", "avg_admitted", "平均录取").col("planCount", "plan_count", "计划数").col("retestCount", "retest_count", "复试数").col("admittedCount", "admitted_count", "录取数")
            .col("confidence", "confidence", "置信度").col("sourceUrl", "source_url", "来源URL").col("rawText", "raw_text", "原始文本").col("extractJson", "extract_json", "抽取JSON")
            .col("status", "status", "审核状态", true).col("errorMessage", "error_message", "错误信息").col("reviewerId", "reviewer_id", "审核人").col("reviewedAt", "reviewed_at", "审核时间").col("reviewNote", "review_note", "审核备注")
            .time("createdAt", "创建时间").time("updatedAt", "更新时间").searchLike("schoolName", "st.school_name").searchLike("programCode", "st.program_code").searchExact("year", "st.year").searchExact("status", "st.status").searchExact("confidence", "st.confidence"));

        return modules;
    }

    private static void addAdmission(Map<String, ModuleMeta> modules, String module, String table, String title, String metricColumns)
    {
        String alias = table.equals("admission_score") ? "a" : table.equals("admission_plan") ? "ap" : "ar";
        ModuleMeta meta = ModuleMeta.of(table, table + " " + alias + " left join program p on p.id = " + alias + ".program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id left join data_source ds on ds.id = " + alias + ".source_id",
            "select " + alias + ".id, " + alias + ".program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, " + alias + ".year, " + prefix(alias, metricColumns) + ", " + alias + ".verify_status, " + alias + ".source_id, coalesce(ds.title, ds.url) source_label, " + alias + ".created_at, " + alias + ".updated_at from " + table + " " + alias + " left join program p on p.id = " + alias + ".program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id left join data_source ds on ds.id = " + alias + ".source_id",
            "order by " + alias + ".year desc, " + alias + ".id desc", null)
            .keyAlias(alias).key("id", "id", "ID").col("programId", "program_id", "专业", true).view("programLabel", "专业方向").col("year", "year", "年份", true);
        for (String column : metricColumns.split(", "))
        {
            meta.col(camel(column), column, admissionLabel(column));
        }
        meta.col("verifyStatus", "verify_status", "可信状态", true).col("sourceId", "source_id", "来源").view("sourceLabel", "来源标题")
            .time("createdAt", "创建时间").time("updatedAt", "更新时间").searchExact("programId", alias + ".program_id").searchExact("year", alias + ".year").searchExact("verifyStatus", alias + ".verify_status");
        modules.put(module, meta);
    }

    private static String option(String module, String id, String label, String from, String order)
    {
        return "select " + id + " id, " + label + " label from " + from + " order by " + order;
    }

    private static String prefix(String alias, String columns)
    {
        return Arrays.stream(columns.split(", ")).map(column -> alias + "." + column).collect(Collectors.joining(", "));
    }

    private static ModuleMeta getMeta(String module)
    {
        ModuleMeta meta = MODULES.get(module);
        if (meta == null)
        {
            throw new ServiceException("不支持的业务模块：" + module);
        }
        return meta;
    }

    private WhereSql buildWhere(ModuleMeta meta, Map<String, String> params)
    {
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (SearchMeta search : meta.searches)
        {
            String value = params.get(search.prop);
            if (!StringUtils.hasText(value))
            {
                continue;
            }
            if (search.like)
            {
                parts.add(search.expression + " like ?");
                args.add("%" + value + "%");
            }
            else
            {
                parts.add(search.expression + " = ?");
                args.add(value);
            }
        }
        return new WhereSql(parts.isEmpty() ? "" : " where " + String.join(" and ", parts), args);
    }

    private KeySql buildKeyWhere(ModuleMeta meta, String encodedKey)
    {
        String[] values = encodedKey.split("_", -1);
        if (values.length != meta.keyColumns.size())
        {
            throw new ServiceException("数据主键无效");
        }
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < meta.keyColumns.size(); i++)
        {
            ColumnMeta column = meta.keyColumns.get(i);
            parts.add(meta.keyAlias + "." + column.column + " = ?");
            args.add(values[i]);
        }
        return new KeySql(String.join(" and ", parts), args);
    }

    private Map<String, Object> toCamelRow(ModuleMeta meta, Map<String, Object> row)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            result.put(camel(entry.getKey()), normalizeRead(entry.getValue()));
        }
        result.put("__key", meta.keyColumns.stream().map(column -> Objects.toString(result.get(column.prop), "")).collect(Collectors.joining("_")));
        return result;
    }

    private List<Object> optionArgs(ModuleMeta meta, Map<String, String> params)
    {
        return Collections.emptyList();
    }

    private void validateRequired(ModuleMeta meta, Map<String, Object> body, boolean insert)
    {
        for (ColumnMeta column : meta.columns)
        {
            if (column.required && !column.readonly && (insert || !column.key))
            {
                Object value = body.get(column.prop);
                if (value == null || !StringUtils.hasText(Objects.toString(value, "")))
                {
                    throw new ServiceException(column.label + "不能为空");
                }
            }
        }
    }

    private static int parsePositiveInt(String value, int defaultValue)
    {
        try
        {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }

    private static Object normalize(Object value)
    {
        if (value instanceof String && !StringUtils.hasText((String) value))
        {
            return null;
        }
        return value;
    }

    private static Object normalizeRead(Object value)
    {
        if (value instanceof Timestamp)
        {
            return value.toString();
        }
        return value;
    }

    private static String csv(Object value)
    {
        String text = value == null ? "" : Objects.toString(value, "");
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    private static String camel(String value)
    {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : value.toCharArray())
        {
            if (ch == '_')
            {
                upper = true;
            }
            else if (upper)
            {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            }
            else
            {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String admissionLabel(String column)
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("score_line", "复试线");
        labels.put("single_math", "数学线");
        labels.put("single_english", "英语线");
        labels.put("single_politics", "政治线");
        labels.put("single_professional", "专业课线");
        labels.put("total_plan", "总计划");
        labels.put("recommended_exemption_plan", "推免计划");
        labels.put("unified_exam_quota", "统考名额");
        labels.put("retest_count", "复试人数");
        labels.put("admitted_count", "录取人数");
        labels.put("first_choice_admitted_count", "一志愿录取");
        labels.put("min_admitted_score", "最低分");
        labels.put("avg_admitted_score", "平均分");
        labels.put("max_admitted_score", "最高分");
        labels.put("has_transfer", "有调剂");
        labels.put("transfer_count", "调剂人数");
        return labels.getOrDefault(column, column);
    }

    private static class ModuleMeta
    {
        private final String tableName;
        private String keyAlias;
        private final String fromSql;
        private final String selectSql;
        private final String orderSql;
        private final String optionSql;
        private final List<ColumnMeta> columns = new ArrayList<>();
        private final List<ColumnMeta> keyColumns = new ArrayList<>();
        private final List<SearchMeta> searches = new ArrayList<>();
        private final List<String> exportProps = new ArrayList<>();

        private ModuleMeta(String tableName, String fromSql, String selectSql, String orderSql, String optionSql)
        {
            this.tableName = tableName;
            this.keyAlias = tableName;
            this.fromSql = fromSql;
            this.selectSql = selectSql;
            this.orderSql = orderSql;
            this.optionSql = optionSql;
        }

        private static ModuleMeta of(String tableName, String fromSql, String selectSql, String orderSql, String optionSql)
        {
            return new ModuleMeta(tableName, fromSql, selectSql, orderSql, optionSql);
        }

        private ModuleMeta keyAlias(String keyAlias)
        {
            this.keyAlias = keyAlias;
            return this;
        }

        private ModuleMeta key(String prop, String column, String label)
        {
            ColumnMeta key = new ColumnMeta(prop, column, label, false, true, false);
            columns.add(key);
            keyColumns.add(key);
            exportProps.add(prop);
            return this;
        }

        private ModuleMeta compositeKey(String firstProp, String firstColumn, String firstLabel, String secondProp, String secondColumn, String secondLabel)
        {
            assignedKey(firstProp, firstColumn, firstLabel);
            assignedKey(secondProp, secondColumn, secondLabel);
            return this;
        }

        private ModuleMeta assignedKey(String prop, String column, String label)
        {
            ColumnMeta key = new ColumnMeta(prop, column, label, true, true, false);
            columns.add(key);
            keyColumns.add(key);
            exportProps.add(prop);
            return this;
        }

        private ModuleMeta col(String prop, String column, String label)
        {
            return col(prop, column, label, false);
        }

        private ModuleMeta col(String prop, String column, String label, boolean required)
        {
            columns.add(new ColumnMeta(prop, column, label, required, false, false));
            exportProps.add(prop);
            return this;
        }

        private ModuleMeta view(String prop, String label)
        {
            columns.add(new ColumnMeta(prop, prop, label, false, false, true));
            exportProps.add(prop);
            return this;
        }

        private ModuleMeta time(String prop, String label)
        {
            columns.add(new ColumnMeta(prop, prop, label, false, false, true));
            exportProps.add(prop);
            return this;
        }

        private ModuleMeta searchLike(String prop, String expression)
        {
            searches.add(new SearchMeta(prop, expression, true));
            return this;
        }

        private ModuleMeta searchExact(String prop, String expression)
        {
            searches.add(new SearchMeta(prop, expression, false));
            return this;
        }

        private String labelOf(String prop)
        {
            return columns.stream().filter(column -> column.prop.equals(prop)).findFirst().map(column -> column.label).orElse(prop);
        }
    }

    private static class ColumnMeta
    {
        private final String prop;
        private final String column;
        private final String label;
        private final boolean required;
        private final boolean key;
        private final boolean readonly;

        private ColumnMeta(String prop, String column, String label, boolean required, boolean key, boolean readonly)
        {
            this.prop = prop;
            this.column = column;
            this.label = label;
            this.required = required;
            this.key = key;
            this.readonly = readonly;
        }
    }

    private static class SearchMeta
    {
        private final String prop;
        private final String expression;
        private final boolean like;

        private SearchMeta(String prop, String expression, boolean like)
        {
            this.prop = prop;
            this.expression = expression;
            this.like = like;
        }
    }

    private static class WhereSql
    {
        protected final String sql;
        protected final List<Object> args;

        private WhereSql(String sql, List<Object> args)
        {
            this.sql = sql;
            this.args = args;
        }
    }

    private static class KeySql extends WhereSql
    {
        private KeySql(String sql, List<Object> args)
        {
            super(sql, args);
        }
    }
}
