package com.ruoyi.postgrad.rag.eval.workbench.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.eval.RagEvaluationCase;
import com.ruoyi.postgrad.rag.eval.RagEvaluationMode;
import com.ruoyi.postgrad.rag.eval.RagEvaluationResult;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseResultEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalFailureCategory;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class RagEvalRunService {
    private static final String ACTIVE_ALIAS = "postgrad_rag_active";
    private final RagEvaluationWorkbenchMapper mapper;
    private final RagEvalDatasetService datasetService;
    private final RagEvaluationService evaluationService;
    private final RagCollectionManager collectionManager;
    private final ThreadPoolTaskExecutor executor;
    private final String embeddingModel;
    private final String rerankModel;

    public RagEvalRunService(RagEvaluationWorkbenchMapper mapper,
                             RagEvalDatasetService datasetService,
                             RagEvaluationService evaluationService,
                             RagCollectionManager collectionManager,
                             @Qualifier("ragEvaluationExecutor") ThreadPoolTaskExecutor executor,
                             @Value("${app.ai.rag.embedding.model-name:BAAI/bge-m3}") String embeddingModel,
                             @Value("${app.ai.rag.rerank.model-name:BAAI/bge-reranker-v2-m3}") String rerankModel) {
        this.mapper = mapper;
        this.datasetService = datasetService;
        this.evaluationService = evaluationService;
        this.collectionManager = collectionManager;
        this.executor = executor;
        this.embeddingModel = embeddingModel.replace(" ", "");
        this.rerankModel = rerankModel;
    }

    @PostConstruct
    public void recoverInterruptedRuns() {
        mapper.markRunningInterrupted();
    }

    public List<RagEvalRun> list(Long datasetId, String status, String runIdKeyword, String datasetChecksum) {
        return mapper.selectRuns(datasetId, status, StringUtils.trimToNull(runIdKeyword),
            StringUtils.trimToNull(datasetChecksum));
    }
    public RagEvalRun get(String runId) {
        RagEvalRun run = mapper.selectRunById(runId);
        if (run == null) throw new ServiceException("Evaluation run not found");
        return run;
    }
    public List<RagEvalCaseResultEntity> results(String runId) { get(runId); return mapper.selectCaseResultsByRunId(runId); }

    public String start(Long datasetId, List<RagEvaluationMode> requestedModes,
                        Map<String, Object> parameters, String operator) {
        RagEvalDataset dataset = datasetService.get(datasetId);
        List<RagEvaluationMode> modes = requestedModes == null || requestedModes.isEmpty()
            ? List.copyOf(EnumSet.allOf(RagEvaluationMode.class)) : List.copyOf(requestedModes);
        Map<String, Object> snapshot = canonicalParameters(parameters);
        String snapshotJson = JSON.toJSONString(snapshot);
        RagEvalRun active = mapper.selectActiveEquivalentRun(datasetId, dataset.getChecksumSha256(), snapshotJson, JSON.toJSONString(modes));
        if (active != null) return active.getRunId();

        List<RagEvalCaseEntity> cases = datasetService.listCases(datasetId);
        if (cases.isEmpty()) throw new ServiceException("Evaluation dataset is empty");
        RagEvalRun run = new RagEvalRun();
        run.setRunId(UUID.randomUUID().toString()); run.setDatasetId(datasetId);
        run.setDatasetChecksum(dataset.getChecksumSha256()); run.setStatus("PENDING");
        run.setModesJson(JSON.toJSONString(modes)); run.setParameterSnapshotJson(snapshotJson);
        run.setQdrantAlias(ACTIVE_ALIAS); run.setQdrantCollection(resolveCollection());
        run.setEmbeddingModel(embeddingModel); run.setRerankModel(rerankModel); run.setGitCommit(gitCommit());
        run.setTotalCases(cases.size() * modes.size()); run.setCompletedCases(0); run.setCreatedBy(operator);
        mapper.insertRun(run);
        try {
            executor.execute(() -> execute(run.getRunId(), modes));
        } catch (TaskRejectedException ex) {
            mapper.completeRun(run.getRunId(), "FAILED", null, "EVAL_CAPACITY_EXHAUSTED", "Evaluation capacity is exhausted");
            throw new ServiceException("Evaluation capacity is exhausted");
        }
        return run.getRunId();
    }

    public void cancel(String runId) {
        get(runId);
        if (mapper.requestCancel(runId) != 1) throw new ServiceException("Run is not cancellable");
    }

    public void review(Long resultId, String category, String note, String reviewer) {
        if (mapper.selectCaseResultById(resultId) == null) throw new ServiceException("Case result not found");
        if (category == null || category.isBlank()) throw new ServiceException("Failure category is required");
        try { RagEvalFailureCategory.valueOf(category.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ServiceException("Unsupported failure category"); }
        mapper.reviewCaseResult(resultId, category, truncate(note, 1000), reviewer);
    }

    public Map<String, Object> compare(String baselineId, String candidateId) {
        RagEvalRun baseline = get(baselineId), candidate = get(candidateId);
        if (!"SUCCEEDED".equals(baseline.getStatus()) || !"SUCCEEDED".equals(candidate.getStatus())) {
            throw new ServiceException("Only completed runs can be compared");
        }
        boolean compatible = Objects.equals(baseline.getDatasetChecksum(), candidate.getDatasetChecksum());
        Map<String, RagEvalCaseResultEntity> left = keyed(results(baselineId));
        Map<String, RagEvalCaseResultEntity> right = keyed(results(candidateId));
        int improved = 0, unchanged = 0, regressed = 0;
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String key : left.keySet()) {
            if (!right.containsKey(key)) continue;
            RagEvalCaseResultEntity a = left.get(key), b = right.get(key);
            int ar = rank(a), br = rank(b);
            String change = br < ar ? "IMPROVED" : br > ar ? "REGRESSED" : "UNCHANGED";
            if (change.equals("IMPROVED")) improved++; else if (change.equals("REGRESSED")) regressed++; else unchanged++;
            changes.add(Map.of("key", key, "baselineRank", ar, "candidateRank", br, "change", change));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("compatible", compatible); response.put("baseline", baseline); response.put("candidate", candidate);
        response.put("improved", improved); response.put("unchanged", unchanged); response.put("regressed", regressed);
        response.put("caseChanges", changes);
        if (compatible) {
            response.put("metricDeltas", metricDeltas(baseline.getAggregateMetricsJson(), candidate.getAggregateMetricsJson()));
            response.put("baselineAverageLatencyMs", averageLatency(left.values()));
            response.put("candidateAverageLatencyMs", averageLatency(right.values()));
            response.put("latencyDeltaMs", averageLatency(right.values()) - averageLatency(left.values()));
        } else {
            response.put("warning", "Dataset checksums differ; direct improvement percentages are suppressed");
        }
        return response;
    }

    public byte[] export(String runId, String format) {
        RagEvalRun run = get(runId);
        if (!"SUCCEEDED".equals(run.getStatus())) throw new ServiceException("Only completed runs can be exported");
        List<RagEvalCaseResultEntity> caseResults = results(runId);
        if ("json".equalsIgnoreCase(format)) return JSON.toJSONBytes(Map.of("run", run, "results", caseResults));
        StringBuilder md = new StringBuilder("# RAG Evaluation Report\n\n");
        md.append("- Run: `").append(runId).append("`\n- Dataset checksum: `").append(run.getDatasetChecksum()).append("`\n");
        md.append("- Collection: `").append(run.getQdrantCollection()).append("`\n- Models: ").append(run.getEmbeddingModel()).append(" / ").append(run.getRerankModel()).append("\n\n");
        md.append("## Metrics\n\n```json\n").append(run.getAggregateMetricsJson()).append("\n```\n\n## Cases\n\n");
        for (RagEvalCaseResultEntity result : caseResults) md.append("- ").append(result.getEvalMode()).append(" case ").append(result.getCaseId()).append(": ").append(Boolean.TRUE.equals(result.getPassed()) ? "PASS" : result.getFailureReason()).append("\n");
        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void execute(String runId, List<RagEvaluationMode> modes) {
        if (mapper.markRunStarted(runId) != 1) return;
        RagEvalRun run = get(runId);
        List<RagEvalCaseEntity> entities = datasetService.listCases(run.getDatasetId());
        Map<String, RagEvalCaseEntity> byKey = entities.stream().collect(Collectors.toMap(RagEvalCaseEntity::getCaseKey, Function.identity()));
        List<RagEvaluationCase> cases = datasetService.toEvaluationCases(run.getDatasetId());
        Map<String, Object> aggregates = new LinkedHashMap<>();
        Map<String, Object> parameters = JSON.parseObject(run.getParameterSnapshotJson(), Map.class);
        int recallTopN = intParameter(parameters, "recallTopN", 20);
        int finalTopK = intParameter(parameters, "finalTopK", 5);
        int completed = 0;
        try {
            for (RagEvaluationMode mode : modes) {
                if (Boolean.TRUE.equals(get(runId).getCancelRequested())) {
                    mapper.completeRun(runId, "CANCELLED", JSON.toJSONString(aggregates), null, null); return;
                }
                RagEvaluationResult result = evaluationService.runDataset(
                    runId + "-" + mode.name(), cases, mode, recallTopN, finalTopK);
                aggregates.put(mode.name(), aggregate(result));
                for (RagEvaluationResult.RagEvaluationCaseResult item : result.getCases()) {
                    RagEvalCaseEntity source = byKey.get(item.getId());
                    if (source == null) continue;
                    mapper.insertCaseResult(toEntity(runId, source.getId(), mode, item, source.getExpectedMetadataJson()));
                    mapper.updateRunProgress(runId, ++completed);
                }
            }
            mapper.completeRun(runId, "SUCCEEDED", JSON.toJSONString(aggregates), null, null);
        } catch (Exception ex) {
            mapper.completeRun(runId, "FAILED", JSON.toJSONString(aggregates), "EVAL_EXECUTION_FAILED", safeError(ex));
        }
    }

    private RagEvalCaseResultEntity toEntity(String runId, Long caseId, RagEvaluationMode mode,
                                              RagEvaluationResult.RagEvaluationCaseResult item, String filters) {
        RagEvalCaseResultEntity e = new RagEvalCaseResultEntity();
        e.setRunId(runId); e.setCaseId(caseId); e.setEvalMode(mode.name()); e.setPassed(item.isPassed());
        e.setFirstRelevantRank(item.getFirstRelevantRank()); e.setRelevantInTopK(item.getRelevantInTopK());
        e.setReciprocalRank(item.getFirstRelevantRank() > 0 ? 1.0 / item.getFirstRelevantRank() : 0.0);
        e.setEffectiveFiltersJson(filters); e.setCandidateSummaryJson(candidateSummary(item.getCandidates()));
        e.setSelectedEvidenceJson(JSON.toJSONString(item.getEvidence())); e.setLatencyMs(item.getLatencyMs()); e.setFailureReason(item.getFailureReason());
        e.setTechnicalErrorCode(item.getTechnicalErrorCode()); e.setTechnicalErrorSummary(item.getTechnicalErrorSummary());
        return e;
    }

    private String candidateSummary(List<RagChunkResult> chunks) {
        List<Map<String, Object>> summary = new ArrayList<>();
        if (chunks != null) for (RagChunkResult c : chunks.stream().limit(20).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", c.getSourceName()); row.put("sourceId", c.getSourceId()); row.put("chunkIndex", c.getChunkIndex());
            row.put("vectorRank", c.getVectorRank()); row.put("vectorScore", c.getVectorScore()); row.put("rerankRank", c.getRerankRank());
            row.put("rerankScore", c.getRerankScore()); row.put("selected", c.isSelectedForPrompt()); row.put("preview", truncate(c.getContentPreview(), 500));
            summary.add(row);
        }
        return JSON.toJSONString(summary);
    }

    private Map<String, Object> canonicalParameters(Map<String, Object> parameters) {
        Map<String, Object> result = new java.util.TreeMap<>();
        result.put("recallTopN", 20); result.put("finalTopK", 5);
        if (parameters != null) result.putAll(parameters);
        return result;
    }
    private Map<String, Object> aggregate(RagEvaluationResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCases", result.getTotalCases()); summary.put("passedCases", result.getPassedCases());
        summary.put("passRate", result.getPassRate()); summary.put("mrr", result.getMrr());
        summary.put("hitAt1", result.getHitAt1()); summary.put("hitAt3", result.getHitAt3());
        summary.put("hitAt5", result.getHitAt5()); summary.put("ndcg5", result.getNdcg5());
        summary.put("filterAccuracy", result.getFilterAccuracy()); summary.put("noAnswerAccuracy", result.getNoAnswerAccuracy());
        summary.put("falsePositiveRate", result.getFalsePositiveRate());
        return summary;
    }
    private int intParameter(Map<String, Object> parameters, String key, int fallback) {
        Object value = parameters == null ? null : parameters.get(key);
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private Map<String, RagEvalCaseResultEntity> keyed(List<RagEvalCaseResultEntity> values) {
        return values.stream().collect(Collectors.toMap(v -> v.getCaseId() + ":" + v.getEvalMode(), Function.identity(), (a,b) -> a, LinkedHashMap::new));
    }
    private Map<String, Double> metricDeltas(String baselineJson, String candidateJson) {
        Map<String, Double> deltas = new LinkedHashMap<>();
        Map<String, Object> baseline = JSON.parseObject(baselineJson, Map.class);
        Map<String, Object> candidate = JSON.parseObject(candidateJson, Map.class);
        for (String mode : baseline.keySet()) {
            if (!(baseline.get(mode) instanceof Map<?, ?> left) || !(candidate.get(mode) instanceof Map<?, ?> right)) continue;
            for (String metric : List.of("passRate", "mrr", "hitAt1", "hitAt3", "hitAt5", "ndcg5", "filterAccuracy", "noAnswerAccuracy", "falsePositiveRate")) {
                Object a = left.get(metric), b = right.get(metric);
                if (a instanceof Number an && b instanceof Number bn) {
                    deltas.put(mode + "." + metric, bn.doubleValue() - an.doubleValue());
                }
            }
        }
        return deltas;
    }
    private double averageLatency(java.util.Collection<RagEvalCaseResultEntity> values) {
        return values.stream().map(RagEvalCaseResultEntity::getLatencyMs).filter(Objects::nonNull)
            .mapToLong(Long::longValue).average().orElse(0.0);
    }
    private int rank(RagEvalCaseResultEntity value) { return value.getFirstRelevantRank() == null || value.getFirstRelevantRank() < 1 ? Integer.MAX_VALUE : value.getFirstRelevantRank(); }
    private String resolveCollection() { try { return collectionManager.resolveAlias(ACTIVE_ALIAS); } catch (Exception e) { return "unknown"; } }
    private String gitCommit() { try { Process p = new ProcessBuilder("git", "rev-parse", "HEAD").start(); return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim(); } catch (Exception e) { return "unknown"; } }
    private String safeError(Throwable error) { String text = error.getMessage(); return truncate(text == null ? error.getClass().getSimpleName() : text, 512); }
    private String truncate(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
}
