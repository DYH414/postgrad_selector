package com.ruoyi.postgrad.rag.eval.workbench.controller;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.postgrad.rag.eval.RagEvaluationMode;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalDatasetService;
import com.ruoyi.postgrad.rag.eval.workbench.service.RagEvalRunService;
import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import com.ruoyi.postgrad.rag.trace.RagRetrievalTrace;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/postgrad/rag-evaluation")
public class RagEvaluationWorkbenchController extends BaseController {
    private final RagEvalDatasetService datasets;
    private final RagEvalRunService runs;
    private final RagEvaluationWorkbenchMapper mapper;
    private final RagRetrievalTraceMapper traceMapper;

    public RagEvaluationWorkbenchController(RagEvalDatasetService datasets, RagEvalRunService runs,
                                            RagEvaluationWorkbenchMapper mapper,
                                            RagRetrievalTraceMapper traceMapper) {
        this.datasets = datasets; this.runs = runs; this.mapper = mapper; this.traceMapper = traceMapper;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:view')")
    @GetMapping("/datasets")
    public TableDataInfo datasets(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String state) {
        startPage();
        return getDataTable(datasets.list(type, state));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:view')")
    @GetMapping("/datasets/{id}")
    public AjaxResult dataset(@PathVariable Long id) {
        return success(Map.of("dataset", datasets.get(id), "cases", datasets.listCases(id)));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PostMapping("/datasets")
    public AjaxResult createDataset(@RequestBody RagEvalDataset dataset) { return success(datasets.create(dataset, getUsername())); }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PutMapping("/datasets/{id}")
    public AjaxResult updateDataset(@PathVariable Long id, @RequestBody RagEvalDataset dataset) {
        dataset.setId(id); return success(datasets.update(dataset, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PostMapping("/datasets/{datasetId}/cases")
    public AjaxResult createCase(@PathVariable Long datasetId, @RequestBody RagEvalCaseEntity evalCase) {
        evalCase.setDatasetId(datasetId); evalCase.setId(null); return success(datasets.saveCase(evalCase, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PutMapping("/cases/{id}")
    public AjaxResult updateCase(@PathVariable Long id, @RequestBody RagEvalCaseEntity evalCase) {
        RagEvalCaseEntity current = mapper.selectCaseById(id);
        if (current == null) return error("Evaluation case not found");
        evalCase.setId(id); evalCase.setDatasetId(current.getDatasetId()); return success(datasets.saveCase(evalCase, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @DeleteMapping("/cases/{id}")
    public AjaxResult deleteCase(@PathVariable Long id) { datasets.deleteCase(id); return success(); }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:freeze')")
    @PostMapping("/datasets/{id}/freeze")
    public AjaxResult freeze(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return success(datasets.freeze(id, request.get("version"), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PostMapping("/datasets/import/{resourceName}")
    public AjaxResult importBundled(@PathVariable String resourceName) {
        if (!List.of("frozen-v3", "safety-v3").contains(resourceName)) {
            return error("Unsupported bundled dataset");
        }
        return success(datasets.importBundled(resourceName, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PostMapping(value = "/datasets/import-jsonl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult importJsonl(@RequestParam String datasetKey, @RequestParam String name,
                                  @RequestParam String version,
                                  @RequestParam(defaultValue = "DEV") String datasetType,
                                  @RequestParam MultipartFile file) throws java.io.IOException {
        String jsonl = new String(file.getBytes(), StandardCharsets.UTF_8);
        return success(datasets.importJsonl(datasetKey, name, version, datasetType, jsonl, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:export')")
    @GetMapping("/datasets/{id}/export")
    public ResponseEntity<byte[]> exportDataset(@PathVariable Long id) {
        RagEvalDataset dataset = datasets.get(id);
        byte[] body = datasets.exportJsonl(id).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(dataset.getDatasetKey() + "-" + dataset.getVersion() + ".jsonl").build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:run')")
    @PostMapping("/runs")
    public AjaxResult startRun(@RequestBody StartRunRequest request) {
        return success(Map.of("runId", runs.start(request.datasetId, request.modes, request.parameters, getUsername())));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:view')")
    @GetMapping("/runs")
    public TableDataInfo listRuns(@RequestParam(required = false) Long datasetId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String runIdKeyword,
                                  @RequestParam(required = false) String datasetChecksum) {
        startPage();
        return getDataTable(runs.list(datasetId, status, runIdKeyword, datasetChecksum));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:view')")
    @GetMapping("/runs/{runId}")
    public AjaxResult run(@PathVariable String runId) {
        com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun run = runs.get(runId);
        return success(Map.of("run", run, "results", runs.results(runId),
            "cases", datasets.listCases(run.getDatasetId())));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:run')")
    @PostMapping("/runs/{runId}/cancel")
    public AjaxResult cancel(@PathVariable String runId) { runs.cancel(runId); return success(); }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @PutMapping("/results/{id}/review")
    public AjaxResult review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        runs.review(id, request.category, request.note, getUsername()); return success();
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:compare')")
    @GetMapping("/compare")
    public AjaxResult compare(@RequestParam String baselineRunId, @RequestParam String candidateRunId) {
        return success(runs.compare(baselineRunId, candidateRunId));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:edit')")
    @GetMapping("/trace-draft/{traceId}")
    public AjaxResult traceDraft(@PathVariable Long traceId) {
        RagRetrievalTrace trace = traceMapper.selectById(traceId);
        if (trace == null) return error("RAG trace not found");
        RagEvalCaseEntity draft = new RagEvalCaseEntity();
        draft.setCaseKey("trace-" + trace.getTraceId()); draft.setQueryText(trace.getQuerySummary());
        draft.setExpectedMetadataJson(trace.getMetadataFiltersJson()); draft.setRelevantConditionsJson("[]");
        draft.setRelevantSourcesJson("[]"); draft.setNoAnswer(false); draft.setSourceDataAvailable(true);
        draft.setExpectedFallback(false); draft.setDifficulty("medium"); draft.setTagsJson(JSON.toJSONString(List.of("production-trace")));
        return success(draft);
    }

    @PreAuthorize("@ss.hasPermi('postgrad:ragEval:export')")
    @GetMapping("/runs/{runId}/report")
    public ResponseEntity<byte[]> report(@PathVariable String runId, @RequestParam(defaultValue = "markdown") String format) {
        byte[] body = runs.export(runId, format);
        String ext = "json".equalsIgnoreCase(format) ? "json" : "md";
        MediaType type = ext.equals("json") ? MediaType.APPLICATION_JSON : new MediaType("text", "markdown", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename("rag-eval-" + runId + "." + ext).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    public static class StartRunRequest {
        public Long datasetId;
        public List<RagEvaluationMode> modes;
        public Map<String, Object> parameters;
    }
    public static class ReviewRequest { public String category; public String note; }
}
