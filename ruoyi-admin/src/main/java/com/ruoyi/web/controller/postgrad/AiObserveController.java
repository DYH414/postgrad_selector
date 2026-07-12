package com.ruoyi.web.controller.postgrad;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/postgrad/ai-observe")
public class AiObserveController extends BaseController {

    private final AiObserveService aiObserveService;

    public AiObserveController(AiObserveService aiObserveService) {
        this.aiObserveService = aiObserveService;
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
}
