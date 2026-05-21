package com.ruoyi.web.controller.postgrad;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.postgrad.service.IReviewService;

/**
 * 数据审核中心 —— staging 审核 + 迁移到正式表。
 */
@RestController
@RequestMapping("/postgrad/review")
public class ReviewController extends BaseController
{
    @Autowired
    private IReviewService reviewService;

    /**
     * 待审核列表（含搜索筛选）
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Map<String, String> params)
    {
        int pageNum = Integer.parseInt(params.getOrDefault("pageNum", "1"));
        int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "10"));
        Map<String, Object> data = reviewService.list(params, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo();
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        rsp.setRows((List<?>) data.get("rows"));
        rsp.setTotal(((Number) data.get("total")).longValue());
        return rsp;
    }

    /**
     * 记录详情
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return AjaxResult.success(reviewService.detail(id));
    }

    /**
     * 审核通过并迁移到正式表
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:edit')")
    @Log(title = "数据审核", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/approve")
    public AjaxResult approve(@PathVariable Long id, @RequestBody Map<String, Object> body)
    {
        String note = body != null ? (String) body.getOrDefault("reviewNote", "") : "";
        reviewService.approve(id, note);
        return AjaxResult.success();
    }

    /**
     * 驳回
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:edit')")
    @Log(title = "数据驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reject")
    public AjaxResult reject(@PathVariable Long id, @RequestBody Map<String, Object> body)
    {
        String note = body != null ? (String) body.getOrDefault("reviewNote", "") : "";
        reviewService.reject(id, note);
        return AjaxResult.success();
    }

    /**
     * 跳过
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:edit')")
    @PostMapping("/{id}/skip")
    public AjaxResult skip(@PathVariable Long id)
    {
        reviewService.skip(id);
        return AjaxResult.success();
    }

    /**
     * 批量通过
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:edit')")
    @Log(title = "批量审核", businessType = BusinessType.UPDATE)
    @PostMapping("/batch-approve")
    public AjaxResult batchApprove(@RequestBody Map<String, Object> body)
    {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        reviewService.batchApprove(ids);
        return AjaxResult.success();
    }

    /**
     * 统计概览
     */
    @PreAuthorize("@ss.hasPermi('postgrad:staging:list')")
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return AjaxResult.success(reviewService.stats());
    }
}
