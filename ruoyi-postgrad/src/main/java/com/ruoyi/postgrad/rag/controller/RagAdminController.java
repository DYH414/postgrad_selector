package com.ruoyi.postgrad.rag.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.RagIndexResult;
import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagSearchTestService;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/postgrad/rag")
public class RagAdminController extends BaseController {

    private final RagDocumentService ragDocumentService;
    private final RagSearchTestService ragSearchTestService;
    private final RagTraceRecorder ragTraceRecorder;

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
                              RagTraceRecorder ragTraceRecorder) {
        this.ragDocumentService = ragDocumentService;
        this.ragSearchTestService = ragSearchTestService;
        this.ragTraceRecorder = ragTraceRecorder;
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
        trace.setTopSources(resp.getChunks().stream()
            .filter(c -> c.isSelectedForPrompt())
            .map(c -> c.getSourceName())
            .distinct()
            .limit(5)
            .toList());
        trace.setTotalLatencyMs(resp.getLatency().getTotalMs());
        ragTraceRecorder.record(trace);

        return success(resp);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/traces")
    public AjaxResult traces(@RequestParam(defaultValue = "20") int limit) {
        List<RagTrace> traces = ragTraceRecorder.listRecent(limit);
        return success(traces);
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
