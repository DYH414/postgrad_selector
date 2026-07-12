# AI 推荐链路生产审计报告

审计日期：2026-07-10  
审计范围：用户端推荐生成、AI 对话、Tool Calling、Redis 状态与记忆、SSE、RAG/Qdrant、性能并发和管理端观测。  
审计方式：静态代码审计、已有单元测试盘点、RAG 管理端真实检索观察，以及公开工程规范对照。未对生产流量、数据库容量、模型供应商 SLA 做压测，因此这份报告不把“代码存在”表述为“线上已验证”。

> **历史快照说明**：本文保留 2026-07-10 的审计结论，不是当前实现说明。后续已完成版本化 Index Run、Qdrant Alias 切换、Payload Index、统一检索入口和 AI Chat 真实 RAG Trace；当前链路以 [AI 推荐链路完整文档](./AI推荐链路完整文档.md) 与 [RAG / Qdrant 运维说明](./RAG-Qdrant-运维说明.md) 为准。

> **治理更新（已通过代码与契约测试）**：Tool Catalog、分组预算、标准化错误、MySQL Tool Trace、SSE 生命周期事件和写操作确认/取消链路已接入。Trace 采用摘要存储并可按 `runId` 查看；完整 Prompt、原始 Tool 参数、确认 token 和异常堆栈不写入 Trace。真实 MySQL 重启持久化与浏览器端验收仍应按部署环境执行。

## 结论

系统已经具备一个可信 AI 应用的核心骨架：**MySQL 规则决定候选范围，LLM 只在受限候选池中做选择和解释；写操作经过后端工具和草稿校验；RAG 已具备结构化 Chunk、Metadata、向量检索、Rerank、引用和评测雏形。**

当前不适合直接称为“企业生产级”的主要原因不是模型能力，而是运行治理尚未闭环：流式聊天锁的生命周期错误、两条 RAG 路径的证据范围不一致、共享线程池没有隔离和准入控制、观测只在单实例内存中保留。

优先修复顺序：P0 流式聊天锁 -> P1 统一 RAG 入口 -> P1 线程池/限流/SSE 事件治理 -> P1 持久化可关联观测 -> P2 记忆策略、实体与意图解析、Qdrant Payload 索引。

## 当前真实链路

```text
用户画像 + MySQL 历年数据
          |
          v
DB 粗筛 -> 规则分档/排序 -> Universe/Workspace -> 三档并行 LLM 精选
          |                                            |
          |                                            v
          |                                     SelectionValidator
          |                                            |
          +-----------------------> Redis 草稿快照 <---+
                                           |
                         +-----------------+------------------+
                         v                                    v
                 SSE 草稿生成状态                   AI 聊天 / Tool Calling
                                                        |
                              +-------------------------+-----------------------+
                              v                                                 v
                   默认 RetrievalAugmentor                         getProgramEvidence 工具
                   (通用、无 Metadata 过滤)                        (program/school 过滤 + fallback)
```

这套架构的正确取舍是：候选身份、分数、计划、年份和来源 URL 仍由 MySQL 事实数据决定；RAG 用于解释、趋势和引用，不参与“直接从全库生成推荐”。这比把所有结构化数据交给模型检索后直接排序可靠得多。

## 已具备的工程能力

| 维度 | 审计结论 | 证据 |
|---|---|---|
| 推荐可信度 | 已实现候选池约束、AI JSON 解析和 SelectionValidator 校验；AI 失败/超时会降级为规则结果。 | `AiSelectorServiceImpl`、`SelectionValidator` |
| 并行生成 | 三个档位彼此独立，通过 `CompletableFuture` 并行执行；单档设 90 秒超时和降级。 | `DraftServiceImpl` |
| 长任务恢复 | 草稿生成任务状态、进度和分档结果落 Redis，前端可凭 taskId + 短期 token 重新订阅。 | `DraftGenerationTaskServiceImpl`、`AppV2RecommendController` |
| 会话持久化 | 展示消息落 MySQL，Redis 失效后可从 MySQL 恢复最近 20 条模型记忆，页面可取最近 200 条。 | `AiChatServiceImpl`、`AiChatMapper.xml` |
| 工具边界 | 读写工具分组，草稿写操作只走后端服务，要求候选在草稿/工作集内，并限制每轮最多一次写操作。 | `V2ChatTools`、`V2DraftActionTools`、`V2ChatToolContext` |
| RAG 证据 | 数据库导出 Markdown/front matter，再生成结构化 section Chunk，写入 Qdrant；支持 program/year/docType/section Filter、向量召回、Rerank、引用和 fallback Trace。 | `StructuredRagChunkBuilder`、`RagEvidenceService` |
| RAG 评测 | 已有学校/专业/年份/section 精确范围的回归评测，并可区分无召回、范围错误、缺 section、源数据缺失。 | `RagEvaluationService` |
| 模型指标 | 已接入 Micrometer 的模型调用耗时、成功失败和 token 指标。 | `AiConfig`、`AiInvocationSnapshotListener` |

## 审计发现

### P0：聊天锁在模型流式生成期间提前释放

`AiChatServiceImpl` 先用 Redis `SET NX` 对同一用户加 60 秒锁，但在调用 `assistant.chat(...).start()` 后方法立即返回，外层 `finally` 随即删除锁。模型的 token、工具调用和最终落库则在异步回调中继续执行。

结果是同一用户可以在上一轮仍在流式输出时发送下一条消息：两轮同时读取同一份 history、分别追加 Redis/DB，造成上下文顺序错乱、工具写草稿竞争、消息显示与真实记忆不一致。该问题会直接破坏“写操作互斥”和对话记忆可靠性。

证据：`AiChatServiceImpl.java:139` 加锁，`:192-253` 启动异步流，`:264` 无条件删锁。

根因级修复：锁应由本轮 stream 的 `onCompleteResponse`、`onError` 和启动失败分支统一释放，使用随机 value + Lua compare-and-delete，避免锁超时后误删后来的持有者；同时将锁 TTL 续租或设为高于模型最大执行时长。需要增加“第二条消息在第一条未结束时必定被拒绝、完成/失败后可再次发送”的集成测试。

### P1：AI 聊天同时运行两条 RAG 路径，证据范围不一致

聊天 `AiServices` 同时注入了通用 `RetrievalAugmentor` 和显式的 `getProgramEvidence(programId, question)` 工具。前者配置的是通用 `EmbeddingStoreContentRetriever`，没有 program/school/year/docType/section Filter；后者才会走严格 Metadata Filter 和受控 fallback。

因此，回答“为什么推荐某学校”时，模型可能同时拿到全库的自动召回片段和工具返回的专业级证据。即使工具证据正确，自动片段仍可能混入其他学校/通用文档，且目前 `ai-chat` Trace 不记录其选中 Chunk 和 Filter，无法证明最终答案到底依据了哪一条证据。

证据：`RagConfig.java:52-89`，`AiChatServiceImpl.java:189`，`RagEvidenceService.java:25-47`。

建议：将“通用政策问答”与“择校事实解释”路由分开。前者可保留默认 RAG；后者仅允许 `getProgramEvidence` 返回的受控证据进入上下文，并在最终回答中渲染 citation。不要让同一次事实型回答双写入两套检索结果。

### P1：线程池混用与 CallerRunsPolicy 会把过载回传到 HTTP/SSE 请求

全局 `threadPoolTaskExecutor` 同时承担草稿长任务、三档 LLM 并行选择、聊天 SSE 和 SSE 轮询任务。配置为核心 50、最大 200、队列 1000，满载后的拒绝策略是 `CallerRunsPolicy`。

在高并发下，任务不会被显式拒绝，而可能由 Web 请求线程自行执行。这样会把排队问题变成接口长尾、Tomcat 线程被占满和 SSE 断连。对外部模型调用这种慢且不确定的 I/O，单一大队列只会延迟失败而不能保护系统。

证据：`ThreadPoolConfig.java:20-41`，`DraftServiceImpl.java` 的三档 `supplyAsync`，`AppV2RecommendController.java:116/206/424`。

建议：至少拆为 `ai-generation`、`sse-polling`、`ai-chat` 三个有名称的执行器；为每类设置小而可观测的队列、明确拒绝响应、用户级并发配额和供应商级限流。将“排队中”作为任务状态返回，而不是让调用线程承担任务。

### P1：SSE 具备恢复雏形，但事件日志无限增长且没有事件幂等协议

草稿任务会把每次进度追加到 `streamEventsJson`，每次追加都会完整反序列化、追加、再整体写回 Redis。长任务进度多时形成 O(n^2) 序列化开销，Redis value 也无单独上限。重新订阅从第 0 个事件重放，但事件没有 `eventId`、客户端确认位点或幂等去重约束。

证据：`DraftGenerationTaskServiceImpl.java:199-211`，`AppV2RecommendController.java:116-190`。

建议：事件改为 Redis Stream 或带递增 sequence 的有界 ring buffer；客户端发送/保存 lastEventId，只补发缺失事件。SSE 断开时应取消无订阅的纯轮询任务，但不能取消已入队的草稿生成任务。

### P1：观测不能跨重启、跨实例或关联到一次完整 Agent 运行

RAG Trace 仅保留本 JVM 最近 200 条，AI 调用快照仅保留 `AtomicReference` 的最后一条；服务重启即丢失，多实例各自看到不同结果。模型指标虽然进入 Micrometer，但报告中没有 traceId 将 HTTP 请求、LLM 调用、Tool 调用、RAG 检索、引用和最终报告串起来。

证据：`RagTraceRecorder.java:19-49`，`AiInvocationSnapshotRecorder.java:13-40`，`AiChatServiceImpl.java:404-416`。

建议：以 `requestId/runId/conversationId` 为关联键，持久化低基数的 Agent Run、Tool Run、RAG Retrieval 和模型调用摘要；指标输出到 Prometheus，Trace 输出到 OpenTelemetry Collector/Tempo/Jaeger。敏感 prompt 和完整回答默认脱敏或采样，不应直接全量持久化。

### P1：Tool Calling 已有限权，但缺少显式确认与动作审计闭环

优点是写工具不会直接执行任意 SQL，且 `programId` 会在草稿/工作集边界校验，单轮只允许一次写操作。这已经缓解了“模型任意代理”的大部分风险。

但删除、替换、批量替换等改变用户报告的操作，仍可能由一句自然语言直接触发；没有统一的“预览 -> 用户确认 -> 执行”的 command id，也没有可查询的工具审计记录。提示词要求“意图不明确时确认”不等于后端强制确认。

建议：把写操作改为两阶段协议：工具只生成 `proposed_action` 和 diff，前端展示后用一次性 confirmation token 提交；后端记录 toolName、参数摘要、前后草稿版本、结果、runId 和操作者。该设计符合 OWASP 对 Prompt Injection 与 Excessive Agency 的风险控制思路。

### P2：记忆策略是“短期窗口 + 展示持久化”，不是长期语义记忆

当前模型上下文最多 20 条消息，Redis 会话缓存 TTL 为 30 分钟；Redis 丢失时从 MySQL 补最近 20 条。开始新草稿会主动终结旧会话并清 Redis，这是防止旧推荐污染新草稿的正确业务选择。

缺口在于没有摘要记忆、没有对“用户稳定偏好/已确认结论/未完成操作”做结构化抽取，也没有 prompt、草稿和知识库版本记录。长对话只能截断早期内容，无法解释“为什么模型还记得或忘记了什么”。

建议：不要无限增大 MessageWindow。新增每轮可更新的 `conversation_summary`、`user_decision_memory` 和 `draft_version`，模型上下文由“短窗口 + 摘要 + 当前草稿事实卡”组成；事实必须标记来源和有效期。

### P2：RAG 的 Metadata 设计正确，但查询理解和 Qdrant 索引尚未完成生产闭环

`school_id/program_id/year/doc_type/section` 已写入 Chunk，且观测台可以从“深圳大学”自动解析 `school_id=15`。但是当前解析器每次查询会全量读取学校列表，且只解析学校，不解析专业、年份、分数意图或文档 section。

此外，集合创建只创建向量配置，没有为常用过滤字段创建 Qdrant Payload Index。Qdrant 官方文档明确建议对计划过滤的字段建立 Payload Index；数据规模继续增长后，Metadata Filter 会成为性能瓶颈。

证据：`RagQueryMetadataResolver.java:38`，`QdrantCollectionService.java:58-74`。

建议：缓存实体词典，加入专业别名、年份、分数/招生/录取等 intent 到 section/docType 的解析；在 collection 初始化时显式创建 `school_id`、`program_id`、`year`、`doc_type`、`section` 的 Payload Index，并把索引状态纳入运维页。

### P2：供应商调用的重试、熔断、取消、成本治理不足

模型、Embedding、Rerank 都设了 120 秒超时；三档 AI 有 90 秒 timeout + 规则降级。这比无超时更稳健，但未见指数退避重试、熔断、供应商错误分类、预算/配额、客户端断开后的取消传播。聊天 SSE 写出失败时仅忽略发送异常，模型可能继续生成并消耗 token。

建议：针对 429/5xx/网络错误建立有上限的重试与抖动，超阈值熔断并启用降级；按用户和运行记录 token/cost budget；SSE 断开后停止无价值的流式生成，长任务则保持任务状态以支持恢复。

## 对企业常见问题的覆盖度

| 主题 | 当前状态 | 判断 |
|---|---|---|
| 幻觉/事实约束 | 强 | 推荐身份和排序由 DB/规则控制，AI 结果再校验。 |
| 写操作越权 | 中 | 有候选边界和单轮写限制，缺少后端强制确认与动作审计。 |
| Prompt Injection | 中低 | 没有看到针对用户输入、RAG 内容的可信度分级和注入检测；应把检索文本视为不可信数据。 |
| 多轮记忆污染 | 中 | 新草稿会隔离旧对话，但缺少摘要化长期记忆和版本关联。 |
| 流式可靠性 | 中 | 有 task 状态与重新订阅，但聊天锁、SSE 事件幂等和取消传播不完善。 |
| RAG 精确召回 | 中 | 结构化 Chunk/Filter/Rerank 已有；通用自动 RAG 未带事实型 Filter，意图解析不完整。 |
| 模型可用性 | 中 | timeout 与部分降级已有；无熔断、重试策略和供应商限流。 |
| 性能/并发 | 中低 | 三档并行有效；线程池混用和大队列会造成过载长尾。 |
| 可观测性 | 中 | 管理端可看状态、Trace 和 token；存储/关联/多实例支持不足。 |
| 离线评测 | 中 | 有少量 Ground Truth；尚无按问题类型、版本、Recall@K、citation precision 的持续评测门禁。 |

## 推荐的 30 天治理路线

### 第一阶段：先修正确性和安全边界

1. 修复聊天锁：覆盖 stream 完整生命周期，compare-and-delete，新增并发回归测试。
2. 统一 RAG 路由：事实型推荐解释只使用 `getProgramEvidence`；通用政策问答才走默认知识库检索。
3. 所有写工具实施“提案 + 显式确认 + 可审计执行”的两阶段协议。
4. 增加 Agent 运行 ID，贯穿 HTTP、模型、工具、RAG 和草稿版本。

### 第二阶段：做运行韧性

1. 隔离线程池并设置用户/全局并发阈值和明确拒绝策略。
2. 将 SSE 事件改为有 sequence 的有界日志，支持断线续传与客户端幂等。
3. 为模型/Embedding/Rerank 建立 429/5xx 分类重试、熔断和降级原因码。
4. 在 Qdrant 创建 Payload Index，并把索引/集合版本纳入重建流程。

### 第三阶段：做可持续评测和可观测性

1. 持久化 Agent Run/Tool Run/RAG Trace 摘要，接入 OpenTelemetry 与 Prometheus。
2. 建立至少 50 条真实问题的评测集，按政策问答、学校事实、专业分数、风险解释、报告引用分类。
3. 每次知识库重建和提示词/模型变更后，跑 Recall@5、Rerank NDCG、正确范围率、citation precision、工具成功率和端到端 P95。
4. 建立告警：模型错误率、P95、空召回率、fallback 率、工具失败率、SSE 断连率、线程池队列深度和单用户 token 消耗。

## 面试表述建议

可以真实地表述为：

> 设计了规则约束与 Agent 编排分层的考研择校链路：MySQL 规则层完成候选粗筛、分档和排序，LLM 仅在受限 Workspace 内通过 Tool Calling 完成精选、解释和受控草稿调整；三档候选用 CompletableFuture 并行生成并通过 SSE 推送。RAG 侧将招生结构化数据生成 Metadata Markdown/section Chunk 写入 Qdrant，使用 Metadata Filter、向量检索与 Rerank 生成可溯源证据，并建设 Ground Truth 评测和运行 Trace。

不要在修复上述 P0/P1 问题之前表述为“已具备企业级生产稳定性”。更准确的说法是“已经完成企业级能力的核心设计与大部分实现，正在补齐高并发、观测持久化与 Agent 安全治理”。

## 参考规范

- [Qdrant Filtering 与 Payload Index](https://qdrant.tech/documentation/search/filtering/)
- [OWASP Top 10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [OpenTelemetry GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/gen-ai/)
