package com.ruoyi.web.controller.postgrad;

import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationContext;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationResult;
import com.ruoyi.postgrad.recommend.service.EvaluationAgentInvocationService;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部评测端点（Python Worker → Java）。由 InternalTokenAuthenticationFilter 保护，
 * 仅 ROLE_INTERNAL_EVAL 可访问，不接受普通登录 JWT。
 */
@RestController
@RequestMapping("/internal/v1/evaluation")
public class InternalEvaluationController {

    private final EvaluationAgentInvocationService invocationService;
    private final AiObserveService aiObserveService;
    private final RagTraceRecorder ragTraceRecorder;

    public InternalEvaluationController(EvaluationAgentInvocationService invocationService,
                                        AiObserveService aiObserveService,
                                        RagTraceRecorder ragTraceRecorder) {
        this.invocationService = invocationService;
        this.aiObserveService = aiObserveService;
        this.ragTraceRecorder = ragTraceRecorder;
    }

    /**
     * 跑一次隔离只读 Agent，内联返回 answer/route + 三类 trace（原始内容，内网可信通道，无需脱敏）。
     * trace 由 runId 关联，复用 AiObserveService/RagTraceRecorder 的按 runId 聚合。
     */
    @PostMapping("/agent-invocations")
    public Map<String, Object> agentInvocation(@RequestBody AgentInvocationRequest request) {
        EvaluationAgentInvocationResult result = invocationService.invoke(
            EvaluationAgentInvocationContext.of(
                request.evaluationRunId, request.caseId,
                "invoke:" + request.evaluationRunId + ":" + request.caseId,
                request.subjectUserId, request.agentConfigVersionId,
                request.query, request.businessPromptText));

        String runId = result.agentRunId();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> agentRun = new LinkedHashMap<>();
        agentRun.put("runId", runId);
        agentRun.put("status", result.status());
        agentRun.put("route", result.route());
        agentRun.put("routeReason", result.routeReason());
        agentRun.put("answer", result.answer());
        agentRun.put("durationMs", result.durationMs());
        agentRun.put("draftChanged", result.draftChanged());
        agentRun.put("errorCode", result.errorCode());
        agentRun.put("errorSummary", result.errorSummary());
        body.put("agentRun", agentRun);
        // 内联三类 trace（按 runId 聚合，复用现有观测服务）
        body.put("llmTraces", aiObserveService.listRunLlmTraces(runId));
        body.put("toolTraces", aiObserveService.listRunToolTraces(runId));
        body.put("ragTraces", ragTraceRecorder.listPersistedByRunId(runId, 100));
        return body;
    }

    public static class AgentInvocationRequest {
        public String evaluationRunId;
        public Long caseId;
        public Long subjectUserId;
        public Long agentConfigVersionId;
        public String query;
        public String businessPromptText;
    }
}
