package com.ruoyi.postgrad.rag.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 评测数据集 — 63 dev + 18 test, structured / general / negative 三段。
 */
public final class RagEvaluationDataset {

    private RagEvaluationDataset() {}

    public static List<RagEvaluationCase> devSet() {
        List<RagEvaluationCase> cases = new ArrayList<>();
        cases.addAll(facts()); cases.addAll(trends()); cases.addAll(quality());
        cases.addAll(general()); cases.addAll(edges());
        return cases;
    }

    public static List<RagEvaluationCase> testSet() {
        List<RagEvaluationCase> cases = new ArrayList<>();
        cases.addAll(factsTest()); cases.addAll(trendsTest()); cases.addAll(qualityTest());
        cases.addAll(generalTest()); cases.addAll(edgesTest());
        return cases;
    }

    public static List<RagEvaluationCase> smokeTests() {
        return List.of(
            c("score-line-smoke","杭电网安2025复试线",
                Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year"),
                List.of(cond("2854","program_year","admission_score","2025")),List.of(),
                "RAG","CITATION_ONLY","easy","fact",List.of("smoke"),false),
            c("admission-plan-smoke","杭电网安2025统考名额",
                Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year"),
                List.of(cond("2854","program_year","admission_plan","2025")),List.of(),
                "RAG","CITATION_ONLY","easy","fact",List.of("smoke"),false),
            c("admission-result-smoke","杭电网安2025录取最低分",
                Map.of("school_id","56","program_id","2854","year","2025","doc_type","program_year"),
                List.of(cond("2854","program_year","admission_result","2025")),List.of(),
                "RAG","CITATION_ONLY","easy","fact",List.of("smoke"),false)
        );
    }

    // ═══════════ 1. facts (12 dev + 3 test) ═══════════
    private static List<RagEvaluationCase> facts() { return List.of(
        c("fact-001","深圳大学 计算机科学与技术 2025 年招生人数",
            Map.of("school_id","15","program_id","2501","year","2025","doc_type","program_year"),
            List.of(cond("2501","program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-002","深圳大学 计算机科学与技术 2025 年复试线",
            Map.of("school_id","15","program_id","2501","year","2025","doc_type","program_year"),
            List.of(cond("2501","program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-003","深圳大学 计算机科学与技术 2025 年录取人数",
            Map.of("school_id","15","program_id","2501","year","2025","doc_type","program_year"),
            List.of(cond("2501","program_year","admission_result","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-004","华侨大学 软件工程 2025 年复试线",
            Map.of("school_id","3","program_id","3253","year","2025","doc_type","program_year"),
            List.of(cond("3253","program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-005","南京农业大学 植物表型组学 2025 年招生人数",
            Map.of("school_id","454","program_id","2227","year","2025","doc_type","program_year"),
            List.of(cond("2227","program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-006","深大 计算机 2025 年录取均分",
            Map.of("school_id","15","program_id","2501","year","2025","doc_type","program_year"),
            List.of(cond("2501","program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("paraphrase","alias"),false),
        c("fact-007","华侨大学的软工 2025 分数切线",
            Map.of("school_id","3","program_id","3253","year","2025","doc_type","program_year"),
            List.of(cond("3253","program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("paraphrase","alias"),false),
        c("fact-008","杭电 2025 年计算机方向统考名额",
            Map.of("school_id","56","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("alias","broad"),false),
        c("fact-009","杭州电子科技大学电子信息方向 2025 录取",
            Map.of("school_id","56","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_result","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("broad"),false),
        c("fact-010","福建师范大学 2025 年 408 专业招生计划",
            Map.of("school_id","20","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("exact"),false),
        c("fact-011","福州大学 2025 年 408 专业录取情况",
            Map.of("school_id","4","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_result","2025"),cond(null,"program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","fact",List.of("exact"),false),
        c("fact-012","深大计算机 2025 复试线和录取人数一起告诉我",
            Map.of("school_id","15","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_score","2025"),cond(null,"program_year","admission_result","2025")),List.of(),
            "TOOL_RAG","PRIMARY","medium","fact",List.of("multi-field"),false)
    );}
    private static List<RagEvaluationCase> factsTest() { return List.of(
        c("fact-test-01","深圳大学 人工智能 2025 复试线",
            Map.of("school_id","15","program_id","1768","year","2025","doc_type","program_year"),
            List.of(cond("1768","program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-test-02","南京农业大学 智慧农业 2025 招生人数",
            Map.of("school_id","454","program_id","2228","year","2025","doc_type","program_year"),
            List.of(cond("2228","program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","easy","fact",List.of("exact"),false),
        c("fact-test-03","华大软工近两年录取情况怎么样",
            Map.of("school_id","3","program_id","3253","doc_type","program_history"),
            List.of(cond("3253","program_history",null,null)),List.of(),
            "RAG","PRIMARY","hard","fact",List.of("paraphrase","alias"),false)
    );}

    // ═══════════ 2. trends (15 dev + 5 test) ═══════════
    private static List<RagEvaluationCase> trends() { return List.of(
        c("trend-001","深大 计算机科学与技术 近三年录取分上涨？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("trend"),false),
        c("trend-002","这个专业的录取分数稳定吗？波动大不大？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","score_trend",null),cond("2501","program_history","risk_factors",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("trend","stability"),false),
        c("trend-003","华侨大学 软件工程 历年复试线趋势",
            Map.of("school_id","3","program_id","3253","doc_type","program_history"),
            List.of(cond("3253","program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","easy","trend",List.of("trend"),false),
        c("trend-004","南农 植物表型组学 招生人数稳定吗？",
            Map.of("school_id","454","program_id","2227","doc_type","program_history"),
            List.of(cond("2227","program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("trend"),false),
        c("trend-005","深大计算机专硕是不是一直在扩招？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","admission_trend",null),cond("2501","program_history","plan_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("alias"),false),
        c("trend-006","深大计科为什么被推荐为冲刺档？风险在哪？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","risk_factors",null),cond("2501","program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("risk"),false),
        c("trend-007","这个专业适合做保底吗？",
            Map.of("school_id","3","program_id","3253","doc_type","program_history"),
            List.of(cond("3253","program_history","risk_factors",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("risk"),false),
        c("trend-008","华侨大学 计算机 为什么风险比较大？",
            Map.of("school_id","3","program_id","3257","doc_type","program_history"),
            List.of(cond("3257","program_history","risk_factors",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("risk"),false),
        c("trend-009","这个专业竞争激烈程度近三年怎么变化？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","risk_factors",null),cond("2501","program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","hard","trend",List.of("competition"),false),
        c("trend-010","南农这个专业是不是越来越难考了？",
            Map.of("school_id","454","program_id","3180","doc_type","program_history"),
            List.of(cond("3180","program_history","score_trend",null),cond("3180","program_history","risk_factors",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("alias"),false),
        c("trend-011","深圳大学 408 专业近两年分数趋势",
            Map.of("school_id","15","doc_type","program_history"),
            List.of(cond(null,"program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("broad"),false),
        c("trend-012","福州大学 2025 年 408 专业录取趋势",
            Map.of("school_id","4","doc_type","program_history"),
            List.of(cond(null,"program_history","score_trend",null),cond(null,"program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","hard","trend",List.of("broad"),false),
        c("trend-013","深大计算机专硕数据完整吗？能看趋势？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","score_trend",null)),List.of(),
            "TOOL_RAG","PRIMARY","hard","trend",List.of("composite"),false),
        c("trend-014","杭电的分数线是不是年年涨？",
            Map.of("school_id","56","doc_type","program_history"),
            List.of(cond(null,"program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","hard","trend",List.of("alias"),false),
        c("trend-015","福建师范大学 408 专业录取稳定吗？",
            Map.of("school_id","20","doc_type","program_history"),
            List.of(cond(null,"program_history","risk_factors",null),cond(null,"program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("broad"),false)
    );}
    private static List<RagEvaluationCase> trendsTest() { return List.of(
        c("trend-test-01","深大 计算机科学与技术 近三年录取稳定？",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","score_trend",null),cond("2501","program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("trend"),false),
        c("trend-test-02","华侨大学 软件工程 招生人数变化",
            Map.of("school_id","3","program_id","3253","doc_type","program_history"),
            List.of(cond("3253","program_history","admission_trend",null)),List.of(),
            "RAG","PRIMARY","medium","trend",List.of("trend"),false),
        c("trend-test-03","南农植物表型组学适不适合冲刺？",
            Map.of("school_id","454","program_id","2227","doc_type","program_history"),
            List.of(cond("2227","program_history","risk_factors",null),cond("2227","program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","hard","trend",List.of("risk","alias"),false),
        c("trend-test-04","深大的 408 专业好不好考？",
            Map.of("school_id","15","doc_type","program_history"),
            List.of(cond(null,"program_history","risk_factors",null),cond(null,"program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","hard","trend",List.of("alias","vague"),false),
        c("trend-test-05","深大计算机科学历年录取走势",
            Map.of("school_id","15","program_id","2501","doc_type","program_history"),
            List.of(cond("2501","program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","easy","trend",List.of("alias"),false)
    );}

    // ═══════════ 3. quality (12 dev + 3 test) ═══════════
    private static List<RagEvaluationCase> quality() { return List.of(
        c("quality-001","深大 计算机科学与技术 数据可靠吗？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","coverage_summary",null),cond("2501","data_quality","source_conflicts",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("source-trust"),false),
        c("quality-002","这个专业录取数据有没有缺失的年份？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","coverage_summary",null),cond("2501","data_quality","missing_years",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("missing-data"),false),
        c("quality-003","华侨大学 软件工程 数据完整度怎么样？",
            Map.of("school_id","3","program_id","3253","doc_type","data_quality"),
            List.of(cond("3253","data_quality","coverage_summary",null),cond("3253","data_quality","missing_fields",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("completeness"),false),
        c("quality-004","为什么推荐这个专业时要谨慎？",
            Map.of("school_id","3","program_id","3257","doc_type","data_quality"),
            List.of(cond("3257","data_quality","recommendation_warning",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("caution"),false),
        c("quality-005","南农 计算机科学与技术 数据来源可靠？",
            Map.of("school_id","454","program_id","800","doc_type","data_quality"),
            List.of(cond("800","data_quality","coverage_summary",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("source-trust"),false),
        c("quality-006","招生计划和录取结果有冲突吗？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","source_conflicts",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("conflict"),false),
        c("quality-007","哪些字段存在缺失？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","missing_fields",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("missing-data"),false),
        c("quality-008","深大这个专业的数据覆盖了哪几年？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","coverage_summary",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("coverage"),false),
        c("quality-009","杭电计算机专业数据完整度如何？",
            Map.of("school_id","56","doc_type","data_quality"),
            List.of(cond(null,"data_quality","coverage_summary",null),cond(null,"data_quality","recommendation_warning",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("alias"),false),
        c("quality-010","深圳大学招生数据来源是否官方？",
            Map.of("school_id","15","doc_type","data_quality"),
            List.of(cond(null,"data_quality","coverage_summary",null),cond(null,"data_quality","source_conflicts",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("source-trust"),false),
        c("quality-011","福州大学 408 专业历年数据有没有断档？",
            Map.of("school_id","4","doc_type","data_quality"),
            List.of(cond(null,"data_quality","missing_years",null),cond(null,"data_quality","coverage_summary",null)),List.of(),
            "RAG","PRIMARY","medium","quality",List.of("gap"),false),
        c("quality-012","福建师范大学计算机方向的数据质量评分",
            Map.of("school_id","20","doc_type","data_quality"),
            List.of(cond(null,"data_quality","coverage_summary",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("exact"),false)
    );}
    private static List<RagEvaluationCase> qualityTest() { return List.of(
        c("quality-test-01","深大计算机专硕的复试线数据来源靠谱吗？",
            Map.of("school_id","15","program_id","2501","doc_type","data_quality"),
            List.of(cond("2501","data_quality","coverage_summary",null),cond("2501","data_quality","source_conflicts",null)),List.of(),
            "RAG","PRIMARY","easy","quality",List.of("paraphrase"),false),
        c("quality-test-02","华侨大学这些专业有没有缺报录比？",
            Map.of("school_id","3","doc_type","data_quality"),
            List.of(cond(null,"data_quality","missing_fields",null),cond(null,"data_quality","coverage_summary",null)),List.of(),
            "RAG","PRIMARY","hard","quality",List.of("alias"),false),
        c("quality-test-03","南农智慧农业分数数据全不全？",
            Map.of("school_id","454","program_id","2228","doc_type","data_quality"),
            List.of(cond("2228","data_quality","coverage_summary",null),cond("2228","data_quality","missing_years",null)),List.of(),
            "RAG","PRIMARY","hard","quality",List.of("alias"),false)
    );}

    // ═══════════ 4. general (12 dev + 3 test) ═══════════
    private static final List<String> RAG_DOCS = List.of(
        "01-408考试介绍","02-考研常见术语","03-国家线与复试","04-调剂政策","05-择校方法论"
    );
    private static List<RagEvaluationCase> general() { return List.of(
        g("general-001","408 考研考什么科目？","easy",List.of("exam")),
        g("general-002","408 各科分值怎么分布的？","easy",List.of("exam")),
        g("general-003","408 计算机基础综合怎么复习？","medium",List.of("exam")),
        g("general-004","408 考啥？","medium",List.of("paraphrase")),
        g("general-005","计算机考研统考是哪几门？","medium",List.of("paraphrase")),
        g("general-006","国家线和院校复试线有什么区别？","easy",List.of("policy")),
        g("general-007","过了国家线为什么还进不了复试？","medium",List.of("policy")),
        g("general-008","什么是差额复试？比例一般多少？","easy",List.of("policy")),
        g("general-009","调剂时一志愿保护是什么意思？","easy",List.of("policy")),
        g("general-010","学硕和专硕在考研时有什么区别？","easy",List.of("methodology")),
        g("general-011","怎么判断一个学校好不好考？","medium",List.of("methodology")),
        g("general-012","专硕和学硕到底怎么选？","hard",List.of("paraphrase"))
    );}
    private static List<RagEvaluationCase> generalTest() { return List.of(
        g("general-test-01","考研 A 区和 B 区有什么区别？","medium",List.of("policy")),
        g("general-test-02","408 统考考哪些科目？每门占多少？","easy",List.of("paraphrase")),
        g("general-test-03","什么是国家线、院校线、专业线？","hard",List.of("composite"))
    );}

    // ═══════════ 5. edge (12 dev + 4 test) ═══════════
    private static List<RagEvaluationCase> edges() { return List.of(
        c("edge-001","火星大学 2025 年计算机复试线",
            Map.of("school_id","99999"),List.of(),List.of(),
            "MYSQL_TOOL",null,"hard","edge",List.of("nonexistent"),true),
        c("edge-002","深大 计算机科学与技术 2018 年录取分数",
            Map.of("school_id","15","program_id","2501","year","2018","doc_type","program_year"),
            List.of(),List.of(),"RAG","PRIMARY","hard","edge",List.of("no-data-year"),true),
        c("edge-003","深圳大学人工智能 2025 年招生名额",
            Map.of("school_id","15","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","hard","edge",List.of("ambiguous"),false),
        c("edge-004","杭电 2025 年招计算机吗？",
            Map.of("school_id","56","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_plan","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","edge",List.of("alias"),false),
        c("edge-005","深大 CS 2025 复试线",
            Map.of("school_id","15","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","hard","edge",List.of("alias","english-abbr"),false),
        c("edge-006","华大计算机专硕 2025 分数线",
            Map.of("school_id","3","year","2025","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_score","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","edge",List.of("alias"),false),
        c("edge-007","清华大学 408 计算机 2025 分数",
            Map.of("school_id","99998"),List.of(),List.of(),
            "MYSQL_TOOL",null,"hard","edge",List.of("wrong-premise"),true),
        c("edge-008","讲一下 408 考什么？",
            Map.of("school_id","15"),List.of(),RAG_DOCS,
            "RAG","PRIMARY","hard","edge",List.of("mismatched-filter"),false),
        c("edge-009","深大 电子信息-大数据 非全 2025 录取",
            Map.of("school_id","15","program_id","2449","year","2025","doc_type","program_year"),
            List.of(cond("2449","program_year","admission_result","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","medium","edge",List.of("part-time"),false),
        c("edge-010","085404 这个代码对应什么专业？",
            Map.of(),List.of(),List.of(),"MYSQL_TOOL",null,"medium","edge",List.of("code-query"),false),
        c("edge-011","深大和福大哪个 408 计算机更好考？",
            Map.of("school_id","15"),List.of(),List.of(),
            "TOOL_RAG","PRIMARY","hard","edge",List.of("multi-intent"),false),
        c("edge-012","华侨大学 计算机技术全日制 2025年3月 录取数据",
            Map.of("school_id","3","program_id","3257","year","2025","doc_type","program_year"),
            List.of(cond("3257","program_year","admission_result","2025")),List.of(),
            "MYSQL_TOOL","CITATION_ONLY","hard","edge",List.of("over-specific"),false)
    );}
    private static List<RagEvaluationCase> edgesTest() { return List.of(
        c("edge-test-01","福州大学 408 2021 年分数线",
            Map.of("school_id","4","year","2021","doc_type","program_year"),
            List.of(),List.of(),"RAG","PRIMARY","hard","edge",List.of("no-data-year"),true),
        c("edge-test-02","师大的计算机好考吗？",
            Map.of("school_id","20","doc_type","program_history"),
            List.of(cond(null,"program_history","risk_factors",null),cond(null,"program_history","score_trend",null)),List.of(),
            "RAG","PRIMARY","hard","edge",List.of("alias","vague"),false),
        c("edge-test-03","深大计算机全日制和非全录取分数一样吗？",
            Map.of("school_id","15","doc_type","program_year"),
            List.of(cond(null,"program_year","admission_score","2025")),List.of(),
            "TOOL_RAG","PRIMARY","hard","edge",List.of("comparison"),false),
        c("edge-test-04","300 分福建有什么计算机专业的学校",
            Map.of(),List.of(),List.of(),
            "MYSQL_TOOL",null,"hard","edge",List.of("should-not-be-rag"),false)
    );}

    // ═══════════ builders ═══════════
    private static RagEvaluationCase c(String id, String query, Map<String,String> metadata,
        List<RagEvaluationCase.RelevantCondition> conditions, List<String> sources,
        String route, String usage, String difficulty, String category,
        List<String> tags, boolean fallback) {
        RagEvaluationCase tc = new RagEvaluationCase(id, query, metadata);
        tc.setRelevantConditions(conditions);
        tc.setRelevantSources(sources);
        tc.setExpectedRoute(route);
        tc.setRagUsage(usage);
        tc.setDifficulty(difficulty);
        tc.setCategory(category);
        tc.setTags(tags);
        tc.setExpectedFallback(fallback);
        return tc;
    }

    private static RagEvaluationCase g(String id, String query, String difficulty, List<String> tags) {
        RagEvaluationCase tc = new RagEvaluationCase(id, query, Map.of());
        tc.setRelevantSources(RAG_DOCS);
        tc.setExpectedRoute("RAG");
        tc.setRagUsage("PRIMARY");
        tc.setDifficulty(difficulty);
        tc.setCategory("general");
        tc.setTags(tags);
        return tc;
    }

    private static RagEvaluationCase.RelevantCondition cond(String pid, String dt, String sec, String yr) {
        return RagEvaluationCase.RelevantCondition.of(pid, dt, sec, yr);
    }
}
