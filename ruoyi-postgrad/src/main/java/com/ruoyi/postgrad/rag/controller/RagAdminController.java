package com.ruoyi.postgrad.rag.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.RagIndexResult;
import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.eval.RagEvaluationResult;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.knowledge.RagMarkdownDocumentService;
import com.ruoyi.postgrad.rag.knowledge.RagMarkdownExportResult;
import com.ruoyi.postgrad.rag.service.RagSearchTestService;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/postgrad/rag")
public class RagAdminController extends BaseController {

    private final RagDocumentService ragDocumentService;
    private final RagSearchTestService ragSearchTestService;
    private final RagTraceRecorder ragTraceRecorder;
    private final RagEvaluationService ragEvaluationService;
    private final RagMarkdownDocumentService ragMarkdownDocumentService;

    @Value("${app.ai.rag.qdrant.collection-name:postgrad_rag_main}")
    private String collectionName;

    @Value("${app.ai.rag.embedding.model-name:BAAI/bge-m3}")
    private String embeddingModelName;

    @Value("${app.ai.rag.rerank.model-name:BAAI/bge-reranker-v2-m3}")
    private String rerankModelName;

    @Value("${app.ai.rag.indexing.chunk-size:500}")
    private int chunkSize;

    @Value("${app.ai.rag.indexing.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${app.ai.rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${app.ai.rag.retrieval.rerank-top-n:3}")
    private int rerankTopN;

    public RagAdminController(RagDocumentService ragDocumentService,
                              RagSearchTestService ragSearchTestService,
                              RagTraceRecorder ragTraceRecorder,
                              RagEvaluationService ragEvaluationService,
                              RagMarkdownDocumentService ragMarkdownDocumentService) {
        this.ragDocumentService = ragDocumentService;
        this.ragSearchTestService = ragSearchTestService;
        this.ragTraceRecorder = ragTraceRecorder;
        this.ragEvaluationService = ragEvaluationService;
        this.ragMarkdownDocumentService = ragMarkdownDocumentService;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/status")
    public AjaxResult status() {
        RagStatus status = ragDocumentService.getStatus();
        Map<String, Object> data = new HashMap<>();
        data.put("indexed", status.isIndexed());
        data.put("enabled", status.isEnabled());
        data.put("documentCount", status.getDocumentCount());
        data.put("chunkCount", status.getChunkCount());
        data.put("lastIndexedAt", status.getLastIndexedAt());
        data.put("lastError", status.getLastError());
        data.put("lastRunMode", status.getLastRunMode());
        data.put("backend", status.getBackend());
        data.put("collectionName", coalesce(status.getCollectionName(), collectionName));
        data.put("reachable", status.isReachable());
        data.put("embeddingModel", embeddingModelName);
        data.put("rerankModel", rerankModelName);
        data.put("chunkSize", chunkSize);
        data.put("chunkOverlap", chunkOverlap);
        data.put("maxResults", maxResults);
        data.put("rerankTopN", rerankTopN);
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:reindex')")
    @PostMapping("/reindex")
    public AjaxResult reindex() {
        RagIndexResult result = ragDocumentService.reindex();
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:reindex')")
    @PostMapping("/reindex-database")
    public AjaxResult reindexDatabase(@RequestParam(defaultValue = "2023") Integer startYear,
                                      @RequestParam(defaultValue = "2025") Integer endYear,
                                      @RequestParam(defaultValue = "1000") int limit) {
        long startMs = System.currentTimeMillis();
        try {
            RagIndexResult result = ragDocumentService.reindexDatabaseKnowledge(startYear, endYear, limit);
            recordDatabaseTrace("database-reindex", startYear, endYear, limit,
                result.getDocumentCount(), result.getChunkCount(), null, startMs);
            Map<String, Object> data = new HashMap<>();
            data.put("documentCount", result.getDocumentCount());
            data.put("chunkCount", result.getChunkCount());
            data.put("startYear", startYear);
            data.put("endYear", endYear);
            data.put("limit", limit);
            data.put("mode", "database");
            return success(data);
        } catch (RuntimeException e) {
            recordDatabaseTrace("database-reindex", startYear, endYear, limit,
                0, 0, e.getMessage(), startMs);
            throw e;
        }
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:reindex')")
    @PostMapping("/generate-documents")
    public AjaxResult generateDocuments(@RequestParam(defaultValue = "2023") Integer startYear,
                                        @RequestParam(defaultValue = "2025") Integer endYear,
                                        @RequestParam(defaultValue = "1000") int limit) {
        long startMs = System.currentTimeMillis();
        try {
            RagMarkdownExportResult result = ragMarkdownDocumentService.generateV2Documents(startYear, endYear, limit);
            recordDatabaseTrace("database-generate", startYear, endYear, limit,
                result.getDocumentCount(), 0, null, startMs);
            Map<String, Object> data = new HashMap<>();
            data.put("documentCount", result.getDocumentCount());
            data.put("outputDirectory", result.getOutputDirectory().toString());
            data.put("writtenFileCount", result.getWrittenFiles().size());
            data.put("writtenFiles", result.getWrittenFiles().stream()
                .limit(20)
                .map(Path::toString)
                .toList());
            data.put("startYear", startYear);
            data.put("endYear", endYear);
            data.put("limit", limit);
            data.put("mode", "database");
            return success(data);
        } catch (RuntimeException e) {
            recordDatabaseTrace("database-generate", startYear, endYear, limit,
                0, 0, e.getMessage(), startMs);
            throw e;
        }
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @PostMapping("/search-test")
    public AjaxResult searchTest(@Valid @RequestBody RagSearchTestRequest request) {
        RagSearchTestResponse resp = ragSearchTestService.searchTest(request);

        // Record trace
        RagTrace trace = new RagTrace();
        trace.setOperationKey("search-test");
        trace.setQuery(request.getQuery());
        trace.setOutcome(resp.getErrorSummary() != null ? RagTrace.Outcome.FAILURE : RagTrace.Outcome.SUCCESS);
        trace.setFailureStage(resp.isRerankFailed() ? "rerank" : null);
        trace.setErrorSummary(resp.getErrorSummary());
        trace.setRetrievedCount(resp.getRetrievedCount());
        trace.setSelectedCount(resp.getSelectedCount());
        trace.setMetadataFilters(request.getMetadataFilters());
        if (resp.getLatency() != null) {
            trace.setEmbeddingLatencyMs(resp.getLatency().getEmbeddingMs());
            trace.setRetrievalLatencyMs(resp.getLatency().getRetrievalMs());
            trace.setRerankLatencyMs(resp.getLatency().getRerankMs());
            trace.setTotalLatencyMs(resp.getLatency().getTotalMs());
        }
        trace.setTopSources(resp.getChunks().stream()
            .filter(c -> c.isSelectedForPrompt())
            .map(c -> c.getSourceName())
            .distinct()
            .limit(5)
            .toList());
        trace.setSelectedChunks(resp.getChunks().stream()
            .filter(c -> c.isSelectedForPrompt())
            .limit(5)
            .map(c -> new RagTrace.SelectedChunk(c.getSourceName(), c.getContentPreview()))
            .toList());
        ragTraceRecorder.record(trace);

        return success(resp);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/traces")
    public AjaxResult traces(@RequestParam(defaultValue = "20") int limit) {
        List<RagTrace> traces = ragTraceRecorder.listRecent(limit);
        return success(traces);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @PostMapping("/evaluate")
    public AjaxResult evaluate() {
        RagEvaluationResult result = ragEvaluationService.runDefault();
        recordEvaluationTrace(result);
        return success(result);
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private void recordDatabaseTrace(String operationKey, Integer startYear, Integer endYear, int limit,
                                     int documentCount, int chunkCount, String errorSummary, long startMs) {
        RagTrace trace = new RagTrace();
        trace.setOperationKey(operationKey);
        trace.setQuery(operationKey + " " + startYear + "-" + endYear);
        trace.setOutcome(errorSummary == null ? RagTrace.Outcome.SUCCESS : RagTrace.Outcome.FAILURE);
        trace.setFailureStage(errorSummary == null ? null : "database-rag");
        trace.setErrorSummary(errorSummary);
        trace.setRetrievedCount(documentCount);
        trace.setSelectedCount(chunkCount);
        trace.setMetadataFilters(Map.of(
            "start_year", String.valueOf(startYear),
            "end_year", String.valueOf(endYear),
            "limit", String.valueOf(limit)
        ));
        trace.setTotalLatencyMs(System.currentTimeMillis() - startMs);
        ragTraceRecorder.record(trace);
    }

    private void recordEvaluationTrace(RagEvaluationResult result) {
        RagTrace trace = new RagTrace();
        trace.setOperationKey("rag-evaluate");
        trace.setQuery("default-rag-evaluation");
        trace.setOutcome(result.getFailedCases() == 0 ? RagTrace.Outcome.SUCCESS : RagTrace.Outcome.FAILURE);
        trace.setFailureStage(result.getFailedCases() == 0 ? null : "evaluation");
        trace.setRetrievedCount(result.getTotalCases());
        trace.setSelectedCount(result.getPassedCases());
        trace.setMetadataFilters(Map.of(
            "total_cases", String.valueOf(result.getTotalCases()),
            "passed_cases", String.valueOf(result.getPassedCases()),
            "failed_cases", String.valueOf(result.getFailedCases())
        ));
        trace.setErrorSummary(result.getCases().stream()
            .filter(c -> !c.isPassed())
            .limit(3)
            .map(c -> c.getId() + ":" + c.getFailureReason())
            .collect(Collectors.joining("; ")));
        ragTraceRecorder.record(trace);
    }
}
