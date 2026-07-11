## Why

AI 推荐核心能力已可用，但静态审计发现流式聊天锁在模型生成结束前释放、事实型聊天存在两条证据范围不同的 RAG 路径、草稿写工具缺少后端强制确认和贯穿链路的运行关联标识。这些问题会在并发、提示注入或事后排查场景中降低结果的可靠性和可解释性。

本变更先完成第一阶段的正确性与安全边界加固，不包含线程池隔离、长期记忆、SSE 事件重构和持久化可观测平台建设。

## What Changes

- 将聊天 Redis 锁的持有期覆盖完整的流式模型执行周期，并采用持有者校验的安全释放方式。
- 将聊天 RAG 路由区分为通用知识问答和候选/专业事实解释；后者仅使用带 Metadata Filter 的程序证据检索。
- 将草稿写操作改为“提案 -> 显式确认 -> 执行”的两阶段协议，阻止模型单次自然语言输入直接修改草稿。
- 新增贯穿 HTTP 请求、聊天、工具调用、RAG 证据和草稿动作的 `runId`，以支持关联审计和问题定位。
- 为上述行为补充针对并发、路由、确认和关联标识的测试。

## Capabilities

### New Capabilities

- `streaming-chat-concurrency`: 保证同一用户的流式对话在完整运行周期内串行化，并在异常后可恢复。
- `agent-action-confirmation`: 对 Agent 发起的草稿写操作执行后端强制的预览、确认和审计。
- `agent-run-correlation`: 为一次 Agent 运行及其工具、RAG、草稿动作分配和传播关联标识。

### Modified Capabilities

- `rag-search-testing`: 为事实型聊天增加受 Metadata 约束的证据检索路由，避免通用检索混入专业事实回答。
- `rag-trace-observability`: Trace 增加 runId、检索路由和工具/证据关联字段。

## Impact

- 主要影响 `AiChatServiceImpl`、`RagConfig`、`RagEvidenceService`、`V2ChatTools`、`V2DraftActionTools`、`AppV2RecommendController` 及用户端聊天交互。
- 草稿写工具的调用契约将改变：写工具不再直接完成变更，客户端必须提交一次性确认令牌。
- 不新增外部基础设施；Redis 继续用于短期锁、确认令牌和运行上下文。
