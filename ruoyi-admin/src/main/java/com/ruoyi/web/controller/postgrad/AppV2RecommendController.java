package com.ruoyi.web.controller.postgrad;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskState;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;
import com.ruoyi.postgrad.recommend.domain.dto.AddBackCandidateRequest;
import com.ruoyi.postgrad.recommend.domain.dto.ChatSendRequest;
import com.ruoyi.postgrad.recommend.domain.dto.RemoveCandidateRequest;
import com.ruoyi.postgrad.recommend.domain.dto.ReplaceCandidateRequest;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;
import com.ruoyi.postgrad.recommend.service.IDraftGenerationTaskService;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.service.IReportService;
import com.ruoyi.postgrad.recommend.service.IAiChatService;

/**
 * AI 推荐 v2 Controller —— 新 AI 推荐报告流程。
 * <p>只做参数校验、调用 Service、返回 AjaxResult / SseEmitter。
 * SSE 端点在此处将 SseEmitter 桥接到 Service 层的回调接口。</p>
 */
@RestController
@RequestMapping("/app/ai-recommend-v2")
public class AppV2RecommendController {

    @Autowired
    private IDraftService draftService;

    @Autowired
    private IDraftGenerationTaskService draftGenerationTaskService;

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
     * 创建草稿生成任务。
     *
     * @return taskId + streamToken，前端随后用 EventSource 订阅进度
     */
    @PostMapping("/draft/generate/start")
    public AjaxResult startGenerateDraft() {
        AppLoginUser user = requireLogin();
        DraftGenerationTaskVO task = draftGenerationTaskService.start(user.getUserId());
        return AjaxResult.success(task);
    }

    /**
     * 订阅草稿生成任务进度。
     * <p>EventSource 不能附带 Authorization header，因此此端点匿名放行，
     * 实际授权依赖 start 接口发放的短期 streamToken。</p>
     */
    @Anonymous
    @GetMapping(value = "/draft/generate/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamGenerateDraft(@RequestParam String taskId,
                                          @RequestParam String streamToken) {
        if (!draftGenerationTaskService.validateStreamToken(taskId, streamToken)) {
            SseEmitter err = new SseEmitter(5_000L);
            AtomicBoolean closed = new AtomicBoolean(false);
            safeSend(err, "error", Map.of("message", "无效的任务或订阅令牌"), closed);
            closed.set(true);
            err.complete();
            return err;
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(error -> closed.set(true));

        CompletableFuture.runAsync(() -> {
            long deadline = System.currentTimeMillis() + 300_000L;
            String lastSignature = "";
            while (!closed.get() && System.currentTimeMillis() < deadline) {
                DraftGenerationTaskState state = draftGenerationTaskService.getState(taskId);
                if (state == null) {
                    safeSend(emitter, "error", Map.of("message", "任务不存在或已过期"), closed);
                    break;
                }

                String signature = state.getStatus() + "|" + state.getPhase() + "|" + state.getMessage()
                    + "|" + state.getUpdatedAt() + "|" + state.getTierJson();
                if (!signature.equals(lastSignature)) {
                    lastSignature = signature;
                    if (DraftGenerationTaskState.STATUS_DONE.equals(state.getStatus())) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("draft", JSON.parseObject(state.getDraftJson()));
                        if (state.getProfileBasisJson() != null) {
                            payload.put("profileBasis", JSON.parseObject(state.getProfileBasisJson()));
                        }
                        payload.put("removedCount", state.getRemovedCount());
                        safeSend(emitter, "done", payload, closed);
                        break;
                    } else if (DraftGenerationTaskState.STATUS_ERROR.equals(state.getStatus())) {
                        safeSend(emitter, "error", Map.of(
                            "message", state.getErrorMessage() != null ? state.getErrorMessage() : "生成失败"), closed);
                        break;
                    } else {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("phase", state.getPhase());
                        payload.put("message", state.getMessage());
                        if (state.getFound() != null) payload.put("found", state.getFound());
                        if (state.getTier() != null) payload.put("tier", state.getTier());
                        // 逐档实时数据：某档 AI 选完后立即推送候选卡片
                        if (state.getTierJson() != null) {
                            payload.put("tierData", JSON.parseObject(state.getTierJson()));
                        }
                        safeSend(emitter, "progress", payload, closed);
                    }
                }

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            closed.set(true);
            emitter.complete();
        });

        return emitter;
    }

    /**
     * SSE 生成草稿（带进度推送）。
     * <p>异步执行生成流程，通过 SseEmitter 推送进度事件。</p>
     *
     * @return SseEmitter，推送 progress → done | error 事件序列
     */
    @PostMapping(value = "/draft/generate", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter generateDraft() {
        AppLoginUser user = requireLogin();
        SseEmitter emitter = new SseEmitter(120_000L);

        CompletableFuture.runAsync(() -> {
            try {
                draftService.generateDraft(user.getUserId(), new DraftGenerationCallback() {
                    @Override
                    public void onProgress(String phase, String message, Integer found, String tier) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("phase", phase);
                        payload.put("message", message);
                        if (found != null) payload.put("found", found);
                        if (tier != null) payload.put("tier", tier);
                        sendSseEvent(emitter, "progress", payload);
                    }

                    @Override
                    public void onTierComplete(String tier, String tierJson) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("phase", "ai_selecting");
                        payload.put("message", "档位完成");
                        payload.put("tier", tier);
                        // 解析 JSON 传到前端，触发增量渲染
                        try {
                            payload.put("tierData", com.alibaba.fastjson2.JSON.parse(tierJson));
                        } catch (Exception ignored) {
                            payload.put("tierData", tierJson);
                        }
                        sendSseEvent(emitter, "progress", payload);
                    }

                    @Override
                    public void onDone(com.ruoyi.postgrad.recommend.domain.DraftVO draft,
                                       com.ruoyi.postgrad.recommend.domain.ProfileBasisVO profileBasis,
                                       int removedCount) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("draft", draft);
                        payload.put("profileBasis", profileBasis);
                        payload.put("removedCount", removedCount);
                        sendSseEvent(emitter, "done", payload);
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("message", error.getMessage());
                        sendSseEvent(emitter, "error", payload);
                        emitter.complete();
                    }
                });
            } catch (Exception e) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", e.getMessage());
                sendSseEvent(emitter, "error", payload);
                emitter.complete();
            }
        });

        return emitter;
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
     * 从工作集选取最佳候选加入草稿（不替换任何已有候选）。
     */
    @PostMapping("/draft/add-from-workspace")
    public AjaxResult addFromWorkspace(@RequestBody Map<String, String> body) {
        AppLoginUser user = requireLogin();
        String tier = body.get("tier");
        if (tier == null || tier.isBlank()) {
            return AjaxResult.error("缺少 tier");
        }
        String preference = body.get("preference") != null ? body.get("preference") : "safer";
        return AjaxResult.success(draftService.addFromWorkspace(user.getUserId(), tier, preference));
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
     * <p>异步执行对话流程，通过 SseEmitter 推送 token 流。</p>
     *
     * @param request 包含用户消息文本
     * @return SseEmitter，推送 token → done(message, draftChanged, toolActionResult) | error
     */
    @PostMapping(value = "/chat/send", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestBody ChatSendRequest request) {
        AppLoginUser user = requireLogin();
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("消息不能为空"));
            return err;
        }

        SseEmitter emitter = new SseEmitter(300_000L);

        CompletableFuture.runAsync(() -> {
            try {
                aiChatService.chat(user.getUserId(), request.getMessage(), new ChatStreamCallback() {
                    @Override
                    public void onToken(String token) {
                        sendSseEvent(emitter, "token", Map.of("text", token));
                    }

                    @Override
                    public void onToolCall(String toolName) {
                        sendSseEvent(emitter, "tool_call", Map.of("tool", toolName));
                    }

                    @Override
                    public void onDone(String fullMessage,
                                       boolean draftChanged,
                                       String toolActionResultJson) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("message", fullMessage);
                        payload.put("draftChanged", draftChanged);
                        if (toolActionResultJson != null && !toolActionResultJson.isBlank()) {
                            payload.put("toolActionResult", JSON.parseObject(toolActionResultJson));
                        }
                        sendSseEvent(emitter, "done", payload);
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("message", error.getMessage());
                        sendSseEvent(emitter, "error", payload);
                        emitter.complete();
                    }
                });
            } catch (Exception e) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("message", e.getMessage());
                sendSseEvent(emitter, "error", payload);
                emitter.complete();
            }
        });

        return emitter;
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

    /**
     * 向 SseEmitter 发送一条命名事件。
     * <p>发送失败（如客户端已断开）时静默忽略。</p>
     *
     * @param emitter   SSE 发射器
     * @param eventName 事件名称（progress / token / done / error）
     * @param payload   事件数据
     * @return true 发送成功，false 客户端已断开
     */
    private boolean sendSseEvent(SseEmitter emitter, String eventName, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(JSON.toJSONString(new LinkedHashMap<>(payload))));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean safeSend(SseEmitter emitter, String eventName, Map<String, Object> payload, AtomicBoolean closed) {
        if (closed.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(JSON.toJSONString(new LinkedHashMap<>(payload))));
            return true;
        } catch (Exception e) {
            closed.set(true);
            return false;
        }
    }
}
