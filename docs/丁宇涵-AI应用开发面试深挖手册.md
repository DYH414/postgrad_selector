# 丁宇涵 AI 应用开发面试深挖手册

适用简历：`丁宇涵-AI应用开发简历.pdf`  
目标岗位：AI 应用开发工程师（Java）  
准备原则：先讲清真实系统，再解释原理、取舍、指标和边界。面试官不怕你没有做完所有企业能力，怕的是把 Demo 说成生产系统、把单次观察说成稳定指标。

---

## 0. 面试冲刺使用方法

现在不再增加项目功能，按下面顺序准备：

1. 先背熟 30 秒项目介绍，确保第一句话能讲出“事实约束 Agent”，而不是泛泛说“AI 择校系统”。
2. 再掌握五条简历亮点的统一回答结构：`问题 -> 方案 -> 关键实现 -> 验证结果 -> 边界`。
3. 每条亮点至少准备一个故障故事，重点讲定位过程，而不是只说最终修复。
4. 数字必须带口径：数据规模、评测集、历史全量索引、当前活动索引和单次观察不能混用。
5. 面试前只修影响演示的阻断问题，不再临时增加 Hybrid Search、文件上传或新的 Agent 框架。

### 五条简历亮点的一句话答案

| 主线 | 一句话答案 |
|---|---|
| 事实约束 Agent | SQL 和规则负责事实筛选，LLM 只在受控候选池内选择和解释，后端校验器拥有最终决定权。 |
| Tool Runtime | 模型只提出工具调用，Runtime 统一负责读写分类、预算、错误协议、确认和审计。 |
| 安全写协议 | 写 Tool 先生成绑定用户和草稿版本的提案，用户确认后由 Redis Lua 原子消费一次性令牌。 |
| 版本化 RAG | 数据生成 Metadata Chunk，经过向量召回与 BGE Rerank；新索引校验通过后用 Qdrant Alias 原子发布。 |
| 长任务治理 | Redis 保存状态和快照，CompletableFuture 并行生成三档，SSE 负责实时增量展示，页面刷新后通过快照恢复任务视图。 |

### 回答深挖题的固定模板

> 当时真正的问题是【业务或工程风险】。我没有直接使用【最简单但不可靠的方案】，而是把链路拆成【核心组件】。其中最关键的约束是【后端确定性边界】。我通过【测试、Trace 或指标】验证结果。当前边界是【尚未完成或不能夸大的部分】；如果继续演进，我会优先做【下一步】。

### 当前演示前检查

- 用户端、管理端和后端能正常启动。
- Qdrant Alias 指向活动版本，状态为 `CONSISTENT`。
- 最近一次已验收的 2024-2025 活动索引快照为 3,299 份逻辑文档、19,769 个 Chunk；简历中的 21,461 个 Chunk 是 2022-2026 历史全量批次。面试当天以管理端实际状态为准。
- 一次“为什么推荐/有什么风险”问题能够产生相同 `runId` 下的 Tool Trace 与 `scoped-evidence` RAG Trace。
- 不在面试中现场重建全量索引或运行完整评测集，提前准备截图和结果文件。

---

## 1. 面试准备优先级

### S 级：必须脱稿讲透

1. 事实约束 Agent 为什么不是“让 LLM 自由推荐”。
2. 16 个 Tool 的调用协议、Runtime 治理和写操作确认。
3. RAG 的数据生成、Chunk、Metadata、Qdrant、Rerank、引用与评测。
4. Redis 状态机、分布式锁、SSE、CompletableFuture 如何共同治理长任务。
5. 一次请求如何通过 `runId` 关联 LLM、RAG、Tool 和 SSE。
6. 项目真实不足：RAG/Tool Trace 已写入 MySQL，但 SSE 事件未持久化、RAG Trace 的用户字段仍有缺口，也没有接入完整 OpenTelemetry APM；性能数字缺少严格基准实验。

### A 级：高频八股，必须能结合项目回答

- Transformer、Attention、Token、上下文窗口、Temperature、Top-p。
- Embedding、相似度、HNSW、Metadata Filter、Rerank、Top-K。
- Agent 与 Workflow、Tool Calling、Memory 与 History、Prompt Injection。
- Redis 分布式锁、Lua 原子性、缓存问题、幂等。
- SSE 与 WebSocket、线程池、CompletableFuture、超时与取消。
- MRR、Hit@K、Recall@K、nDCG、No-answer Accuracy、FPR。

### B 级：二面或负责人容易问

- 为什么不用 Elasticsearch、Milvus、pgvector；为什么没有做 Hybrid Search。
- 数据扩大 100 倍后哪里先出现瓶颈。
- 多实例部署后 Trace、锁、SSE 和内存状态如何变化。
- 模型限流、Qdrant 故障、Redis 故障、Rerank 超时时用户看到什么。
- Agent、RAG、Prompt、微调分别解决什么问题。

---

## 2. 开场项目介绍

### 2.1 30 秒版本

> 这是一个基于事实约束的 AI 考研择校 Agent。系统先通过 MySQL 和规则层，从 2,023 个计算机专业项目中构建分档候选池，再让 LLM 只在受控候选范围内做精选和风险解释，避免模型凭空推荐。对话侧通过 16 个 Tool 查询事实或调整草稿，Tool Runtime 负责读写分类、预算、错误协议、确认令牌和 Trace。证据侧将结构化招生数据生成带 Metadata 的 Chunk，写入 Qdrant，经过向量召回和 BGE Rerank 后注入模型；长任务则用 Redis 状态机、分布式锁、CompletableFuture 和 SSE 做并行生成、刷新恢复与进度推送。

### 2.2 两分钟版本

> 我把系统拆成四层。
>
> 第一层是确定性推荐层。数据库先按 408 科目、地区、分数和用户画像做粗筛，Java 规则层计算 gap、分档、综合评分和去重，每档只保留不超过 30 条候选。LLM 不接触全量学校，只在候选空间内精选 3/4/3 共 10 所。返回结果还要经过池内 ID、重复项、数据完整度和档位上限等硬校验，异常时使用规则候选降级。
>
> 第二层是 Agent Tool Runtime。LangChain4j 暴露 16 个领域 Tool，包含 9 个读工具和 7 个写工具。所有调用都经过 Catalog 和 Runtime，做预算控制、标准结果码、异常脱敏和 MySQL Trace。写操作不由模型直接提交，而是先生成绑定用户、runId 和草稿版本的提案，再由用户确认，通过 Redis Lua 原子消费一次性令牌后执行。
>
> 第三层是 RAG。系统将 MySQL 招生事实和通用知识组织成语义化文档，按学校、专业、年份、文档类型和章节生成 Metadata Chunk，Embedding 后写入 Qdrant。查询时先解析 Metadata Filter，再向量召回、BGE Rerank、选取 Top-K 证据并生成引用。索引更新先写候选 Collection，校验后通过 Alias 原子切换活动版本。
>
> 第四层是运行治理。推荐生成的三档任务用 CompletableFuture 并行执行，Redis 保存状态机、锁和结果快照，SSE 增量推送进度。`runId` 贯穿模型、RAG、Tool 和 SSE，管理端能够查看检索过滤、候选排名、Tool 结果码和各阶段耗时。

### 2.3 架构图口述

```text
用户画像
  -> MySQL 粗筛
  -> 规则分档/评分/去重
  -> 每档 <= 30 条 Workspace
  -> LLM 三档精选（并行）
  -> SelectionValidator
  -> Redis 草稿快照
  -> SSE 增量展示

用户追问
  -> Agent 判断是否调用 Tool
  -> READ: DB / RAG / 草稿上下文
  -> WRITE: 提案 -> 用户确认 -> Lua 一次性消费 -> 修改草稿
  -> runId 关联 LLM / RAG / Tool / SSE
```

---

## 3. 简历第一条：事实约束型 Agent

简历表述：

> 将 2,023 个计算机专业项目经 DB 粗筛、规则分档与评分去重收敛至每档不超过 30 条候选，使 LLM 在受控候选空间内完成推荐与风险分析，并通过五层校验拦截池外 ID、重复结果和低质量数据。

### 3.1 面试官第一层追问

**Q：为什么要把规则层和 AI 层拆开？**

> 择校中的分数、招生名额、科目、地区和院校层次是结构化事实，适合由 SQL 和确定性规则处理；推荐理由、风险解释和自然语言交互更适合 LLM。若让 LLM 直接从所有学校中推荐，会出现三个问题：上下文过大、结果不可复现、可能生成不存在的学校或专业 ID。因此我把 LLM 的权限限制为“在候选集合中选择和解释”，后端规则才是最终业务约束。

**Q：这还是 Agent 吗，还是普通 Workflow？**

> 推荐草稿生成主链路更接近确定性 Workflow，因为顺序固定且业务约束强；用户进入对话后，模型会根据意图动态选择查询、对比、RAG 证据和草稿操作 Tool，这一部分具有 Agent 的观察、决策、执行闭环。我的取舍是：能确定的流程不交给 Agent，只有工具选择和自然语言交互保留模型自主性。

**Q：2,023 条怎么收敛？**

> 先限定两套 408 科目组合并按用户画像查询，按 `programId` 去重；计算预估分与历年均分的 gap，排除明显不匹配项；按规则分成冲刺、稳妥、保底；结合数据完整度、名额风险、院校层次和偏好进行综合排序；最后每档截断至 30 条以内，交给 LLM 精选 3/4/3。

### 3.2 深挖问题

**Q：为什么每档是 30，不是 10 或 100？**

> 30 是工程折中，不是理论最优值。过少会降低候选召回和多样性，过多会增加 Prompt Token、延迟和模型选择噪声。当前项目用固定上限控制上下文，但还没有完成候选池上限的系统消融实验，所以面试中不能声称 30 是评测得到的最优值。

**Q：五层校验分别是什么？**

> 可按代码行为概括为：空 ID 校验、候选池成员校验、重复项校验、数据完整度校验、档位数量上限校验。最关键的是候选池成员校验，因为它把模型输出从开放文本约束成后端已知的 `programId` 集合。

**Q：规则分档和 LLM 判断冲突怎么办？**

> 后端规则拥有最终决定权。LLM 不能修改档位边界，也不能返回池外 ID；它只在同一档位候选中做精选和解释。如果 LLM 输出无效，校验器删除无效项，必要时用规则排序 Top-N 降级。

**Q：为什么数据完整度 C 直接排除？**

> 推荐系统不只追求相关性，还要控制决策风险。缺少关键分数、年份或名额的数据即使语义匹配，也无法支持“稳妥/保底”判断。当前策略选择保守排除；更成熟的方案可以按字段重要性做置信度评分，而不是统一 C 级排除。

**Q：五层校验能消除幻觉吗？**

> 不能。它能拦截结构层面的池外 ID、重复和低质量候选，但理由文本仍可能出现无依据判断，所以还需要 RAG 证据、引用和事实字段约束。准确说法是“降低可检测的业务幻觉”，不是“消除幻觉”。

### 3.3 负责人视角的挑战题

**Q：候选池先截断会不会把真正合适的学校删掉？**

> 会，这是两阶段架构的典型风险。当前做法通过较宽的 DB 粗筛、每档 30 条和综合评分保持召回，但尚未建立“最终相关学校是否进入候选池”的专门 Recall 指标。下一步应把候选生成与 LLM 精选拆开评测：先评候选 Recall@30，再评精选命中率和用户接受率。

**Q：为什么不用学习排序？**

> 当前缺少足够的用户点击、采纳和录取反馈标签，直接训练排序模型容易得到伪精度。规则层可解释、冷启动成本低，适合当前规模。等有稳定反馈后，可以把规则分数作为 baseline，再尝试 LambdaMART 或轻量排序模型。

### 3.4 代码证据

- 候选池构建：`CandidatePoolServiceImpl.java`
- 分档领域规则：`SchoolFact.java`
- AI 精选：`AiSelectorServiceImpl.java`
- 结果校验：`SelectionValidator.java`
- 三档并行与降级：`DraftServiceImpl.java`

---

## 4. 简历第二条：Agent Tool Runtime

简历表述：

> 统一治理 16 个 Tool 的读写分类、调用预算、标准错误协议与异常脱敏，并通过 runId 关联 LLM、Tool、RAG 与 SSE Trace，实现调用链追踪和异常定位。

### 4.1 先讲清 Tool Calling 协议

```text
后端向模型提供 Tool 名称、描述和 JSON 参数 Schema
  -> 模型返回 tool_call(name, arguments)
  -> LangChain4j 解析并调用 Java @Tool 方法
  -> Runtime 做权限、预算、参数和状态检查
  -> 执行业务方法
  -> 生成结构化 Tool Result
  -> Tool Result 回传模型
  -> 模型继续生成最终答复
```

模型只负责“提出调用”，真正的执行、鉴权、事务、超时、审计和幂等都必须由后端负责。

### 4.2 当前 16 个 Tool 怎么分类

**9 个 READ：**

- `getProgramDetail`
- `getProgramEvidence`
- `searchPrograms`
- `comparePrograms`
- `searchKnowledgeBase`
- `getUserProfile`
- `analyzeDraftHealth`
- `getDraftContext`
- `getRefillCandidates`

**7 个 WRITE：**

- `removeDraftCandidate`
- `addDraftCandidate`
- `confirmRefillCandidate`
- `replaceDraftCandidate`
- `fillTier`
- `batchRemoveDraftCandidates`
- `batchReplaceDraftCandidates`

### 4.3 高频追问

**Q：为什么不能直接在每个 Tool 里写 if 判断？**

> 分散判断会导致新 Tool 忘记接入治理、错误格式不统一，也无法自动核对暴露给模型的工具集合。Catalog 将 category、预算组、预估结果 Token、是否需要确认等属性集中声明；启动时反射比较 `@Tool` 方法和 Catalog，避免出现“模型能调用但治理层不知道”的漏网工具。

**Q：预算控制了什么？**

> 当前是领域级预算：限制单轮 READ 次数、各组调用次数、WRITE 次数和工具结果的估算 Token，防止模型循环调用或把大量结果塞进上下文。这里的 Token 是估算，不是模型 tokenizer 的精确 usage，不能包装成真实成本统计。

**Q：标准结果协议有什么价值？**

> 裸字符串无法区分“正常无数据”和“系统异常”。统一结果包含 status、code、retryable、runId、callId、toolName 和 content，模型、前端、Trace 都可以基于明确状态处理。例如 `NO_DATA` 是成功执行但没有数据，`TOOL_EXECUTION_FAILED` 是执行异常。

**Q：异常脱敏怎么做？**

> Runtime 捕获原始异常，服务端日志保留排障信息，但返回模型和前端的是稳定错误码与安全摘要，不直接暴露 SQL、堆栈、密钥或内部路径。脱敏不能只做字符串替换，还要避免把原始异常拼进 Tool Result。

**Q：runId 和 callId 有什么区别？**

> `runId` 标识一轮 AI 对话或任务；一轮中可能调用多个 Tool，所以每次调用再生成唯一 `callId`。Trace 关系是一对多：一个 runId 对应多个 callId，也可能对应一次 RAG 检索和一条 SSE 流。

**Q：为什么要把 SSE 也关联进去？**

> SSE 不是持久化 Trace，而是这一轮运行的实时输出通道。关联它的意义是：用户看到的 token、tool_call、confirmation_required、done/error 事件都带同一个 runId，排障时可以从前端异常跳到对应 Tool/RAG/LLM 记录。准确表述是“共享关联 ID”，不是“SSE 被持久化”。

**Q：Agent 调错不存在的 Tool 怎么办？**

> Tool 列表由实际注册 Schema 提供，Catalog 在启动时核对绑定集合；如果模型仍生成未知工具名，应由框架的 hallucinated tool 策略返回受控错误，并记录 Trace，而不能尝试动态反射任意方法。

### 4.4 当前设计的真实边界

- Catalog 元数据中的 `concurrencySafe` 不等于已经实现通用并发调度器。
- Runtime 是择校领域固定工具集，不是通用 Agent 平台。
- Tool Trace 与 RAG Trace 已持久化到 MySQL，但 SSE 事件未持久化，聊天消息也没有直接保存 `runId`，因此不能说已经实现完整分布式 APM。
- 还没有完整统计真实模型 Token、费用和 Tool P95/P99。

### 4.5 代码证据

- 工具目录：`AgentToolCatalog.java`
- 统一执行：`AgentToolRuntime.java`
- 统一结果：`AgentToolResult.java`
- 工具预算：`AiToolBudget.java`
- Tool Trace：`AiToolTraceService`、`ai_tool_call_trace.sql`
- 绑定工具：`V2BoundChatTools.java`、`V2BoundDraftActionTools.java`

---

## 5. 简历第三条：Agent 安全写协议

简历表述：

> 写操作采用“提案—确认—执行”机制，通过 Redis Lua 原子消费一次性令牌、草稿版本哈希及单轮写门控，防止重复确认、过期提案覆盖和模型连续写入。

### 5.1 为什么必须确认

> Tool Calling 的参数来自概率模型。查询错误通常只影响回答质量，写操作错误会真实修改用户草稿。因此 READ 可以自动执行，WRITE 必须把“模型建议”和“业务提交”分开：模型只生成提案，用户确认后后端才执行。

### 5.2 完整流程

```text
LLM 选择 WRITE Tool
  -> Runtime 不直接修改
  -> 生成 Proposal
       userId
       runId / parentCallId
       toolName / arguments
       draftVersionHash
       expiresAt
       one-time token
  -> SSE confirmation_required
  -> 用户确认
  -> 后端校验用户、过期时间、草稿版本
  -> Redis Lua 原子读取并删除 token
  -> 执行真实写操作
  -> 记录确认与 Tool Trace
```

### 5.3 高频追问

**Q：为什么 Lua 能防止重复确认？**

> 如果先 GET 再 DEL，中间可能被两个请求同时读取。Lua 脚本在 Redis 中原子执行，把校验与删除合成不可分割操作，只有第一个确认能消费令牌，后续请求拿不到令牌。

**Q：一次性令牌等于幂等吗？**

> 不完全等于。它防止同一提案被重复确认，但不能覆盖“令牌消费成功后业务写入失败，客户端重试”的所有情况。严格幂等还需要业务操作 ID、数据库唯一约束或幂等结果表。

**Q：草稿版本哈希解决什么问题？**

> 提案生成后，用户或另一轮对话可能已经修改草稿。确认时比较当前草稿版本与提案绑定的版本，若不一致则拒绝执行，防止旧提案覆盖新状态。这属于乐观并发控制。

**Q：为什么还需要单轮写门控？**

> 模型可能在一次回复中连续调用多个有副作用工具，导致 remove、replace、fillTier 相互叠加。单轮只允许一个写意图，复合动作由后端专用 Tool 原子表达，降低不可预测副作用。

**Q：Redis 崩溃会怎样？**

> 当前确认令牌和热状态依赖 Redis，故障时应 fail-close：不执行写操作并提示稍后重试，不能绕过确认直接提交。更强方案是把 Proposal 审计同步持久化到数据库，Redis 只负责快速原子消费。

### 5.4 容易答错的点

- 不要说 Lua 脚本在任何 Redis 集群场景都天然安全；需要保证相关 Key 在同一 hash slot。
- 不要说分布式锁保证 exactly-once；锁过期、进程暂停和网络分区仍会造成风险。
- 不要说用户确认能防 Prompt Injection；它只降低写副作用，仍需参数校验和权限控制。

---

## 6. 简历第四条：版本化 RAG

简历表述：

> 基于 Qdrant + BGE Rerank 完成 21,461 个 Chunk 的过滤、召回与重排，结合候选 Collection 校验、Alias 原子切换和 Trace 实现索引平滑发布与链路观测；离线评测包含 MRR、Hit@K、nDCG、Filter Accuracy 和 No-answer 指标。

### 6.1 RAG 数据从哪里来

> 数据源有两类。第一类是 MySQL 中学校、专业、年份、分数、招生计划和数据质量等结构化事实；第二类是 408、国家线、调剂和择校方法等通用 Markdown。数据库事实先被整理成领域文档，再按语义章节生成 Chunk，而不是简单把 SQL 行直接向量化。

### 6.2 为什么结构化 MySQL 还要做 RAG

> 精确分数、条件筛选仍应走数据库 Tool，RAG 不是 MySQL 的替代品。RAG 的价值是把多年份事实、趋势、风险、缺失字段和通用政策组织成适合解释的证据片段。项目采用 Tool + RAG 分工：精确查询用 DB，解释性问题用证据检索，复杂问题可以先查事实再取证据。

### 6.3 文档和 Chunk 怎么设计

> Chunk 按领域语义切分，常见 section 包括基本信息、考试科目、历年分数趋势、招生计划趋势、录取趋势、风险因素、来源质量和缺失字段。Metadata 至少包含 `school_id`、`program_id`、`year`、`doc_type`、`section`、`source_id`、`chunk_index` 和版本信息。这样既能语义检索，也能用结构化条件限定证据范围。

**Q：为什么不按固定 500 字切？**

> 固定长度实现简单，但可能把“标题”和“数据”拆开，或把不同年份、不同专业混在一个 Chunk。领域分段能保证每个 Chunk 自解释、引用粒度明确；过长章节仍可在语义边界内二次切分。

**Q：Overlap 越大越好吗？**

> 不是。Overlap 能减少跨边界信息丢失，但会造成重复向量、结果同质化、索引膨胀和上下文浪费。应基于实际 bad case 调整，而不是固定套模板。

### 6.4 Embedding 八股

**Q：Embedding 是什么？**

> 它把文本映射到高维稠密向量，使语义相近文本在向量空间中更接近。生成模型预测下一个 Token，Embedding 模型优化的是语义表示或对比学习目标，两者用途不同。

**Q：更高维一定更好吗？**

> 不一定。维度提高可能增加表达能力，也增加内存、网络和检索计算；效果还取决于训练数据、语言、领域、最大输入长度和查询/文档编码方式。必须通过评测选择。

**Q：Cosine、Dot Product、Euclidean 怎么选？**

> Cosine 比较方向，忽略模长；点积同时受方向和模长影响；欧氏距离比较几何距离。若向量做 L2 归一化，Cosine 与点积排序等价，欧氏距离也可转换为单调关系。必须与 Embedding 模型推荐的距离函数一致。

**Q：换 Embedding 模型为什么要重建？**

> 不同模型的维度和向量空间不兼容，新查询向量不能与旧文档向量直接比较。即使维度相同，语义空间也不同，所以要建立新 Collection，再切 Alias。

### 6.5 Qdrant 八股

**Collection、Point、Vector、Payload：**

- Collection：共享向量配置的一组 Point，类似向量数据的顶层集合。
- Point：唯一 ID + Vector + Payload。
- Vector：文本的数值表示。
- Payload：可过滤的结构化 Metadata，不等于关系数据库完整行。

**Q：HNSW 是什么？**

> HNSW 是分层可导航小世界图，通过高层快速跳转、低层精细搜索实现近似最近邻。`m` 影响每个节点连接数和内存，`ef_construct` 影响建索引质量与耗时，`ef_search` 影响查询召回率与延迟。当前项目使用 Qdrant 默认/配置能力，但没有做 HNSW 参数消融，面试时应讲原理，不要声称已经调优。

**Q：Payload Filter 为什么要建索引？**

> 没有 Payload Index 时，过滤可能需要扫描更多候选。对高选择性字段如 `program_id`、`school_id` 建 keyword/integer 索引，可以让 Qdrant 在向量搜索过程中利用过滤条件缩小范围。低选择性字段未必值得全部建索引。

**Q：为什么选 Qdrant？**

> 当前项目需要 Java 接入、Metadata Filter、Payload、Alias 和独立向量服务，Qdrant 部署简单且契合需求。pgvector 更适合向量规模较小且希望复用 PostgreSQL 的场景；Elasticsearch 强于全文检索和混合搜索；Milvus 更偏大规模分布式向量场景。这个选择是需求匹配，不是说 Qdrant 在所有维度都最好。

**Q：项目做了 Hybrid Search 吗？**

> 当前核心链路是 Metadata Filter + Dense Vector Retrieval + BGE Rerank，没有 BM25/Sparse 融合，因此不能说做了 Hybrid Search。若要增强学校简称、专业代码和精确术语召回，可增加 Sparse/BM25，再用 RRF 融合候选。

### 6.6 Rerank 八股

**Q：召回和 Rerank 的职责有什么区别？**

> 召回阶段用 Bi-Encoder 独立编码 query 和文档，适合大规模快速检索，目标是保证相关证据进入候选池；Cross-Encoder Reranker 联合编码 query-document，排序更精细但计算昂贵，所以只处理 Top-N。Reranker 无法挽救没有进入候选池的文档。

**Q：为什么先召回 20 再 Rerank Top5？**

> Rerank 需要有重排空间，只召回 5 再重排 5 不能改善候选覆盖。20 和 5 是当前评测配置，真正最优值需要在 Recall、MRR、延迟和成本之间做消融。

**Q：Rerank 失败怎么办？**

> 记录失败原因并保留向量排序结果作为降级，用户仍能得到候选证据，但 Trace 要标明不是完整 Rerank 结果。不能因非核心排序服务失败让整个问答直接报错。

### 6.7 Alias 发布流程

```text
生成 corpus
  -> 创建 postgrad_rag_<version> 候选 Collection
  -> 批量 Embedding / Upsert
  -> 校验非空、Point 数量与 Chunk 数一致
  -> 原子切换 postgrad_rag_active Alias
  -> 新请求读取新版本
  -> 旧 Collection 延迟清理
```

**Q：Alias 为什么比删库重建好？**

> 在线查询始终使用稳定别名。新索引在独立 Collection 中构建，未完成前不影响旧索引；校验通过后 Alias 原子切换，避免用户读取半成品。失败发生在切换前时，旧 Alias 保持不变。

**Q：这能叫零停机吗？**

> Qdrant 的 Alias 切换本身是原子的，但当前项目没有完成高并发下“构建期间持续检索”的正式压测，因此更稳妥地说“平滑发布”或“避免读取半成品”，不要写“经过验证的零停机”。

**Q：当前发布门禁够吗？**

> 不够完整。目前核心校验是 Point 数量与 Chunk 数一致，能发现缺写，但发现不了内容错位、Metadata 错误和 Embedding 质量退化。更强门禁应加入抽样 payload 校验、固定 smoke query、评测阈值和版本兼容检查。

### 6.8 RAG 评测指标

**MRR：**

```text
RR_i = 1 / 第一个相关结果的排名
MRR = 所有正样本 RR 的平均值
```

适合衡量“第一个可用证据是否靠前”。如果结果排名为 1、2、无命中，则 MRR 为 `(1 + 1/2 + 0) / 3 = 0.5`。

**Hit@K：**前 K 条是否至少出现一个相关结果，是 case 级命中率。  
**Recall@K：**Top-K 找回的相关证据数 / 所有相关证据数。  
**nDCG@K：**考虑每个位置的相关性折损，并用理想排序归一化到 `[0,1]`。  
**Filter Accuracy：**带 Metadata 约束的正样本中，返回 Chunk 是否全部满足预期范围。空结果不能自动算过滤正确。  
**No-answer Accuracy：**无答案样本中正确拒答或空召回比例。  
**False Positive Rate：**无答案样本中错误返回可用证据的比例。

### 6.9 当前可验证的 frozen 消融结果

运行产物：`target/rag-eval/20260713T141603.010Z-frozen-v1.json`。数据集为 18 条正向/边界排序用例和独立 4 条安全拒答用例，记录了数据集 SHA-256、Git commit、Alias 和实际 Collection。

| 指标 | Vector Only | Vector + BGE Rerank |
|---|---:|---:|
| MRR | 0.593 | 0.639 |
| Hit@1 | 44.4% | 50.0% |
| Hit@3 | 77.8% | 77.8% |
| Hit@5 | 77.8% | 77.8% |
| nDCG@5 | 0.646 | 0.648 |
| 总延迟 P50（22 条） | 152 ms | 284 ms |
| 总延迟 P95（22 条） | 495 ms | 465 ms |
| No-answer Accuracy（4 条） | 75% | 75% |
| FPR（4 条） | 25% | 25% |

**如何解释这组结果：**

> Rerank 主要改善了首个相关结果的位置，MRR 从 0.593 提升到 0.639，Hit@1 从 44.4% 提升到 50%，但 Hit@3/5 没变，说明候选覆盖没有改善，符合 Rerank 只能重排不能补召回的原理。P50 从 152ms 增加到 284ms，体现了 Cross-Encoder 的额外成本。P95 反而略低不能直接解释为 Rerank 更快，因为只有 22 条单次样本，网络抖动会影响尾延迟，不能据此得出稳定性能结论。No-answer 指标没有改善，说明拒答问题需要阈值、路由或检索策略治理，而不是靠 Rerank。

### 6.10 简历指标与冻结集指标为什么不同

简历中的 `MRR 0.742、Hit@5 84%、Filter Accuracy 100%` 来自 **63 条分层开发集**，覆盖 structured、general、negative 等场景，用于迭代检索策略和观察分类表现。它的样本更多，但参与过开发反馈，不能包装成完全无偏的最终测试结果。

冻结消融使用 **18 条排序用例 + 4 条 safety 用例**，固定 checksum、Git commit、Alias 和 Collection，对同一数据分别运行 Vector Only 与 Vector + BGE Rerank。它的作用是隔离变量、验证 Rerank 增益和拒答安全性，而不是替代 63 条开发集。

**面试回答：**

> 简历写的是 63 条分层开发集结果，因为它覆盖面更广；为了避免只报告调参集，我又冻结了独立版本做消融。冻结集上 Rerank 将 MRR 从 0.593 提升到 0.639、Hit@1 从 44.4% 提升到 50%，但 Hit@5 不变。两组数字回答的问题不同，我不会把开发集指标说成最终泛化能力。

**Q：为什么不直接把冻结集结果写进简历？**

> 冻结集目前只有 18 条排序和 4 条 safety，用来验证方向和做消融很合适，但样本量还不足以单独代表整体效果。简历使用覆盖更广的开发集指标，面试时同时披露冻结集结果和样本量，这是当前最诚实也最完整的口径。

### 6.11 21,461 个 Chunk 怎么回答

> 21,461 是 2022-2026 本地历史全量索引批次的结果，对应 3,581 份逻辑文档。它证明的是数据生成和索引规模，不代表在线 QPS 或效果。线上索引会随起止年份和数据更新而变化，所以面试时要明确“本地全量批次”，不要把它说成永远固定的线上数量。

---

## 7. 简历第五条：Redis、SSE 与并行长任务

简历表述：

> 基于 Redis 构建 Agent 长任务治理机制，结合 7 阶段状态机、分布式锁、结果快照与 SSE 增量推送，实现刷新恢复和重复提交拦截；通过 CompletableFuture 三档并行，将生成耗时从 60s+ 缩短至约 15s。

### 7.1 为什么需要状态机

> LLM 推荐不是一个短事务，包含画像、候选池、三档精选、校验和保存。只依赖 HTTP 连接会导致刷新后看不到进度、重复点击产生并发任务、后端异常后状态不明确。Redis 保存运行状态、阶段、草稿快照和锁，前端可以恢复任务视图。

### 7.2 SSE 高频题

**Q：为什么用 SSE，不用 WebSocket？**

> 当前主要是服务端向浏览器单向推送 token、进度和 Tool 事件，SSE 基于 HTTP、实现简单、适合文本流；WebSocket 适合高频双向通信。聊天请求仍可用 POST 提交，响应使用 `text/event-stream` 流式读取。

**Q：SSE 和普通流式 HTTP 有什么关系？**

> SSE 是在长连接 HTTP 响应上定义的文本事件格式，常见字段有 `event`、`id`、`data`，事件之间用空行分隔。服务端要及时 flush，反向代理要关闭缓冲或调整超时。

**Q：断线后一定能恢复模型生成吗？**

> 不能混淆两件事。SSE 重连只能恢复事件消费；是否能恢复任务取决于服务端是否独立保存任务状态和事件序号。项目通过 Redis 状态与快照恢复页面，但并不等于模型底层请求可从中断 token 继续生成。

**Q：客户端关闭后模型还在跑怎么办？**

> 当前风险是 SSE 写出失败不一定能向底层模型 HTTP 调用传播取消，可能继续消耗 Token。更成熟方案要在 completion/error/disconnect 中传播 CancellationToken，取消 CompletableFuture 和下游 HTTP 请求，并记录取消状态。

### 7.3 CompletableFuture 高频题

**Q：为什么并行能提速？**

> 三档精选彼此独立，原串行耗时近似 `T_reach + T_steady + T_safe`，并行后理论上接近 `max(T_reach, T_steady, T_safe) + 调度开销`。它适合模型 API 这种 I/O 等待任务。

**Q：`supplyAsync` 不传 Executor 会怎样？**

> 默认进入 `ForkJoinPool.commonPool`。业务服务应显式使用有界线程池，避免与其他异步任务争抢公共池，也便于设置核心线程、最大线程、队列、拒绝策略和监控。

**Q：`orTimeout` 会取消底层 HTTP 吗？**

> 不保证。它让 CompletableFuture 以 TimeoutException 异常完成，但供应商请求可能仍在执行。因此还要在 HTTP 客户端配置连接/读取/总超时，并支持取消传播。

**Q：为什么 I/O 密集线程池可以比 CPU 核数大？**

> 线程大量时间在等待网络，不持续占用 CPU，可以设置更多并发线程；但上限仍受内存、连接池、供应商配额和上下文切换影响。不能简单套 `2N+1`。

### 7.4 Redis 分布式锁高频题

**正确模式：**

```text
SET key uniqueValue NX PX ttl
释放时用 Lua：只有 value 仍等于自己的 uniqueValue 才 DEL
```

**Q：为什么不能直接 DEL？**

> 线程 A 的锁过期后线程 B 获得同名锁，A 完成后直接 DEL 会误删 B 的锁。必须比较 owner token 再删除，并将比较和删除放入 Lua 原子执行。

**Q：锁 TTL 到期但任务没结束怎么办？**

> 可能出现第二个任务进入，因此需要合理 TTL、续租/watchdog，或者依赖业务幂等与版本控制兜底。当前草稿生成锁有固定 TTL，面试时应承认超长任务是边界风险。

### 7.5 “60s+ 到 15s”如何安全回答

> 这是开发过程中的端到端观察值，优化同时包含三档并行、Prompt 精简和 SQL/模型调用调整，没有保存同一画像、同一模型、同一候选池下各 30 次的串行/并行 P50/P95。因此简历可以作为工程结果描述，但面试时必须主动说明不是严格性能基准。若被要求证明，应提出固定输入和模型、预热、串行与并行各运行至少 30 次，记录 TTFT、首档时间、总完成时间、P50/P95 和错误率。

---

## 8. Memory、History 与上下文管理

### 8.1 三个概念必须分清

- History：完整聊天记录，面向用户展示，应该持久化并可追溯。
- Chat Memory：本次送给模型的消息窗口，可能裁剪、摘要或注入额外上下文。
- Business State：草稿、画像、候选池、提案等结构化状态，不能只依赖自然语言记忆。

### 8.2 项目怎么做

> 聊天会话和消息有 MySQL 持久化；Redis 保存对话热状态、任务状态和草稿快照；模型上下文使用有限消息与结构化业务上下文。草稿不靠模型“记住”，而是每次通过 Tool 或 Context 从后端读取，这是比无限堆聊天记录更可靠的设计。

### 8.3 高频追问

**Q：上下文窗口很大，为什么还要裁剪？**

> 更大的窗口仍增加输入成本和首 Token 延迟，也会引入无关信息和注意力稀释。工具结果、RAG Chunk 和历史消息会共同占用窗口，必须按任务选择上下文。

**Q：摘要记忆有什么风险？**

> 摘要可能丢失数字、否定条件和用户最新修改。原始 History 应保留，关键业务状态独立结构化持久化，摘要只用于辅助模型，不应成为唯一事实源。

**Q：项目有长期语义记忆吗？**

> 当前重点是会话历史、用户画像和业务草稿持久化，不是把所有对话做向量化的长期记忆。不要把 RAG 知识库等同于用户长期记忆。

---

## 9. LLM 与 Prompt 高频八股

### 9.1 Transformer 与 Attention

**Q：Self-Attention 怎么算？**

```text
Q = XWq, K = XWk, V = XWv
Attention(Q,K,V) = softmax(QK^T / sqrt(dk))V
```

Q 表示当前 Token 想查询什么，K 表示各 Token 可匹配的特征，V 是被聚合的信息。除以 `sqrt(dk)` 是为了避免维度增大后点积过大导致 softmax 饱和。标准全注意力需要构造 `n x n` 分数矩阵，时间和显存通常为 `O(n^2)`。

**Q：多头注意力有什么用？**

> 不同头在不同子空间学习不同关系，例如局部依赖、实体关系和长距离关联；各头输出拼接后线性映射。它不是简单把同一个注意力重复多次。

**Q：位置编码为什么需要？**

> Attention 本身对输入排列缺少顺序感，需要位置编码注入序列位置。常见有绝对位置编码、RoPE、ALiBi。应用开发岗掌握作用和长上下文外推风险即可。

### 9.2 Token 与采样

**Q：Token 是字吗？**

> 不是。Tokenizer 可能把中文字符、英文子词、数字或标点编码成 Token。字符数不能精确等于 Token 数，成本和上下文必须以模型 tokenizer 为准。

**Q：Temperature 和 Top-p？**

> Temperature 调整 logits 的尖锐程度，越高越随机；Top-p 只在累计概率达到 p 的最小候选集合中采样。结构化选择、Tool 参数和事实问答通常使用较低随机性；创意生成可适当提高。不要同时极端调整多个采样参数。

**Q：为什么会幻觉？**

> 生成模型优化的是下一个 Token 概率，不是数据库一致性；训练知识可能过期、上下文可能不足、检索可能错误，模型还会倾向生成连贯答案。RAG、Tool、结构化输出和后端校验只能分层缓解，不能从数学上彻底消除。

### 9.3 Prompt、RAG、微调怎么选

| 手段 | 适合解决 | 不适合解决 |
|---|---|---|
| Prompt | 明确任务、格式、少量示例和行为边界 | 注入大量动态知识 |
| RAG | 动态/私有知识、引用、可更新事实 | 稳定改变模型风格和能力 |
| Tool | 精确查询、计算、外部动作、实时数据 | 开放文本知识解释的全部问题 |
| Fine-tuning | 稳定风格、格式、特定行为或能力 | 频繁变化事实的实时更新 |

**项目答案：**精确招生事实由 Tool/MySQL 提供，多年份趋势与政策证据由 RAG 提供，Prompt 规定模型只能依据候选和证据回答，后端校验最终业务约束。目前没有微调，因为数据量和收益不足，且事实更新更适合 RAG。

### 9.4 Prompt Injection

**Q：RAG 检索到“忽略系统提示”怎么办？**

> 检索内容是数据，不是可信指令。需要在系统 Prompt 中区分指令与证据，对外部文档做来源和权限控制，限制 Tool 权限，高风险写操作要求确认，并对输出做业务校验。仅靠一句“忽略文档中的指令”不够。

---

## 10. 可观测性与可靠性

### 10.1 当前能观测什么

- LLM：模型调用结果、延迟和运行关联信息。
- RAG：Metadata Filter、召回数量、选中数量、向量排名、Rerank 排名、Embedding/Qdrant/Rerank 阶段耗时、Top 来源。
- Tool：runId、callId、工具名、读写分类、状态、结果码、重试属性、估算 Token、耗时和确认过期时间。
- SSE：run、token、tool_call、tool_result、confirmation_required、done、error 等实时事件。
- 索引：构建 run、阶段、文档数、Chunk 数、活动 Collection、失败摘要。

### 10.2 Trace 和 Log、Metric 的区别

- Log：离散事件和上下文，适合看异常细节。
- Metric：可聚合数值，如 QPS、P95、错误率、Token 成本。
- Trace：一次请求跨组件的因果链，包含多个 Span。

当前项目实现的是领域级 Trace 和运行记录，不是完整 OpenTelemetry 分布式追踪。`runId` 是业务关联 ID，不等价于标准 traceId/spanId。

### 10.3 高频故障题

**Q：RAG 失败但回答成功是什么情况？**

> 可能是检索降级为无证据回答、模型使用已有上下文、或者 Rerank 失败后保留向量结果。必须检查 Trace 中 route、retrieved/selected、fallback 和 error，不能只看最终 HTTP 200。

**Q：Trace 写库失败要不要让主流程失败？**

> 通常 fail-open，避免观测系统拖垮用户请求，但必须记录本地错误并对 Trace 丢失率告警。高合规场景可能要求审计落库失败时 fail-close，要按业务风险决定。

**Q：多实例后 RAG Trace 有什么问题？**

> 当前 RAG Trace 和 Tool Trace 已写入 MySQL，因此不再是单实例内存记录，重启后仍可查询。现有不足是部分 RAG Trace 的 `user_id` 可能为空、聊天消息没有直接保存 `runId`、SSE 事件没有持久化，也没有标准 traceId/spanId。多实例下可以依靠 MySQL 聚合业务 Trace，但要形成完整调用图，还需要统一上下文传播并接入 OpenTelemetry Collector、日志和指标平台。

### 10.4 应监控的 AI 应用指标

- 体验：TTFT、总耗时、Token/s、取消率。
- 模型：输入/输出 Token、费用、429/5xx、超时率。
- RAG：空召回率、Recall@K、MRR、Rerank 增益、引用覆盖和忠实度。
- Agent：Tool 选择准确率、参数错误率、平均步骤数、预算拒绝率、写确认率。
- 系统：线程池活跃数/队列、Redis/Qdrant/DB 延迟、SSE 在线连接数。

---

## 11. Java 与后端八股：必须能接住

### 11.1 Java 并发

**synchronized 与 ReentrantLock：**前者由 JVM 管理，语法简单；后者支持可中断、超时、公平锁和多个 Condition，但必须 finally 解锁。  
**volatile：**保证可见性和一定的有序性，不保证复合操作原子性。  
**AtomicBoolean：**通过 CAS 保证单变量原子更新，项目中可用于防止 SSE 重复结束或锁重复释放。  
**ThreadLocal：**线程局部变量，异步回调换线程后不会自动继承；线程池中还必须 remove，避免数据串用和内存泄漏。  
**CompletableFuture：**区分 `thenApply`、`thenCompose`、`thenCombine`、`allOf`、`exceptionally`、`handle`；解释显式 Executor 和超时边界。

### 11.2 Spring

**Q：`@Transactional` 为什么可能失效？**

> 自调用绕过代理、方法非 public、异常被捕获未抛出、抛出 checked exception 但未配置 rollbackFor、对象不是 Spring Bean、跨线程执行等。

**Q：SSE 请求占用 Tomcat 线程吗？**

> `SseEmitter` 使用 Servlet 异步能力，初始请求线程可释放，但每次发送仍需要执行线程，连接本身占用 socket 和容器资源；下游模型回调、线程池和代理配置仍决定可扩展性。

### 11.3 MySQL

**索引：**B+Tree 适合范围查询和排序；联合索引遵循最左前缀，区分覆盖索引、回表、索引下推。  
**事务隔离：**读未提交、读已提交、可重复读、串行化；MySQL InnoDB 默认可重复读，通过 MVCC 和 Next-Key Lock 处理一致性与幻读。  
**慢 SQL 排查：**慢查询日志、EXPLAIN、扫描行数、索引选择、回表、临时表/filesort、连接池等待。  
**项目关联：**候选查询包含多表事实，应该优先用过滤索引降低扫描，再在 Java 层做复杂规则评分；不要把所有业务计算都塞进 SQL。

### 11.4 Redis

**数据结构：**String 用于状态/锁，Hash 适合对象字段，ZSet 适合排序，Stream 适合消息流。  
**缓存穿透：**不存在 Key 被反复查询，可用参数校验、空值缓存、Bloom Filter。  
**缓存击穿：**热点 Key 失效并发打 DB，可用互斥重建、逻辑过期。  
**缓存雪崩：**大量 Key 同时失效，可用 TTL 抖动、分批预热、限流降级。  
**持久化：**RDB 快照、AOF 追加，取舍恢复速度、数据完整性和写放大。  
**项目关联：**Redis 是热状态与协调层，MySQL 才是聊天历史、报告和 Tool Trace 等持久事实来源。

### 11.5 MyBatis

准备：`#{}` 预编译防注入，`${}` 文本替换有注入风险；一级缓存是 SqlSession 级，二级缓存是 Mapper namespace 级；动态 SQL、批量写入、N+1 查询、分页和映射问题。

### 11.6 Docker 与 Nginx

准备：镜像与容器区别、Volume 持久化、网络、健康检查、restart policy；Nginx 反向代理、HTTPS、静态资源、SSE 代理缓冲和超时。Qdrant 数据必须挂载 Volume，端口可只绑定本机，通过后端访问，避免公开暴露管理面。

---

## 12. 实习经历深挖

### 12.1 多模态识别花名册

**Q：为什么多模态模型比传统 OCR 好？**

> OCR 擅长文字识别，但复杂表格还需要版面分析和字段归属恢复；多模态模型能结合视觉布局和语义输出结构化字段。代价是结果具有随机性、成本更高，所以必须限制 JSON Schema、做字段校验和失败回退。

**Q：“字段缺失与错位基本消除”如何证明？**

> 如果没有固定测试集和错误率统计，不要说“完全消除”。稳妥回答是：在项目测试样表中明显减少了字段错位，后端仍保留必填字段、类型、学号唯一性和班级映射校验；简历这句话若被追问，应坦诚没有严格 OCR benchmark。

### 12.2 RabbitMQ 异步任务

**Q：为什么改异步？**

> 图片识别耗时长且依赖外部模型，同步 HTTP 会占用连接并容易超时。上传后立即返回 taskId，消费者执行识别、解析和校验，前端轮询或订阅状态，更适合长任务。

**Q：如何避免重复消费？**

> 消费至少一次时必须业务幂等：用 taskId 做唯一键，处理前检查状态，数据库更新使用条件更新；只有业务提交成功后 ACK。失败按异常类型重试，超过阈值进死信队列并支持人工重放。

**Q：消息顺序重要吗？**

> 同一个任务的状态更新需要顺序，可以用同一 routing key/队列、单任务版本号或数据库条件更新防止旧状态覆盖新状态；不同任务之间无需全局顺序。

---

## 13. 高频系统设计题

### 13.1 “设计一个企业知识库问答系统”

回答框架：

1. 明确数据源、更新频率、权限、答案风险和 SLA。
2. 摄取：解析、清洗、去重、版本、ACL、语义 Chunk。
3. 索引：Embedding、Dense/Sparse、Payload Index、增量与全量版本。
4. 查询：意图理解、Query Rewrite、权限 Filter、Hybrid Recall、Rerank、阈值。
5. 生成：证据注入、引用、拒答、结构化输出。
6. 评测：检索、生成、端到端、延迟与成本。
7. 可靠性：超时、重试、熔断、旧索引保留、降级。
8. 可观测性：traceId、Prompt/模型/索引版本、候选和分数。

### 13.2 “如果数据扩大 100 倍”

> 先做容量测算，不直接换架构。关注 Embedding 吞吐与成本、Qdrant 内存和 HNSW 构建时间、Payload 索引、分片/副本、批量 Upsert、旧版本清理、Rerank QPS。通用文档和学校专业数据可按租户或领域分区；高频 Query 做版本感知缓存。真正瓶颈很可能先出现在外部 Embedding/Rerank 配额，而不是 Java 代码。

### 13.3 “模型供应商故障”

> 分类处理 429、5xx、连接超时、读取超时和业务拒绝；仅对暂时性错误做带抖动的指数退避，并设置总时间预算；推荐精选失败则规则降级，Rerank 失败保留向量序，Embedding 失败则不能伪装成有证据回答；高价值请求可切备用模型，但要保证 Prompt 和输出协议兼容。

### 13.4 “如何防止用户越权检索”

> 权限必须在检索前进入 Metadata Filter，而不是只在 Prompt 中要求模型保密；缓存 Key 包含租户/用户权限和索引版本；Tool 执行再次做服务端鉴权；Trace 和引用避免泄露无权限文档标题。当前项目知识主要是公共招生数据，没有完整多租户 ACL，不应声称已实现企业权限 RAG。

---

## 14. 最容易翻车的 20 个问题

1. 2,023 的统计 SQL 和时间点是什么？
2. 每档 30 为什么是合理值，有无消融？
3. 五层校验能覆盖文本理由幻觉吗？
4. 规则分档阈值由谁制定，有无录取结果回测？
5. 当前到底是 16 个还是 15 个 Tool？答案：当前 Catalog 为 16 个，9 READ + 7 WRITE。
6. Tool 预算 Token 是真实 tokenizer 统计吗？答案：不是，是结果规模估算。
7. 一次性令牌是否等于 exactly-once？答案：不是。
8. 草稿版本 Hash 是如何计算和比较的？
9. 结构化 MySQL 为什么还要 RAG？
10. 当前有没有 BM25 + Dense Hybrid Search？答案：没有。
11. Reranker 能补回没有召回的文档吗？答案：不能。
12. 为什么 Point 数相等不代表索引质量正确？
13. Alias 切换后失败能否自动回滚？当前并非完整自动 reconciliation。
14. 21,461 是线上当前值吗？答案：是本地历史全量批次值，线上会随构建范围变化。
15. MRR 0.639 的样本量是多少？18 条 frozen 排序用例。
16. Rerank 为什么 Hit@5 没提升？候选集合没变，只改善部分排序。
17. 60s+ 到 15s 是否有 30 次 P95 证据？答案：没有严格基准。
18. `orTimeout` 是否终止底层模型请求？答案：不保证。
19. RAG Trace 是否持久化、多实例共享？已持久化到 MySQL，可跨实例查询；但用户关联、SSE 事件和标准 Span 链仍不完整。
20. 项目是否经过真实业务 A/B？没有，主要是本地/线上功能验收与离线评测。

---

## 15. STAR 故事库

### 故事 1：从自由生成改为事实约束

- S：LLM 直接推荐可能编造学校和专业 ID。
- T：保证推荐可解释且结果属于真实数据。
- A：MySQL/规则层构建候选池，LLM 只精选；增加成员、重复、完整度、上限校验与规则降级。
- R：把不可控开放生成改为受控候选选择，池外 ID 可被确定性拦截。

### 故事 2：写 Tool 副作用治理

- S：模型一次回复可能连续调用多个写 Tool，造成草稿状态叠加。
- T：让自然语言修改可控、可确认、可审计。
- A：READ/WRITE Catalog、单轮写门控、提案确认、版本 Hash、Lua 一次性令牌、Tool Trace。
- R：模型不能未经用户确认直接修改草稿，重复确认和过期提案被后端拒绝。

### 故事 3：RAG 版本发布

- S：原地重建 Collection 会导致在线检索读取空库或半成品。
- T：索引构建失败时保留旧版本。
- A：候选 Collection 构建、Point 校验、Alias 原子切换、运行状态持久化。
- R：切换前失败不影响活动别名；线上检索始终通过稳定 Alias。

### 故事 4：Rerank 消融

- S：仅凭主观搜索结果无法证明 Rerank 有价值。
- T：在相同候选和数据集上比较排序质量与延迟。
- A：冻结 18 条排序集和 4 条 safety 集，记录 checksum、commit、collection；运行 Vector Only 与 Rerank 双模式。
- R：MRR 0.593→0.639，Hit@1 44.4%→50%，P50 152ms→284ms；说明排序改善但付出延迟成本。

### 故事 5：长任务并行与恢复

- S：三档模型调用串行，用户等待长，刷新后状态丢失。
- T：缩短等待并支持任务恢复。
- A：CompletableFuture 并行、显式线程池、单档超时降级、Redis 状态与快照、SSE 增量事件。
- R：开发观察中总耗时由 60s+ 降至约 15s；但未形成严格 P50/P95 基准，面试中主动说明口径。

---

## 16. 三轮模拟面试题

### 一面：基础与真实性

1. 两分钟介绍项目。
2. 为什么它是 Agent，不只是聊天机器人？
3. 规则层与 LLM 层怎么分工？
4. Tool Calling 的完整协议是什么？
5. 16 个 Tool 如何防止模型乱调？
6. RAG 从 MySQL 到 Qdrant 的完整链路是什么？
7. Chunk 为什么按 section 切？
8. Metadata Filter 在检索前还是检索后？
9. Recall 与 Rerank 的区别？
10. SSE 为什么比 WebSocket 合适？
11. Redis 锁为什么用 Lua 释放？
12. CompletableFuture 异常怎么处理？

### 二面：取舍、指标与故障

1. 候选池上限 30 如何证明合理？
2. 五层校验仍可能漏掉哪些幻觉？
3. 为什么结构化数据不全部走 SQL Tool？
4. MRR、Hit@K、nDCG 如何计算？
5. Rerank 消融说明了什么，为什么 Hit@5 不变？
6. Qdrant Alias 切换和数据库状态不是同一事务，半成功怎么办？
7. Rerank 超时、Embedding 超时分别怎么降级？
8. Tool 确认令牌被并发提交两次怎么办？
9. SSE 断线后如何防重复事件？
10. Redis 锁提前过期怎么办？
11. ThreadLocal 为什么在模型异步回调中会丢？
12. 如何把当前观测升级为 OpenTelemetry？

### 三面/负责人面：系统与边界

1. 如果取消 LLM，产品还剩什么价值？
2. 如果只保留一个技术亮点，你选什么，为什么？
3. 为什么没有做 Hybrid Search？什么时候必须做？
4. 你如何证明用户真的更信任有引用的回答？
5. 1000 个并发生成请求如何限流和容量规划？
6. 多实例部署后 Redis、SSE、Trace 有哪些变化？
7. 如何防 Prompt Injection 调用写 Tool？
8. 评测集由你自己标注，如何控制偏差？
9. 当前最严重的技术债是什么？
10. 给你一周，你会优先补什么？

负责人视角的推荐答案：优先补真实流量下的端到端指标、RAG/Agent 回归门禁、取消传播与多实例可观测性，而不是继续增加 Tool 数量或引入多 Agent。

---

## 17. 面试前证据清单

面试前在本机准备好以下页面或文件，防止被追问时只有口头描述：

1. 系统在线首页、AI 推荐页、RAG 观测台、AI 观测台。
2. `AgentToolCatalog.java`：证明 16 个 Tool 与 READ/WRITE 分类。
3. `SelectionValidator.java`：证明候选池硬校验。
4. `AgentToolRuntime.java`：证明预算、错误协议、Trace。
5. `AgentActionConfirmationService`：证明确认和 Lua 消费。
6. `RagIndexBuildService.java`：证明候选 Collection 和 Alias。
7. `RagRetrievalService.java`：证明 Filter、向量召回和 Rerank 降级。
8. frozen 评测报告 JSON：证明指标和运行元数据。
9. 一张 Tool Trace 和一张 RAG Trace 截图。
10. 一条失败案例：说明你如何定位、修复和验证。

---

## 18. 近期面试趋势与参考资料

近期 AI 应用岗位的共同趋势是：不再满足于“会调用模型 API”，而是深挖 RAG 效果、Agent 工具安全、上下文与记忆、延迟成本、失败恢复和可观测性。面经只能用于判断频率，技术事实以官方文档为准。

### 岗位与面试题趋势

- [JavaGuide AI 应用开发面试题专题](https://javaguide.cn/ai/interview-questions/)
- [百度开发者中心：RAG 到 Agent 面试指南](https://developer.baidu.com/article/detail.html?id=5573507)
- [百度校园招聘](https://talent.baidu.com/jobs/list?recruitType=GRADUATE)
- [AI 应用开发工程师面试题库](https://mianshidashi.cn/interview-questions/roles/ai-application-development)
- [AI 应用开发面试指南 GitHub](https://github.com/guocong-bincai/ai-interview-guide)

### LangChain4j

- [LangChain4j Introduction](https://docs.langchain4j.dev/intro/)
- [LangChain4j Tools](https://docs.langchain4j.dev/tutorials/tools/)
- [LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag/)
- [LangChain4j Chat Memory](https://docs.langchain4j.dev/tutorials/chat-memory/)

### Qdrant

- [Qdrant Overview](https://qdrant.tech/documentation/overview/)
- [Collections and Aliases](https://qdrant.tech/documentation/manage-data/collections/)
- [Payload and Payload Index](https://qdrant.tech/documentation/concepts/payload/)
- [Hybrid Queries](https://qdrant.tech/documentation/search/hybrid-queries/)
- [Reranking Hybrid Search](https://qdrant.tech/documentation/advanced-tutorials/reranking-hybrid-search/)

### Java、Redis 与 SSE

- [Oracle CompletableFuture API](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Redis Lua Scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- [MDN Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)

### RAG 评测

- [LangSmith RAG Evaluation](https://docs.langchain.com/langsmith/evaluate-rag-tutorial)
- [LangSmith Evaluation Concepts](https://docs.langchain.com/langsmith/evaluation-concepts)

---

## 19. 最后一分钟速记

> 我的项目不是让 LLM 自由推荐，而是让规则系统确定候选边界，让 Agent 在边界内使用 Tool 完成查询、解释和受控修改。RAG 负责提供可更新、可引用的趋势与政策证据，MySQL Tool 负责精确事实。Tool Runtime 解决模型调用外部能力后的预算、错误、副作用和审计问题；Redis、CompletableFuture 与 SSE 解决长任务并发、状态和体验问题；Qdrant Alias 与 frozen 评测解决知识库发布和效果验证问题。

遇到不会的问题时，不要编造。使用这个回答结构：

> 当前项目怎么做 → 为什么这样取舍 → 已验证到什么程度 → 还存在哪个边界 → 如果进入生产下一步怎么补。
