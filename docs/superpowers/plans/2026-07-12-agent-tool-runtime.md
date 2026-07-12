# Agent Tool Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重写现有 LangChain4j Tool 和业务服务的前提下，接入生产 Tool Budget、统一错误协议、MySQL Tool Trace、SSE 生命周期事件及管理端观测。

**Architecture:** 保留 `V2BoundChatTools` 与 `V2BoundDraftActionTools`，所有调用统一经过 `AgentToolRuntime`。Runtime 依据 `AgentToolCatalog` 消费当前 `runId` 的线程安全 Budget，调用原有 delegate，标准化结果，并以 fail-open 方式写入 MySQL Trace、推送 SSE；权限、预算、确认和草稿版本冲突保持 fail-closed。

**Tech Stack:** Java 17、Spring Boot 4、LangChain4j 1.15.0、MyBatis、MySQL、Redis、SSE、Vue 2 管理端、Vue 3 用户端、JUnit 5、Mockito。

## Global Constraints

- 不引入 Tool Search、MCP、Subagent、Spring AOP 或新的通用 Hook 框架。
- 不改 RAG 检索策略、推荐规则和候选池业务。
- 底层业务 Tool 第一阶段继续返回 `String`。
- 不保存完整 Prompt、原始 Tool 参数、完整 Tool 结果、确认 Token 或异常堆栈。
- Trace 写入失败不得阻断 Tool；预算、确认和冲突校验失败必须阻断执行。
- 保留现有 `tool_call`、`done.toolActionResult` 和确认接口兼容行为。
- 删除只减少一所；替换前后总数一致；任何隐式自动补位均视为回归。
- 每个任务只提交该任务文件，不夹带其他工作区变更。

---

## File Map

**New runtime files**

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolDefinition.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolCatalog.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolResult.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolRuntime.java`

**New persistence files**

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/domain/AiToolCallTrace.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/mapper/AiToolCallTraceMapper.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/service/AiToolTraceService.java`
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/AiToolCallTraceMapper.xml`
- `sql/ai_tool_call_trace.sql`

---

### Task 1: Tool Catalog And Thread-Safe Budget

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolDefinition.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolCatalog.java`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolCatalogTest.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/ai/AiToolBudget.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/domain/AgentRunContext.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/ai/AiToolBudgetTest.java`

**Interfaces:**
- Produces: `AgentToolCatalog.require(String)` and `definitions()`.
- Produces: `AiToolBudget.tryUse(BudgetGroup, int)` returning `BudgetDecision`.
- Produces: `AgentRunContext.getToolBudget()`.

- [ ] **Step 1: Write failing Catalog and Budget tests**

Add these assertions:

    @Test
    void catalogCoversEveryBoundToolExactlyOnce() {
        AgentToolCatalog catalog = new AgentToolCatalog();
        Set<String> annotated = Stream.of(V2BoundChatTools.class, V2BoundDraftActionTools.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .map(Method::getName)
            .collect(Collectors.toSet());
        assertEquals(16, annotated.size());
        assertEquals(annotated, catalog.definitions().stream()
            .map(AgentToolDefinition::name)
            .collect(Collectors.toSet()));
    }

    @Test
    void fourthSearchAndSecondWriteAreRejected() {
        AiToolBudget budget = AiToolBudget.chatTurnDefaults();
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertTrue(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());
        assertFalse(budget.tryUse(BudgetGroup.SEARCH, 100).allowed());

        AiToolBudget writeBudget = AiToolBudget.chatTurnDefaults();
        assertTrue(writeBudget.tryUse(BudgetGroup.WRITE, 100).allowed());
        assertFalse(writeBudget.tryUse(BudgetGroup.WRITE, 100).allowed());
    }

- [ ] **Step 2: Run focused tests and verify failure**

    mvn -pl ruoyi-postgrad -am -Dtest=AgentToolCatalogTest,AiToolBudgetTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL because Catalog and group-based Budget types do not exist.

- [ ] **Step 3: Implement immutable metadata and complete Catalog**

Public contract:

    public record AgentToolDefinition(
        String name,
        ToolCategory category,
        AiToolBudget.BudgetGroup budgetGroup,
        int estimatedResultTokens,
        boolean confirmationRequired,
        boolean concurrencySafe
    ) {
        public enum ToolCategory { READ, WRITE, USER_INTERACTION }
    }

Register exactly:

    getProgramDetail             READ  DETAIL   500  false true
    getProgramEvidence           READ  RAG      800  false true
    searchPrograms               READ  SEARCH   700  false true
    comparePrograms              READ  DETAIL   700  false true
    searchKnowledgeBase          READ  RAG      800  false true
    getUserProfile               READ  CONTEXT  300  false true
    analyzeDraftHealth           READ  CONTEXT  500  false true
    getDraftContext              READ  CONTEXT  500  false true
    getRefillCandidates          READ  SEARCH   500  false true
    removeDraftCandidate         WRITE WRITE    300  true  false
    addDraftCandidate            WRITE WRITE    300  true  false
    confirmRefillCandidate       WRITE WRITE    300  true  false
    replaceDraftCandidate        WRITE WRITE    400  true  false
    fillTier                     WRITE WRITE    400  true  false
    batchRemoveDraftCandidates   WRITE WRITE    500  true  false
    batchReplaceDraftCandidates  WRITE WRITE    600  true  false

Constructor validation reflects both Bound classes and throws `IllegalStateException` unless reflected names equal registered names.

- [ ] **Step 4: Replace string matching with synchronized group accounting**

Exact nested types:

    public enum BudgetGroup { SEARCH, DETAIL, RAG, CONTEXT, WRITE, OTHER }

    public record BudgetDecision(
        boolean allowed,
        String code,
        int totalReadCalls,
        int groupCalls,
        int estimatedResultTokens
    ) {}

`chatTurnDefaults()` uses read total 6, token estimate 3000, and group limits SEARCH=3, DETAIL=2, RAG=2, CONTEXT=3, WRITE=1. Implement synchronized `tryUse` with an `EnumMap`. WRITE does not increment read total, but all accepted results consume estimated-result budget. Preserve `reportDefaults()` with equivalent larger group limits.

Add `AiToolBudget toolBudget` to `AgentRunContext`; `start(userId)` creates chat defaults.

- [ ] **Step 5: Run tests and commit**

Expected: focused tests PASS.

    git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/ai/AiToolBudget.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/domain/AgentRunContext.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/ai/AiToolBudgetTest.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolCatalogTest.java
    git commit -m "feat(agent): add tool catalog and runtime budget"

---

### Task 2: Persist Tool Trace In MySQL

**Files:**
- Create: `sql/ai_tool_call_trace.sql`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/domain/AiToolCallTrace.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/mapper/AiToolCallTraceMapper.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/service/AiToolTraceService.java`
- Create: `ruoyi-postgrad/src/main/resources/mapper/postgrad/AiToolCallTraceMapper.xml`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/observe/tool/service/AiToolTraceServiceTest.java`

**Interfaces:**
- Produces: insert, complete, cancel, listRecent and listByRunId.
- Query methods compute display-only `EXPIRED` status.

- [ ] **Step 1: Write failing service tests**

    @Test
    void completesTraceWithSanitizedTerminalFields() {
        AiToolCallTraceMapper mapper = mock(AiToolCallTraceMapper.class);
        AiToolTraceService service = new AiToolTraceService(mapper);
        service.complete("call-1", "SUCCEEDED", "OK", false,
            "3 candidates", 120, 42L, null);
        verify(mapper).complete("call-1", "SUCCEEDED", "OK", false,
            "3 candidates", 120, 42L, null);
    }

    @Test
    void displaysExpiredWithoutUpdatingDatabase() {
        AiToolCallTrace row = new AiToolCallTrace();
        row.setStatus("WAITING_CONFIRMATION");
        row.setConfirmationExpiresAt(LocalDateTime.now().minusMinutes(1));
        AiToolCallTraceMapper mapper = mock(AiToolCallTraceMapper.class);
        when(mapper.selectRecent(50)).thenReturn(List.of(row));
        List<AiToolCallTrace> result = new AiToolTraceService(mapper).listRecent(50);
        assertEquals("EXPIRED", result.get(0).getStatus());
        verify(mapper, never()).updateStatus(anyString(), anyString());
    }

- [ ] **Step 2: Run test and verify failure**

    mvn -pl ruoyi-postgrad -am -Dtest=AiToolTraceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test

- [ ] **Step 3: Add migration**

    CREATE TABLE IF NOT EXISTS ai_tool_call_trace (
        id BIGINT NOT NULL AUTO_INCREMENT,
        call_id VARCHAR(36) NOT NULL,
        parent_call_id VARCHAR(36) NULL,
        run_id VARCHAR(36) NOT NULL,
        user_id BIGINT NOT NULL,
        operation_key VARCHAR(64) NOT NULL,
        tool_name VARCHAR(64) NOT NULL,
        tool_category VARCHAR(24) NOT NULL,
        budget_group VARCHAR(24) NOT NULL,
        status VARCHAR(32) NOT NULL,
        result_code VARCHAR(64) NULL,
        retryable TINYINT(1) NOT NULL DEFAULT 0,
        confirmation_required TINYINT(1) NOT NULL DEFAULT 0,
        arguments_summary VARCHAR(1000) NULL,
        result_summary VARCHAR(1000) NULL,
        result_token_estimate INT NOT NULL DEFAULT 0,
        duration_ms BIGINT NULL,
        started_at DATETIME(3) NOT NULL,
        completed_at DATETIME(3) NULL,
        confirmation_expires_at DATETIME(3) NULL,
        PRIMARY KEY (id),
        UNIQUE KEY uk_tool_call_id (call_id),
        KEY idx_tool_run_time (run_id, started_at),
        KEY idx_tool_status_time (status, started_at),
        KEY idx_tool_user_time (user_id, started_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Tool 调用追踪';

Append the manual retention statement:

    DELETE FROM ai_tool_call_trace
    WHERE started_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

- [ ] **Step 4: Implement JavaBean, Mapper and XML**

`AiToolCallTrace` exposes JavaBean accessors for every SQL column using `Long`, `Integer`, `Boolean`, `String` and `LocalDateTime`.

Mapper contract:

    int insert(AiToolCallTrace trace);
    int complete(String callId, String status, String resultCode,
                 boolean retryable, String resultSummary,
                 int resultTokenEstimate, long durationMs,
                 LocalDateTime confirmationExpiresAt);
    int updateStatus(String callId, String status);
    List<AiToolCallTrace> selectRecent(int limit);
    List<AiToolCallTrace> selectByRunId(String runId, int limit);

Use `@Param` on every scalar. XML sets `completed_at=NOW(3)` except for `WAITING_CONFIRMATION`, orders queries by `started_at DESC`, and never selects more than 100 rows.

- [ ] **Step 5: Implement service and rerun test**

Public methods:

    void insert(AiToolCallTrace trace)
    void complete(String callId, String status, String code, boolean retryable,
                  String resultSummary, int resultTokenEstimate,
                  long durationMs, LocalDateTime confirmationExpiresAt)
    void cancel(String callId)
    List<AiToolCallTrace> listRecent(int limit)
    List<AiToolCallTrace> listByRunId(String runId, int limit)

Clamp limits to 1..100. Convert expired waiting rows only in returned objects. Expected: test PASS.

- [ ] **Step 6: Commit**

    git add sql/ai_tool_call_trace.sql ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool ruoyi-postgrad/src/main/resources/mapper/postgrad/AiToolCallTraceMapper.xml ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/observe/tool/service/AiToolTraceServiceTest.java
    git commit -m "feat(observe): persist agent tool traces"

---

### Task 3: Standard Result And Unified Runtime

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolResult.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolRuntime.java`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolResultTest.java`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolRuntimeTest.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/V2ChatToolContext.java`

**Interfaces:**
- Produces: `execute(Context, String, Map<String,Object>, Supplier<String>)`.
- Produces: `rejectWithoutExecution(...)` for LangChain4j handlers.
- Produces: call-ID scope and Tool lifecycle listener.

- [ ] **Step 1: Write failing result tests**

    @Test
    void normalizesEmptyArrayAsNoData() {
        JSONObject value = JSON.parseObject(
            AgentToolResult.normalize("[]", "call-1", "run-1", "searchPrograms"));
        assertTrue(value.getBooleanValue("ok"));
        assertEquals("NO_DATA", value.getString("code"));
    }

    @Test
    void preservesConfirmationWithoutInventingToken() {
        String raw = "{\"ok\":true,\"confirmationRequired\":true,\"summary\":\"replace\"}";
        JSONObject value = JSON.parseObject(
            AgentToolResult.normalize(raw, "call-1", "run-1", "replaceDraftCandidate"));
        assertEquals("WAITING_CONFIRMATION", value.getString("code"));
        assertTrue(value.getBooleanValue("confirmationRequired"));
        assertFalse(value.containsKey("confirmationToken"));
    }

- [ ] **Step 2: Write failing Runtime tests**

Cover:

    budgetRejectionDoesNotInvokeDelegate()
    traceFailureDoesNotBlockSuccessfulTool()
    executionFailureReturnsSanitizedError()
    emitsStartedThenCompletedWithSameCallId()

The exception test throws text containing a SQL statement and local path, then asserts neither appears in returned JSON or listener payload.

- [ ] **Step 3: Run tests and verify failure**

    mvn -pl ruoyi-postgrad -am -Dtest=AgentToolResultTest,AgentToolRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test

- [ ] **Step 4: Implement normalization**

Exact API:

    static String normalize(String raw, String callId, String runId, String toolName)
    static String error(String code, String message, boolean retryable,
                        String callId, String runId, String toolName)
    static Summary summarize(String normalizedJson)

    record Summary(String status, String code, boolean retryable,
                   boolean confirmationRequired, String resultSummary,
                   int tokenEstimate) {}

Rules: preserve existing `ok` objects; wrap other JSON in `data`; blank/empty arrays become `NO_DATA`; confirmation becomes `WAITING_CONFIRMATION`; summaries max 1000 chars; token estimate is `ceil(length/2.0)`.

- [ ] **Step 5: Add call-ID scope and Runtime**

Add `CURRENT_CALL_ID` and:

    public static String currentCallId()

    public static <T> T callWith(Context context, String callId, Supplier<T> supplier)

The overload restores previous Context and call ID in `finally`. Extend Context with Budget and nested listener; preserve old init overloads with default Budget and no-op listener.

Runtime sequence:

    create callId
    require definition
    emit started
    try budget
    insert STARTED trace best-effort
    reject without delegate when budget fails
    execute delegate inside callWith(context, callId, supplier)
    normalize and summarize
    complete SUCCEEDED or WAITING_CONFIRMATION best-effort
    emit completed and optional confirmation event
    catch Throwable and return sanitized TOOL_EXECUTION_FAILED

For a definition with `confirmationRequired=true`, Runtime reads `context.lastActionResultJson()` only after the delegate returns. It emits `onConfirmationRequired` from that UI-only JSON, while the normalized Tool result returned to the LLM continues to exclude `confirmationToken`.

`rejectWithoutExecution` must not call `Catalog.require` for `TOOL_NOT_FOUND`; unknown names are persisted as category/budget group `OTHER` with terminal status `REJECTED`.

Nested listener:

    void onStarted(String runId, String callId, String toolName, String category)
    void onCompleted(String runId, String callId, String toolName,
                     String status, String code, long durationMs)
    void onConfirmationRequired(String runId, String callId,
                                String summary, String confirmationToken)

- [ ] **Step 6: Run tests and commit**

Expected: focused tests PASS.

    git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/V2ChatToolContext.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolResult.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/AgentToolRuntime.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/runtime
    git commit -m "feat(agent): add governed tool runtime"

---

### Task 4: Integrate All Tools, LangChain Errors And SSE

**Files:**
- Modify: `V2BoundChatTools.java` and `V2BoundDraftActionTools.java`.
- Modify: `ChatStreamCallback.java` and `AiChatServiceImpl.java`.
- Modify: `AppV2RecommendController.java`.
- Modify: `AiChatServiceImplTest.java` and `AgentRuntimeFlowContractTest.java`.
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/BoundToolRuntimeContractTest.java`.

**Interfaces:**
- Consumes Runtime and Run Budget.
- Produces old `tool_call` plus new `tool_result` and `confirmation_required`.

- [ ] **Step 1: Add failing lifecycle contract**

Capture callbacks and assert the same call ID appears in order:

    tool_call:<callId>
    tool_result:<callId>:<code>

Add `BoundToolRuntimeContractTest`: construct both Bound wrappers with mocked delegates and a mocked Runtime, invoke all 9 read methods and all 7 write methods once, then assert `verify(runtime, times(16)).execute(...)`. Because the Runtime mock does not invoke suppliers, also assert `verifyNoInteractions(readDelegate, writeDelegate)`. This proves no wrapper bypasses governance.

- [ ] **Step 2: Run focused tests and verify failure**

    mvn -pl ruoyi-postgrad -am -Dtest=AiChatServiceImplTest,AgentRuntimeFlowContractTest,BoundToolRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test

- [ ] **Step 3: Route all 16 Bound methods through Runtime**

Constructors accept Runtime. Each method invokes Runtime exactly once. Safe arguments:

    getProgramDetail: programId
    getProgramEvidence: programId, truncated question
    searchPrograms: truncated keyword, tier, region
    comparePrograms: programId1, programId2
    searchKnowledgeBase: truncated question
    getUserProfile/analyzeDraftHealth/getDraftContext: empty
    getRefillCandidates: tier, count
    writes: IDs, tier, collection size only

Nullable strings use `LinkedHashMap`, never `Map.of`.

Example:

    return runtime.execute(context, "getProgramDetail",
        Map.of("programId", programId),
        () -> delegate.getProgramDetail(programId));

- [ ] **Step 4: Add backward-compatible callbacks and SSE**

Add default methods:

    onToolCall(String toolName, String callId, String runId, String category)
    onToolResult(String toolName, String callId, String runId,
                 String status, String code, long durationMs)
    onConfirmationRequired(String callId, String runId,
                           String summary, String confirmationToken)

Controller emits only approved fields. Keep old callbacks and `done.toolActionResult`.

- [ ] **Step 5: Configure verified LangChain4j 1.15.0 handlers**

Add to the AI Service builder:

    hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.from(
        request, runtime.rejectWithoutExecution(toolContext, request.name(),
            "TOOL_NOT_FOUND", "请求的工具不存在，请选择已提供的工具。", true)))

    toolArgumentsErrorHandler((error, ctx) -> ToolErrorHandlerResult.text(
        runtime.rejectWithoutExecution(toolContext,
            ctx.toolExecutionRequest().name(), "INVALID_ARGUMENTS",
            "工具参数无效，请修正参数后重试。", true)))

    toolExecutionErrorHandler((error, ctx) -> ToolErrorHandlerResult.text(
        runtime.rejectWithoutExecution(toolContext,
            ctx.toolExecutionRequest().name(), "TOOL_EXECUTION_FAILED",
            "工具执行失败，请基于已有信息继续。", false)))

Initialize Context with `AgentRunContext.getToolBudget()` and a callback listener. Remove full partial arguments and full result logs.

- [ ] **Step 6: Run tests and commit**

    mvn -pl ruoyi-admin -am -Dtest=AiChatServiceImplTest,AgentRuntimeFlowContractTest,BoundToolRuntimeContractTest,AgentActionConfirmationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: PASS.

    git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/V2BoundChatTools.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/V2BoundDraftActionTools.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/service/ChatStreamCallback.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/service/impl/AiChatServiceImpl.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppV2RecommendController.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/impl/AiChatServiceImplTest.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/impl/AgentRuntimeFlowContractTest.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/tool/BoundToolRuntimeContractTest.java
    git commit -m "feat(agent): govern tool execution and stream lifecycle"

---

### Task 5: Trace Confirmation And Explicit Cancellation

**Files:**
- Modify: `AgentActionProposal.java`, `AgentActionProposalService.java`, `AgentActionConfirmationService.java`.
- Modify: `V2DraftActionTools.java` and `AppV2RecommendController.java`.
- Modify: `user-ui/src/api/recommend-v2.js` and `AiRecommendV2Workspace.vue`.
- Modify: Java confirmation tests and `user-ui/tests/agentActionConfirmation.contract.test.mjs`.

**Interfaces:**
- Produces proposal `parentCallId` and `expiresAt`.
- Produces `AgentActionConfirmationService.cancel(userId, token)`.
- Produces `POST /app/ai-recommend-v2/chat/action/cancel`.

- [ ] **Step 1: Write failing proposal/confirmation/cancel tests**

Assert proposal parent/expiry, confirmation child Trace, conflict child status, and:

    Map<String,Object> cancelled = service.cancel(7L, "token");
    assertEquals(true, cancelled.get("ok"));
    verify(traceService).cancel("call-parent");
    verifyNoInteractions(tools);

- [ ] **Step 2: Run tests and verify failure**

    mvn -pl ruoyi-postgrad -am -Dtest=AgentActionProposalServiceTest,AgentActionConfirmationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
    Push-Location user-ui; node tests/agentActionConfirmation.contract.test.mjs; Pop-Location

- [ ] **Step 3: Extend proposal**

Add:

    private String parentCallId;
    private LocalDateTime expiresAt;

Change signature:

    propose(Long userId, String runId, String parentCallId, String action,
            Map<String,Object> arguments, String summary, String draftHash)

Set expiry to now plus the existing five-minute TTL. `V2DraftActionTools` passes `V2ChatToolContext.currentCallId()`.

- [ ] **Step 4: Add child Trace and cancel behavior**

Confirmation creates child Trace with `operation_key=confirmed-action` before draft hash validation. Conflict completes child as `CONFLICT/BUSINESS_CONFLICT` without mutation. Success/failure completes child accordingly.

`cancel` atomically consumes proposal, changes parent Trace to `CANCELLED`, returns `ok/runId/action`, and never calls a draft Tool.

- [ ] **Step 5: Add endpoint and user behavior**

Add API:

    export function cancelAgentAction(confirmationToken) {
      return request({
        url: '/app/ai-recommend-v2/chat/action/cancel',
        method: 'post',
        data: { confirmationToken }
      })
    }

Store `confirmation_required` as `pendingAction` during SSE. On `done` use pending action or legacy `toolActionResult`. On Element Plus `cancel` or `close`, call cancel API and do not reload draft.

- [ ] **Step 6: Run tests and commit**

    mvn -pl ruoyi-admin -am -Dtest=AgentActionProposalServiceTest,AgentActionConfirmationServiceTest,AgentActionConfirmationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
    Push-Location user-ui; node tests/agentActionConfirmation.contract.test.mjs; Pop-Location

Expected: PASS.

    git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/domain/AgentActionProposal.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/service/AgentActionProposalService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/service/AgentActionConfirmationService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/V2DraftActionTools.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppV2RecommendController.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/AgentActionProposalServiceTest.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/AgentActionConfirmationServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/postgrad/AgentActionConfirmationControllerTest.java user-ui/src/api/recommend-v2.js user-ui/src/views/ai-recommend-v2/AiRecommendV2Workspace.vue user-ui/tests/agentActionConfirmation.contract.test.mjs
    git commit -m "feat(agent): trace confirmed and cancelled actions"

---

### Task 6: Expose Tool Trace In AI Observe Console

**Files:**
- Modify: `AiObserveService.java`, `AiObserveServiceImpl.java`, `AiObserveController.java`.
- Modify: corresponding Java tests.
- Modify: `ruoyi-ui/src/api/postgrad/aiObserve.js` and `ruoyi-ui/src/views/postgrad/observe/index.vue`.
- Create: `ruoyi-ui/tests/aiToolTrace.contract.test.mjs`.

**Interfaces:**
- Produces `GET /postgrad/ai-observe/tool-traces?limit=50`.
- Produces `GET /postgrad/ai-observe/runs/{runId}/tool-traces`.

- [ ] **Step 1: Write failing Controller and frontend contracts**

Controller test mocks recent and Run lists and verifies Ajax success. Node contract asserts both URLs, `el-table`, `runId`, `resultCode`, `durationMs` and Run drawer.

- [ ] **Step 2: Run and verify failure**

    mvn -pl ruoyi-admin -am -Dtest=AiObserveControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
    Push-Location ruoyi-ui; node tests/aiToolTrace.contract.test.mjs; Pop-Location

- [ ] **Step 3: Add secured service and Controller methods**

Service:

    List<AiToolCallTrace> listRecentToolTraces(int limit);
    List<AiToolCallTrace> listRunToolTraces(String runId);

Both endpoints retain `postgrad:aiObserve:view`. Reject blank Run ID. Limit recent and Run results to 100.

- [ ] **Step 4: Add API and compact operational UI**

API:

    getRecentToolTraces(limit = 50)
    getRunToolTraces(runId)

Add one full-width panel below current grids, not a nested card. Columns:

    startedAt | runId | toolName | toolCategory
    status | resultCode | durationMs | budgetGroup

Click Run ID to open `el-drawer`. Show summaries only inside drawer; text wraps and page does not overflow horizontally.

- [ ] **Step 5: Test, build and commit**

    mvn -pl ruoyi-admin -am -Dtest=AiObserveControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
    Push-Location ruoyi-ui; node tests/aiToolTrace.contract.test.mjs; npm run build:prod; Pop-Location

Expected: tests PASS and production build exits 0.

    git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/service/AiObserveService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/service/impl/AiObserveServiceImpl.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiObserveController.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/observe/service/impl/AiObserveServiceImplTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/postgrad/AiObserveControllerTest.java ruoyi-ui/src/api/postgrad/aiObserve.js ruoyi-ui/src/views/postgrad/observe/index.vue ruoyi-ui/tests/aiToolTrace.contract.test.mjs
    git commit -m "feat(observe): expose agent tool traces"

---

### Task 7: Full Regression, Database Acceptance And Documentation

**Files:**
- Modify: `docs/AI推荐链路完整文档.md`
- Modify: `docs/AI推荐链路生产审计报告.md`
- Modify: `README.md`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/impl/DraftMutationServiceImplCoreTest.java`
- Verify all files from Tasks 1-6.

- [ ] **Step 1: Lock the no-implicit-refill baseline with regression tests**

Add one test that removes a candidate and verifies `refillPolicyService.evaluate(...)` is never called and draft count decreases by one. Add one replacement test with an `auto` refill fixture available, then verify total count remains unchanged and the refill service is never called.

- [ ] **Step 2: Run complete backend regression**

    mvn -pl ruoyi-postgrad,ruoyi-admin -am test

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run frontend contracts and builds**

    Push-Location user-ui
    node tests/agentActionConfirmation.contract.test.mjs
    npm run build
    Pop-Location

    Push-Location ruoyi-ui
    node tests/aiToolTrace.contract.test.mjs
    npm run build:prod
    Pop-Location

Expected: contracts print success and builds exit 0.

- [ ] **Step 4: Apply migration and verify restart persistence**

Apply `sql/ai_tool_call_trace.sql` to configured local MySQL. Execute one read Tool call, restart backend, then query:

    SELECT call_id, run_id, tool_name, status, result_code, duration_ms
    FROM ai_tool_call_trace
    ORDER BY started_at DESC
    LIMIT 10;

Expected: pre-restart row remains and contains no prompt, Token or raw exception.

- [ ] **Step 5: Execute four acceptance scenarios**

1. Ask why a recommended program is suitable: expect `getProgramEvidence` and matching RAG Trace under one Run ID.
2. Run four SEARCH calls in a test turn: expect fourth `REJECTED/TOOL_BUDGET_EXCEEDED` and only three delegate calls.
3. Request replacement then cancel: expect parent `CANCELLED` and unchanged draft.
4. Request replacement then confirm: expect parent waiting, child succeeded and unchanged total draft count.

- [ ] **Step 6: Update docs using verified facts only**

Document Catalog groups/limits, error codes, Trace privacy/retention, SSE order, confirmation/cancel behavior, observe endpoints, and actual commands/results. Do not claim unmeasured token savings or performance improvements.

- [ ] **Step 7: Review diff and commit**

    git diff --check
    git status --short
    git diff --stat

Confirm no secret, Token, build output, IDE file or unrelated change is staged.

    git add README.md docs/AI推荐链路完整文档.md docs/AI推荐链路生产审计报告.md ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/recommend/service/impl/DraftMutationServiceImplCoreTest.java
    git commit -m "docs: document agent tool runtime operations"

---

## Completion Gate

- All 16 Tool methods pass Catalog validation and Runtime execution.
- Budget rejection prevents delegate execution.
- Errors returned to LLM and SSE are sanitized.
- Tool Trace survives backend restart and is queryable by Run ID.
- Proposal, cancellation, conflict and confirmed execution are distinguishable.
- Existing RAG Trace and `done.toolActionResult` remain functional.
- Replacement never changes total count or triggers implicit refill.
- Backend tests, frontend contracts and both production builds pass.
- AI 观测台 exposes recent calls and Run detail without sensitive raw content.
