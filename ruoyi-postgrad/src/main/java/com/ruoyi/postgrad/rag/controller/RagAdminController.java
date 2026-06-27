package com.ruoyi.postgrad.rag.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.RagIndexResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/postgrad/rag")
public class RagAdminController extends BaseController {

    private final RagDocumentService ragDocumentService;

    public RagAdminController(RagDocumentService ragDocumentService) {
        this.ragDocumentService = ragDocumentService;
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:view')")
    @GetMapping("/status")
    public AjaxResult status() {
        return success(ragDocumentService.getStatus());
    }

    @PreAuthorize("@ss.hasPermi('postgrad:rag:reindex')")
    @PostMapping("/reindex")
    public AjaxResult reindex() {
        RagIndexResult result = ragDocumentService.reindex();
        return success(result);
    }
}
