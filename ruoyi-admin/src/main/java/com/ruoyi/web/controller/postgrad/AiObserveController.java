package com.ruoyi.web.controller.postgrad;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/postgrad/ai-observe")
public class AiObserveController extends BaseController {

    private final AiObserveService aiObserveService;
    private final RagTraceRecorder ragTraceRecorder;

    public AiObserveController(AiObserveService aiObserveService) {
        this(aiObserveService, null);
    }

    @Autowired
    public AiObserveController(AiObserveService aiObserveService,
                               RagTraceRecorder ragTraceRecorder) {
        this.aiObserveService = aiObserveService;
        this.ragTraceRecorder = ragTraceRecorder;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:aiObserve:view')")
    @GetMapping("/overview")
    public AjaxResult overview() {
        return success(aiObserveService.getOverview());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:aiObserve:view')")
    @GetMapping("/tool-traces")
    public AjaxResult toolTraces(@RequestParam(defaultValue = "50") int limit) {
        return success(aiObserveService.listRecentToolTraces(limit));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:aiObserve:view')")
    @GetMapping("/runs/{runId}/tool-traces")
    public AjaxResult runToolTraces(@PathVariable String runId) {
        if (runId == null || runId.isBlank()) return error("runId 不能为空");
        return success(aiObserveService.listRunToolTraces(runId));
    }

    @PreAuthorize("@ss.hasPermi('postgrad:aiObserve:view')")
    @GetMapping("/runs/{runId}")
    public AjaxResult runDetail(@PathVariable String runId) {
        if (runId == null || runId.isBlank()) return error("runId 不能为空");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("runId", runId);
        detail.put("toolTraces", aiObserveService.listRunToolTraces(runId));
        detail.put("ragTraces", ragTraceRecorder == null
            ? java.util.List.of()
            : ragTraceRecorder.listPersistedByRunId(runId, 100));
        detail.put("ssePersisted", false);
        return success(detail);
    }
}
