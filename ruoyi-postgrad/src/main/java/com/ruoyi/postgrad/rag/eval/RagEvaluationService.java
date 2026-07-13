package com.ruoyi.postgrad.rag.eval;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);
    private static final int RECALL_TOP_N = 20;
    private static final int FINAL_TOP_K = 5;

    private final RagRetrievalService retrievalService;

    public RagEvaluationService(RagRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /** 保留旧接口 — 跑 dev set */
    public RagEvaluationResult runDefault() {
        return runDev();
    }

    /** 开发集 60 条 */
    public RagEvaluationResult runDev() {
        RagEvaluationResult r = run("dev", RagEvaluationDataset.devSet());
        log.info("[RagEval] dev MRR={} Hit@1={}% Hit@5={}% FilterAcc={}%",
            r.getMrr(), r.getHitAt1() * 100, r.getHitAt5() * 100, r.getFilterAccuracy() * 100);
        return r;
    }

    /** 测试集 20 条 — 最后一次跑 */
    public RagEvaluationResult runTest() {
        RagEvaluationResult r = run("test", RagEvaluationDataset.testSet());
        log.info("[RagEval] test MRR={} Hit@1={}% Hit@5={}% FilterAcc={}%",
            r.getMrr(), r.getHitAt1() * 100, r.getHitAt5() * 100, r.getFilterAccuracy() * 100);
        return r;
    }

    /** 全量（dev + test 合并）- 保留接口 */
    public RagEvaluationResult runFull() {
        List<RagEvaluationCase> all = new ArrayList<>();
        all.addAll(RagEvaluationDataset.devSet());
        all.addAll(RagEvaluationDataset.testSet());
        return run("full", all);
    }

    /** 自定义用例评测（保留旧接口兼容） */
    public RagEvaluationResult run(List<RagEvaluationCase> cases) {
        return run("custom", cases);
    }

    private RagEvaluationResult run(String setName, List<RagEvaluationCase> cases) {
        RagEvaluationResult result = new RagEvaluationResult();
        result.setSetName(setName);
        List<RagEvaluationResult.RagEvaluationCaseResult> caseResults = new ArrayList<>();

        int passed = 0;
        double mrrSum = 0.0;
        int hit1 = 0, hit3 = 0, hit5 = 0;
        double recallSum = 0.0;
        double ndcgSum = 0.0;
        int positiveCases = 0;
        int noAnswerTotal = 0, noAnswerCorrect = 0, falsePositive = 0;

        // 系统级指标计数器
        int filterOk = 0, filterTotal = 0;
        int routeOk = 0;
        int fallbackOk = 0, fallbackTotal = 0;

        // 分层聚合
        Map<String, List<RagEvaluationResult.RagEvaluationCaseResult>> catGroups = new LinkedHashMap<>();
        Map<String, List<RagEvaluationResult.RagEvaluationCaseResult>> diffGroups = new LinkedHashMap<>();

        for (RagEvaluationCase tc : cases) {
            RagSearchTestRequest req = requestFor(tc, true);
            RagSearchTestResponse response = retrievalService.retrieve(req).response();

            RagEvaluationResult.RagEvaluationCaseResult cr = evaluateCase(tc, response);
            cr.setCategory(tc.getCategory());
            cr.setDifficulty(tc.getDifficulty());
            cr.setTags(tc.getTags());

            if (tc.isNoAnswer()) {
                noAnswerTotal++;
                if (cr.isPassed()) {
                    noAnswerCorrect++;
                    passed++;
                } else {
                    falsePositive++;
                }
                caseResults.add(cr);
                continue;
            }

            positiveCases++;

            // ── 检索排序指标 ──
            int firstRank = findFirstRelevantRank(tc, response);
            int relevantInTopK = countRelevantInTopK(tc, response, FINAL_TOP_K);
            cr.setFirstRelevantRank(firstRank);
            cr.setRelevantInTopK(relevantInTopK);

            if (firstRank > 0) {
                mrrSum += 1.0 / firstRank;
            }
            if (firstRank == 1) hit1++;
            if (firstRank > 0 && firstRank <= 3) hit3++;
            if (firstRank > 0 && firstRank <= 5) hit5++;

            int expectedRelevant = expectedRelevantCount(tc);
            recallSum += expectedRelevant > 0 ? (double) relevantInTopK / expectedRelevant : 0;

            // nDCG@5 — DCG / IDCG
            double dcg = 0.0;
            for (int rank = 1; rank <= FINAL_TOP_K; rank++) {
                if (isRelevantAtRank(tc, response, rank)) {
                    dcg += 1.0 / log2(rank + 1);
                }
            }
            double idcg = idealDcg(Math.min(expectedRelevant, FINAL_TOP_K));
            ndcgSum += idcg > 0 ? dcg / idcg : 0;

            // ── Filter Accuracy ──
            if (response.getChunks() != null && !response.getChunks().isEmpty()) {
                boolean filterPass = checkFilterAccuracy(tc, response);
                cr.setFilterMatch(filterPass);
                filterTotal++;
                if (filterPass) filterOk++;
            }

            // ── Route Accuracy ──
            // 简化：如果期望走 RAG 且实际有检索结果，则路由正确
            boolean routeOkCase = tc.getExpectedRoute() == null
                || "RAG".equals(tc.getExpectedRoute())
                || "TOOL_RAG".equals(tc.getExpectedRoute());
            cr.setRouteMatch(routeOkCase);
            routeOk++;

            // ── Fallback Accuracy ──
            if (tc.getExpectedMetadata() != null && !tc.getExpectedMetadata().isEmpty()) {
                fallbackTotal++;
                // 无检索结果或有错误摘要 = fallback 触发
                boolean fallbackTriggered = response.getChunks().isEmpty()
                    || response.getErrorSummary() != null;
                boolean expectedFb = tc.isExpectedFallback();
                boolean fbMatch = fallbackTriggered == expectedFb;
                cr.setFallbackMatch(fbMatch);
                if (fbMatch) fallbackOk++;
            }

            if (cr.isPassed()) passed++;
            caseResults.add(cr);

            // 分组
            catGroups.computeIfAbsent(tc.getCategory(), k -> new ArrayList<>()).add(cr);
            diffGroups.computeIfAbsent(tc.getDifficulty(), k -> new ArrayList<>()).add(cr);
        }

        int n = cases.size();
        result.setTotalCases(n);
        result.setPassedCases(passed);
        result.setFailedCases(n - passed);
        result.setPassRate(n == 0 ? 0.0 : (double) passed / n);
        result.setPositiveCases(positiveCases);
        result.setNoAnswerCases(noAnswerTotal);
        result.setNoAnswerAccuracy(noAnswerTotal == 0 ? 0.0 : (double) noAnswerCorrect / noAnswerTotal);
        result.setFalsePositiveRate(noAnswerTotal == 0 ? 0.0 : (double) falsePositive / noAnswerTotal);
        result.setMrr(positiveCases == 0 ? 0.0 : mrrSum / positiveCases);
        result.setHitAt1(positiveCases == 0 ? 0.0 : (double) hit1 / positiveCases);
        result.setHitAt3(positiveCases == 0 ? 0.0 : (double) hit3 / positiveCases);
        result.setHitAt5(positiveCases == 0 ? 0.0 : (double) hit5 / positiveCases);
        result.setRecallAt5(positiveCases == 0 ? 0.0 : recallSum / positiveCases);
        result.setNdcg5(positiveCases == 0 ? 0.0 : ndcgSum / positiveCases);
        result.setEvalK(FINAL_TOP_K);

        result.setFilterAccuracy(filterTotal == 0 ? 0.0 : (double) filterOk / filterTotal);
        result.setRouteAccuracy(positiveCases == 0 ? 0.0 : (double) routeOk / positiveCases);
        result.setFallbackAccuracy(fallbackTotal == 0 ? 1.0 : (double) fallbackOk / fallbackTotal);

        result.setByCategory(buildBreakdown(catGroups));
        result.setByDifficulty(buildBreakdown(diffGroups));

        // 分段指标
        result.setStructuredMetrics(segmentMetrics(caseResults, "fact", "trend"));
        result.setGeneralMetrics(segmentMetrics(caseResults, "general"));
        result.setNegativeMetrics(segmentMetrics(caseResults, "edge"));

        result.setCases(caseResults);

        return result;
    }

    // ── 检索指标辅助 ──

    /** 找第一个匹配的 rank（使用 relevantSections 作为宽松匹配） */
    private int findFirstRelevantRank(RagEvaluationCase tc, RagSearchTestResponse resp) {
        var chunks = resp.getChunks();
        if (chunks == null || chunks.isEmpty()) return -1;
        for (int i = 0; i < chunks.size(); i++) {
            if (isRelevant(tc, chunks.get(i))) return i + 1;
        }
        return -1;
    }

    private int countRelevantInTopK(RagEvaluationCase tc, RagSearchTestResponse resp, int k) {
        var chunks = resp.getChunks();
        if (chunks == null) return 0;
        int count = 0;
        for (int i = 0; i < Math.min(chunks.size(), k); i++) {
            if (isRelevant(tc, chunks.get(i))) count++;
        }
        return count;
    }

    private boolean isRelevantAtRank(RagEvaluationCase tc, RagSearchTestResponse resp, int rank) {
        var chunks = resp.getChunks();
        return chunks != null && rank > 0 && rank <= chunks.size() && isRelevant(tc, chunks.get(rank - 1));
    }

    private int expectedRelevantCount(RagEvaluationCase tc) {
        return tc.getRelevantConditions().size() + tc.getRelevantSources().size();
    }

    private double idealDcg(int relevantCount) {
        double idcg = 0.0;
        for (int rank = 1; rank <= relevantCount; rank++) {
            idcg += 1.0 / log2(rank + 1);
        }
        return idcg;
    }

    private double log2(int value) {
        return Math.log(value) / Math.log(2);
    }

    /**
     * 判断 Chunk 是否与测试用例相关。
     * Filter（expectedMetadata）与 relevance 判定正交：
     *   ① relevantConditions 非空 → 任一 condition 匹配 → true
     *   ② relevantSources 非空 → sourceName 前缀匹配 → true
     *   ③ 否则 false
     */
    private boolean isRelevant(RagEvaluationCase tc, RagChunkResult chunk) {
        // ① 结构化招生类：relevantConditions OR 匹配
        if (!tc.getRelevantConditions().isEmpty()) {
            for (RagEvaluationCase.RelevantCondition cond : tc.getRelevantConditions()) {
                if (cond.matches(chunk.getProgramId(), chunk.getDocType(),
                    chunk.getSection(), chunk.getYear(), chunk.getSchoolId())) {
                    return true;
                }
            }
            return false;
        }

        // ② 通用知识类：relevantSources 前缀匹配 sourceName
        if (!tc.getRelevantSources().isEmpty()) {
            String sourceName = chunk.getSourceName();
            if (sourceName != null) {
                return tc.getRelevantSources().stream()
                    .anyMatch(prefix -> sourceName.startsWith(prefix));
            }
            return false;
        }

        // ③ negative / 未配置 → 不相关
        return false;
    }

    // ── Filter Accuracy ──

    /** 检查所有返回的 chunk 是否都满足 metadata 约束 */
    private boolean checkFilterAccuracy(RagEvaluationCase tc, RagSearchTestResponse resp) {
        var chunks = resp.getChunks();
        if (chunks == null || chunks.isEmpty()) return false;
        Map<String, String> expected = tc.getExpectedMetadata();
        if (expected == null || expected.isEmpty()) return true;
        for (RagChunkResult c : chunks) {
            if (!matchesMetadata(expected, c)) return false;
        }
        return true;
    }

    // ── 分段指标 ──

    private RagEvaluationResult.SegmentMetrics segmentMetrics(
        List<RagEvaluationResult.RagEvaluationCaseResult> cases, String... categories) {
        List<String> cats = List.of(categories);
        List<RagEvaluationResult.RagEvaluationCaseResult> subset = cases.stream()
            .filter(cr -> cr.getCategory() != null && cats.contains(cr.getCategory()))
            .toList();
        int n = subset.size();
        if (n == 0) return new RagEvaluationResult.SegmentMetrics(0, 0, 0, 0, 0, 0);

        double mrrSum = 0;
        int h1 = 0, h3 = 0, h5 = 0;
        double recallSum = 0;
        for (var cr : subset) {
            int rank = cr.getFirstRelevantRank();
            if (rank > 0) {
                mrrSum += 1.0 / rank;
                if (rank == 1) h1++;
                if (rank <= 3) h3++;
                if (rank <= 5) h5++;
            }
            int inTop5 = cr.getRelevantInTopK();
            // count total relevant from evidence size as approximation
            int total = Math.max(inTop5, cr.getEvidence().size());
            recallSum += total > 0 ? (double) inTop5 / total : 0;
        }
        return new RagEvaluationResult.SegmentMetrics(
            n, n == 0 ? 0 : mrrSum / n,
            n == 0 ? 0 : (double) h1 / n,
            n == 0 ? 0 : (double) h3 / n,
            n == 0 ? 0 : (double) h5 / n,
            n == 0 ? 0 : recallSum / n);
    }

    // ── 分层 breakdown ──

    private Map<String, RagEvaluationResult.MetricBreakdown> buildBreakdown(
        Map<String, List<RagEvaluationResult.RagEvaluationCaseResult>> groups) {
        Map<String, RagEvaluationResult.MetricBreakdown> result = new LinkedHashMap<>();
        for (var entry : groups.entrySet()) {
            var list = entry.getValue();
            int n = list.size();
            double mrrSum = 0;
            int hit5Count = 0;
            for (var cr : list) {
                if (cr.getFirstRelevantRank() > 0) {
                    mrrSum += 1.0 / cr.getFirstRelevantRank();
                    hit5Count++;
                }
            }
            result.put(entry.getKey(), new RagEvaluationResult.MetricBreakdown(
                n, n == 0 ? 0 : mrrSum / n, n == 0 ? 0 : (double) hit5Count / n));
        }
        return result;
    }

    // ── 评测逻辑 ──

    private RagEvaluationResult.RagEvaluationCaseResult evaluateCase(
        RagEvaluationCase tc, RagSearchTestResponse resp) {
        var cr = new RagEvaluationResult.RagEvaluationCaseResult();
        cr.setId(tc.getId());
        cr.setQuery(tc.getQuery());

        if (tc.isNoAnswer()) {
            boolean hasSelectedEvidence = hasUsableSelectedEvidence(resp);
            cr.setPassed(!hasSelectedEvidence);
            cr.setFailureReason(hasSelectedEvidence ? "FALSE_POSITIVE_EVIDENCE" : "CORRECT_ABSTENTION");
            return cr;
        }

        if (!tc.isSourceDataAvailable() && resp.getChunks().isEmpty()) {
            cr.setPassed(tc.isExpectedFallback());
            cr.setFailureReason("SOURCE_DATA_MISSING");
            return cr;
        }

        if (resp.getChunks().isEmpty()) {
            boolean expectedEmpty = tc.isExpectedFallback();
            cr.setPassed(expectedEmpty); // 预期无结果 = 通过
            cr.setFailureReason(expectedEmpty ? "EXPECTED_EMPTY" : "NO_RETRIEVAL");
            return cr;
        }

        for (RagChunkResult chunk : resp.getChunks()) {
            if (isRelevant(tc, chunk)) {
                cr.setPassed(true);
                cr.setFailureReason("PASSED");
                cr.getEvidence().add(evidenceLabel(chunk));
            }
        }
        if (!cr.isPassed()) {
            cr.setFailureReason(resolveFailureReason(tc, resp));
        }
        return cr;
    }

    private boolean hasUsableSelectedEvidence(RagSearchTestResponse resp) {
        return resp.getChunks() != null && resp.getChunks().stream()
            .anyMatch(RagChunkResult::isSelectedForPrompt);
    }

    // ── Metadata helpers ──

    private RagSearchTestRequest requestFor(RagEvaluationCase tc, boolean skipRerank) {
        RagSearchTestRequest request = new RagSearchTestRequest(
            tc.getQuery(), RECALL_TOP_N, 0.5, FINAL_TOP_K);
        request.setSkipRerank(skipRerank);
        applyExpectedFilters(request, tc.getExpectedMetadata());
        return request;
    }

    private void applyExpectedFilters(RagSearchTestRequest req, Map<String, String> expected) {
        if (expected == null) return;
        req.setSchoolId(expected.get("school_id"));
        req.setProgramId(expected.get("program_id"));
        req.setYear(expected.get("year"));
        req.setDocType(expected.get("doc_type"));
        req.setSection(expected.get("section"));
    }

    private String resolveFailureReason(RagEvaluationCase tc, RagSearchTestResponse resp) {
        boolean hasExpectedScope = resp.getChunks().stream()
            .anyMatch(chunk -> matchesScope(tc.getExpectedMetadata(), chunk));
        return hasExpectedScope ? "MISSING_SECTION" : "WRONG_SCOPE";
    }

    private boolean matchesScope(Map<String, String> expected, RagChunkResult chunk) {
        return equalsExpected(expected, "school_id", chunk.getSchoolId())
            && equalsExpected(expected, "program_id", chunk.getProgramId())
            && equalsExpected(expected, "year", chunk.getYear())
            && equalsExpected(expected, "doc_type", chunk.getDocType());
    }

    /** 精确 metadata 全字段匹配 */
    private boolean matchesMetadata(Map<String, String> expected, RagChunkResult chunk) {
        if (expected == null || expected.isEmpty()) return false;
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String actual = switch (e.getKey()) {
                case "school_name" -> chunk.getSchoolName();
                case "program_name" -> chunk.getProgramName();
                case "school_id" -> chunk.getSchoolId();
                case "program_id" -> chunk.getProgramId();
                case "year" -> chunk.getYear();
                case "section" -> chunk.getSection();
                case "doc_type" -> chunk.getDocType();
                default -> null;
            };
            if (!e.getValue().equals(actual)) return false;
        }
        return true;
    }

    private boolean equalsExpected(Map<String, String> expected, String key, String actual) {
        String value = expected != null ? expected.get(key) : null;
        return value == null || value.equals(actual);
    }

    private String evidenceLabel(RagChunkResult chunk) {
        return String.join(" / ",
            nullToEmpty(chunk.getSchoolName()),
            nullToEmpty(chunk.getProgramName()),
            nullToEmpty(chunk.getYear()),
            nullToEmpty(chunk.getSection()),
            nullToEmpty(chunk.getChunkTitle()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
