package com.ruoyi.web.controller.postgrad;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
