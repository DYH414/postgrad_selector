package com.ruoyi.postgrad.recommend.tool.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ruoyi.postgrad.domain.ai.AiToolBudget;
import com.ruoyi.postgrad.domain.ai.AiToolBudget.BudgetGroup;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import org.junit.jupiter.api.Test;

class AgentToolRuntimeTest {

    @Test
    void budgetRejectionDoesNotInvokeDelegate() {
        AiToolBudget budget = AiToolBudget.chatTurnDefaults();
        for (int index = 0; index < 3; index++) {
            assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        }
        AtomicBoolean invoked = new AtomicBoolean();
        AgentToolRuntime runtime = new AgentToolRuntime(new AgentToolCatalog(), mock(AiToolTraceService.class));
        V2ChatToolContext.Context context = context(budget, new CapturingListener());

        String result = runtime.execute(context, "searchPrograms", Map.of(), () -> {
            invoked.set(true);
            return "{}";
        });

        assertFalse(invoked.get());
        assertTrue(result.contains("TOOL_BUDGET_EXCEEDED"));
    }

    @Test
    void traceFailureDoesNotBlockSuccessfulTool() {
        AiToolTraceService traceService = mock(AiToolTraceService.class);
        doThrow(new RuntimeException("database unavailable")).when(traceService).insert(any());
        AgentToolRuntime runtime = new AgentToolRuntime(new AgentToolCatalog(), traceService);

        String result = runtime.execute(context(AiToolBudget.chatTurnDefaults(), new CapturingListener()),
            "getUserProfile", Map.of(), () -> "{\"ok\":true,\"name\":\"Ada\"}");

        assertTrue(result.contains("\"ok\":true"));
    }

    @Test
    void executionFailureReturnsSanitizedError() {
        CapturingListener listener = new CapturingListener();
        AgentToolRuntime runtime = new AgentToolRuntime(new AgentToolCatalog(), mock(AiToolTraceService.class));
        String secret = "select * from applicant at C:\\Users\\secret\\data.sql";

        String result = runtime.execute(context(AiToolBudget.chatTurnDefaults(), listener),
            "getUserProfile", Map.of(), () -> { throw new IllegalStateException(secret); });

        assertTrue(result.contains("TOOL_EXECUTION_FAILED"));
        assertFalse(result.contains("select *"));
        assertFalse(result.contains("C:\\Users"));
        assertFalse(listener.events.stream().anyMatch(event -> event.contains("select *") || event.contains("C:\\Users")));
    }

    @Test
    void emitsStartedThenCompletedWithSameCallId() {
        CapturingListener listener = new CapturingListener();
        AgentToolRuntime runtime = new AgentToolRuntime(new AgentToolCatalog(), mock(AiToolTraceService.class));

        runtime.execute(context(AiToolBudget.chatTurnDefaults(), listener), "getUserProfile", Map.of(), () -> "{}");

        assertTrue(listener.events.get(0).startsWith("started:"));
        assertTrue(listener.events.get(1).startsWith("completed:"));
        assertEqualsCallId(listener.events.get(0), listener.events.get(1));
    }

    private static V2ChatToolContext.Context context(AiToolBudget budget,
        V2ChatToolContext.ToolLifecycleListener listener) {
        return V2ChatToolContext.init(7L, null, null, "run-1", budget, listener);
    }

    private static void assertEqualsCallId(String started, String completed) {
        String startedId = started.split(":")[1];
        String completedId = completed.split(":")[1];
        assertNotEquals("", startedId);
        assertTrue(startedId.equals(completedId));
    }

    private static final class CapturingListener implements V2ChatToolContext.ToolLifecycleListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onStarted(String runId, String callId, String toolName, String category) {
            events.add("started:" + callId);
        }

        @Override
        public void onCompleted(String runId, String callId, String toolName, String status,
            String code, long durationMs) {
            events.add("completed:" + callId + ":" + code);
        }

        @Override
        public void onConfirmationRequired(String runId, String callId, String summary,
            String confirmationToken) {
            events.add("confirmation:" + callId);
        }
    }
}
