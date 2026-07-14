# AI 推荐 Agent 全链路与可观测性说明

> 适用范围：408 考研择校平台当前的 AI 推荐、AI Chat、Tool Calling、RAG 与管理端观测能力。本文以当前代码和已验收链路为准，区分确定性业务决策、模型生成和检索证据三个边界。

---

## 1. 设计目标与边界

系统的目标不是让 LLM 直接“猜学校”，而是在可验证的结构化事实与候选范围内，让模型承担解释、比较、追问和受控修改工作。

| 能力 | 主责任方 | LLM 的角色 |
|---|---|---|
| 候选筛选、分档、排序 | MySQL + 后端规则 | 不直接决定候选身份和分数 |
| 推荐理由、风险解释、对话追问 | Agent + LLM | 基于候选事实和 Tool 结果生成 |
| 历年趋势、风险证据、政策知识 | RAG | 提供可引用的证据片段 |
| 草稿修改 | Tool + 用户确认 | 只生成修改提案，不直接落库 |
| 运行状态与排障 | Redis、MySQL Trace、SSE、管理端 | 不参与业务决策 |

核心原则：

1. **结构化事实优先。** 分数、招生名额、院校、专业、年份等硬事实来自 MySQL；RAG 不替代确定性筛选。
2. **候选空间受控。** LLM 只能在后端构造的候选池和 Tool 返回范围内选择、解释或提出修改。
3. **写操作先确认。** 草稿变更先产生提案，再由用户确认或取消。
4. **每次调用可关联。** 一次对话通过 `runId` 关联 SSE、Tool Trace、RAG Trace 与业务日志。

---

## 2. 全链路架构

```text
                         ┌──────────────────────────────────┐
                         │ 用户端 AI 推荐工作台               │
                         │ 画像、候选草稿、AI Chat、确认操作  │
                         └───────────────┬──────────────────┘
                                         │ HTTP / SSE
                                         v
┌───────────────────────────────────────────────────────────────────────────┐
│ Spring Boot                                                                │
│                                                                           │
│  画像 -> 候选池构建 -> 规则分档/排序 -> 三档并行生成 -> 推荐草稿           │
│                                 │                                         │
│                                 └-> AI Chat / LangChain4j AiService        │
│                                         │                                  │
│                         AgentToolRuntime + AgentToolCatalog               │
│                              │                  │                         │
│                              │                  ├-> MySQL Tool Trace      │
│                              │                  ├-> SSE 事件              │
│                              │                  └-> 写操作确认提案        │
│                              │                                            │
│                              ├-> MySQL 查询 Tool                          │
│                              └-> RAG Evidence / 通用检索                  │
└──────────────────────────────┬────────────────────────────────────────────┘
                               │
      ┌────────────────────────┼──────────────────────────┐
      v                        v                          v
 MySQL 招生事实           Redis 运行状态              Qdrant 知识库
 school/program/...       草稿/锁/快照/会话           向量 + Payload
      │                                                    │
      └-> 结构化 RAG 文档 -> Embedding -> Rerank <────────┘
```

---

## 3. 推荐生成链路：用户画像到最终草稿

### 3.1 输入与事实基座

用户画像包括预估分、目标地区、本科层次、跨考情况、风险偏好和院校层次偏好等。后端从 MySQL 读取院校、学院、专业、招生计划、录取分数、复试线、科目组合与数据质量信息，构造统一的 `SchoolFact` 事实卡。

事实卡保留 `programId`、`schoolId`、专业代码、数据年份、均分、最低/最高分、招生名额、来源和数据完整度等字段。这样同名专业但招生单位、研究方向或年份不同的记录不会被 LLM 错误合并。

### 3.2 四层候选池

```text
MySQL 408 项目全集
  -> Universe：满足基本约束、分差在可接受范围内的宽候选集
  -> Workspace：规则过滤、分档、排序、去同校后的 AI 工作空间
  -> Pool：每档进一步收敛，供 Tool 修改草稿时选择
  -> Draft：冲刺/稳妥/保底三档最终可见草稿
```

当前约定中，`Workspace` 每档最多约 30 条，最终 `Draft` 为 3/4/3 共 10 条。候选宇宙、工作集、精选池与草稿都写入 Redis，默认 7 天 TTL，便于刷新恢复和对话连续追问。

### 3.3 规则层与 AI 层分工

| 阶段 | 实现方式 | 目的 |
|---|---|---|
| DB 粗筛 | SQL 过滤 408 科目、学习方式、地区、专业方向、分数范围 | 缩小搜索空间 |
| 规则分档 | 依据分差、名额风险、院校层次和用户偏好 | 给出可解释的冲/稳/保边界 |
| 综合排序 | 后端评分、去重、多样性控制 | 避免 LLM 处理大量重复候选 |
| AI 精选 | LangChain4j 调用模型，按档生成推荐理由和风险分析 | 处理自然语言解释与偏好权衡 |

模型只看到受控事实卡和 Tool 返回，不应该凭空生成学校、专业或 `programId`。候选 ID、均分、招生人数等硬约束由后端校验与事实卡约束保证。

### 3.4 并行与流式输出

三档推荐任务使用 `CompletableFuture` 并行生成，完成一档后通过 SSE 立即推送，用户不必等待全部结果。SSE 事件用于实时展示；Redis 负责保存任务状态、草稿快照、对话状态和分布式锁，因此刷新页面或重复提交时可以恢复/拒绝，而非重新盲跑整条任务。

---

## 4. AI Chat 与 Agent 工作流

### 4.1 一次对话的生命周期

```text
用户消息
  -> AppV2RecommendController 创建 runId
  -> 初始化 V2ChatToolContext(userId, Redis, Mapper, runId, Budget)
  -> 可选：通用 RAG 请求级预检索
  -> LangChain4j AiService 流式调用
  -> 模型输出 token 或发起 Tool Call
  -> AgentToolRuntime 治理并执行 Tool
  -> Tool 结果回注模型，继续生成
  -> SSE done / error，释放上下文
```

`runId` 是一次 AI Chat 的关联 ID；`callId` 是同一轮中每一个 Tool 调用的唯一 ID。二者不能混用：一个 `runId` 可以对应多次 Tool 调用。

### 4.2 Tool Catalog：把隐式规则变成声明元数据

`AgentToolCatalog` 在启动时校验已绑定的 LangChain4j `@Tool` 方法与注册表完全一致，避免新增 Tool 忘记纳入治理。当前共注册 16 个 Tool：

| 类别 | Tool | 说明 |
|---|---|---|
| 查询 | `searchPrograms`、`comparePrograms`、`getProgramDetail` | MySQL 确定性专业/院校查询 |
| RAG | `getProgramEvidence`、`searchKnowledgeBase` | 专业级证据与通用知识检索 |
| 上下文 | `getUserProfile`、`analyzeDraftHealth`、`getDraftContext` | 用户画像和当前草稿状态 |
| 候选补充 | `getRefillCandidates` | 从后端候选池中受控补充 |
| 草稿写入 | `addDraftCandidate`、`removeDraftCandidate`、`replaceDraftCandidate`、`fillTier`、`batchRemoveDraftCandidates`、`batchReplaceDraftCandidates`、`confirmRefillCandidate` | 生成草稿修改提案或经确认后执行 |

每个 Tool 声明以下运行属性：

- `READ` / `WRITE` 类别；
- 预算组：`SEARCH`、`DETAIL`、`RAG`、`CONTEXT`、`WRITE`；
- 预估结果 Token；
- 是否需要用户确认；
- 是否允许并发执行。

### 4.3 AgentToolRuntime：统一执行入口

所有绑定 Tool 经过 `AgentToolRuntime`，执行顺序为：

```text
Tool 名称校验
  -> 读取 Catalog 元数据
  -> 检查本 runId 的 Budget
  -> 写入 STARTED Trace
  -> 调用原有业务 Tool delegate
  -> 标准化为 AgentToolResult
  -> 写入终态 Trace + 推送 SSE tool_result
```

未知 Tool、预算耗尽、非法参数、业务无数据和执行异常不会把原始堆栈直接返回给模型。Runtime 返回受控的 `resultCode` 和摘要，避免模型把底层异常误解为业务事实。

### 4.4 写操作的两阶段确认

草稿写操作不会在模型发出 Tool Call 后直接生效：

```text
模型请求替换/删除/新增
  -> WRITE Tool 生成 proposal（含草稿版本 hash、动作摘要）
  -> SSE: confirmation_required
  -> 前端展示修改内容
  -> 用户确认：确认接口执行真实草稿变更
     或用户取消：消费提案、父调用标记 CANCELLED，不改草稿
```

确认令牌是一次性的，并检查用户归属、过期时间和草稿版本冲突。这样能够阻止模型连续自动改写，也避免用户在草稿已变化时确认旧提案。

---

## 5. RAG 知识库与检索链路

### 5.1 RAG 的定位

RAG 用于补充“为什么推荐、历年趋势、风险、数据质量、政策和术语”等语义证据；它**不负责**候选池的分数筛选和确定性排序。

| 问题类型 | 优先路径 |
|---|---|
| “深圳大学 300 分左右有什么专业” | `searchPrograms` 读取 MySQL；按分数和招生约束筛选 |
| “408 考什么、调剂是什么意思” | 通用 RAG 检索 `rag-documents/` |
| “杭州电子科技大学网安方向为什么被推荐，有什么风险” | `getProgramEvidence(programId, question)` 精确检索 |
| 报告中展示来源 | RAG citation，关联文档/章节/元数据 |

### 5.2 文档与 Metadata

数据库中每个有效专业项目派生三类逻辑文档：

| 文档类型 | 内容 |
|---|---|
| `program_history` | 历年分数、录取、招生趋势和风险因素 |
| `program_year` | 范围内最新可用年份的招生事实快照 |
| `data_quality` | 数据覆盖、缺失年份、来源冲突 |

通用知识文档位于 `ruoyi-postgrad/src/main/resources/rag-documents/`。逻辑文档按业务 section 切成 Chunk，例如 `score_trend`、`admission_trend`、`risk_factors`，而非仅按固定字符数机械切分。

Qdrant Payload 的关键字段为：`school_id`、`program_id`、`year`、`doc_type`、`section`、`source_type`。它们支持在向量检索前缩小语义搜索空间，并可作为引用溯源信息返回给前端。

### 5.3 索引构建与无损切换

```text
MySQL / 通用 Markdown
  -> 文档装配与 section Chunk
  -> BAAI/bge-m3 Embedding
  -> 写入 Candidate Collection
  -> point count / 索引校验
  -> Qdrant Alias: postgrad_rag_active 原子切换
```

每次重建生成一个版本化 Collection：`postgrad_rag_<timestamp>_<suffix>`。构建阶段为：

```text
ASSEMBLY -> EMBEDDING -> VALIDATION -> ALIAS_SWITCH -> SUCCEEDED
```

构建失败时活动 Alias 保持指向旧 Collection；只有候选集合校验通过后才切换。因此重建过程中在线检索不会读到半成品。索引任务状态持久化在 MySQL `rag_index_run`，管理端可按 Index `runId` 轮询进度。

### 5.4 查询：Filter + Vector Retrieval + Rerank

```text
问题 + 可解析实体/上下文
  -> 生成 Metadata Filter（例如 school_id=15、program_id=3123）
  -> BAAI/bge-m3 向量召回 Top-N
  -> BAAI/bge-reranker-v2-m3 重排
  -> 选择 Top-K evidence chunks
  -> 注入提示词 / 返回 citation / 写 RAG Trace
```

严格 Filter 没有结果时，系统只允许在同一 `program_id` 范围内按受控规则放宽，Trace 标记 `fallbackUsed` 与原因，不能悄悄把证据扩大到其他学校或专业。

### 5.5 当前 RAG 评测基线

当前开发集共 63 条，已统计整体检索指标，并披露了 `structured`、`general` 与 `negative` 三类的分段结果：

| 指标 | 当前结果 |
|---|---:|
| PassRate | 88.9% |
| MRR | 0.742 |
| Hit@1 / Hit@3 / Hit@5 | 68% / 78% / 84% |
| Filter Accuracy | 100% |
| Structured（27 条）MRR | 0.786 |
| General（12 条）MRR | 0.854 |
| Negative（12 条）MRR | 0.375 |

`Filter Accuracy=100%` 证明 Metadata 约束在该开发集上按预期工作。`negative` 场景的低 MRR 不能简单包装为“表现良好”：它提示需要补充 **误召回率、正确拒答率或无证据回答率** 等负样例专用指标，作为后续的阈值和路由优化方向。

> 数据核对项：已披露的三段样本数为 `27 + 12 + 12 = 51`，与总开发集 63 条相差 12 条。对外表述“按三段分别统计”前，应确认剩余 12 条属于哪一类，或在评测输出中补充第四类及其指标。

---

## 6. SSE、状态与持久化设计

### 6.1 SSE 事件

用户端通过 SSE 接收流式过程，当前重要事件包括：

| 事件 | 含义 | 关键字段 |
|---|---|---|
| `run` | 本轮运行开始 | `runId` |
| `token` | 模型文本增量 | 文本片段、`runId` |
| `tool_call` | Tool 开始调用 | `tool`、`callId`、`runId`、`category` |
| `tool_result` | Tool 返回受控结果 | `status`、`resultCode`、摘要、`runId` |
| `confirmation_required` | 写操作等待用户确认 | `callId`、`runId`、修改摘要 |
| `done` | 本轮正常结束 | 结果、是否修改、`runId` |
| `error` | 本轮异常结束 | 安全错误摘要、`runId` |

SSE 不负责长期存储；它的职责是把同一 `runId` 的实时进度交给用户端。持久化由 Redis 和 MySQL 承担。

### 6.2 持久化边界

| 数据 | 存储 | 用途 | 当前边界 |
|---|---|---|---|
| 候选池、草稿、任务锁、会话/快照 | Redis | 长任务恢复、去重、并发保护 | TTL 到期后清理 |
| Tool Trace | MySQL `ai_tool_call_trace` | 审计、排障、按 `runId` 查询 | 存摘要，不存完整参数/完整回答；建议定期清理 30 天前记录 |
| RAG 索引任务 | MySQL `rag_index_run` | 构建状态、版本、进度和错误 | 可在重启后查询 |
| RAG Trace | `RagTraceRecorder` 内存环形缓冲 | 最近检索调试、管理端展示 | **后端重启后清空，不是长期审计** |
| SSE 流 | HTTP 长连接 | 实时 UI 反馈 | 断开后由 Redis 快照/任务状态辅助恢复 |

### 6.3 runId 的可观测性价值

同一 `runId` 将用户等待体验、Tool 执行与检索证据关联起来：

```text
runId=abc
  SSE: run -> tool_call(getProgramEvidence) -> token -> done
  Tool Trace: getProgramEvidence 508ms, getProgramDetail 15ms
  RAG Trace: filter、召回数、选中数、Embedding/Qdrant/Rerank 耗时
```

排障时可判断慢在哪里：模型首 Token、向量化、Qdrant 检索、Rerank、具体 Tool，还是 SSE 连接本身。SSE 不是被持久化的对象，而是共享同一个关联键的实时展示通道。

---

## 7. 管理端观测与验收方式

### 7.1 RAG 观测台

路径：`/postgrad/ops/rag`

- 展示活动 Collection、Qdrant 连通性、文档/Chunk 数、Embedding/Rerank 模型；
- 创建和轮询 RAG Index Run；
- 直接运行检索测试（不经过 LLM）；
- 展示 Metadata Filter、向量排名、Rerank 排名、Top 来源与分阶段耗时；
- 展示最近 RAG Trace。

### 7.2 AI 观测台

路径：`/postgrad/ops/ai-observe`

- 汇总模型调用指标与最近调用快照；
- 展示 RAG 健康快照；
- 展示 MySQL 持久化的最近 Agent Tool 调用；
- 点击 `runId` 查看本轮完整 Tool 调用链、结果码、摘要和耗时。

关键接口：

```text
GET /postgrad/ai-observe/overview
GET /postgrad/ai-observe/tool-traces?limit=50
GET /postgrad/ai-observe/runs/{runId}/tool-traces

GET /postgrad/rag/status
GET /postgrad/rag/traces?limit=20
POST /postgrad/rag/search-test
POST /postgrad/rag/index-runs?startYear=2022&endYear=2026
GET /postgrad/rag/index-runs/{runId}
```

### 7.3 已验证的端到端样例

用户提问“杭州电子科技大学-电子信息-网安方向为什么被推荐，有什么风险”时，可在管理端按同一 `runId` 看到：

- `getProgramEvidence`，`READ`，`SUCCEEDED`，预算组 `RAG`；
- `getProgramDetail`，`READ`，`SUCCEEDED`，预算组 `DETAIL`；
- 调用耗时和结果摘要，以及 RAG 观测台中的检索 Filter、召回和重排信息。

这证明“用户问答 -> Agent Tool -> RAG 证据 -> SSE 输出 -> MySQL Tool Trace -> 管理端查看”的链路已打通。

---

## 8. 可靠性策略与当前限制

### 已实现的防护

| 风险 | 当前策略 |
|---|---|
| 模型幻觉学校/专业 ID | 候选池与 MySQL Tool 限制；结构化事实卡；后端校验 |
| Tool 无限循环或结果过长 | 分组 Budget、结果 Token 预估、受控结果码 |
| 模型直接修改草稿 | WRITE Tool 两阶段确认、一次性令牌、版本 hash 校验 |
| 重建索引影响在线检索 | 候选 Collection 校验 + Qdrant Alias 原子切换 |
| 重复提交/并发草稿修改 | Redis 锁、任务状态机、草稿快照和版本冲突校验 |
| Tool 故障暴露底层细节 | Runtime 标准化错误与摘要 Trace，不向模型返回堆栈 |
| 观测数据泄露 | Tool Trace 仅存参数字段名、结果摘要和运行元数据 |

### 当前明确限制与下一步

1. **RAG Trace 不持久化。** 当前仅适合最近问题调试；若要长期审计，应新增 `rag_retrieval_trace` 表或接入 OpenTelemetry/Tempo。
2. **负样例评测指标不完整。** 应新增正确拒答率、误召回率、引用覆盖率和答案忠实度，不能只用 MRR/Hit@K 衡量。
3. **实体解析仍可增强。** 用户只说学校名或别名时，应先通过确定性实体解析映射 `schoolId/programId`，再进入 scoped RAG，减少模型猜测参数。
4. **模型调用指标与 Tool/RAG Trace 的关联可继续强化。** 当前共享 `runId`，后续可将模型调用摘要也持久化为 Agent Run 表，形成完整跨重启链路。
5. **生产部署仍需补齐运维组件。** 包括密钥管理、限流、超时/重试策略、告警、备份和多实例 Trace 聚合。

---

## 9. 面试与答辩的简述版本

> 我把 AI 择校拆成规则层、Agent 层和证据层。规则层用 MySQL 和后端评分构建可验证候选池，LLM 只在候选空间内解释和选择；Agent 通过 LangChain4j Tool Calling 查询事实或发起草稿修改，但所有 Tool 都经过统一 Runtime 做分类、预算、结果码和 Trace 治理，写操作必须用户确认。RAG 从结构化招生数据生成按专业/年份/章节组织的文档，结合 Metadata Filter、向量召回和 Rerank 提供趋势与风险证据；Qdrant 用 Alias 实现版本化索引无损切换。一次对话通过 runId 关联 SSE、Tool Trace 与 RAG Trace，管理端可以定位一轮请求具体慢在模型、检索还是 Tool。
