# RAG / Qdrant 运维说明

本文描述当前生效的 RAG 运行方式。MySQL 是招生事实的唯一来源；Qdrant 提供语义召回、重排、证据引用和检索观测，不参与候选池的确定性排序。

## 架构与数据流

```text
MySQL 招生数据                 通用 Markdown
       |                            |
       v                            v
程序历史 / 最新事实 / 数据质量  rag-documents/
       \                            /
        -> 结构化 section Chunk + Metadata
        -> Embedding (BAAI/bge-m3)
        -> Candidate Collection
        -> point-count 校验
        -> Qdrant Alias 原子切换
        -> postgrad_rag_active

查询 -> Metadata 解析/过滤 -> Vector Retrieval -> BGE Rerank -> Top-K 证据
```

数据库数据每个专业项目生成三类逻辑文档：

| 文档类型 | 用途 | 典型章节 |
|---|---|---|
| `program_history` | 范围内历年趋势与风险 | `score_trend`、`admission_trend`、`risk_factors` |
| `program_year` | 范围内最新可用年份的事实快照 | `admission_score`、`admission_plan`、`admission_result` |
| `data_quality` | 覆盖度、缺失字段与来源冲突 | `coverage_summary`、`missing_years`、`source_conflicts` |

`program_year` 不是机械取结束年份：对每个专业选取查询范围内最新有数据的年份。批量构建会覆盖全部有效专业项目，不依赖按字母排序的隐式上限。

## 关键组件

| 组件 | 当前约定 |
|---|---|
| Qdrant | REST `http://localhost:6333`，gRPC `6334` |
| 活动别名 | `postgrad_rag_active` |
| 版本集合 | `postgrad_rag_<timestamp>_<suffix>`，由构建任务创建 |
| Embedding | SiliconFlow `BAAI/bge-m3` |
| Rerank | SiliconFlow `BAAI/bge-reranker-v2-m3` |
| 通用知识 | `ruoyi-postgrad/src/main/resources/rag-documents/` |
| 数据库 Markdown 镜像 | `./rag-data/generated/`，派生产物，不提交 Git |
| 构建记录 | MySQL `rag_index_run` |

常用 Payload 字段：`school_id`、`program_id`、`year`、`doc_type`、`section`、`source_type`。候选集合创建时会为常用过滤字段建立 Qdrant Payload Index。

## 本地启动

```powershell
docker compose up -d qdrant
Invoke-RestMethod http://localhost:6333/collections | ConvertTo-Json -Depth 8
```

Qdrant Dashboard：`http://localhost:6333/dashboard`

后端需要配置：

```powershell
$env:SILICONFLOW_API_KEY="sk-..."
$env:QDRANT_URL="http://localhost:6333"
$env:QDRANT_HOST="localhost"
$env:QDRANT_GRPC_PORT="6334"
```

本地默认不需要 `QDRANT_API_KEY`。不要把版本集合名称写死在配置中；运行时统一通过 `postgrad_rag_active` 访问活动知识库。

## 创建或重建知识库

后端启动不会清空或自动重建知识库。管理员从管理端“RAG 观测台”启动一次 Index Run，或调用接口：

```text
POST /postgrad/rag/index-runs?startYear=2022&endYear=2026
GET  /postgrad/rag/index-runs/{runId}
GET  /postgrad/rag/status
```

构建状态依次为：`ASSEMBLY -> EMBEDDING -> VALIDATION -> ALIAS_SWITCH -> SUCCEEDED`。`FAILED` 时旧 Alias 保持不变，候选集合会被清理；`SUCCEEDED` 后 Alias 才切到新集合，因此在线检索不会读到半成品。

本地一次 `2022-2026` 的已验证构建示例为 `3,581` 份逻辑文档、`21,461` 个 Chunk。实际数量随数据库有效项目和数据覆盖变化。

确认 Alias：

```powershell
Invoke-RestMethod http://localhost:6333/aliases | ConvertTo-Json -Depth 8
```

预期能看到：

```text
postgrad_rag_active -> postgrad_rag_<version>
```

## 检索测试与验收

管理端 `/postgrad/rag` 的“检索测试”不经过 LLM，直接验证：

```text
Embedding -> Metadata Filter -> Qdrant -> Rerank -> Top-K
```

例如输入 `深圳大学 300 分左右有什么专业`，系统应解析并应用 `school_id=15`。检查：

- `retrievedCount > 0`、`selectedCount > 0`；
- 返回结果的 `schoolId`、`programId`、`docType`、`section` 与查询范围一致；
- `vectorRank` 与 `rerankRank` 均可见；
- Trace 中有 Embedding/Retrieval/Rerank 分阶段耗时和 Top 来源。

精确证据问题使用 `program_id` 限定。例如“杭州电子科技大学某专业为什么被推荐、有什么风险”会使用 `program_history`，优先召回 `risk_factors`、`score_trend`、`source_quality`。严格过滤无结果时，只允许在同一 `program_id` 内按受控规则放宽，并在 Trace 标记 `fallbackUsed` 与原因。

## AI 推荐与聊天中的使用边界

| 场景 | 事实主源 | RAG 作用 |
|---|---|---|
| 候选池、分档、排序 | MySQL + 规则 | 不参与排序 |
| “300 分有什么专业” | `searchPrograms` MySQL Tool | 通用 RAG 可提供辅助上下文，不能取代分数筛选 |
| “为什么推荐/风险/趋势” | 候选 `programId` + MySQL | `getProgramEvidence` 精确召回可引用证据 |
| 报告生成 | MySQL 事实快照 | 生成 `ragCitations` 追溯来源 |
| 通用政策、408 科目 | `rag-documents/` | 通用检索与回答来源 |

AI Chat 的通用问题会在流式模型调用前执行一次请求级检索，并将同一份结果注入上下文与 `ai-chat` Trace。因此 Trace 的召回数、选中数、过滤条件、Top 来源和 E/Q/R 耗时与该次聊天一致，不依赖异步线程的临时状态。

## 观测与故障排查

接口：

```text
GET /postgrad/rag/status
GET /postgrad/rag/traces?limit=20
POST /postgrad/rag/search-test
```

常见 `operationKey`：

| operationKey | 说明 |
|---|---|
| `search-test` | 管理端直接检索测试 |
| `ai-chat` | 用户聊天的通用 RAG 预检索 |
| `recommendation-evidence` | `getProgramEvidence` 或报告的专业级证据检索 |

`RagTraceRecorder` 仍保留单实例内存最近记录，同时将脱敏、截断后的检索摘要异步写入 MySQL `rag_retrieval_trace`。管理端优先查询持久化记录，数据库写入失败时回退内存记录且不影响在线回答。SSE 事件历史仍不持久化。

| 现象 | 排查方式 |
|---|---|
| `reachable=false` | 检查 Qdrant 容器和 `QDRANT_URL` |
| `indexed=false` | 启动新的 Index Run，查看该 `runId` 的失败阶段与错误摘要 |
| 构建中检索仍正常 | 正常，Alias 仍指向旧活动集合 |
| 搜索无结果 | 先确认 Alias、文档/Chunk 数，再检查 `school_id`、`program_id`、`year`、`doc_type` 过滤是否过窄 |
| AI Chat Trace 为 0/0 | 确认后端已加载请求级预检索版本；成功聊天应记录真实 `retrievedCount/selectedCount` |
| 同名专业分数不同 | 比较 `collegeName`、`programCode`、`degreeType`、`dataYear`；不同招生单位或年份是独立项目 |

## 持久化评测工作台

管理端“RAG 评测工作台”复用在线 `RagRetrievalService`，不会维护第二套检索实现。数据保存在：

| 表 | 内容 |
|---|---|
| `rag_eval_dataset` | 数据集类型、版本、状态、SHA-256 与审计信息 |
| `rag_eval_case` | Query、Metadata、相关证据条件、No-answer 与人工备注 |
| `rag_eval_run` | 状态、参数快照、Alias/Collection、模型、Git commit 与聚合指标 |
| `rag_eval_case_result` | Vector/Rerank 排名、候选摘要、延迟、技术错误与人工诊断 |

推荐工作流：

```text
创建 DEV / 导入 JSONL
  -> 人工维护 Ground Truth
  -> 标记 READY
  -> 冻结为不可变版本并生成 JSONL + manifest
  -> 提交 VECTOR_ONLY + VECTOR_RERANK 后台实验
  -> 查看失败用例并标注原因
  -> 与相同 dataset checksum 的历史 Run 对比
  -> 导出 JSON / Markdown 报告
```

后台实验使用独立有界线程池，不修改在线检索配置；队列耗尽会返回稳定容量错误。应用重启时，未完成的 `RUNNING` 实验标记为 `INTERRUPTED`，已落库的逐用例结果保留。只有相同数据集 SHA-256 的已完成 Run 才展示直接指标变化，避免把数据集变化误写成模型优化。

排名类正样本统计 MRR、Hit@K、Recall@5、nDCG@5 和 Filter Accuracy；SAFETY 数据集单独统计 No-answer Accuracy 与 False Positive Rate。指标用于检索回归与消融，不等同于最终回答忠实度或用户转化效果。

评测权限分为 `postgrad:ragEval:view/edit/run/freeze/compare/export`。非冻结探索 Run 的历史保留应由运维按容量定期清理；冻结数据集、基线 Run 和已引用报告不应自动删除。

当前工作台不包含 BM25/Sparse 混合召回、知识文档上传、OCR、LLM-as-judge，也不接入 LangSmith、Ragas 或 Python sidecar。上述能力应在独立实验验证收益后再引入，不能与本次评测控制面混为一体。
