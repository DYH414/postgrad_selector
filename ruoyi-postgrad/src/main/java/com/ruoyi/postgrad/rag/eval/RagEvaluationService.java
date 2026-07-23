package com.ruoyi.postgrad.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);
    private static final int RECALL_TOP_N = 20;
    private static final int FINAL_TOP_K = 5;
    private static final String ACTIVE_ALIAS = "postgrad_rag_active";
    private static final Path DEFAULT_REPORT_DIRECTORY = Path.of("target", "rag-eval");
    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
        .withZone(ZoneOffset.UTC);

    private final RagRetrievalService retrievalService;
    private final RagEvaluationDatasetLoader datasetLoader;
    private final RagCollectionManager collectionManager;
    private final Path reportDirectory;

    public RagEvaluationService(RagRetrievalService retrievalService) {
        this(retrievalService, new RagEvaluationDatasetLoader(), null, DEFAULT_REPORT_DIRECTORY);
    }

    @Autowired
    public RagEvaluationService(RagRetrievalService retrievalService, RagCollectionManager collectionManager) {
        this(retrievalService, new RagEvaluationDatasetLoader(), collectionManager, DEFAULT_REPORT_DIRECTORY);
    }

    RagEvaluationService(RagRetrievalService retrievalService,
                         RagEvaluationDatasetLoader datasetLoader,
                         RagCollectionManager collectionManager,
                         Path reportDirectory) {
        this.retrievalService = retrievalService;
        this.datasetLoader = datasetLoader;
        this.collectionManager = collectionManager;
        this.reportDirectory = reportDirectory;
    }

    /**
     * Runs the frozen ranking set in vector-only and vector-rerank modes, while
     * calculating abstention separately from the safety set.
     */
    public RagEvaluationRunReport runFrozenV1Ablation() {
        return runFrozenAblation(
            "frozen-v1", datasetLoader.loadFrozenV1(), datasetLoader.verifyFrozenV1Checksum(),
            "safety-v1", datasetLoader.loadSafetyV1(), datasetLoader.verifySafetyV1Checksum());
    }

    /**
     * Runs the reviewed V3 corpus benchmark. Positive ranking metrics and
     * abstention metrics remain separate so negative cases cannot dilute MRR.
     */
    public RagEvaluationRunReport runFrozenV3Ablation() {
        return runFrozenAblation(
            "frozen-v3", datasetLoader.loadFrozenV3(), datasetLoader.verifyFrozenV3Checksum(),
            "safety-v3", datasetLoader.loadSafetyV3(), datasetLoader.verifySafetyV3Checksum());
    }

    private RagEvaluationRunReport runFrozenAblation(
        String rankingDataset, List<RagEvaluationCase> frozenCases, String frozenChecksum,
        String safetyDataset, List<RagEvaluationCase> safetyCases, String safetyChecksum) {
        Map<RagEvaluationMode, RagEvaluationResult> results = new EnumMap<>(RagEvaluationMode.class);
        Map<RagEvaluationMode, RagEvaluationResult> safetyResults =
            new EnumMap<>(RagEvaluationMode.class);
        Map<RagEvaluationMode, RagEvaluationRunReport.AbstentionMetrics> abstention =
            new EnumMap<>(RagEvaluationMode.class);
        Map<RagEvaluationMode, RagEvaluationRunReport.LatencyMetrics> latencies =
            new EnumMap<>(RagEvaluationMode.class);

        for (RagEvaluationMode mode : RagEvaluationMode.values()) {
            List<Long> modeLatencies = new ArrayList<>();
            RagEvaluationResult rankingResult = run(rankingDataset + "-" + mode.name().toLowerCase(),
                frozenCases, mode.isSkipRerank(), modeLatencies);
            RagEvaluationResult safetyResult = run(safetyDataset + "-" + mode.name().toLowerCase(),
                safetyCases, mode.isSkipRerank(), modeLatencies);
            results.put(mode, rankingResult);
            safetyResults.put(mode, safetyResult);
            RagEvaluationRunReport.AbstentionMetrics abstentionMetrics =
                new RagEvaluationRunReport.AbstentionMetrics(
                safetyResult.getNoAnswerCases(), safetyResult.getPassedCases(),
                safetyResult.getNoAnswerAccuracy(), safetyResult.getFalsePositiveRate());
            abstentionMetrics.setTechnicalErrorCases((int) safetyResult.getCases().stream()
                .filter(item -> item.getTechnicalErrorCode() != null).count());
            abstention.put(mode, abstentionMetrics);
            latencies.put(mode, latencyMetrics(modeLatencies));
        }

        RagEvaluationRunReport report = new RagEvaluationRunReport();
        report.setResults(results);
        report.setSafetyResults(safetyResults);
        report.setAbstentionMetrics(abstention);
        report.setLatencyMetrics(latencies);
        report.setPairedComparison(pairedComparison(
            results.get(RagEvaluationMode.VECTOR_ONLY),
            results.get(RagEvaluationMode.VECTOR_RERANK)));
        report.setRunMetadata(buildRuntimeMetadata(
            rankingDataset, frozenCases.size(), frozenChecksum,
            safetyDataset, safetyCases.size(), safetyChecksum));
        writeReport(report, rankingDataset);
        return report;
    }

    static RagEvaluationRunReport.PairedComparison pairedComparison(
        RagEvaluationResult vectorOnly, RagEvaluationResult reranked) {
        Map<String, Integer> baselineRanks = new HashMap<>();
        for (RagEvaluationResult.RagEvaluationCaseResult item : vectorOnly.getCases()) {
            baselineRanks.put(item.getId(), item.getFirstRelevantRank());
        }
        int compared = 0;
        int improved = 0;
        int tied = 0;
        int regressed = 0;
        for (RagEvaluationResult.RagEvaluationCaseResult item : reranked.getCases()) {
            Integer baselineRank = baselineRanks.get(item.getId());
            if (baselineRank == null) continue;
            compared++;
            int comparison = compareRanks(item.getFirstRelevantRank(), baselineRank);
            if (comparison < 0) improved++;
            else if (comparison > 0) regressed++;
            else tied++;
        }
        return new RagEvaluationRunReport.PairedComparison(
            compared, improved, tied, regressed);
    }

    private static int compareRanks(int rerankedRank, int baselineRank) {
        int normalizedReranked = rerankedRank < 1 ? Integer.MAX_VALUE : rerankedRank;
        int normalizedBaseline = baselineRank < 1 ? Integer.MAX_VALUE : baselineRank;
        return Integer.compare(normalizedReranked, normalizedBaseline);
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

    public RagEvaluationResult runDataset(String setName, List<RagEvaluationCase> cases,
                                          RagEvaluationMode mode) {
        return runDataset(setName, cases, mode, RECALL_TOP_N, FINAL_TOP_K);
    }

    public RagEvaluationResult runDataset(String setName, List<RagEvaluationCase> cases,
                                          RagEvaluationMode mode, int recallTopN, int finalTopK) {
        if (mode == null) {
            throw new IllegalArgumentException("Evaluation mode is required");
        }
        if (recallTopN < 1 || finalTopK < 1 || finalTopK > recallTopN) {
            throw new IllegalArgumentException("Invalid evaluation retrieval parameters");
        }
        return run(setName, cases, mode.isSkipRerank(), null, recallTopN, finalTopK);
    }

    private RagEvaluationResult run(String setName, List<RagEvaluationCase> cases) {
        return run(setName, cases, true, null);
    }

    private RagEvaluationResult run(String setName, List<RagEvaluationCase> cases,
                                    boolean skipRerank, List<Long> latencies) {
        return run(setName, cases, skipRerank, latencies, RECALL_TOP_N, FINAL_TOP_K);
    }

    private RagEvaluationResult run(String setName, List<RagEvaluationCase> cases,
                                    boolean skipRerank, List<Long> latencies,
                                    int recallTopN, int finalTopK) {
        RagEvaluationResult result = new RagEvaluationResult();
        result.setSetName(setName);
        List<RagEvaluationResult.RagEvaluationCaseResult> caseResults = new ArrayList<>();

        int passed = 0;
        double mrrSum = 0.0;
        int hit1 = 0, hit3 = 0, hit5 = 0;
        double recallSum = 0.0;
        double ndcgSum = 0.0;
        double precisionSum = 0.0;
        int positiveCases = 0;
        int noAnswerTotal = 0, noAnswerCorrect = 0, falsePositive = 0;

        // 系统级指标计数器
        int filterOk = 0, filterTotal = 0, filterEmpty = 0;
        int fallbackOk = 0, fallbackTotal = 0;

        // 分层聚合
        Map<String, List<RagEvaluationResult.RagEvaluationCaseResult>> catGroups = new LinkedHashMap<>();
        Map<String, List<RagEvaluationResult.RagEvaluationCaseResult>> diffGroups = new LinkedHashMap<>();

        for (RagEvaluationCase tc : cases) {
            RagSearchTestRequest req = requestFor(tc, skipRerank, recallTopN, finalTopK);
            RagSearchTestResponse response;
            try {
                response = retrievalService.retrieve(req).response();
            } catch (RuntimeException ex) {
                RagEvaluationResult.RagEvaluationCaseResult failed = new RagEvaluationResult.RagEvaluationCaseResult();
                failed.setId(tc.getId());
                failed.setQuery(tc.getQuery());
                failed.setPassed(false);
                failed.setFailureReason("TECHNICAL_ERROR");
                failed.setTechnicalErrorCode("RETRIEVAL_FAILED");
                failed.setTechnicalErrorSummary(safeError(ex));
                failed.setCategory(tc.getCategory());
                failed.setDifficulty(tc.getDifficulty());
                failed.setTags(tc.getTags());
                caseResults.add(failed);
                catGroups.computeIfAbsent(tc.getCategory(), k -> new ArrayList<>()).add(failed);
                diffGroups.computeIfAbsent(tc.getDifficulty(), k -> new ArrayList<>()).add(failed);
                if (tc.isNoAnswer()) noAnswerTotal++; else positiveCases++;
                continue;
            }
            if (latencies != null && response.getLatency() != null) {
                latencies.add(response.getLatency().getTotalMs());
            }

            RagEvaluationResult.RagEvaluationCaseResult cr = evaluateCase(tc, response);
            cr.setCandidates(response.getChunks());
            cr.setLatencyMs(response.getLatency() == null ? 0L : response.getLatency().getTotalMs());
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
            int relevantInTopK = countRelevantInTopK(tc, response, finalTopK);
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
            double dcg = discountedGainAtK(tc, response, finalTopK);
            double idcg = idealDcg(Math.min(expectedRelevant, finalTopK));
            ndcgSum += idcg > 0 ? dcg / idcg : 0;

            // ── Precision@K ──
            precisionSum += relevantInTopK / (double) finalTopK;

            // ── Filter Accuracy ──
            if (tc.getExpectedMetadata() != null && !tc.getExpectedMetadata().isEmpty()) {
                if (response.getChunks() == null || response.getChunks().isEmpty()) {
                    filterEmpty++;
                } else {
                    boolean filterPass = checkFilterAccuracy(tc, response);
                    cr.setFilterMatch(filterPass);
                    filterTotal++;
                    if (filterPass) filterOk++;
                }
            }

            // This service starts after routing and evaluates retrieval only.
            // Route quality must be measured by a separate Agent route dataset.
            cr.setRouteMatch(null);

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
        result.setPrecisionAt5(positiveCases == 0 ? 0.0 : precisionSum / positiveCases);
        result.setEvalK(finalTopK);

        result.setFilterAccuracy(filterTotal == 0 ? 0.0 : (double) filterOk / filterTotal);
        result.setFilterEvaluatedCases(filterTotal);
        result.setFilterEmptyCases(filterEmpty);
        result.setRouteAccuracy(0.0);
        result.setRouteEvaluated(false);
        result.setFallbackAccuracy(fallbackTotal == 0 ? 1.0 : (double) fallbackOk / fallbackTotal);

        result.setByCategory(buildBreakdown(catGroups));
        result.setByDifficulty(buildBreakdown(diffGroups));

        // 分段指标
        result.setStructuredMetrics(segmentMetrics(caseResults, "school"));
        result.setGeneralMetrics(segmentMetrics(caseResults, "general"));
        result.setNegativeMetrics(segmentMetrics(caseResults, "edge"));

        result.setCases(caseResults);

        // 截断 contentPreview 防止 JSON 响应过大导致 SocketTimeout
        truncatePreviews(caseResults);

        return result;
    }

    private static final int MAX_PREVIEW_LEN = 200;

    private static void truncatePreviews(List<RagEvaluationResult.RagEvaluationCaseResult> cases) {
        for (RagEvaluationResult.RagEvaluationCaseResult c : cases) {
            if (c.getCandidates() == null) continue;
            for (RagChunkResult chunk : c.getCandidates()) {
                String raw = chunk.getContentPreview();
                if (raw != null && raw.length() > MAX_PREVIEW_LEN) {
                    chunk.setContentPreview(raw.substring(0, MAX_PREVIEW_LEN) + "…");
                }
            }
        }
    }

    private RagEvaluationRunReport.LatencyMetrics latencyMetrics(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return new RagEvaluationRunReport.LatencyMetrics(0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Long::compareTo);
        return new RagEvaluationRunReport.LatencyMetrics(sorted.size(),
            nearestRank(sorted, 0.50), nearestRank(sorted, 0.95));
    }

    private long nearestRank(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    private Map<String, String> buildRuntimeMetadata(
        String frozenDataset, int frozenCaseCount, String frozenChecksum,
        String safetyDataset, int safetyCaseCount, String safetyChecksum) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("runAtUtc", Instant.now().toString());
        metadata.put("frozenDataset", frozenDataset);
        metadata.put("frozenCaseCount", Integer.toString(frozenCaseCount));
        metadata.put("frozenChecksum", frozenChecksum);
        metadata.put("safetyDataset", safetyDataset);
        metadata.put("safetyCaseCount", Integer.toString(safetyCaseCount));
        metadata.put("safetyChecksum", safetyChecksum);
        metadata.put("recallTopN", Integer.toString(RECALL_TOP_N));
        metadata.put("finalTopK", Integer.toString(FINAL_TOP_K));
        metadata.put("minScore", "0.5");
        metadata.put("relevanceGroundTruth", "content_hash");
        metadata.put("qdrantAlias", ACTIVE_ALIAS);
        appendQdrantMetadata(metadata);
        appendGitMetadata(metadata);
        return metadata;
    }

    private void appendQdrantMetadata(Map<String, String> metadata) {
        if (collectionManager == null) {
            metadata.put("qdrantCollection", "unknown");
            metadata.put("qdrantCollectionReason", "RagCollectionManager is unavailable");
            return;
        }
        try {
            String collection = collectionManager.resolveAlias(ACTIVE_ALIAS);
            if (collection == null || collection.isBlank()) {
                metadata.put("qdrantCollection", "unknown");
                metadata.put("qdrantCollectionReason", "Alias could not be resolved");
                return;
            }
            metadata.put("qdrantCollection", collection);
        } catch (RuntimeException e) {
            metadata.put("qdrantCollection", "unknown");
            metadata.put("qdrantCollectionReason", e.getMessage());
        }
    }

    private void appendGitMetadata(Map<String, String> metadata) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                metadata.put("gitCommit", "unknown");
                metadata.put("gitCommitReason", "git rev-parse timed out");
                return;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() == 0 && !output.isBlank()) {
                metadata.put("gitCommit", output);
            } else {
                metadata.put("gitCommit", "unknown");
                metadata.put("gitCommitReason", output.isBlank() ? "git rev-parse failed" : output);
            }
            appendGitDirtyMetadata(metadata);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            metadata.put("gitCommit", "unknown");
            metadata.put("gitCommitReason", e.getMessage());
        }
    }

    private void writeReport(RagEvaluationRunReport report, String datasetName) {
        try {
            Files.createDirectories(reportDirectory);
            Path reportPath = createReportPath(datasetName);
            report.setReportPath(reportPath.toString());
            Path markdownPath = reportPath.resolveSibling(
                reportPath.getFileName().toString().replaceFirst("\\.json$", ".md"));
            report.getRunMetadata().put("markdownReportPath", markdownPath.toString());
            Files.writeString(markdownPath, markdown(report), StandardCharsets.UTF_8);
            Files.writeString(reportPath, JSON.toJSONString(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write RAG evaluation report", e);
        }
    }

    private void appendGitDirtyMetadata(Map<String, String> metadata)
        throws IOException, InterruptedException {
        Path statusOutput = Files.createTempFile("rag-eval-git-status-", ".txt");
        try {
            Process dirty = new ProcessBuilder("git", "status", "--porcelain")
                .redirectErrorStream(true)
                .redirectOutput(statusOutput.toFile())
                .start();
            if (dirty.waitFor(15, TimeUnit.SECONDS) && dirty.exitValue() == 0) {
                metadata.put("gitDirty", Boolean.toString(Files.size(statusOutput) > 0));
            } else {
                dirty.destroyForcibly();
                metadata.put("gitDirty", "unknown");
                metadata.put("gitDirtyReason", "git status timed out or failed");
            }
        } finally {
            Files.deleteIfExists(statusOutput);
        }
    }

    private String markdown(RagEvaluationRunReport report) {
        RagEvaluationResult vector = report.getResults().get(RagEvaluationMode.VECTOR_ONLY);
        RagEvaluationResult rerank = report.getResults().get(RagEvaluationMode.VECTOR_RERANK);
        RagEvaluationRunReport.PairedComparison paired = report.getPairedComparison();
        RagEvaluationRunReport.AbstentionMetrics safety =
            report.getAbstentionMetrics().get(RagEvaluationMode.VECTOR_RERANK);
        RagEvaluationRunReport.LatencyMetrics vectorLatency =
            report.getLatencyMetrics().get(RagEvaluationMode.VECTOR_ONLY);
        RagEvaluationRunReport.LatencyMetrics rerankLatency =
            report.getLatencyMetrics().get(RagEvaluationMode.VECTOR_RERANK);
        return """
            # RAG Frozen Evaluation

            ## Reproducibility

            - Dataset: %s (%d cases)
            - Dataset SHA-256: `%s`
            - Safety set: %s (%d cases)
            - Safety SHA-256: `%s`
            - Qdrant alias: `%s`
            - Qdrant collection: `%s`
            - Git commit: `%s`
            - Dirty worktree: %s

            ## Retrieval Ablation

            | Mode | MRR | Hit@1 | Hit@3 | Hit@5 | nDCG@5 | P50 | P95 |
            | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
            | Vector only | %.4f | %.2f%% | %.2f%% | %.2f%% | %.4f | %d ms | %d ms |
            | Vector + BGE Rerank | %.4f | %.2f%% | %.2f%% | %.2f%% | %.4f | %d ms | %d ms |

            Paired cases: improved %d, tied %d, regressed %d (total %d).

            ## Safety

            - Cases: %d
            - No-answer accuracy: %.2f%%
            - False-positive rate: %.2f%%
            - Technical errors: %d

            Ranking metrics are computed only from reviewed positive cases. Relevance ground truth
            uses exact `content_hash`; safety metrics are reported separately.
            """.formatted(
            report.getRunMetadata().get("frozenDataset"), vector.getTotalCases(),
            report.getRunMetadata().get("frozenChecksum"),
            report.getRunMetadata().get("safetyDataset"), safety.getTotalCases(),
            report.getRunMetadata().get("safetyChecksum"),
            report.getRunMetadata().get("qdrantAlias"),
            report.getRunMetadata().get("qdrantCollection"),
            report.getRunMetadata().get("gitCommit"),
            report.getRunMetadata().getOrDefault("gitDirty", "unknown"),
            vector.getMrr(), vector.getHitAt1() * 100, vector.getHitAt3() * 100,
            vector.getHitAt5() * 100, vector.getNdcg5(),
            vectorLatency.getP50Ms(), vectorLatency.getP95Ms(),
            rerank.getMrr(), rerank.getHitAt1() * 100, rerank.getHitAt3() * 100,
            rerank.getHitAt5() * 100, rerank.getNdcg5(),
            rerankLatency.getP50Ms(), rerankLatency.getP95Ms(),
            paired.getImprovedCases(), paired.getTiedCases(), paired.getRegressedCases(),
            paired.getComparedCases(),
            safety.getTotalCases(), safety.getNoAnswerAccuracy() * 100,
            safety.getFalsePositiveRate() * 100, safety.getTechnicalErrorCases());
    }

    private Path createReportPath(String datasetName) throws IOException {
        String baseName = REPORT_TIMESTAMP.format(Instant.now()) + "-" + datasetName;
        for (int suffix = 0; ; suffix++) {
            String filename = baseName + (suffix == 0 ? "" : "-" + suffix) + ".json";
            Path candidate = reportDirectory.resolve(filename);
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Keep an immutable report for every run, even within the same millisecond.
            }
        }
    }

    // ── 检索指标辅助 ──

    /** 找第一个匹配的 rank（使用 relevantSections 作为宽松匹配） */
    private int findFirstRelevantRank(RagEvaluationCase tc, RagSearchTestResponse resp) {
        var chunks = resp.getChunks();
        if (chunks == null || chunks.isEmpty()) return -1;
        for (int i = 0; i < chunks.size(); i++) {
            RagChunkResult chunk = chunks.get(i);
            if (chunk.isSelectedForPrompt() && isRelevant(tc, chunk)) return i + 1;
        }
        return -1;
    }

    private int countRelevantInTopK(RagEvaluationCase tc, RagSearchTestResponse resp, int k) {
        var chunks = resp.getChunks();
        if (chunks == null) return 0;
        Set<String> matchedUnits = new HashSet<>();
        for (int i = 0; i < Math.min(chunks.size(), k); i++) {
            RagChunkResult chunk = chunks.get(i);
            String unit = chunk.isSelectedForPrompt() ? relevanceUnit(tc, chunk) : null;
            if (unit != null) matchedUnits.add(unit);
        }
        return matchedUnits.size();
    }

    private double discountedGainAtK(RagEvaluationCase tc, RagSearchTestResponse resp, int k) {
        var chunks = resp.getChunks();
        if (chunks == null || chunks.isEmpty()) return 0.0;
        Set<String> matchedUnits = new HashSet<>();
        double dcg = 0.0;
        for (int index = 0; index < Math.min(chunks.size(), k); index++) {
            RagChunkResult chunk = chunks.get(index);
            String unit = chunk.isSelectedForPrompt() ? relevanceUnit(tc, chunk) : null;
            if (unit != null && matchedUnits.add(unit)) {
                dcg += 1.0 / log2(index + 2);
            }
        }
        return dcg;
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
        return relevanceUnit(tc, chunk) != null;
    }

    private String relevanceUnit(RagEvaluationCase tc, RagChunkResult chunk) {
        // ① 结构化招生类：relevantConditions OR 匹配
        if (!tc.getRelevantConditions().isEmpty()) {
            for (int index = 0; index < tc.getRelevantConditions().size(); index++) {
                RagEvaluationCase.RelevantCondition cond = tc.getRelevantConditions().get(index);
                if (cond.matches(chunk.getContentHash(), chunk.getProgramId(), chunk.getDocType(),
                    chunk.getSection(), chunk.getYear(), chunk.getSchoolId())) {
                    return "condition:" + index;
                }
            }
            return null;
        }

        // ② 通用知识类：relevantSources 前缀匹配 sourceName
        if (!tc.getRelevantSources().isEmpty()) {
            String sourceName = chunk.getSourceName();
            if (sourceName != null) {
                for (String prefix : tc.getRelevantSources()) {
                    if (sourceName.startsWith(prefix)) {
                        return "source:" + prefix;
                    }
                }
            }
            return null;
        }

        // ③ negative / 未配置 → 不相关
        return null;
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
        if (n == 0) return new RagEvaluationResult.SegmentMetrics(0, 0, 0, 0, 0, 0, 0);

        double mrrSum = 0;
        int h1 = 0, h3 = 0, h5 = 0;
        double recallSum = 0;
        double precisionSum = 0;
        int evalK = 5;
        for (var cr : subset) {
            int rank = cr.getFirstRelevantRank();
            if (rank > 0) {
                mrrSum += 1.0 / rank;
                if (rank == 1) h1++;
                if (rank <= 3) h3++;
                if (rank <= 5) h5++;
            }
            int inTop5 = cr.getRelevantInTopK();
            precisionSum += (double) inTop5 / evalK;
            // count total relevant from evidence size as approximation
            int total = Math.max(inTop5, cr.getEvidence().size());
            recallSum += total > 0 ? (double) inTop5 / total : 0;
        }
        return new RagEvaluationResult.SegmentMetrics(
            n, n == 0 ? 0 : mrrSum / n,
            n == 0 ? 0 : (double) h1 / n,
            n == 0 ? 0 : (double) h3 / n,
            n == 0 ? 0 : (double) h5 / n,
            n == 0 ? 0 : recallSum / n,
            n == 0 ? 0 : precisionSum / n);
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
            double precisionSum = 0;
            int evalK = 5;
            for (var cr : list) {
                if (cr.getFirstRelevantRank() > 0) {
                    mrrSum += 1.0 / cr.getFirstRelevantRank();
                    hit5Count++;
                }
                precisionSum += (double) cr.getRelevantInTopK() / evalK;
            }
            result.put(entry.getKey(), new RagEvaluationResult.MetricBreakdown(
                n, n == 0 ? 0 : mrrSum / n, n == 0 ? 0 : (double) hit5Count / n,
                n == 0 ? 0 : precisionSum / n));
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
            if (chunk.isSelectedForPrompt() && isRelevant(tc, chunk)) {
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

    private RagSearchTestRequest requestFor(RagEvaluationCase tc, boolean skipRerank,
                                            int recallTopN, int finalTopK) {
        RagSearchTestRequest request = new RagSearchTestRequest(
            tc.getQuery(), recallTopN, 0.5, finalTopK);
        request.setSkipRerank(skipRerank);
        applyExpectedFilters(request, tc.getExpectedMetadata());
        return request;
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        String value = message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
        return value.substring(0, Math.min(value.length(), 512));
    }

    private void applyExpectedFilters(RagSearchTestRequest req, Map<String, String> expected) {
        if (expected == null) return;
        req.setSchoolId(expected.get("school_id"));
        req.setSchoolName(expected.get("school_name"));
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
            && equalsExpected(expected, "school_name", chunk.getSchoolName())
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
