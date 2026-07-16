# RAG 持久化观测与评测平台设计

## 目标

在现有 RAG 检索测试、内存 Trace、Tool Trace、索引批次和离线评测基础上，建设一套可以重复执行、长期保存和跨版本对比的管理端能力。

整体分三期交付，本次优先实现第一期：

1. 持久化 RAG Trace，并通过 `runId` 关联 AI、Tool、RAG 和 SSE 生命周期；
2. 将现有 Retrieval Evaluation 产品化为可保存、可回放、可对比的实验；
3. 增加真实 Agent + LLM 端到端评测与参数实验。

## 非目标

- 不训练或微调 Embedding、Rerank、LLM 模型；“微调面板”定义为检索参数、Prompt 和索引版本实验面板。
- 不在第一期引入独立 ClickHouse、Elasticsearch、Langfuse 或 OpenTelemetry Collector。
- 不把完整 Prompt、完整 Chunk 正文、完整 Tool 参数或用户隐私数据无条件持久化。
- 不让观测写入失败阻塞在线 Agent 回答。

## 当前基础

项目已经具备：

- `RagRetrievalService` 统一执行 Metadata Filter、向量召回和 Rerank；
- `RagTraceRecorder` 保存最近 200 条内存 Trace；
- `ai_tool_call_trace` 持久化 Tool 生命周期；
- `rag_index_run` 持久化索引构建批次；
- Frozen/Safety 数据集及 MRR、Hit@K、Filter Accuracy、拒答和消融评测；
- 管理端 RAG 观测台和 AI 观测台；
- SSE 中的 `run`、`tool_call`、`tool_result` 和确认事件。

主要缺口是 RAG Trace 重启丢失、Vector Score 未完整记录、检索评测不经过 Agent/LLM、实验结果缺少统一持久化模型。

## 总体架构

```text
在线请求
  -> Agent Run
      -> LLM Invocation
      -> Tool Call
      -> RAG Retrieval
      -> SSE Event
            |
            +-- runId 统一关联
            |
            +-- MySQL 摘要持久化

离线评测
  -> Dataset Version
  -> Evaluation Run
      -> Retrieval Mode
      -> Agent E2E Mode
  -> Case Result
  -> Aggregate Metrics
  -> Run A/B Comparison
```

## 第一期：持久化全链路观测

### 数据模型

新增 `rag_retrieval_trace`：

| 字段 | 说明 |
|---|---|
| `id` | 自增主键 |
| `trace_id` | 单次检索唯一 ID |
| `run_id` | 关联 AI/SSE/Tool 的运行 ID，可空 |
| `operation_key` | `ai-chat`、`search-test`、`report-evidence`、`evaluation` |
| `retrieval_route` | `general`、`scoped-evidence`、`program-broad` 等 |
| `query_summary` | 脱敏并截断后的查询摘要 |
| `metadata_filters_json` | 实际生效的过滤条件 |
| `retrieved_count` | 向量召回数量 |
| `selected_count` | Rerank 后选中数量 |
| `fallback_route` | 发生的回退路径 |
| `top_sources_json` | 来源、Chunk ID、排名和分数摘要 |
| `embedding_ms` | Query Embedding 耗时 |
| `retrieval_ms` | Qdrant 检索耗时 |
| `rerank_ms` | Rerank 耗时 |
| `total_ms` | 总耗时 |
| `outcome` | `SUCCESS`、`DEGRADED`、`FAILURE` |
| `failure_stage` | 失败阶段 |
| `error_code` | 稳定错误码 |
| `error_summary` | 脱敏错误摘要 |
| `created_at` | 创建时间 |

不持久化完整 Chunk 正文，只保存 `chunk_id`、来源名称、vector/rerank 排名、分数和短预览。默认保留 30 天，清理操作独立于建表 SQL。

### 写入策略

- `RagTraceRecorder` 继续保留内存环形队列，兼容现有管理端实时体验；
- 新增持久化 Service，以异步方式写入 MySQL；
- 写入异常只记日志和 Micrometer counter，不影响检索结果；
- `search-test`、Agent RAG Tool、报告 Citation 和 Evaluation 都使用同一个 recorder；
- `runId` 由在线请求上下文传入，不在检索内部重新生成不相关 ID。

### Vector Score 修复

`RagRetrievalService` 必须将 `EmbeddingMatch.score()` 写入 `RagChunkResult.vectorScore`，并同时记录：

- `vectorRank`；
- `vectorScore`；
- `rerankRank`；
- `rerankScore`；
- 最终是否被选中。

这样管理端和评测才能解释 Rerank 对单条结果的改善或退化。

### runId 关联

一次在线聊天使用同一个 `runId`：

```text
SSE run event
  -> LLM invocation snapshot
  -> ai_tool_call_trace.run_id
  -> rag_retrieval_trace.run_id
```

SSE 不作为持久化事实源，只负责把该 `runId` 暴露给前端。管理端以 `runId` 聚合查询各子链路。

### 管理端

AI 观测台增加“运行详情”抽屉：

- AI 调用摘要；
- Tool 调用时间线；
- RAG 检索时间线；
- 每次检索的 Metadata、召回/选中、排名变化、耗时、降级与错误；
- SSE 已知生命周期状态。

RAG 观测台保留索引管理和检索测试，Trace 列表改为读取持久化分页接口，同时支持按 `runId`、时间、结果和 operation 筛选。

## 第二期：可重复 Retrieval Evaluation

### 数据模型

新增：

- `rag_eval_dataset`：数据集、版本、类型和冻结状态；
- `rag_eval_case`：问题、预期过滤条件、目标 Chunk、拒答预期；
- `rag_eval_run`：索引版本、模型、TopK、阈值、Rerank 参数和状态；
- `rag_eval_result`：每条 Case 的结果、排名、耗时和失败原因。

JSONL 继续作为可版本控制的源格式。管理端支持导入，不直接替代仓库 Frozen 数据集；冻结版本导入后禁止原地修改，只能复制为新版本。

### 指标

- MRR、Hit@1/3/5、平均排名；
- Filter Accuracy；
- No-answer Accuracy、False Positive Rate；
- Vector Only 与 Vector + Rerank 改善/持平/退化数量；
- Embedding、Retrieval、Rerank、总耗时 P50/P95；
- 每个 Case 的预期与实际差异。

### 实验对比

管理端可以选择两个已完成 Run，对比总体指标、延迟和逐 Case 变化。比较必须使用同一冻结数据集版本；不同数据集只允许并列展示，不计算直接提升比例。

## 第三期：Agent + LLM 端到端评测

### Case 定义

在 Retrieval 预期之外增加：

- `expected_route`；
- `expected_tools`；
- `reference_answer`；
- `must_include` / `must_not_include`；
- `required_citations`；
- `should_abstain`。

### 评分顺序

1. 确定性规则：Tool 是否正确、RAG 是否触发、过滤条件、引用、ID、数值和拒答；
2. Groundedness：答案 Claim 是否能由 Citation 支撑；
3. LLM Judge：只评估完整性、相关性、表达和过度推断。

LLM Judge 必须固定模型、Prompt 版本、温度和 JSON Schema。Judge 原始输出与解析结果均保留摘要，单独统计 Judge 失败，不能把解析失败算作被测 Agent 失败。

### 核心指标

- Tool Selection Accuracy；
- RAG Trigger Precision / Recall；
- Citation Precision / Recall；
- Grounded Claim Rate；
- Unsupported Claim Count；
- Task Success Rate；
- Token、估算成本、首事件与完整响应 P50/P95。

## 参数实验面板

可实验参数限定为：

- 活动或历史索引版本；
- Vector TopK；
- Minimum Score；
- Rerank 开关和 TopN；
- Prompt 版本；
- LLM 模型。

第一版不在线修改生产配置。实验参数只作用于独立 Evaluation Run，验证后再通过正常配置发布流程上线。

## 隐私与保留策略

- 用户 Query 默认截断和基础脱敏；
- Tool 参数只保存字段名摘要；
- Chunk 只保存 ID、来源和短预览；
- Trace 默认保留 30 天，评测 Run 和冻结数据集长期保留；
- 删除用户数据时，应能按 `userId/runId` 清理相关在线 Trace；
- 管理接口沿用 RuoYi 权限控制。

## 可靠性

- Trace 持久化 fail-open；
- Evaluation Run 使用独立线程池和并发上限，不占用在线推荐线程池；
- Run 支持 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED`；
- 单 Case 失败不终止整个数据集，记录错误后继续；
- 服务重启后将未完成 Run 标记为 `INTERRUPTED`，允许重新运行；
- Frozen Run 保存索引 Collection、模型和参数快照，保证结果可解释。

## 分期验收

### 第一期

- RAG Trace 重启后仍可查询；
- 管理端按 `runId` 同时看到 Tool 与 RAG 调用；
- Vector Score、Rerank Score 和排名变化可见；
- Trace 写库失败不影响在线回答；
- 检索测试、Agent RAG Tool、报告证据均产生持久化 Trace。

### 第二期

- 同一冻结数据集可以重复执行；
- 每次 Run 保存完整参数与索引版本；
- 管理端可比较两个 Run 及逐 Case 变化；
- 聚合指标可以由 Case Result 重新计算得到。

### 第三期

- 测试真实 Agent Tool Calling 和最终 LLM 答案；
- 确定性评分与 LLM Judge 分开显示；
- 可以定位失败来自路由、检索、工具、生成、引用还是 Judge；
- 在线请求与评测请求在容量和数据上隔离。
