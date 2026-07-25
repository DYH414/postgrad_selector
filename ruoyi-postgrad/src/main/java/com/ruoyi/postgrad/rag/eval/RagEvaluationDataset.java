package com.ruoyi.postgrad.rag.eval;

import java.util.List;
import java.util.Map;

/**
 * Evaluation cases for the active unstructured corpus.
 *
 * <p>Structured scores, quotas, rankings and historical trends are evaluated
 * through MySQL tools, not through this retrieval-only dataset.</p>
 */
public final class RagEvaluationDataset {

    private RagEvaluationDataset() {}

    public static List<RagEvaluationCase> devSet() {
        return List.of(
            general("general-001", "408 考研考哪些科目，各科多少分", "01-408考试介绍", "easy"),
            general("general-002", "计算机考研统考 408 的题型和考试时间", "01-408考试介绍", "medium"),
            general("general-003", "没过国家线是不是就没戏了", "02-考研国家线与复试", "medium"),
            general("general-004", "A 区和 B 区国家线有什么区别", "02-考研国家线与复试", "easy"),
            general("general-005", "过了国家线为什么还可能进不了复试", "02-考研国家线与复试", "medium"),
            general("general-006", "考研调剂需要满足什么条件", "03-调剂政策", "easy"),
            general("general-007", "调剂的平行志愿和锁定时间是什么意思", "03-调剂政策", "medium"),
            general("general-008", "学硕和专硕有什么区别", "04-考研术语与常识", "easy"),
            general("general-009", "全日制和非全日制研究生怎么选", "04-考研术语与常识", "medium"),
            general("general-010", "怎么判断一所学校适不适合冲刺", "05-择校方法论", "medium"),
            general("general-011", "考研择校的冲稳保应该怎么划分", "05-择校方法论", "easy"),
            general("general-012", "计算机学科评估和 985 211 标签怎么看", "06-学科评估与院校层次", "medium"),

            school("school-001", "杭州电子科技大学研究生学费和学制", "杭州电子科技大学", "easy"),
            school("school-002", "杭电硕士研究生报考条件是什么", "杭州电子科技大学", "medium"),
            school("school-003", "重庆邮电大学研究生奖助学金政策", "重庆邮电大学", "easy"),
            school("school-004", "重邮硕士招生简章里对考生有哪些要求", "重庆邮电大学", "medium"),
            school("school-005", "电子科技大学硕士研究生培养和学制", "电子科技大学", "medium"),
            school("school-006", "福州大学研究生学费是多少", "福州大学", "easy"),
            school("school-007", "福建师范大学硕士研究生报考条件", "福建师范大学", "easy"),
            school("school-008", "华侨大学研究生招生联系方式", "华侨大学", "medium"),
            school("school-009", "南京农业大学硕士招生计划和报考要求", "南京农业大学", "medium"),
            school("school-010", "北京邮电大学研究生学费学制", "北京邮电大学", "easy"),
            school("school-011", "安徽大学硕士研究生奖助政策", "安徽大学", "medium"),
            school("school-012", "北京工业大学研究生报考条件", "北京工业大学", "medium")
        );
    }

    public static List<RagEvaluationCase> testSet() {
        return List.of(
            general("general-test-01", "408 到底考啥", "01-408考试介绍", "hard"),
            general("general-test-02", "国家线过不了还有机会调剂吗", "02-考研国家线与复试", "hard"),
            general("general-test-03", "一志愿保护是什么意思", "03-调剂政策", "medium"),
            general("general-test-04", "专硕和学硕到底怎么选", "04-考研术语与常识", "hard"),
            general("general-test-05", "300 分该怎么制定冲稳保策略", "05-择校方法论", "medium"),
            general("general-test-06", "双非学校里有哪些计算机学科实力较强", "06-学科评估与院校层次", "hard"),
            school("school-test-01", "杭电读研一年要交多少钱", "杭州电子科技大学", "hard"),
            school("school-test-02", "重邮研究生有哪些奖助支持", "重庆邮电大学", "medium"),
            school("school-test-03", "福大硕士要读几年", "福州大学", "hard"),
            school("school-test-04", "北邮研究生报名要满足哪些要求", "北京邮电大学", "medium"),
            noAnswer("edge-test-01", "星河大学研究生招生简章", "星河大学"),
            noAnswer("edge-test-02", "深圳大学研究生学费学制", "深圳大学")
        );
    }

    public static List<RagEvaluationCase> smokeTests() {
        return List.of(
            general("smoke-general", "没过国家线还能调剂吗", "02-考研国家线与复试", "easy"),
            school("smoke-school", "杭州电子科技大学研究生学费", "杭州电子科技大学", "easy"),
            noAnswer("smoke-no-answer", "星河大学招生简章", "星河大学")
        );
    }

    private static RagEvaluationCase general(
        String id, String query, String sourcePrefix, String difficulty) {
        RagEvaluationCase testCase = new RagEvaluationCase(id, query, Map.of());
        testCase.setRelevantSources(List.of(sourcePrefix));
        testCase.setExpectedRoute("KNOWLEDGE_RAG");
        testCase.setRagUsage("PRIMARY");
        testCase.setDifficulty(difficulty);
        testCase.setCategory("general");
        return testCase;
    }

    private static RagEvaluationCase school(
        String id, String query, String schoolName, String difficulty) {
        RagEvaluationCase testCase = new RagEvaluationCase(id, query, Map.of(
            "school_name", schoolName,
            "doc_type", "admission_guide"
        ));
        testCase.setRelevantSources(List.of(schoolName + "-招生简章"));
        testCase.setExpectedRoute("KNOWLEDGE_RAG");
        testCase.setRagUsage("PRIMARY");
        testCase.setDifficulty(difficulty);
        testCase.setCategory("school");
        return testCase;
    }

    private static RagEvaluationCase noAnswer(String id, String query, String schoolName) {
        RagEvaluationCase testCase = new RagEvaluationCase(id, query, Map.of(
            "school_name", schoolName,
            "doc_type", "admission_guide"
        ));
        testCase.setNoAnswer(true);
        testCase.setSourceDataAvailable(false);
        testCase.setExpectedFallback(true);
        testCase.setExpectedRoute("KNOWLEDGE_RAG");
        testCase.setRagUsage("PRIMARY");
        testCase.setDifficulty("hard");
        testCase.setCategory("edge");
        return testCase;
    }
}
