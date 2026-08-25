package com.ruoyi.web.controller.postgrad;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.observe.service.AiObserveService;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.service.AgentEvalRunService;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationContext;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationResult;
import com.ruoyi.postgrad.recommend.service.EvaluationAgentInvocationService;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final AgentEvalRunService agentEvalRunService;

    public InternalEvaluationController(EvaluationAgentInvocationService invocationService,
                                        AiObserveService aiObserveService,
                                        RagTraceRecorder ragTraceRecorder,
                                        AgentEvalRunService agentEvalRunService) {
        this.invocationService = invocationService;
        this.aiObserveService = aiObserveService;
        this.ragTraceRecorder = ragTraceRecorder;
        this.agentEvalRunService = agentEvalRunService;
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

    /**
     * Python Worker 回调写 case 结果（§4 端点4）。
     * <ul>
     *   <li>同 resultChecksum → 200 + outcome=REPLAYED（幂等重放）</li>
     *   <li>不同 resultChecksum → 409（内容冲突，不覆盖）</li>
     *   <li>run 已取消 → 409（拒收迟到回调）</li>
     * </ul>
     */
    @PutMapping("/runs/{runId}/cases/{caseId}/result")
    public Map<String, Object> agentCaseResult(@PathVariable String runId,
                                               @PathVariable Long caseId,
                                               @RequestBody AgentCaseResultRequest request) {
        AgentEvalCaseResult entity = toEntity(request);
        String outcome;
        try {
            outcome = agentEvalRunService.saveAgentCaseResult(runId, caseId, entity);
        } catch (ServiceException ex) {
            String code = ex.getMessage();
            if ("EVAL_RUN_CANCELLED".equals(code) || "EVAL_RESULT_CONFLICT".equals(code)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, code);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, code);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", outcome);
        body.put("runId", runId);
        body.put("caseId", caseId);
        return body;
    }

    /** Python Worker callback for failures before any case result can be persisted. */
    @PutMapping("/runs/{runId}/failure")
    public Map<String, Object> agentRunFailure(@PathVariable String runId,
                                               @RequestBody AgentRunFailureRequest request) {
        agentEvalRunService.failRun(runId, request.errorCode, request.errorSummary);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", "FAILED");
        body.put("runId", runId);
        return body;
    }

    private AgentEvalCaseResult toEntity(AgentCaseResultRequest r) {
        AgentEvalCaseResult e = new AgentEvalCaseResult();
        e.setAgentRunId(r.agentRunId);
        e.setPassed(r.passed);
        e.setAgentSuccess(r.agentSuccess);
        e.setRouteAccuracy(r.routeAccuracy);
        e.setRequiredToolHit(r.requiredToolHit);
        e.setForbiddenToolCalled(r.forbiddenToolCalled);
        e.setToolSuccessRate(r.toolSuccessRate != null ? BigDecimal.valueOf(r.toolSuccessRate) : null);
        e.setCitationPresent(r.citationPresent);
        e.setExpectedAbstention(r.expectedAbstention);
        e.setTraceComplete(r.traceComplete != null ? r.traceComplete : false);
        e.setFaithfulness(r.faithfulness != null ? BigDecimal.valueOf(r.faithfulness) : null);
        e.setAnswerRelevance(r.answerRelevance != null ? BigDecimal.valueOf(r.answerRelevance) : null);
        e.setCitationCorrectness(r.citationCorrectness != null ? BigDecimal.valueOf(r.citationCorrectness) : null);
        e.setJudgeVariance(r.judgeVariance != null ? BigDecimal.valueOf(r.judgeVariance) : null);
        e.setJudgePassed(r.judgePassed);
        e.setRuleResultJson(r.ruleResultJson != null ? r.ruleResultJson : "{}");
        e.setJudgeResultJson(r.judgeResultJson);
        e.setFailureReasonsJson(r.failureReasonsJson != null ? r.failureReasonsJson : "[]");
        e.setLatencyMs(r.latencyMs);
        e.setInputTokens(r.inputTokens != null ? r.inputTokens : 0);
        e.setOutputTokens(r.outputTokens != null ? r.outputTokens : 0);
        e.setResultChecksum(r.resultChecksum);
        e.setTechnicalErrorCode(r.technicalErrorCode);
        e.setTechnicalErrorSummary(r.technicalErrorSummary);
        return e;
    }

    // ── Request bodies ──────────────────────────────────────────────────────

    public static class AgentInvocationRequest {
        public String evaluationRunId;
        public Long caseId;
        public Long subjectUserId;
        public Long agentConfigVersionId;
        public String query;
        public String businessPromptText;
    }

    /** §4 端点4：Python 回调写 case 结果的请求体（snake_case → Java 字段由 Jackson 映射）。 */
    public static class AgentCaseResultRequest {
        public String agentRunId;
        public Boolean passed;
        public Boolean agentSuccess;
        public Boolean routeAccuracy;
        public Boolean requiredToolHit;
        public Boolean forbiddenToolCalled;
        public Double toolSuccessRate;
        public Boolean citationPresent;
        public Boolean expectedAbstention;
        public Boolean traceComplete;
        public Double faithfulness;
        public Double answerRelevance;
        public Double citationCorrectness;
        public Double judgeVariance;
        public Boolean judgePassed;
        public String ruleResultJson;
        public String judgeResultJson;
        public String failureReasonsJson;
        public Long latencyMs;
        public Integer inputTokens;
        public Integer outputTokens;
        public String resultChecksum;
        public String technicalErrorCode;
        public String technicalErrorSummary;
    }

    public static class AgentRunFailureRequest {
        public String errorCode;
        public String errorSummary;
    }
}
