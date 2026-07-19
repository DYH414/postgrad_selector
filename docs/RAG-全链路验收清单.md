# RAG 全链路验收清单

目标：确认当前 Alias 指向一个完整、可检索、可观测且不会中断在线流量的知识库版本。

## 1. 前置条件

- 后端已读取 `SILICONFLOW_API_KEY`；
- Qdrant 已启动，`http://localhost:6333/dashboard` 可访问；
- MySQL 有学校、专业、复试线、招生计划、录取结果或数据质量记录；
- 管理端账号拥有 `postgrad:rag:view` 与 `postgrad:rag:reindex` 权限。

## 2. 版本化构建

在 `/postgrad/rag` 设置年份范围并点击“重建完整知识库”。页面会调用：

```text
POST /postgrad/rag/index-runs?startYear=2022&endYear=2026
GET  /postgrad/rag/index-runs/{runId}
```

验收点：

- 立即返回 `runId`，页面进入轮询而非等待 HTTP 长连接；
- 状态依次经过 `ASSEMBLY`、`EMBEDDING`、`VALIDATION`、`ALIAS_SWITCH`；
- 成功状态为 `SUCCEEDED`，并显示文档数、Chunk 数、活动版本；
- 失败状态保留错误阶段和摘要，旧活动 Alias 不变；
- `rag-data/generated/` 可看到数据库生成的 Markdown 镜像，但该目录是派生产物，不纳入 Git。

本地 `2022-2026` 的已验证示例为 `3,581` 份文档、`21,461` 个 Chunk；实际数量随有效项目数和年份覆盖变化。

## 3. Alias 与零中断验证

构建期间用另一浏览器窗口执行一次检索测试；旧知识库应持续返回结果。构建成功后执行：

```powershell
Invoke-RestMethod http://localhost:6333/aliases | ConvertTo-Json -Depth 8
```

验收点：

- 存在 `postgrad_rag_active`；
- 它指向 `postgrad_rag_<timestamp>_<suffix>`；
- 管理端“活动版本”与 Alias 指向一致；
- 不应使用固定 `postgrad_rag_main` 作为运行时查询集合。

## 4. Metadata 检索测试

在管理端输入：

```text
深圳大学 300 分左右有什么专业
```

验收点：

- 自动解析并显示 `school_id=15`；
- `retrievedCount > 0`，`selectedCount > 0`；
- 结果来源属于深圳大学，且可查看 `programId`、`docType`、`section`、年份；
- 显示 `vectorRank`、`rerankRank`、`rerankScore` 与 `selectedForPrompt`；
- Trace 显示 Embedding、Retrieval、Rerank 三段耗时。

再以已知项目测试精确范围，例如：

```text
杭州电子科技大学-电子信息-网安方向 为什么被推荐，有什么风险
```

验收点：`program_id`、`school_id`、`doc_type=program_history` 被应用；严格过滤无结果时，Trace 可见受控 fallback，且不会扩展到其他学校。

## 5. 用户端 AI Chat

### 结构化探索

提问：`深圳大学 300 分左右有什么专业`。

- `searchPrograms` 从 MySQL 返回分数、名额和项目事实；
- 同名专业显示 `displayName`，例如“电子信息-人工智能（教育学部，085410，2025）”；
- 不同招生单位或数据年份不可被合并为同一项目；
- 通用 RAG 可以作为辅助上下文，但不替代 MySQL 的分数筛选。

### 精确证据

提问：`帮我分析一下 杭州电子科技大学-电子信息-网安方向 为什么被推荐，有什么风险`。

- Tool 调用出现 `getProgramEvidence(programId, question)`；
- 回答中的学校、分数和计划以 MySQL 事实为准；
- RAG 返回风险、趋势和来源质量等 Chunk，并保留 citation；
- `recommendation-evidence` Trace 中可见过滤条件、召回/选中与 fallback。

### AI Chat Trace

在 `/postgrad/rag` 刷新最近 Trace。

- `operationKey=ai-chat` 且 `retrievalRoute=general`；
- 显示真实 `retrievedCount/selectedCount`，不能固定为 `0/0`；
- 显示 Top 来源、Metadata Filter、Embedding/Retrieval/Rerank 耗时；
- 聊天重复或改写上一问题时，不应将其表述为“用户补充的信息”。

## 6. 运行限制

- `RagTraceRecorder` 的最近记录保留在内存，同时异步持久化摘要到 `rag_retrieval_trace`；后端重启后应仍可查询数据库记录；
- RAG 空召回可降级为 MySQL 工具或明确说明数据不足，不能编造；
- 构建任务在后台执行，禁止连续点击创建多个 Index Run；
- 不在生产活动集合上运行会删除/切换 Alias 的集成测试。
