# RAG 索引生命周期重构设计

## 1. 背景与根因

当前系统存在两条独立且互相覆盖的索引链路：

- 应用启动时，`RagInitializer` 调用普通 `reindex()`，清空 `postgrad_rag_main` 后只索引 `rag-documents/` 下的 5 份通用文档。
- 管理端数据库索引调用 `reindexDatabaseKnowledge()`，再次清空同一个 Collection，再从 MySQL 导出结构化 Chunk。

因此 Collection 中最终只保留最后一次执行的语料。Qdrant 数据虽然通过 Docker volume 持久化，但应用启动会主动删除并覆盖已持久化的索引。同时，索引状态仅保存在 JVM 的 `AtomicReference` 中，重启后无法恢复；同步 HTTP 重建还会因 Embedding 和入库耗时产生前端超时。

根因不是单个配置错误，而是索引生命周期、语料组装和在线查询共享了同一个可破坏的写入目标。

## 2. 目标

本次重构必须实现：

1. 通用知识和数据库知识在一次构建中形成完整语料，不再互相覆盖。
2. 在线查询始终指向已验收的活动索引，构建失败不得影响旧索引。
3. 应用启动只恢复和检查索引状态，不执行清空或全量 Embedding。
4. 重建任务异步执行并可观察，HTTP 请求不等待完整索引过程。
5. 索引批次、模型、数据范围、进度、数量和错误可持久化追踪。
6. 旧链路可以重构，但最终组件边界必须清晰、简单且只有一个正式重建入口。

## 3. 非目标

本次不实现：

- 多节点 Qdrant 集群或跨机房容灾。
- RabbitMQ 驱动的分布式索引工作流。
- 全量文档管理系统、在线 Markdown 编辑器或复杂审批流。
- BM25、稀疏向量和混合检索升级。
- RAG 检索算法、Rerank 模型和 Agent Prompt 的大范围调整。
- MySQL 源数据的 CDC 实时增量索引；第一版仍采用受控全量构建。

## 4. 核心原则

### 4.1 单一正式构建入口

废弃“普通文档重建”和“数据库文档重建”分别清空同一 Collection 的设计。所有正式重建统一进入 `RagIndexBuildService`，由它组装完整语料并管理索引生命周期。

保留“生成数据库 Markdown”作为可独立执行的运维能力，但它不再代表索引已经更新，也不能直接操作活动 Collection。

### 4.2 数据职责

| 数据 | 责任存储 | 定位 |
|---|---|---|
| 学校、专业、历年招生事实 | MySQL | 结构化事实源 |
| 通用考研知识 | `rag-documents/` | 人工维护语料 |
| 数据库生成 Markdown | `${RAG_DATA_DIR}/generated/` | 可读、可审计的派生产物 |
| Chunk、Embedding、Payload | Qdrant | 可重建检索索引 |
| 索引批次与活动版本信息 | MySQL | 索引控制面 |
| 构建互斥锁、短期进度缓存 | Redis | 临时运行态 |

人工维护的通用文档保留在 classpath。运行时生成的数据库 Markdown 写入应用外部持久目录，不能写入打包后的 classpath。数据库 Markdown 和结构化 Chunk 必须由同一份规范化文档模型生成，使用相同的 `source_id`、`document_version` 和 `content_hash`，防止展示文档与向量内容漂移；索引过程直接消费规范化模型，不重新解析 Markdown。

## 5. 总体架构

```text
rag-documents/ ───────────────┐
                              │
MySQL → Canonical Documents ──┼─→ RagCorpusAssembler
          ├─→ 外部 Markdown   │          │
          └─→ StructuredChunk ┘          ▼
                                   完整 Chunk 集合
                                          │
                                 Candidate Collection
                                          │
                          Payload Index → Batch Embedding
                                          │
                                数量校验 + 检索冒烟
                                          │
                                原子切换 active alias
```

查询端只依赖稳定别名：

```text
Retriever → postgrad_rag_active → postgrad_rag_<version>
```

## 6. 组件边界

### 6.1 `RagCorpusAssembler`

负责组装一次构建需要的完整语料：

- 加载 `rag-documents/` 通用文档并生成标准 Chunk。
- 从 `RagKnowledgeExportService` 获取数据库规范化文档。
- 从同一规范化模型生成外部 Markdown 和结构化 Chunk，索引不依赖源代码目录是否可写。
- 统一补齐 Metadata，并检查 `source_id`、Chunk ID 和内容哈希唯一性。

该组件不连接 Qdrant，不管理任务状态。

### 6.2 `RagCollectionManager`

封装 Qdrant Collection 生命周期：

- 创建版本 Collection。
- 在写入前创建 Payload Index。
- 查询点数量、Collection 配置和 alias 指向。
- 原子切换 `postgrad_rag_active`。
- 删除失败候选版本和过期旧版本。

禁止提供“清空活动 Collection 后原地重建”的公开方法。

### 6.3 `RagIndexBuildService`

唯一正式构建编排器：

1. 获取构建锁并创建 `rag_index_run`。
2. 组装完整语料。
3. 创建候选 Collection 和 Payload Index。
4. 分批调用 Embedding 并写入 Qdrant。
5. 执行数量校验和检索冒烟。
6. 在数据库事务中更新批次状态。
7. 原子切换 alias，并将新版本标记为活动版本。
8. 延迟清理旧版本并释放锁。

### 6.4 `RagIndexStatusService`

负责管理端状态读取：

- 读取 MySQL 最近批次和活动版本。
- 实时检查 Qdrant alias、Collection 可达性和点数量。
- 将持久化状态与实时状态合并为统一响应。

它替代 `AtomicReference<RagStatus>` 作为事实状态源。

### 6.5 `RagIndexJobExecutor`

使用受控的 Spring `TaskExecutor` 执行单个后台构建任务。Redis 分布式锁限制同一时间只有一个构建；第一版不引入消息队列。

## 7. Collection 与 Payload 设计

### 7.1 命名

- 活动别名：`postgrad_rag_active`
- 版本 Collection：`postgrad_rag_<yyyyMMddHHmmss>_<shortRunId>`

任何检索代码只能配置活动别名，不能直接绑定具体版本。

### 7.2 必需 Payload

每个 Chunk 都必须包含：

- `source_type`: `general` 或 `database`
- `source_id`
- `source_name`
- `doc_type`
- `section`
- `chunk_index`
- `content`
- `content_hash`
- `document_version`
- `embedding_version`
- `indexed_at`

学校专业证据额外包含：

- `school_id`
- `school_name`
- `program_id`
- `program_name`
- `year`

通用文档不写入空字符串形式的学校、专业和年份字段，而是省略不适用字段。

为实际过滤字段预先创建 Payload Index：

- `source_type`: keyword
- `school_id`: keyword
- `program_id`: keyword
- `year`: keyword
- `doc_type`: keyword
- `section`: keyword

现有 Metadata 和过滤请求均使用字符串精确匹配，因此第一版统一使用 keyword，禁止同一字段混用空字符串、数字和字符串。将来确有年份范围检索需求时，再通过新索引版本将 `year` 迁移为 integer。

## 8. 索引批次持久化

新增 `rag_index_run`：

| 字段 | 含义 |
|---|---|
| `run_id` | 批次 UUID，主键 |
| `status` | `PENDING/RUNNING/VALIDATING/SWITCHING/SUCCEEDED/FAILED/INTERRUPTED` |
| `candidate_collection` | 候选版本 Collection |
| `previous_collection` | 切换前活动版本 |
| `embedding_model` | Embedding 模型 |
| `embedding_dimension` | 向量维度 |
| `start_year/end_year` | 数据构建范围 |
| `general_document_count` | 通用文档数 |
| `database_document_count` | 数据库文档数 |
| `chunk_count/indexed_chunk_count` | 预期和已写入 Chunk 数 |
| `progress_stage/progress_percent` | 当前阶段和进度 |
| `error_code/error_summary` | 脱敏错误信息 |
| `started_at/finished_at/created_at/updated_at` | 时间字段 |

索引：`(status, created_at)`、`(created_at)`。同一时刻的运行中唯一性由 Redis 锁和服务端二次检查共同保证。

## 9. 构建状态机

```text
PENDING
   ↓
RUNNING ───────→ FAILED
   ↓
VALIDATING ────→ FAILED
   ↓
SWITCHING ─────→ FAILED
   ↓
SUCCEEDED
```

应用启动时，数据库中遗留的 `PENDING/RUNNING/VALIDATING/SWITCHING` 批次统一标记为 `INTERRUPTED`。旧 alias 不变，因此检索继续可用。

## 10. 构建与切换流程

1. 管理端提交重建请求，服务端校验年份范围和模型配置。正式构建索引该范围内全部符合条件的数据，不接受会截断语料的 `limit` 参数。
2. 若已有运行中任务，返回现有 `runId`，不重复启动。
3. 新任务立即返回 `runId`，前端轮询状态接口。
4. 后台创建候选 Collection，先创建 Payload Index，再摄取数据。
5. Embedding 与 Qdrant upsert 使用有限批次；批次大小配置化，默认从保守值开始。
6. 每批更新 `indexed_chunk_count` 和进度，失败时保留明确阶段。
7. 校验候选 Collection 的点数量、向量维度、Payload 完整率和至少一组固定检索用例。
8. 验收通过后原子切换 alias。
9. 新版本标记成功；旧版本至少保留一个，用于人工或自动回滚。

## 11. 启动与读取流程

启动时不再调用 `reindex()`。初始化器只执行：

1. 读取 `postgrad_rag_active` 指向。
2. 检查活动 Collection 可达性、维度和点数量。
3. 恢复管理端状态。
4. 将未结束批次标记为 `INTERRUPTED`。

如果没有活动 alias，RAG 状态为 `NOT_INITIALIZED`，检索走现有无 RAG 降级路径，并提示管理员执行首次构建。

## 12. 管理端接口与交互

正式接口收敛为：

- `POST /postgrad/rag/index-runs`: 创建完整索引任务并返回 `runId`。
- `GET /postgrad/rag/index-runs/{runId}`: 查询阶段、进度、数量和错误。
- `GET /postgrad/rag/status`: 查询活动版本与最近批次。

原“普通重建”和“数据库重建”入口不再直接操作 Collection。管理端保留一个明确的“重建完整知识库”主操作；“生成数据库文档”作为辅助运维操作单独展示。第一阶段保留上一成功 Collection，但不提供回滚接口；需要回滚时通过受控运维命令切换 alias，后续再根据实际频率决定是否产品化。

## 13. 失败与一致性策略

- 语料组装失败：不创建或删除活动索引。
- Embedding 部分失败：任务失败，候选 Collection 不切换；旧索引继续服务。
- Qdrant 部分写入失败：允许按批次有限重试，超过阈值后失败，不在旧索引上补写。
- 校验失败：禁止切换 alias。
- alias 切换失败：活动索引保持原状，任务记录失败。
- 切换成功但数据库状态更新失败：启动恢复逻辑以 Qdrant alias 为实时事实，修正控制面状态并记录告警。
- 浏览器断开或超时：后台任务不受影响，用户可通过 `runId` 恢复查看。
- 重复请求：返回当前运行任务，防止重复消耗 Embedding 配额。

## 14. 旧代码重构边界

允许并建议进行以下定向重构：

- 拆分当前同时承担状态、加载、切分、Embedding 和 Collection 管理的 `RagDocumentService`。
- 将 `QdrantCollectionService.clearCollection()` 从正式构建链路移除，改为版本 Collection 的创建、切换和清理 API。
- 将 `RagInitializer` 改成只检查和恢复状态。
- 统一普通文档和数据库证据的 Metadata 规范。
- 合并管理端两个会覆盖数据的重建入口。

不进行与 RAG 生命周期无关的 Agent、推荐规则或页面重构。

## 15. 迁移策略

1. 创建 `rag_index_run` 表，并配置外部 `${RAG_DATA_DIR}`。
2. 部署支持 alias 的读取代码，但暂时保留原 Collection。
3. 首次执行完整构建，生成版本 Collection 和外部 Markdown 镜像。
4. 校验成功后创建 `postgrad_rag_active` alias。
5. 查询端切换为使用 alias。
6. 禁用旧的启动自动重建、原地清空入口和正式构建的 `limit` 参数。
7. 确认一个发布周期稳定后，再清理旧 `postgrad_rag_main`。

迁移过程保证任意时刻至少有一个可查询 Collection。

## 16. 测试设计

### 16.1 单元测试

- 语料组装同时包含通用文档和数据库文档。
- Metadata 类型和必需字段校验。
- 确定性 Chunk ID 与内容哈希。
- 启动初始化不会调用 Collection 删除或 Embedding。
- 构建锁拒绝并发任务并返回现有 `runId`。
- 任一构建阶段失败都不会切换 alias。
- 校验通过后只执行一次原子切换。
- 中断批次在启动时转换为 `INTERRUPTED`。

### 16.2 集成测试

- 使用真实 Qdrant 测试候选 Collection 创建、Payload Index 和 alias 切换。
- 构建失败时旧 alias 仍可检索。
- 成功构建后活动集合同时存在 `general` 和 `database` Payload。
- `school_id`、`program_id`、`year` 和 `doc_type` 过滤正确。

### 16.3 接口与前端测试

- 创建任务接口快速返回 `runId`。
- 轮询能看到阶段、进度和最终数量。
- 页面刷新后仍能恢复当前任务。
- 失败原因可见且不会显示“已索引”。
- 重复点击不会创建第二个任务。

### 16.4 验收用例

1. 完整构建后，文档统计同时覆盖 5 份通用文档和数据库证据。
2. 重启后活动版本、文档数和 Chunk 数保持不变。
3. 查询“深圳大学”能够命中 `school_id=15` 的数据库证据。
4. 查询“调剂是什么意思”能够命中通用政策文档。
5. 人为制造 Embedding 失败，旧索引仍能完成检索。
6. 构建期间用户检索无空窗。
7. 重复提交只存在一个运行中批次。
8. 管理端不再因等待完整构建而出现 HTTP 超时。

## 17. 后续演进

在本设计稳定后，再独立评估：

- 基于 `content_hash` 的增量 upsert 和删除传播。
- 将外部生成目录升级为对象存储并增加文档版本下载能力。
- RAG 评测集门禁替代基础冒烟校验。
- 混合检索、查询路由和多 Collection Retriever。
- Qdrant snapshot、定期备份和多节点部署。
