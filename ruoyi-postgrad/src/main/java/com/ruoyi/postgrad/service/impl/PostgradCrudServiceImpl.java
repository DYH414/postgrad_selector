package com.ruoyi.postgrad.service.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.domain.ColumnMeta;
import com.ruoyi.postgrad.domain.KeySql;
import com.ruoyi.postgrad.domain.ModuleMeta;
import com.ruoyi.postgrad.domain.SearchMeta;
import com.ruoyi.postgrad.domain.WhereSql;
import com.ruoyi.postgrad.mapper.PostgradCrudMapper;
import com.ruoyi.postgrad.service.IPostgradCrudService;

/**
 * 通用CRUD Service实现 —— 元数据驱动的动态业务模块。
 * <p>将原来 Controller 中的 ModuleMeta/JdbcTemplate/SQL拼接/校验逻辑全部下沉到此处。</p>
 *
 * @author ruoyi
 */
@Service
public class PostgradCrudServiceImpl implements IPostgradCrudService
{
    private static final Map<String, ModuleMeta> MODULES = buildModules();

    @Autowired
    private PostgradCrudMapper mapper;

    // ═══════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public TableDataInfo list(String module, Map<String, String> params)
    {
        ModuleMeta meta = getMeta(module);
        int pageNum = parsePositiveInt(params.get("pageNum"), 1);
        int pageSize = parsePositiveInt(params.get("pageSize"), 10);
        WhereSql where = buildWhere(meta, params);

        Long total = mapper.selectCount(meta, where.getSql(), where.getArgs());
        List<Map<String, Object>> rows = mapper.selectList(meta, where.getSql(), where.getArgs(), pageSize,
                (pageNum - 1) * pageSize)
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

    @Override
    public List<Map<String, Object>> export(String module, Map<String, String> params)
    {
        ModuleMeta meta = getMeta(module);
        WhereSql where = buildWhere(meta, params);
        return mapper.selectExport(meta, where.getSql(), where.getArgs())
                .stream()
                .map(row -> toCamelRow(meta, row))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getInfo(String module, String id)
    {
        ModuleMeta meta = getMeta(module);
        KeySql keySql = buildKeyWhere(meta, id);
        List<Map<String, Object>> rows = mapper.selectById(meta, keySql.getSql(), keySql.getArgs());
        if (rows.isEmpty())
        {
            return null;
        }
        return toCamelRow(meta, rows.get(0));
    }

    @Override
    public List<Map<String, Object>> optionselect(String module, Map<String, String> params)
    {
        ModuleMeta meta = getMeta(module);
        if (!StringUtils.hasText(meta.getOptionSql()))
        {
            return Collections.emptyList();
        }
        return optionRows(module, meta, params);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int add(String module, Map<String, Object> body)
    {
        ModuleMeta meta = getMeta(module);
        validateRequired(meta, body, true);
        List<ColumnMeta> columns = meta.getColumns().stream()
                .filter(c -> !c.isReadonly() && body.containsKey(c.getProp()))
                .collect(Collectors.toList());
        if (columns.isEmpty())
        {
            throw new ServiceException("没有可保存的字段");
        }

        List<String> colNames = columns.stream().map(ColumnMeta::getColumn).collect(Collectors.toList());
        List<Object> values = columns.stream().map(c -> normalize(body.get(c.getProp()))).collect(Collectors.toList());
        try
        {
            return mapper.insert(meta, colNames, values);
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("新增失败，请检查是否重复或关联数据不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int edit(String module, Map<String, Object> body)
    {
        ModuleMeta meta = getMeta(module);
        validateRequired(meta, body, false);
        String encodedKey = Objects.toString(body.get("__key"), "");
        if (!StringUtils.hasText(encodedKey))
        {
            encodedKey = meta.getKeyColumns().stream()
                    .map(c -> Objects.toString(body.get(c.getProp()), ""))
                    .collect(Collectors.joining("_"));
        }
        KeySql keySql = buildKeyWhere(meta, encodedKey);
        List<ColumnMeta> columns = meta.getColumns().stream()
                .filter(c -> !c.isReadonly() && !c.isKey() && body.containsKey(c.getProp()))
                .collect(Collectors.toList());
        if (columns.isEmpty())
        {
            throw new ServiceException("没有可更新的字段");
        }

        List<String> colNames = columns.stream().map(ColumnMeta::getColumn).collect(Collectors.toList());
        List<Object> setValues = columns.stream().map(c -> normalize(body.get(c.getProp()))).collect(Collectors.toList());
        try
        {
            return mapper.update(meta, colNames, setValues, keySql.getSql(), keySql.getArgs());
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("修改失败，请检查是否重复或关联数据不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int remove(String module, String ids)
    {
        ModuleMeta meta = getMeta(module);
        int count = 0;
        try
        {
            for (String id : ids.split(","))
            {
                KeySql keySql = buildKeyWhere(meta, id);
                count += mapper.delete(meta, keySql.getSql(), keySql.getArgs());
            }
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ServiceException("删除失败，该数据已被其他业务数据引用");
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Module Definitions (whitelist)
    // ═══════════════════════════════════════════════════════════════════

    private static Map<String, ModuleMeta> buildModules()
    {
        Map<String, ModuleMeta> modules = new LinkedHashMap<>();

        modules.put("college", ModuleMeta.of("college",
                "college c left join school s on s.id = c.school_id",
                "select c.id, c.school_id, s.name school_name, c.name, c.website, c.graduate_url, c.created_at, c.updated_at from college c left join school s on s.id = c.school_id",
                "order by s.name, c.name",
                ModuleMeta.option("college", "c.id", "concat(s.name, ' / ', c.name)", "college c left join school s on s.id = c.school_id", "s.name, c.name"))
                .keyAlias("c").key("id", "id", "ID").col("schoolId", "school_id", "学校", true)
                .view("schoolName", "学校名称").col("name", "name", "学院名称", true)
                .col("website", "website", "学院官网").col("graduateUrl", "graduate_url", "研招信息页")
                .time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchLike("name", "c.name").searchExact("schoolId", "c.school_id"));

        modules.put("subject", ModuleMeta.of("subject", "subject",
                "select id, code, name, subject_type, exam_category, created_at from subject",
                "order by code",
                ModuleMeta.option("subject", "id", "concat(code, ' ', name)", "subject", "code"))
                .key("id", "id", "ID").col("code", "code", "科目代码", true).col("name", "name", "科目名称", true)
                .col("subjectType", "subject_type", "科目类型", true).col("examCategory", "exam_category", "考试类别")
                .time("createdAt", "创建时间").searchLike("code", "code").searchLike("name", "name")
                .searchExact("subjectType", "subject_type"));

        modules.put("program", ModuleMeta.of("program",
                "program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "select p.id, p.college_id, c.school_id, s.province, s.city, c.name college_name, s.name school_name, p.program_code, p.program_name, p.research_direction, p.discipline_category, p.first_discipline, p.study_mode, p.degree_type, p.exam_type, p.score_scale, p.retest_subjects, p.is_408, (select group_concat(concat(sub.code, ' ', sub.name) order by ps.subject_order separator ' / ') from program_subject ps left join subject sub on sub.id = ps.subject_id where ps.program_id = p.id) exam_subjects, exists(select 1 from admission_score a where a.program_id = p.id) has_score, exists(select 1 from admission_plan ap where ap.program_id = p.id) has_plan, exists(select 1 from admission_result ar where ar.program_id = p.id) has_result, exists(select 1 from data_source ds where ds.source_owner = s.name or ds.source_owner = c.name or ds.title like concat('%', s.name, '%')) has_source, p.protects_first_choice, p.is_joint_program, p.status, p.created_at, p.updated_at from program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "order by s.name, c.name, p.program_code",
                ModuleMeta.option("program", "p.id", "concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name)", "program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id", "s.name, c.name, p.program_code"))
                .keyAlias("p").key("id", "id", "ID").col("collegeId", "college_id", "学院", true)
                .view("schoolId", "学校ID").view("province", "省份").view("city", "城市")
                .view("schoolName", "学校").view("collegeName", "学院")
                .col("programCode", "program_code", "专业代码", true).col("programName", "program_name", "专业名称", true)
                .col("researchDirection", "research_direction", "研究方向")
                .col("disciplineCategory", "discipline_category", "学科门类")
                .col("firstDiscipline", "first_discipline", "一级学科")
                .col("studyMode", "study_mode", "学习方式", true).col("degreeType", "degree_type", "学位类型", true)
                .col("examType", "exam_type", "考试类型", true).col("scoreScale", "score_scale", "满分", true)
                .col("retestSubjects", "retest_subjects", "复试科目").view("examSubjects", "初试科目")
                .col("is408", "is_408", "408").view("hasScore", "复试线").view("hasPlan", "招生计划")
                .view("hasResult", "拟录取").view("hasSource", "来源")
                .col("protectsFirstChoice", "protects_first_choice", "保护一志愿")
                .col("isJointProgram", "is_joint_program", "联培").col("status", "status", "状态", true)
                .time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchLike("programCode", "p.program_code").searchLike("programName", "p.program_name")
                .searchExact("schoolId", "s.id").searchExact("collegeId", "p.college_id")
                .searchExact("province", "s.province").searchExact("city", "s.city")
                .searchExact("studyMode", "p.study_mode").searchExact("degreeType", "p.degree_type")
                .searchExact("is408", "p.is_408").searchExact("status", "p.status")
                .searchExists("subjectCode",
                        "exists(select 1 from program_subject ps2 left join subject sub2 on sub2.id = ps2.subject_id where ps2.program_id = p.id and sub2.code = ?)"));

        modules.put("programSubject", ModuleMeta.of("program_subject",
                "program_subject ps left join program p on p.id = ps.program_id left join subject sub on sub.id = ps.subject_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "select ps.program_id, ps.subject_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, concat(sub.code, ' ', sub.name) subject_label, ps.subject_order from program_subject ps left join program p on p.id = ps.program_id left join subject sub on sub.id = ps.subject_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "order by s.name, c.name, p.program_code, ps.subject_order", null)
                .keyAlias("ps").compositeKey("programId", "program_id", "专业", "subjectId", "subject_id", "科目")
                .view("programLabel", "专业方向").view("subjectLabel", "考试科目")
                .col("subjectOrder", "subject_order", "科目顺序", true)
                .searchExact("schoolId", "s.id").searchExact("collegeId", "c.id")
                .searchExact("programId", "ps.program_id").searchExact("subjectId", "ps.subject_id"));

        modules.put("dataSource", ModuleMeta.of("data_source", "data_source",
                "select id, source_type, url, title, source_owner, publish_date, local_file_path, file_hash, page_hash, fetched_at, robots_checked, robots_allowed, terms_checked, commercial_use_risk, copyright_risk, created_at, updated_at from data_source",
                "order by created_at desc",
                ModuleMeta.option("dataSource", "id", "coalesce(title, url, concat('来源#', id))", "data_source",
                        "created_at desc"))
                .key("id", "id", "ID").col("sourceType", "source_type", "来源类型", true)
                .col("url", "url", "来源URL").col("title", "title", "来源标题")
                .col("sourceOwner", "source_owner", "来源主体").col("publishDate", "publish_date", "发布日期")
                .col("localFilePath", "local_file_path", "本地文件").col("fileHash", "file_hash", "文件Hash")
                .col("pageHash", "page_hash", "页面Hash").col("fetchedAt", "fetched_at", "抓取时间")
                .col("robotsChecked", "robots_checked", "已查robots")
                .col("robotsAllowed", "robots_allowed", "robots允许").col("termsChecked", "terms_checked", "已查条款")
                .col("commercialUseRisk", "commercial_use_risk", "商用风险")
                .col("copyrightRisk", "copyright_risk", "版权风险").time("createdAt", "创建时间")
                .time("updatedAt", "更新时间").searchExact("sourceType", "source_type")
                .searchLike("title", "title").searchLike("sourceOwner", "source_owner"));

        addAdmission(modules, "admissionScore", "admission_score",
                "score_line, single_math, single_english, single_politics, single_professional");
        addAdmission(modules, "admissionPlan", "admission_plan",
                "total_plan, recommended_exemption_plan, unified_exam_quota, retest_count");
        addAdmission(modules, "admissionResult", "admission_result",
                "admitted_count, first_choice_admitted_count, min_admitted_score, avg_admitted_score, max_admitted_score, has_transfer, transfer_count");

        modules.put("programYearDataQuality", ModuleMeta.of("program_year_data_quality",
                "program_year_data_quality q left join program p on p.id = q.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "select q.id, q.program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, q.year, q.has_score, q.has_plan, q.has_result, q.has_official_source, q.has_conflict, q.completeness_level, q.missing_fields, q.last_checked_at, q.created_at, q.updated_at from program_year_data_quality q left join program p on p.id = q.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "order by q.year desc, q.completeness_level, q.id desc", null)
                .keyAlias("q").key("id", "id", "ID").col("programId", "program_id", "专业", true)
                .view("programLabel", "专业方向").col("year", "year", "年份", true)
                .col("hasScore", "has_score", "复试线").col("hasPlan", "has_plan", "招生计划")
                .col("hasResult", "has_result", "录取结果").col("hasOfficialSource", "has_official_source", "官网来源")
                .col("hasConflict", "has_conflict", "存在冲突").col("completenessLevel", "completeness_level", "完整度", true)
                .col("missingFields", "missing_fields", "缺失字段").col("lastCheckedAt", "last_checked_at", "检查时间")
                .time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchExact("programId", "q.program_id").searchExact("year", "q.year")
                .searchExact("completenessLevel", "q.completeness_level"));

        modules.put("dataCollectionTask", ModuleMeta.of("data_collection_task",
                "data_collection_task t left join program p on p.id = t.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "select t.id, t.program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, t.task_type, t.target_year, t.priority, t.status, t.source_hint_url, t.failure_reason, t.created_by, t.assigned_to, t.started_at, t.finished_at, t.created_at, t.updated_at from data_collection_task t left join program p on p.id = t.program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "order by t.status, t.priority desc, t.created_at desc", null)
                .keyAlias("t").key("id", "id", "ID").col("programId", "program_id", "专业", true)
                .view("programLabel", "专业方向").col("taskType", "task_type", "任务类型", true)
                .col("targetYear", "target_year", "目标年份", true).col("priority", "priority", "优先级")
                .col("status", "status", "状态", true).col("sourceHintUrl", "source_hint_url", "来源提示")
                .col("failureReason", "failure_reason", "失败原因").col("createdBy", "created_by", "创建来源")
                .col("assignedTo", "assigned_to", "处理人").col("startedAt", "started_at", "开始时间")
                .col("finishedAt", "finished_at", "完成时间").time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchExact("programId", "t.program_id").searchExact("targetYear", "t.target_year")
                .searchExact("taskType", "t.task_type").searchExact("status", "t.status"));

        modules.put("staging", ModuleMeta.of("staging",
                "staging st left join data_collection_task t on t.id = st.task_id left join data_source ds on ds.id = st.source_id left join program p on p.id = st.matched_program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "select st.id, st.task_id, st.source_id, st.matched_program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) matched_program_label, st.source_type, st.school_name, st.college_name, st.city, st.program_code, st.program_name, st.exam_subjects, st.year, st.score_line, st.single_math, st.single_english, st.single_politics, st.single_professional, st.min_admitted, st.avg_admitted, st.plan_count, st.retest_count, st.admitted_count, st.confidence, st.source_url, st.raw_text, st.extract_json, st.status, st.error_message, st.reviewer_id, st.reviewed_at, st.review_note, st.created_at, st.updated_at from staging st left join data_collection_task t on t.id = st.task_id left join data_source ds on ds.id = st.source_id left join program p on p.id = st.matched_program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id",
                "order by st.created_at desc", null)
                .keyAlias("st").key("id", "id", "ID").col("taskId", "task_id", "任务")
                .col("sourceId", "source_id", "来源").col("matchedProgramId", "matched_program_id", "匹配专业")
                .view("matchedProgramLabel", "匹配专业方向").col("sourceType", "source_type", "采集来源", true)
                .col("schoolName", "school_name", "学校名称", true).col("collegeName", "college_name", "学院名称")
                .col("city", "city", "城市").col("programCode", "program_code", "专业代码")
                .col("programName", "program_name", "专业名称").col("examSubjects", "exam_subjects", "初试科目")
                .col("year", "year", "年份").col("scoreLine", "score_line", "复试线")
                .col("singleMath", "single_math", "数学线").col("singleEnglish", "single_english", "英语线")
                .col("singlePolitics", "single_politics", "政治线")
                .col("singleProfessional", "single_professional", "专业课线")
                .col("minAdmitted", "min_admitted", "最低录取").col("avgAdmitted", "avg_admitted", "平均录取")
                .col("planCount", "plan_count", "计划数").col("retestCount", "retest_count", "复试数")
                .col("admittedCount", "admitted_count", "录取数").col("confidence", "confidence", "置信度")
                .col("sourceUrl", "source_url", "来源URL").col("rawText", "raw_text", "原始文本")
                .col("extractJson", "extract_json", "抽取JSON").col("status", "status", "审核状态", true)
                .col("errorMessage", "error_message", "错误信息").col("reviewerId", "reviewer_id", "审核人")
                .col("reviewedAt", "reviewed_at", "审核时间").col("reviewNote", "review_note", "审核备注")
                .time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchLike("schoolName", "st.school_name").searchLike("programCode", "st.program_code")
                .searchExact("year", "st.year").searchExact("status", "st.status")
                .searchExact("confidence", "st.confidence"));

        return modules;
    }

    private static void addAdmission(Map<String, ModuleMeta> modules, String module, String table,
            String metricColumns)
    {
        String alias = table.equals("admission_score") ? "a" : table.equals("admission_plan") ? "ap" : "ar";
        ModuleMeta meta = ModuleMeta.of(table,
                table + " " + alias
                        + " left join program p on p.id = " + alias + ".program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id left join data_source ds on ds.id = "
                        + alias + ".source_id",
                "select " + alias + ".id, " + alias + ".program_id, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) program_label, "
                        + alias + ".year, " + prefix(alias, metricColumns) + ", " + alias
                        + ".verify_status, " + alias + ".source_id, coalesce(ds.title, ds.url) source_label, " + alias
                        + ".created_at, " + alias + ".updated_at from " + table + " " + alias
                        + " left join program p on p.id = " + alias
                        + ".program_id left join college c on c.id = p.college_id left join school s on s.id = c.school_id left join data_source ds on ds.id = "
                        + alias + ".source_id",
                "order by " + alias + ".year desc, " + alias + ".id desc", null)
                .keyAlias(alias).key("id", "id", "ID").col("programId", "program_id", "专业", true)
                .view("programLabel", "专业方向").col("year", "year", "年份", true);
        for (String column : metricColumns.split(", "))
        {
            meta.col(ModuleMeta.camel(column), column, ModuleMeta.admissionLabel(column));
        }
        meta.col("verifyStatus", "verify_status", "可信状态", true).col("sourceId", "source_id", "来源")
                .view("sourceLabel", "来源标题").time("createdAt", "创建时间").time("updatedAt", "更新时间")
                .searchExact("schoolId", "s.id").searchExact("collegeId", "c.id")
                .searchExact("programId", alias + ".program_id").searchExact("year", alias + ".year")
                .searchExact("verifyStatus", alias + ".verify_status");
        modules.put(module, meta);
    }

    private static String prefix(String alias, String columns)
    {
        return Arrays.stream(columns.split(", ")).map(c -> alias + "." + c).collect(Collectors.joining(", "));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  WHERE / Key building
    // ═══════════════════════════════════════════════════════════════════

    private ModuleMeta getMeta(String module)
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
        for (SearchMeta search : meta.getSearches())
        {
            String value = params.get(search.getProp());
            if (!StringUtils.hasText(value))
            {
                continue;
            }
            if (search.isLike())
            {
                args.add("%" + value + "%");
                parts.add(search.getExpression() + " like " + indexedParam("whereArgs", args.size() - 1));
            }
            else if (search.isExists())
            {
                args.add(value);
                parts.add(search.getExpression().replace("?", indexedParam("whereArgs", args.size() - 1)));
            }
            else
            {
                args.add(value);
                parts.add(search.getExpression() + " = " + indexedParam("whereArgs", args.size() - 1));
            }
        }
        return new WhereSql(parts.isEmpty() ? "" : " where " + String.join(" and ", parts), args);
    }

    private KeySql buildKeyWhere(ModuleMeta meta, String encodedKey)
    {
        String[] values = encodedKey.split("_", -1);
        if (values.length != meta.getKeyColumns().size())
        {
            throw new ServiceException("数据主键无效");
        }
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < meta.getKeyColumns().size(); i++)
        {
            ColumnMeta col = meta.getKeyColumns().get(i);
            args.add(values[i]);
            parts.add(meta.getKeyAlias() + "." + col.getColumn() + " = " + indexedParam("keyArgs", args.size() - 1));
        }
        return new KeySql(String.join(" and ", parts), args);
    }

    private static String indexedParam(String paramName, int index)
    {
        return "#{" + paramName + "[" + index + "]}";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, Object> toCamelRow(ModuleMeta meta, Map<String, Object> row)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            result.put(ModuleMeta.camel(entry.getKey()), normalizeRead(entry.getValue()));
        }
        result.put("__key", meta.getKeyColumns().stream()
                .map(c -> Objects.toString(result.get(c.getProp()), ""))
                .collect(Collectors.joining("_")));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> optionRows(String module, ModuleMeta meta, Map<String, String> params)
    {
        if ("subject".equals(module))
        {
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "select id, concat(code, ' ', name) label from subject where 1=1");
            if (StringUtils.hasText(params.get("keyword")))
            {
                args.add("%" + params.get("keyword") + "%");
                String keywordParam = indexedParam("args", args.size() - 1);
                sql.append(" and (code like ").append(keywordParam)
                        .append(" or name like ").append(keywordParam).append(")");
            }
            sql.append(" order by code");
            return mapper.selectOption(sql.toString(), args);
        }
        if ("college".equals(module))
        {
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "select c.id id, c.school_id schoolId, concat(s.name, ' / ', c.name) label from college c left join school s on s.id = c.school_id where 1=1");
            if (StringUtils.hasText(params.get("schoolId")))
            {
                args.add(params.get("schoolId"));
                sql.append(" and c.school_id = ").append(indexedParam("args", args.size() - 1));
            }
            if (StringUtils.hasText(params.get("keyword")))
            {
                args.add("%" + params.get("keyword") + "%");
                sql.append(" and (s.name like ").append(indexedParam("args", args.size() - 1))
                        .append(" or c.name like ").append(indexedParam("args", args.size() - 1)).append(")");
            }
            sql.append(" order by s.name, c.name");
            return mapper.selectOption(sql.toString(), args);
        }
        if ("program".equals(module))
        {
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "select p.id id, p.college_id collegeId, c.school_id schoolId, concat(s.name, ' / ', c.name, ' / ', p.program_code, ' ', p.program_name) label from program p left join college c on c.id = p.college_id left join school s on s.id = c.school_id where 1=1");
            if (StringUtils.hasText(params.get("schoolId")))
            {
                args.add(params.get("schoolId"));
                sql.append(" and c.school_id = ").append(indexedParam("args", args.size() - 1));
            }
            if (StringUtils.hasText(params.get("collegeId")))
            {
                args.add(params.get("collegeId"));
                sql.append(" and p.college_id = ").append(indexedParam("args", args.size() - 1));
            }
            if (StringUtils.hasText(params.get("keyword")))
            {
                args.add("%" + params.get("keyword") + "%");
                String keywordParam = indexedParam("args", args.size() - 1);
                sql.append(" and (s.name like ").append(keywordParam)
                        .append(" or c.name like ").append(keywordParam)
                        .append(" or p.program_code like ").append(keywordParam)
                        .append(" or p.program_name like ").append(keywordParam)
                        .append(" or p.research_direction like ").append(keywordParam).append(")");
            }
            sql.append(" order by s.name, c.name, p.program_code");
            return mapper.selectOption(sql.toString(), args);
        }
        if ("dataSource".equals(module))
        {
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "select id, coalesce(title, url, concat('来源#', id)) label from data_source where 1=1");
            if (StringUtils.hasText(params.get("keyword")))
            {
                args.add("%" + params.get("keyword") + "%");
                String keywordParam = indexedParam("args", args.size() - 1);
                sql.append(" and (title like ").append(keywordParam)
                        .append(" or url like ").append(keywordParam)
                        .append(" or source_owner like ").append(keywordParam).append(")");
            }
            sql.append(" order by created_at desc");
            return mapper.selectOption(sql.toString(), args);
        }
        return mapper.selectOption(meta.getOptionSql(), Collections.emptyList());
    }

    private void validateRequired(ModuleMeta meta, Map<String, Object> body, boolean insert)
    {
        for (ColumnMeta col : meta.getColumns())
        {
            if (col.isRequired() && !col.isReadonly() && (insert || !col.isKey()))
            {
                Object value = body.get(col.getProp());
                if (value == null || !StringUtils.hasText(Objects.toString(value, "")))
                {
                    throw new ServiceException(col.getLabel() + "不能为空");
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
}
