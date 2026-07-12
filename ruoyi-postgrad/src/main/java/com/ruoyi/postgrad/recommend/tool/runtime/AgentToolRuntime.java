package com.ruoyi.postgrad.recommend.tool.runtime;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.domain.ai.AiToolBudget.BudgetDecision;
import com.ruoyi.postgrad.domain.ai.AiToolBudget.BudgetGroup;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRuntime {

    private final AgentToolCatalog catalog;
    private final AiToolTraceService traceService;

    public AgentToolRuntime(AiToolTraceService traceService) {
        this(new AgentToolCatalog(), traceService);
    }

    public AgentToolRuntime(AgentToolCatalog catalog, AiToolTraceService traceService) {
        this.catalog = catalog;
        this.traceService = traceService;
    }

    public String execute(V2ChatToolContext.Context context, String toolName, Map<String, Object> arguments,
        Supplier<String> delegate) {
        AgentToolDefinition definition;
        try {
            definition = catalog.require(toolName);
        } catch (IllegalArgumentException ignored) {
            return rejectWithoutExecution(context, toolName, "TOOL_NOT_FOUND",
                "请求的工具不存在，请选择已提供的工具。", true);
        }
        String callId = UUID.randomUUID().toString();
        long startedNanos = System.nanoTime();
        context.toolLifecycleListener().onStarted(context.runId(), callId, toolName, definition.category().name());
        safeInsert(trace(context, callId, definition, "STARTED", argumentSummary(arguments)));

        BudgetDecision decision = context.toolBudget().tryUse(definition.budgetGroup(),
            definition.estimatedResultTokens());
        if (!decision.allowed()) {
            return finish(context, callId, definition, startedNanos,
                AgentToolResult.error(decision.code(), "工具调用预算已用尽，请基于已有信息继续。", true,
                    callId, context.runId(), toolName));
        }
        try {
            String raw = V2ChatToolContext.callWith(context, callId, delegate);
            return finish(context, callId, definition, startedNanos,
                AgentToolResult.normalize(raw, callId, context.runId(), toolName));
        } catch (Throwable ignored) {
            return finish(context, callId, definition, startedNanos,
                AgentToolResult.error("TOOL_EXECUTION_FAILED", "工具执行失败，请基于已有信息继续。", false,
                    callId, context.runId(), toolName));
        }
    }

    public String rejectWithoutExecution(V2ChatToolContext.Context context, String toolName, String code,
        String message, boolean retryable) {
        String callId = UUID.randomUUID().toString();
        long startedNanos = System.nanoTime();
        AgentToolDefinition definition = new AgentToolDefinition(toolName,
            AgentToolDefinition.ToolCategory.READ, BudgetGroup.OTHER, 0, false, true);
        context.toolLifecycleListener().onStarted(context.runId(), callId, toolName, definition.category().name());
        safeInsert(trace(context, callId, definition, "STARTED", "rejected before execution"));
        return finish(context, callId, definition, startedNanos,
            AgentToolResult.error(code, message, retryable, callId, context.runId(), toolName));
    }

    private String finish(V2ChatToolContext.Context context, String callId, AgentToolDefinition definition,
        long startedNanos, String normalized) {
        AgentToolResult.Summary summary = AgentToolResult.summarize(normalized);
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
        safeComplete(callId, summary, durationMs);
        context.toolLifecycleListener().onCompleted(context.runId(), callId, definition.name(),
            summary.status(), summary.code(), durationMs);
        emitConfirmation(context, callId, summary);
        return normalized;
    }

    private void emitConfirmation(V2ChatToolContext.Context context, String callId, AgentToolResult.Summary summary) {
        if (!summary.confirmationRequired() || context.lastActionResultJson() == null) {
            return;
        }
        try {
            JSONObject action = JSON.parseObject(context.lastActionResultJson());
            String token = action.getString("confirmationToken");
            if (token != null && !token.isBlank()) {
                context.toolLifecycleListener().onConfirmationRequired(context.runId(), callId,
                    summary.resultSummary(), token);
            }
        } catch (RuntimeException ignored) {
            // UI-only action metadata must not affect the Tool result.
        }
    }

    private AiToolCallTrace trace(V2ChatToolContext.Context context, String callId,
        AgentToolDefinition definition, String status, String argumentsSummary) {
        AiToolCallTrace trace = new AiToolCallTrace();
        trace.setCallId(callId);
        trace.setRunId(context.runId());
        trace.setUserId(context.userId());
        trace.setOperationKey("tool-execution");
        trace.setToolName(definition.name());
        trace.setToolCategory(definition.category().name());
        trace.setBudgetGroup(definition.budgetGroup().name());
        trace.setStatus(status);
        trace.setRetryable(false);
        trace.setConfirmationRequired(definition.confirmationRequired());
        trace.setArgumentsSummary(argumentsSummary);
        trace.setResultTokenEstimate(0);
        trace.setStartedAt(LocalDateTime.now());
        return trace;
    }

    private void safeInsert(AiToolCallTrace trace) {
        try {
            traceService.insert(trace);
        } catch (RuntimeException ignored) {
            // Trace is intentionally fail-open.
        }
    }

    private void safeComplete(String callId, AgentToolResult.Summary summary, long durationMs) {
        try {
            traceService.complete(callId, summary.status(), summary.code(), summary.retryable(),
                summary.resultSummary(), summary.tokenEstimate(), durationMs, null);
        } catch (RuntimeException ignored) {
            // Trace is intentionally fail-open.
        }
    }

    private String argumentSummary(Map<String, Object> arguments) {
        return arguments == null || arguments.isEmpty() ? "no arguments"
            : "fields=" + String.join(",", arguments.keySet());
    }
}
