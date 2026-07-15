package com.ruoyi.postgrad.rag.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.rag.build.RagIndexBuildService;
import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import com.ruoyi.postgrad.rag.eval.RagEvaluationResult;
import com.ruoyi.postgrad.rag.eval.RagEvaluationService;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/postgrad/rag")
public class RagAdminController extends BaseController {

    private final RagIndexBuildService ragIndexBuildService;
    private final RagIndexStatusService ragIndexStatusService;
    private final RagIndexRunMapper ragIndexRunMapper;
    private final RagRetrievalService ragRetrievalService;
    private final RagTraceRecorder ragTraceRecorder;
    private final RagEvaluationService ragEvaluationService;

    public RagAdminController(RagIndexBuildService ragIndexBuildService,
                              RagIndexStatusService ragIndexStatusService,
                              RagIndexRunMapper ragIndexRunMapper,
                              RagRetrievalService ragRetrievalService,
                              RagTraceRecorder ragTraceRecorder,
                              RagEvaluationService ragEvaluationService) {
        this.ragIndexBuildService = ragIndexBuildService;
        this.ragIndexStatusService = ragIndexStatusService;
        this.ragIndexRunMapper = ragIndexRunMapper;
        this.ragRetrievalService = ragRetrievalService;
        this.ragTraceRecorder = ragTraceRecorder;
        this.ragEvaluationService = ragEvaluationService;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/status")
    public AjaxResult status() {
        return success(ragIndexStatusService.getStatus());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:reindex')")
    @PostMapping("/index-runs")
    public AjaxResult startIndexRun(@RequestParam(defaultValue = "2023") Integer startYear,
                                    @RequestParam(defaultValue = "2025") Integer endYear) {
        String runId = ragIndexBuildService.startBuild(startYear, endYear);
        Map<String, Object> data = new HashMap<>();
        data.put("runId", runId);
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/index-runs/{runId}")
    public AjaxResult getIndexRun(@PathVariable String runId) {
        RagIndexRun run = ragIndexRunMapper.selectByRunId(runId);
        if (run == null) {
            return error("Index run not found: " + runId);
        }
        return success(run);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @PostMapping("/search-test")
    public AjaxResult searchTest(@Valid @RequestBody RagSearchTestRequest request) {
        RagSearchTestResponse resp = ragRetrievalService.retrieve(request).response();

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

    @PreAuthorize("@ss.hasPermi('postgrad:rag:eval')")
    @PostMapping("/eval/run")
    public AjaxResult runEval() {
        return success(ragEvaluationService.runDev());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:eval')")
    @PostMapping("/eval/dev")
    public AjaxResult runEvalDev() {
        return success(ragEvaluationService.runDev());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:eval')")
    @PostMapping("/eval/test")
    public AjaxResult runEvalTest() {
        return success(ragEvaluationService.runTest());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:eval')")
    @PostMapping("/eval/frozen-v1")
    public AjaxResult runFrozenEvaluation() {
        return success(ragEvaluationService.runFrozenV1Ablation());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:eval')")
    @PostMapping("/eval/full")
    public AjaxResult runEvalFull() {
        return success(ragEvaluationService.runFull());
    }
}
