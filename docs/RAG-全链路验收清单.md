# RAG 全链路验收清单

目标：验证数据库结构化数据可以变成可检索、可观测、可溯源的推荐证据，而不是只确认 Qdrant 里“有一些向量”。

## 1. 前置条件

- 后端已读取 `SILICONFLOW_API_KEY`
- Qdrant 已启动，`http://localhost:6333/dashboard` 可访问
- MySQL 中存在学校、专业、复试线、招生计划、录取结果、数据来源或数据质量记录
- 管理端账号有 `postgrad:rag:view` 和 `postgrad:rag:reindex` 权限

## 2. MySQL -> RAG 文档

在管理端 `/postgrad/rag` 执行“生成数据库文档”：

| 参数 | 首次建议 |
|------|----------|
| startYear | 2023 |
| endYear | 2025 |
| limit | 1000 |

验收点：

- 返回 `mode=database`
- `documentCount > 0`
- `writtenFileCount > 0`
- 输出目录指向 `rag-generated/`
- 文档内容包含学校、专业、年份、历年数据、数据质量或来源信息
- 页面提示“Qdrant 尚未更新”，不会把生成文档误认为已索引

## 3. RAG 文档 -> Qdrant

推荐执行“生成并索引数据库 RAG”完成闭环；如果分步操作，则在生成文档后继续执行“索引数据库 RAG”。

- `/postgrad/rag/status` 中 `reachable=true`
- `indexed=true`
- `documentCount > 0`
- `chunkCount > 0`
- `lastRunMode=database`
- Qdrant dashboard 可看到 collection `postgrad_rag_main`
- 如果生成成功但索引失败，页面应保留文档生成摘要，并提示索引失败

## 4. Metadata 检索

在检索测试中输入具体问题，例如：

```text
杭州电子科技大学 电子信息 网安方向 推荐风险 历年趋势
```

逐步增加 filter：

| filter | 验收点 |
|--------|--------|
| 无 filter | 能召回相关考研/专业片段 |
| `schoolId=56` | 结果集中学校 id 一致 |
| `programId=2854` | 结果集中专业 id 一致 |
| `year=2025` | 命中对应年份或 fallback 有记录 |
| `docType=program_year` / `section=admission_score` | 能按文档类型和章节缩小结果 |

验收点：

- `appliedFilters` 展示实际过滤条件
- `vectorRank` 有值
- `rerankRank` 有值或能解释 rerank 失败
- `selectedForPrompt=true` 的片段数量符合 `rerankTopN`

## 5. Grounded RAG 评测

在管理端调用 RAG 评测。

验收点：

- 默认评测 case 使用具体学校、专业、年份，而不是“某学校某专业”这类占位问题
- 默认 case 至少覆盖：
  - 杭州电子科技大学 / `schoolId=56`
  - 电子信息-网安方向 / `programId=2854`
  - 2025 年
  - `admission_score`、`admission_plan`、`admission_result`
- 评测失败时返回明确原因：`NO_RETRIEVAL`、`WRONG_SCOPE`、`MISSING_SECTION`、`SOURCE_DATA_MISSING`
- 演示验收建议 `passRate >= 0.66`；如果数据源补齐，应提升到 `passRate >= 0.8`
- 最近 Trace 中出现 `operationKey=rag-evaluate`

## 6. 推荐链路引用

生成或打开一份 AI 推荐报告。

验收点：

- 报告 JSON 中存在 `ragCitations`
- 用户端报告展示“推荐证据引用”
- 专业级 citation 能按 `programId` 分组
- 通用 citation 不强行挂到错误专业上
- citation 展示 `title/sourceName`、`docType`、`section`、`year`、`contentPreview`

## 7. AI 对话证据

在用户端 AI 对话里问：

```text
帮我分析一下 杭州电子科技大学-电子信息-网安方向 为什么被推荐，有什么风险
```

验收点：

- 工具调用出现 `getProgramEvidence`
- 回答仍以 MySQL 的专业、分数、计划等事实为准
- 如果 message metadata 中有 evidence，聊天气泡显示紧凑引用
- 无 evidence 时聊天正常显示，不出现空引用区域

## 8. 管理端 Trace

在 `/postgrad/rag` 查看最近 Trace：

| operationKey | 验收点 |
|--------------|--------|
| `database-generate` | 记录文档数、耗时、参数 |
| `database-reindex` | 记录 chunk 数、耗时、参数 |
| `search-test` | 记录 query、filters、召回数、选中数、embedding/retrieval/rerank 分段耗时 |
| `recommendation-evidence` | 记录 program/school filters、fallback、top sources |
| `rag-evaluate` | 记录总 case、通过 case、失败 case 和代表性失败原因 |
| `ai-chat` | 记录用户对话触发的 AI 调用结果 |

## 9. 已知限制

- 数据库数据稀疏时，RAG 证据会偏向数据质量说明或触发 fallback。
- generated Markdown 是数据库派生产物，默认不要把大量批量输出提交进 Git；只提交必要样例或代码资源结构。
- Trace 是内存环形缓冲，后端重启会清空。
- Qdrant collection 重建会短时间影响检索结果，演示前需要等索引完成。
