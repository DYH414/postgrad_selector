# Agent Tool Runtime 治理与可观测设计

## 1. 背景与目标

当前 AI 对话已经具备 LangChain4j Tool Calling、写操作确认、`runId`、RAG Trace、SSE 和模型调用指标，但 Tool 治理仍分散在 Bound Tool、业务 Tool、回调和日志中：

- `AiToolBudget` 尚未进入生产执行链。
- Tool 成功、无数据、参数错误和执行异常缺少统一结果协议。
- Tool 仅推送调用开始事件，缺少结果、错误码和耗时。
- Tool、模型、RAG 和写操作审计虽然包含 `runId`，但无法从持久化数据完整还原一次运行。
- 当前内存观测数据在服务重启后丢失，多实例之间不共享。

本设计以最低改造成本建立统一的 Agent Tool Runtime，完成以下目标：

1. 对全部 16 个 Tool 建立显式分类和预算元数据。
2. 在 Tool 真正执行前实施线程安全的单轮调用预算。
3. 统一 Tool 返回结果、错误码和脱敏策略。
4. 通过 MySQL 持久化 Tool Trace，并用 `runId` 关联模型、RAG 和 SSE。
5. 在现有 AI 观测台展示最近 Tool 调用和单次 Run 详情。

## 2. 范围与非目标

### 2.1 本期范围

- Tool Catalog 与 Tool Runtime。
- Tool Budget 生产接入。
- Tool Result 标准化与 LangChain4j 错误处理器。
- MySQL Tool Trace。
- SSE Tool 生命周期事件。
- AI 观测台 Tool Trace 表格和 Run 详情。
- 写操作提案与确认执行的父子 Trace。

### 2.2 非目标

- 不引入 Tool Search、MCP、Subagent 或通用 Hook 平台。
- 不改造 RAG 检索策略和草稿推荐业务。
- 不把 16 个 Tool 重写成 Programmatic `ToolProvider`。
- 不保存完整 Prompt、完整 Tool 参数、完整 Tool 结果或原始异常堆栈。
- 不在本期实现复杂 Trace 图表和数据导出。

自动补位已从业务链路移除，本设计以“删除只减少一所、替换保持数量不变、补齐只能显式触发”为回归基线。

## 3. 总体架构

```text
AiChatServiceImpl
  -> AgentRunContext(runId, userId, AiToolBudget)
  -> V2BoundChatTools / V2BoundDraftActionTools
  -> AgentToolRuntime.execute(...)
       1. AgentToolCatalog 查询元数据
       2. BudgetGuard 原子消费预算
       3. AiToolTraceService 写入 STARTED
       4. 执行原有 delegate Tool
       5. AgentToolResult 标准化
       6. AiToolTraceService 更新终态
       7. ToolEventListener 推送 SSE
  -> LangChain4j 将标准结果回写 LLM
```

`AgentToolRuntime` 只负责治理，不包含招生查询、RAG、草稿修改或候选池业务。

Tool 生命周期监听器作为 `AgentToolRuntime` 内部接口定义，由 `V2ChatToolContext.Context` 持有当前 SSE 适配实例，不再为监听器单独增加顶层类型。

## 4. 组件设计

### 4.1 AgentToolDefinition

Tool 元数据定义包含：

```text
name
category: READ | WRITE | USER_INTERACTION
budgetGroup: SEARCH | DETAIL | RAG | CONTEXT | WRITE | OTHER
estimatedResultTokens
confirmationRequired
concurrencySafe
```

### 4.2 AgentToolCatalog

Catalog 是 16 个 Tool 元数据的唯一来源。应用启动时通过反射校验两个 Bound Tool 中的所有 `@Tool` 方法：

- 每个 Tool 必须存在定义。
- Tool 名称不得重复。
- Catalog 不得包含已经删除的 Tool。

第一阶段使用显式 Java 注册表，不依赖 Spring AOP。

### 4.3 AgentToolRuntime

统一入口：

```java
execute(context, toolName, safeArguments, supplier)
```

执行规则：

1. Tool 未注册时拒绝执行。
2. Budget 超限时拒绝执行，不能调用 delegate。
3. Trace 插入失败时记录日志但继续执行，观测链路 fail-open。
4. Tool 业务异常统一转换为脱敏结果。
5. Budget、权限、确认和业务冲突属于强约束，必须 fail-closed。

### 4.4 AgentToolResult

统一协议：

```json
{
  "ok": false,
  "code": "TOOL_BUDGET_EXCEEDED",
  "message": "本轮工具调用次数已达到上限，请基于现有信息回答。",
  "retryable": false,
  "data": null,
  "meta": {
    "callId": "...",
    "runId": "...",
    "toolName": "searchPrograms"
  }
}
```

为降低迁移风险，底层业务 Tool 第一阶段继续返回 `String`，Runtime 兼容处理：

- 已有 `{ok,...}` JSON：保留顶层结构并补齐 `code`、`retryable` 和 `meta`。
- 普通 JSON 对象或数组：包装进 `data`。
- 空数组：返回 `ok=true, code=NO_DATA`。
- 普通文本：包装进 `data`。
- 异常：返回脱敏错误，不暴露原始异常。

现有 `confirmationRequired` 和 `confirmationToken` 顶层字段保持兼容。

## 5. Tool Budget

每个 `runId` 创建独立 Budget。Budget 由 Tool worker 共享，因此消费方法必须使用同步块或原子计数保证线程安全。

默认配置：

| 预算项 | 单轮上限 |
|---|---:|
| 读取 Tool 总数 | 6 |
| SEARCH | 3 |
| DETAIL | 2 |
| RAG | 2 |
| CONTEXT | 3 |
| WRITE 提案 | 1 |
| Tool 结果 Token 估算总量 | 3000 |

Tool 分组：

| Budget Group | Tool |
|---|---|
| SEARCH | `searchPrograms`, `getRefillCandidates` |
| DETAIL | `getProgramDetail`, `comparePrograms` |
| RAG | `getProgramEvidence`, `searchKnowledgeBase` |
| CONTEXT | `getUserProfile`, `getDraftContext`, `analyzeDraftHealth` |
| WRITE | 添加、删除、替换、补齐和批量修改 Tool |

所有阈值允许通过配置覆盖。一次对话最多生成一个 WRITE 提案，避免后一个确认令牌覆盖前一个提案。

## 6. 错误协议

| 错误码 | 含义 | LLM 可重试 |
|---|---|---:|
| `OK` | 正常完成 | 否 |
| `NO_DATA` | 查询成功但无数据 | 可调整条件 |
| `WAITING_CONFIRMATION` | 等待用户确认 | 否 |
| `TOOL_BUDGET_EXCEEDED` | 单轮预算耗尽 | 否 |
| `INVALID_ARGUMENTS` | 参数格式错误 | 是 |
| `TOOL_NOT_FOUND` | 模型调用不存在的 Tool | 是 |
| `TOOL_CONTEXT_MISSING` | Tool 上下文缺失 | 否 |
| `BUSINESS_CONFLICT` | 草稿版本冲突等业务冲突 | 否 |
| `DEPENDENCY_UNAVAILABLE` | MySQL、Redis 或 Qdrant 暂不可用 | 是 |
| `TOOL_EXECUTION_FAILED` | 未分类执行异常 | 否 |

LangChain4j AI Service 必须配置：

- `hallucinatedToolNameStrategy`
- `toolArgumentsErrorHandler`
- `toolExecutionErrorHandler`

服务端日志只记录 `runId`、`callId`、Tool 名称、状态、错误码和耗时，不再记录完整 Tool 参数与完整结果。

## 7. MySQL Tool Trace

新增表 `ai_tool_call_trace`：

| 字段 | 类型 | 约束与用途 |
|---|---|---|
| `id` | bigint | 主键，自增 |
| `call_id` | varchar(36) | 唯一调用 ID |
| `parent_call_id` | varchar(36) | 确认执行关联提案调用 |
| `run_id` | varchar(36) | 关联模型、RAG 和 SSE |
| `user_id` | bigint | 当前用户 |
| `operation_key` | varchar(64) | `ai-chat`、`confirmed-action` 等 |
| `tool_name` | varchar(64) | Tool 名称 |
| `tool_category` | varchar(24) | READ、WRITE、USER_INTERACTION |
| `budget_group` | varchar(24) | 预算分组 |
| `status` | varchar(32) | 调用状态 |
| `result_code` | varchar(64) | 标准结果码 |
| `retryable` | tinyint | 是否可重试 |
| `confirmation_required` | tinyint | 是否等待确认 |
| `arguments_summary` | varchar(1000) | 白名单脱敏参数摘要 |
| `result_summary` | varchar(1000) | 结果摘要，不保存完整结果 |
| `result_token_estimate` | int | 结果 Token 估算 |
| `duration_ms` | bigint | 执行耗时 |
| `started_at` | datetime(3) | 开始时间 |
| `completed_at` | datetime(3) | 终止时间 |
| `confirmation_expires_at` | datetime(3) | 确认过期时间 |

索引：

```sql
UNIQUE KEY uk_tool_call_id (call_id)
KEY idx_tool_run_time (run_id, started_at)
KEY idx_tool_status_time (status, started_at)
KEY idx_tool_user_time (user_id, started_at)
```

Tool Trace 默认保留 30 天。本期提供清理 SQL，不新增定时任务。管理端默认查询最近 50 条，单次 Run 最多返回 100 条。

## 8. 状态机与写操作确认

普通 Tool：

```text
STARTED
  -> SUCCEEDED
  -> FAILED
  -> REJECTED
```

写操作：

```text
STARTED
  -> WAITING_CONFIRMATION
       -> CANCELLED
       -> EXPIRED（查询时根据 expiresAt 计算）
       -> 创建子调用 CONFIRMED_ACTION
            -> SUCCEEDED
            -> FAILED
            -> CONFLICT
```

确认执行使用新的 `call_id`，通过 `parent_call_id` 关联提案，并沿用同一个 `run_id`。确认执行不重复消耗聊天查询预算，但继续执行一次性 Token、用户归属和草稿 Hash 校验。

用户点击“暂不调整”时，用户端调用：

```text
POST /app/ai-recommend-v2/chat/action/cancel
```

后端按当前用户原子消费提案 Token，并把父 Trace 更新为 `CANCELLED`。取消接口只改变提案状态，不执行任何草稿写操作。若用户关闭页面或没有明确取消，数据库保持 `WAITING_CONFIRMATION`，管理端查询时在 `confirmation_expires_at` 之后展示为计算状态 `EXPIRED`。

## 9. SSE 协议

保留现有 `run`、`tool_call`、`token`、`done` 和 `error` 事件，新增：

```text
tool_result
confirmation_required
```

`tool_result` 示例：

```json
{
  "runId": "...",
  "callId": "...",
  "tool": "replaceDraftCandidate",
  "status": "WAITING_CONFIRMATION",
  "code": "WAITING_CONFIRMATION",
  "durationMs": 83
}
```

`confirmation_required` 示例：

```json
{
  "runId": "...",
  "callId": "...",
  "summary": "将杭州电子科技大学替换为南京农业大学",
  "confirmationToken": "..."
}
```

`confirmationToken` 只发送给当前登录用户，不写数据库、不进入模型结果、不进入日志。第一阶段继续在 `done.toolActionResult` 保留确认信息，保证旧前端兼容。

## 10. 管理端观测

现有 AI 观测台增加“最近 Tool 调用”表格：

```text
时间 | Run ID | Tool | 类型 | 状态 | 错误码 | 耗时 | Budget Group
```

新增接口：

```text
GET /postgrad/ai-observe/tool-traces?limit=50
GET /postgrad/ai-observe/runs/{runId}/tool-traces
```

点击 `runId` 展示同一轮 Tool 调用，并与现有模型调用快照和 RAG Trace 使用同一个 `runId`。本期不新增独立页面。

## 11. 文件边界

新增：

```text
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/tool/runtime/
  AgentToolDefinition.java
  AgentToolCatalog.java
  AgentToolResult.java
  AgentToolRuntime.java

ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/observe/tool/
  domain/AiToolCallTrace.java
  mapper/AiToolCallTraceMapper.java
  service/AiToolTraceService.java

ruoyi-postgrad/src/main/resources/mapper/postgrad/AiToolCallTraceMapper.xml
sql/ai_tool_call_trace.sql
```

修改：

- `AiToolBudget.java`
- `AgentRunContext.java`
- `V2ChatToolContext.java`
- `V2BoundChatTools.java`
- `V2BoundDraftActionTools.java`
- `AgentActionConfirmationService.java`
- `AiChatServiceImpl.java`
- `ChatStreamCallback.java`
- `AppV2RecommendController.java`
- `AgentActionProposalService.java`
- `AiObserveController.java`
- `ruoyi-ui/src/api/postgrad/aiObserve.js`
- `ruoyi-ui/src/views/postgrad/observe/index.vue`

## 12. 测试策略

必须覆盖：

1. Catalog 恰好覆盖全部 16 个 `@Tool`，无遗漏和重名。
2. 第 4 次 SEARCH 被拒绝，delegate 未执行。
3. 单轮第二个 WRITE 提案被拒绝。
4. 空数组标准化为 `NO_DATA`，不记录为异常。
5. Tool 抛出数据库异常时，LLM、SSE 和 Trace 均不包含原始异常文本。
6. Tool Trace 数据库不可用时，读取 Tool 仍正常返回。
7. 写提案产生 `WAITING_CONFIRMATION`。
8. 用户确认后产生带 `parent_call_id` 的子 Trace。
9. 草稿变化后确认返回 `CONFLICT`，写操作不执行。
10. SSE 顺序满足 `run -> tool_call -> tool_result -> token/done`。
11. 服务重启后 MySQL Trace 仍可查询。
12. 管理端可按 `runId` 查询同一轮的 Tool 记录。
13. 删除操作只减少一所，替换操作前后总数一致，不发生隐式补位。

## 13. 验收标准

### 13.1 读取与 RAG

用户询问某个专业为什么被推荐时，管理端可以通过同一个 `runId` 查看：

```text
LLM 调用
getProgramEvidence: SUCCEEDED
RAG scoped-evidence
最终回复
```

Trace 必须展示 Tool 分类、预算组、结果码和耗时。

### 13.2 写操作

用户要求替换学校时：

```text
replaceDraftCandidate: WAITING_CONFIRMATION
用户确认
CONFIRMED_ACTION: SUCCEEDED
```

提案和确认通过 `parent_call_id` 关联，草稿总数保持不变。

### 13.3 Budget 与异常

- 超出 SEARCH 上限后 Tool 不再执行，模型收到 `TOOL_BUDGET_EXCEEDED`。
- 不存在的 Tool 和错误参数不会中断整个对话，模型可以基于标准错误修正。
- 依赖异常不会把 SQL、路径、凭证或堆栈返回模型与用户。
- Trace 数据库异常不会阻断正常 Tool 查询。
