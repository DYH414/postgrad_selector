package com.ruoyi.web.controller.postgrad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.recommend.domain.dto.AddBackCandidateRequest;
import com.ruoyi.postgrad.recommend.domain.dto.ChatSendRequest;
import com.ruoyi.postgrad.recommend.domain.dto.RemoveCandidateRequest;
import com.ruoyi.postgrad.recommend.domain.dto.ReplaceCandidateRequest;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.service.IReportService;
import com.ruoyi.postgrad.recommend.service.IAiChatService;

/**
 * AI 推荐 v2 Controller —— 新 AI 推荐报告流程。
 * <p>只做参数校验、调用 Service、返回 AjaxResult / SseEmitter。
 * 所有请求体使用强类型 DTO，不出现裸 Map。</p>
 */
@RestController
@RequestMapping("/app/ai-recommend-v2")
public class AppV2RecommendController {

    @Autowired
    private IDraftService draftService;

    @Autowired
    private IReportService reportService;

    @Autowired
    private IAiChatService aiChatService;

    // ── 草稿端点 ──

    /**
     * 获取当前草稿。
     *
     * @return 当前用户的草稿状态
     */
    @GetMapping("/draft")
    public AjaxResult getDraft() {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(draftService.getDraft(user.getUserId()));
    }

    /**
     * SSE 生成草稿（带进度推送）。
     *
     * @return SseEmitter，推送 progress → done | error 事件序列
     */
    @PostMapping(value = "/draft/generate", produces = "text/event-stream")
    public SseEmitter generateDraft() {
        AppLoginUser user = requireLogin();
        // TODO: 校验用户画像是否有预计分数，缺失时在 SSE error 事件中提示
        return draftService.generateDraft(user.getUserId());
    }

    /**
     * 从草稿中移除候选。
     *
     * @param request 包含要移除的 programId
     * @return 更新后的草稿
     */
    @PostMapping("/draft/remove")
    public AjaxResult removeCandidate(@RequestBody RemoveCandidateRequest request) {
        AppLoginUser user = requireLogin();
        if (request.getProgramId() == null) {
            return AjaxResult.error("缺少 programId");
        }
        return AjaxResult.success(draftService.removeCandidate(user.getUserId(), request.getProgramId()));
    }

    /**
     * 替换草稿中的候选。
     *
     * @param request 包含要移除的 programId、目标档位、替换偏好
     * @return 更新后的草稿 + 新替换进来的候选
     */
    @PostMapping("/draft/replace")
    public AjaxResult replaceCandidate(@RequestBody ReplaceCandidateRequest request) {
        AppLoginUser user = requireLogin();
        if (request.getRemoveProgramId() == null || request.getTier() == null) {
            return AjaxResult.error("缺少 removeProgramId 或 tier");
        }
        String preference = request.getPreference() != null ? request.getPreference() : "safer";
        return AjaxResult.success(
            draftService.replaceCandidate(user.getUserId(), request.getRemoveProgramId(), request.getTier(), preference));
    }

    /**
     * 将之前移除的候选加回草稿。
     *
     * @param request 包含要加回的 programId
     * @return 更新后的草稿
     */
    @PostMapping("/draft/add-back")
    public AjaxResult addBackCandidate(@RequestBody AddBackCandidateRequest request) {
        AppLoginUser user = requireLogin();
        if (request.getProgramId() == null) {
            return AjaxResult.error("缺少 programId");
        }
        return AjaxResult.success(draftService.addBackCandidate(user.getUserId(), request.getProgramId()));
    }

    /**
     * 获取同档替代候选列表。
     *
     * @param tier      目标档位：reach / steady / safe
     * @param excludeId 排除的 programId（可选）
     * @return 可选候选列表（按综合得分排序）
     */
    @GetMapping("/draft/alternatives")
    public AjaxResult getAlternatives(@RequestParam String tier, @RequestParam(required = false) Long excludeId) {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(draftService.getAlternatives(user.getUserId(), tier, excludeId));
    }

    // ── 报告端点 ──

    /**
     * 从草稿生成最终报告。
     * <p>最终报告候选集与生成时草稿完全一致。</p>
     *
     * @return 报告详情
     */
    @PostMapping("/report/generate")
    public AjaxResult generateReport() {
        AppLoginUser user = requireLogin();
        // TODO: 校验草稿至少 1 个可信候选，否则提示先生成草稿
        return AjaxResult.success(reportService.generateReport(user.getUserId()));
    }

    /**
     * 查看报告详情。
     *
     * @param id 报告 ID
     * @return 报告详情
     */
    @GetMapping("/report/{id}")
    public AjaxResult getReport(@PathVariable Long id) {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(reportService.getReport(user.getUserId(), id));
    }

    /**
     * 我的 v2 报告列表。
     *
     * @return 报告摘要列表（按生成时间倒序）
     */
    @GetMapping("/reports")
    public AjaxResult listReports() {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(reportService.listReports(user.getUserId()));
    }

    // ── 对话端点 ──

    /**
     * 开始/重置对话。
     *
     * @return 对话 ID + 初始消息 + 快捷选项
     */
    @PostMapping("/chat/start")
    public AjaxResult startChat() {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(aiChatService.startChat(user.getUserId()));
    }

    /**
     * SSE 流式对话。
     *
     * @param request 包含用户消息文本
     * @return SseEmitter，推送 token → done(message, draftAction) | error
     */
    @PostMapping(value = "/chat/send", produces = "text/event-stream")
    public SseEmitter chat(@RequestBody ChatSendRequest request) {
        AppLoginUser user = requireLogin();
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("消息不能为空"));
            return err;
        }
        return aiChatService.chat(user.getUserId(), request.getMessage());
    }

    /**
     * 恢复对话（Redis 未过期时）。
     *
     * @return 对话状态 + 最近消息 + 数据来源标记
     */
    @GetMapping("/chat/resume")
    public AjaxResult resumeChat() {
        AppLoginUser user = requireLogin();
        return AjaxResult.success(aiChatService.resumeChat(user.getUserId()));
    }

    // ── 工具方法 ──

    /**
     * 从 SecurityContext 获取当前 App 端登录用户。
     *
     * @return 当前 App 登录用户
     * @throws SecurityException 未登录时抛出
     */
    private AppLoginUser requireLogin() {
        AppLoginUser user = null;
        try {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser) {
                user = (AppLoginUser) principal;
            }
        } catch (Exception ignored) {
        }
        if (user == null) {
            throw new SecurityException("未登录");
        }
        return user;
    }
}
