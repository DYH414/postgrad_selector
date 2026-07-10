# RAG / Qdrant 运维说明

本文是本地开发和演示环境的 RAG 速查手册。当前项目有两条知识路径：

| 路径 | 目录 | 作用 |
|------|------|------|
| 通用知识 | `ruoyi-postgrad/src/main/resources/rag-documents/` | 考研术语、政策、择校方法等通用问答 |
| 数据库证据 | `ruoyi-postgrad/src/main/resources/rag-generated/` | 从 MySQL 学校、专业、复试线、录取结果、招生计划、数据质量生成的推荐证据 |

MySQL 仍然是学校、专业、分数、计划、年份、来源 URL 的事实源；Qdrant 负责语义召回、rerank 和引用溯源，不替代确定性查询。

## 组件

| 组件 | 作用 | 默认值 |
|------|------|--------|
| Qdrant | RAG 向量库 | `http://localhost:6333` / gRPC `6334` |
| Collection | RAG 文档集合 | `postgrad_rag_main` |
| Embedding | 文档与查询向量化 | SiliconFlow `BAAI/bge-m3` |
| Rerank | 检索片段重排 | SiliconFlow `BAAI/bge-reranker-v2-m3` |
| 通用文档目录 | 手工知识来源 | `rag-documents/` |
| 数据库文档目录 | MySQL 生成证据来源 | `rag-generated/` |

## 启动 Qdrant

```powershell
docker compose up -d qdrant
curl http://localhost:6333/collections
```

Qdrant Web UI:

```text
http://localhost:6333/dashboard
```

## 后端环境变量

```powershell
$env:SILICONFLOW_API_KEY="sk-..."
$env:QDRANT_URL="http://localhost:6333"
$env:QDRANT_HOST="localhost"
$env:QDRANT_GRPC_PORT="6334"
$env:QDRANT_COLLECTION="postgrad_rag_main"
```

`QDRANT_API_KEY` 仅在 Qdrant 开启鉴权时设置；本地 Docker 默认不需要。

## 判断 RAG 是否启用

日志出现以下内容表示 collection 和索引流程正常：

```text
[RagQdrant] Collection postgrad_rag_main created or updated
[RagInit] Index complete
```

接口检查：

```powershell
curl http://localhost:6333/collections
curl http://localhost:6333/collections/postgrad_rag_main
```

管理后台页面：

```text
/postgrad/rag
```

关键状态：

| 字段 | 期望 |
|------|------|
| `enabled` | `true` |
| `indexed` | `true` |
| `reachable` | `true` |
| `backend` | `qdrant` |
| `documentCount` / `chunkCount` | 大于 0 |
| `lastError` | 空 |

## 数据库 RAG 文档生成

第一波推荐证据优先从 MySQL 生成，而不是手工写 Markdown。管理端 `/postgrad/rag` 提供参数：

| 参数 | 建议首跑值 | 说明 |
|------|------------|------|
| `startYear` | `2023` | 生成数据起始年份 |
| `endYear` | `2025` | 生成数据结束年份 |
| `limit` | `1000` | 限制导出的专业数量，防止首跑过慢 |

后端接口：

```text
POST /postgrad/rag/generate-documents
```

返回值重点看：

| 字段 | 含义 |
|------|------|
| `documentCount` | 生成的数据库 RAG 文档数 |
| `writtenFileCount` | 写入资源目录的文件数 |
| `outputDirectory` | 输出目录，通常是 `rag-generated/` |
| `mode` | `database` |

数据库 RAG 文档应该带有稳定 metadata，例如：

| metadata | 用途 |
|----------|------|
| `school_id` / `program_id` | 推荐报告和聊天按候选专业精确检索 |
| `school_name` / `program_name` | 用户可读展示 |
| `year` | 历年分数线、录取结果、招生计划过滤 |
| `doc_type` | 区分 `program_history`、`program_year_summary`、`data_quality` 等 |
| `section` | 区分分数趋势、招生计划、风险、数据质量 |
| `source_url` / `source_owner` | 引用溯源 |
| `content_hash` | 生成文档和 chunk 的稳定追踪 |

## 重新索引

手工通用知识重新索引：

```text
POST /postgrad/rag/reindex
```

数据库推荐证据重新索引：

```text
POST /postgrad/rag/reindex-database
```

两者都会写入同一个 Qdrant collection，但用途不同。演示推荐证据时，优先执行：

1. 生成数据库文档
2. 索引数据库 RAG
3. 用 metadata 检索测试验证指定学校/专业

## Metadata 检索测试

管理页“检索测试”支持查询文本加 metadata filter。它不经过 LLM，用于直接验证：

```text
Embedding -> Qdrant -> Rerank -> TopN
```

请求示例：

```json
{
  "query": "广州大学 网络空间安全 录取趋势",
  "programId": "101",
  "schoolId": "22",
  "year": "2025",
  "docType": "program_history",
  "section": "score_trend",
  "maxResults": 5,
  "rerankTopN": 3
}
```

返回结果重点看：

| 字段 | 含义 |
|------|------|
| `appliedFilters` | 实际生效的 metadata filter |
| `results[].sourceName` | 命中的文档来源 |
| `results[].schoolId` / `programId` | 是否命中目标学校/专业 |
| `results[].docType` / `section` | 是否命中目标文档类型和语义章节 |
| `results[].contentPreview` | 命中的切片内容预览 |
| `results[].vectorRank` | Qdrant 向量召回排序 |
| `results[].rerankRank` | rerank 后排序 |
| `results[].selectedForPrompt` | 是否进入最终上下文候选 |

## 推荐链路如何使用 RAG

RAG 不参与候选池排序，也不替代 MySQL。推荐链路里它只做证据层：

| 阶段 | 是否使用 RAG | 说明 |
|------|--------------|------|
| CandidatePool / Workspace | 否 | 使用 MySQL 结构化数据和规则打分 |
| AI 选择 | 间接 | Prompt 仍以结构化 facts 为主 |
| AI 对话解释 | 是 | `getProgramEvidence(programId, question)` 检索专业证据 |
| 报告生成 | 是 | `ragCitations` 来自选中 chunk，前端展示引用 |
| 管理观测 | 是 | Trace 记录 filters、fallback、召回数、选中数、top sources |

## Trace 观测

最近 Trace 接口：

```text
GET /postgrad/rag/traces?limit=20
```

Trace 只保存在后端进程内存中，重启后会清空；它用于演示和排查最近请求，不作为长期审计日志。

重点 operation key：

| operationKey | 含义 |
|--------------|------|
| `search-test` | 管理端检索测试 |
| `recommendation-evidence` | 推荐报告或聊天获取专业证据 |
| `database-generate` | 生成数据库 RAG 文档 |
| `database-reindex` | 索引数据库 RAG 文档 |
| `ai-chat` | AI 对话中发生的 RAG 检索 |

推荐证据 Trace 应重点确认：

- `metadataFilters` 包含预期的 `program_id`、`school_id`、`doc_type` 或 `section`
- `retrievedCount` 大于 0
- `selectedCount` 大于 0
- `fallbackUsed` 可解释，且 fallback 模式不会悄悄扩大到无关学校
- `topSources` 能对应到 `rag-generated/` 文件

## 已知限制

- 数据稀疏时，部分专业没有历年录取结果或复试线，RAG 会返回数据质量说明或触发受控 fallback。
- `RagTraceRecorder` 是内存环形缓冲，不是长期审计系统。
- 批量生成的 Markdown 属于派生产物；默认只保留少量样例或按需求提交，避免把大量数据库快照混进代码提交。
- Qdrant collection 清空重建会影响当前检索结果，演示前应确认索引完成。

## 常见问题

| 现象 | 处理 |
|------|------|
| `Embedding API key not configured` | 检查 `SILICONFLOW_API_KEY` 是否被后端进程读取 |
| `Qdrant unavailable` | 确认 `docker ps` 中 `postgrad-qdrant` 正在运行，并检查 `curl http://localhost:6333/collections` |
| IDEA Docker 插件报 API `1.24` 太旧 | 不用 IDEA 启动 Docker，直接命令行 `docker compose up -d qdrant` |
| Qdrant dashboard 无 collection | 后端还未完成 RAG 初始化，或 `SILICONFLOW_API_KEY` 缺失 |
| 检索测试有结果但推荐报告无引用 | 确认报告候选的 `programId` 有对应 metadata，且 `recommendation-evidence` Trace 无错误 |
| 严格 filter 返回空 | 放宽 `section` 或 `year`，再看 Trace 中的 fallback 记录 |
