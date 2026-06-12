# 概要设计 — AI推荐报告重构

## 0. 设计前提与范围校准

本迭代不是在旧 AI 推荐链路上补充“右侧草稿”能力，而是将 AI 推荐主链路重构为：

`用户画像 -> 候选池 -> 事实卡 -> 风险分档 -> AI 取舍 -> 安全校验 -> 报告草稿 -> 最终报告快照`

旧链路中以对话书签、`addToReport/removeFromReport` 工具、`analyze()` 直接生成报告、`generateReport(conversationId)` 直接从会话生成最终报告的语义，作为旧入口逐步废弃。外部 API 是否保留取决于前端迁移节奏；若保留，必须只作为兼容适配层调用新草稿链路，不再拥有独立业务决策。

本设计默认采纳需求文档“待确认问题”的建议选项，但这些问题当前未见产品负责人在需求文档中显式确认，因此架构状态标记为 `NEED_USER_DECISION`。进入编码前必须由产品负责人确认；若未确认，LLD 只能按以下默认前提继续设计，不能视为最终需求结论。

| 问题 | 默认设计前提 | 状态 |
|------|--------------|------|
| Q1：专业方向缺失时是否允许生成初始草稿 | 允许，默认使用 408 计算机相关范围，并在推荐依据中明示 | NEED_USER_DECISION |
| Q2：同一学校多个专业方向是否允许同时进入主报告 | 允许，但主报告默认限制同校数量，并展示取舍原因 | NEED_USER_DECISION |
| Q3：最终报告生成后是否允许原报告直接编辑 | 不允许直接编辑，只能回到草稿重新生成新报告 | NEED_USER_DECISION |
| Q4：待核验候选是否需要第一阶段展示 | 第一阶段只在摘要中提示数据不足，不做独立主列表 | NEED_USER_DECISION |
| Q5：管理端追溯功能是否纳入第一阶段 | 第一阶段保存追溯信息，完整管理端页面后置 | NEED_USER_DECISION |
| Q6：候选事实卡中哪些事实必须可见 | 默认展示关键摘要，允许展开查看更多 | NEED_USER_DECISION |
| Q7：用户在聊天中新增偏好后是否立即影响草稿 | 只有明确要求重排、替换、补充时才改变草稿 | NEED_USER_DECISION |
| Q8：极端情况下候选池只有 5 所是否强行补满 | 不强行补满，只展示可信候选并说明原因 | NEED_USER_DECISION |

以上前提如产品负责人未确认，应退回需求分析师补齐确认结论。

### 0.1 草稿生命周期

状态定义：

- `GENERATING`：草稿创建成功，候选池、事实卡、分档、AI 取舍或安全校验仍在执行。
- `READY`：草稿生成完成，允许用户查看、移出、加回、聊天调整、生成最终报告。
- `FAILED`：草稿生成失败或安全校验后无可用主报告候选，保留失败原因与可重试信息。
- `FINALIZED`：草稿已生成最终报告快照，禁止继续修改该草稿。
- `ABANDONED`：用户主动放弃或系统清理未完成草稿，禁止生成最终报告。

状态流转：

```
POST /drafts
  -> GENERATING
    -> READY
    -> FAILED

FAILED
  -> POST /drafts/{draftId}/retry -> GENERATING
  -> POST /drafts/{draftId}/abandon -> ABANDONED

READY
  -> remove / restore / chat adjustment -> READY
  -> POST /drafts/{draftId}/reports -> FINALIZED
  -> POST /drafts/{draftId}/abandon -> ABANDONED

FINALIZED / ABANDONED 为终态，不允许再修改。
```

接口状态约束：

| 接口 | 允许状态 | 禁止状态 | 说明 |
|------|----------|----------|------|
| `GET /basis` | 不依赖草稿状态 | - | 只读画像依据 |
| `POST /drafts` | - | - | 创建新草稿 |
| `GET /drafts/{draftId}` | `GENERATING/READY/FAILED/FINALIZED/ABANDONED` | - | 所有状态可查，用于展示进度或失败原因 |
| `GET /drafts/{draftId}/progress` | `GENERATING/FAILED/READY` | `FINALIZED/ABANDONED` | 轮询生成进度 |
| `POST /drafts/{draftId}/retry` | `FAILED` | 其他状态 | 仅失败草稿允许重试 |
| `POST /drafts/{draftId}/remove` | `READY` | `GENERATING/FAILED/FINALIZED/ABANDONED` | 修改草稿并递增版本 |
| `POST /drafts/{draftId}/candidates/{programId}/restore` | `READY` | `GENERATING/FAILED/FINALIZED/ABANDONED` | 修改草稿并递增版本 |
| `POST /drafts/{draftId}/adjustments/chat` | `READY` | `GENERATING/FAILED/FINALIZED/ABANDONED` | 解释类可不改版本；调整类递增版本 |
| `POST /drafts/{draftId}/reports` | `READY` | `GENERATING/FAILED/FINALIZED/ABANDONED` | 成功后流转为 `FINALIZED` |
| `POST /drafts/{draftId}/abandon` | `GENERATING/READY/FAILED` | `FINALIZED/ABANDONED` | 放弃当前草稿 |

失败与重试策略：

- `FAILED` 必须记录 `failureCode/failureMessage/retryable`。
- 候选池为空、画像缺少预计分数为不可直接重试，用户需修改画像或条件。
- AI 超时、AI 输出非法、临时系统异常为可重试，允许 `POST /retry` 重新进入 `GENERATING`。
- 重试不得复用上一次 AI 非法输出；可复用同版本画像和候选硬条件重新生成候选事实卡。
- `READY` 状态下不自动重试或自动补齐。

### 0.2 生成模式

`POST /app/ai-recommend/drafts` 采用异步生成 + 轮询模式：

1. 接口立即创建 `ai_report_draft`，状态为 `GENERATING`，返回 `draftId/status/snapshotVersion/progressUrl`。
2. 后端通过内部异步任务或 RabbitMQ 执行候选池、事实卡、风险分档、AI 取舍、安全校验和草稿落库。
3. 前端轮询 `GET /app/ai-recommend/drafts/{draftId}/progress` 或 `GET /app/ai-recommend/drafts/{draftId}`。
4. 本迭代不要求 SSE 推送草稿生成过程；已有聊天 SSE 不作为草稿生成通道。

## 1.1 API 设计

| 方法 | 路径 | 说明 | 请求体 | 响应体 | 关联需求 | 备注 |
|------|------|------|--------|--------|----------|------|
| GET | `/app/ai-recommend/basis` | 获取生成草稿前的推荐依据摘要 | - | `profileBasis, defaultScope, missingFields` | F001 | 用户端；不触发 AI |
| POST | `/app/ai-recommend/drafts` | 创建新的 AI 推荐报告草稿 | `{ profileOverride?, targetCounts?, source? }` | `draftId, status, snapshotVersion, progressUrl` | F002,F003,F004,F005,F006,F007,F008,F018 | 异步生成；替代旧 `analyze()` 主语义 |
| GET | `/app/ai-recommend/drafts/{draftId}/progress` | 查询草稿生成进度 | - | `draftId, status, progress, failureCode?, retryable?` | F002,F008 | 轮询接口 |
| GET | `/app/ai-recommend/drafts/{draftId}` | 查询报告草稿详情 | - | `draftId, basis, poolSummary, tiers, removedCandidates, traceSummary` | F001,F008,F018 | 右侧报告草稿唯一读取来源 |
| POST | `/app/ai-recommend/drafts/{draftId}/retry` | 重试失败草稿 | `{ snapshotVersion }` | `draftId, status, snapshotVersion, progressUrl` | F002,F008 | 仅 `FAILED` 且 `retryable=true` |
| POST | `/app/ai-recommend/drafts/{draftId}/remove` | 从草稿移出候选 | `{ programId, snapshotVersion, reason? }` | `draftId, snapshotVersion, tiers, removedCandidates, countSummary` | F009 | 避免 DELETE body 兼容问题；记录用户否定状态 |
| POST | `/app/ai-recommend/drafts/{draftId}/candidates/{programId}/restore` | 加回已移除候选 | `{ snapshotVersion, reason? }` | `draftId, snapshotVersion, tiers, restoreResult` | F011 | 重新经过硬条件和安全校验 |
| POST | `/app/ai-recommend/drafts/{draftId}/adjustments/chat` | 通过聊天调整或分析草稿 | `{ message, snapshotVersion, conversationId? }` | `actionType, changed, reply, draft?, alternatives?, snapshotVersion` | F010,F012,F013,F014 | 明确动作才改变草稿；解释类不改草稿 |
| POST | `/app/ai-recommend/drafts/{draftId}/reports` | 由当前草稿生成最终报告 | `{ confirmSnapshotVersion }` | `reportId, status, snapshotSummary` | F015,F016 | 最终报告唯一来源；不重新选校 |
| POST | `/app/ai-recommend/drafts/{draftId}/abandon` | 放弃草稿 | `{ snapshotVersion }` | `draftId, status` | F008 | `FINALIZED` 后不可放弃 |
| GET | `/app/ai-recommend/reports` | 查询历史 AI 推荐报告列表 | - | `reports[]` | F017 | 只返回当前用户报告 |
| GET | `/app/ai-recommend/reports/{reportId}` | 查看历史报告快照 | - | `reportId, profileSnapshot, factSnapshot, tiers, summary` | F017 | 历史正文不被当前数据改写 |
| GET | `/postgrad/ai-recommend/reports/{reportId}/trace` | 管理端查看报告追溯信息 | - | `profileSnapshot, candidateTrace, aiTrace, adjustmentTrace, missingTraceFields` | F019 | 管理端；需 RuoYi 权限 |

### 1.1.1 权限和数据归属

- 用户端所有 `/app/ai-recommend/**` 接口使用自定义 App JWT 当前用户，不使用 RuoYi `@PreAuthorize`。
- `draftId` 必须属于当前 `userId`，否则返回 403。
- `reportId` 必须属于当前 `userId`，否则返回 403。
- `draftId` 状态不满足接口约束时返回 409，并返回 `currentStatus`。
- `snapshotVersion` 冲突返回 409，并返回 `currentSnapshotVersion` 和当前草稿摘要。
- 管理端追溯接口使用 RuoYi 权限标识：`postgrad:aiReport:trace`。
- 管理端 trace 查询不受用户归属限制，但必须记录管理员访问日志；无权限返回 403。

### 1.1.2 snapshot_version 规则

- 新草稿创建时 `snapshot_version=1`。
- 草稿从 `GENERATING` 成功进入 `READY` 时递增 1。
- 用户移出、加回、替换、补充、明确重排、放弃草稿时递增 1。
- 解释类聊天不改变草稿内容，不递增版本。
- 生成最终报告时必须提交 `confirmSnapshotVersion`；与当前版本不一致返回 409，不生成报告。
- 草稿成功 `FINALIZED` 后状态为终态，禁止移出、加回、聊天调整、重试、放弃；再次生成报告返回 409。
- `FAILED` 重试成功进入 `READY` 时递增版本；失败仍保持原版本并更新失败信息。

### 旧接口废弃策略

| 旧接口/能力 | 新定位 | 处理方式 |
|-------------|--------|----------|
| `POST /app/ai-recommend/analyze` | 旧一键分析入口 | 标记废弃；短期内转调 `POST /drafts` 并返回 `draftId`，不再直接表达最终报告语义 |
| `POST /app/ai-recommend/generate-report` | 旧会话生成报告入口 | 标记废弃；只允许在能定位到 `draftId` 时转调 `POST /drafts/{draftId}/reports` |
| `GET /app/ai-recommend/bookmarks/{conversationId}` | 旧右侧书签读取 | 标记废弃；前端迁移到 `GET /drafts/{draftId}` |
| `DELETE /app/ai-recommend/bookmarks/{conversationId}/{programId}` | 旧移出书签 | 标记废弃；转调 `POST /drafts/{draftId}/remove` |
| `AiRecommendationTools.addToReport/removeFromReport` | 旧工具写草稿能力 | 不再作为主链路写入口；AI 工具只能辅助只读分析或输出建议，不直接改报告草稿 |

## 1.2 数据模型变更

新增表：`ai_report_draft`

- `id`: BIGINT [PK]
- `user_id`: BIGINT [NOT NULL]
- `conversation_id`: VARCHAR(64) [NULL]
- `status`: VARCHAR(32) [NOT NULL]，`GENERATING/READY/FAILED/FINALIZED/ABANDONED`
- `profile_snapshot`: JSON [NOT NULL]
- `basis_snapshot`: JSON [NOT NULL]
- `pool_summary_json`: JSON [NULL]
- `draft_json`: JSON [NOT NULL]，当前右侧草稿展示快照，来源于 `ai_report_draft_candidate` 聚合
- `removed_json`: JSON [NULL]，用户移出、拒绝、加回记录摘要，稳定状态以 `ai_report_draft_candidate` 为准
- `trace_json`: JSON [NULL]，候选池、AI 输出、安全校验、排除原因摘要，明细以 `ai_candidate_decision_trace` 为准
- `failure_code`: VARCHAR(64) [NULL]
- `failure_message`: VARCHAR(512) [NULL]
- `retryable`: TINYINT [NOT NULL DEFAULT 0]
- `snapshot_version`: INT [NOT NULL]
- `created_at`, `updated_at`: DATETIME

索引和约束：

- PK：`pk_ai_report_draft(id)`
- IDX：`idx_ai_report_draft_user_status(user_id, status, updated_at)`
- IDX：`idx_ai_report_draft_conversation(conversation_id)`
- CHECK：`status in ('GENERATING','READY','FAILED','FINALIZED','ABANDONED')`

新增表：`ai_report_draft_candidate`

- `id`: BIGINT [PK]
- `draft_id`: BIGINT [NOT NULL]
- `user_id`: BIGINT [NOT NULL]
- `program_id`: BIGINT [NOT NULL]
- `school_id`: BIGINT [NOT NULL]
- `tier`: VARCHAR(32) [NOT NULL]，`reach/steady/safe/pending`
- `system_tier`: VARCHAR(32) [NOT NULL]
- `ai_suggested_tier`: VARCHAR(32) [NULL]
- `status`: VARCHAR(32) [NOT NULL]，`included/removed/rejected/pending`
- `source`: VARCHAR(32) [NOT NULL]，`ai_selected/user_restored/user_requested_alternative`
- `reason_json`: JSON [NULL]，AI 推荐理由、风险提示、取舍说明
- `fact_snapshot`: JSON [NOT NULL]
- `remove_reason`: VARCHAR(512) [NULL]
- `created_at`, `updated_at`: DATETIME

索引和约束：

- PK：`pk_ai_report_draft_candidate(id)`
- UK：`uk_draft_candidate_program(draft_id, program_id)`
- IDX：`idx_draft_candidate_draft_status(draft_id, status, tier)`
- IDX：`idx_draft_candidate_user_program(user_id, program_id)`
- CHECK：`tier in ('reach','steady','safe','pending')`
- CHECK：`status in ('included','removed','rejected','pending')`

新增表：`ai_report_snapshot`

- `id`: BIGINT [PK]
- `draft_id`: BIGINT [NOT NULL]
- `user_id`: BIGINT [NOT NULL]
- `profile_snapshot`: JSON [NOT NULL]
- `fact_snapshot`: JSON [NOT NULL]
- `ai_opinion_snapshot`: JSON [NOT NULL]
- `adjustment_snapshot`: JSON [NULL]
- `report_json`: JSON [NOT NULL]
- `summary_json`: JSON [NULL]
- `created_at`: DATETIME

索引和约束：

- PK：`pk_ai_report_snapshot(id)`
- UK：`uk_ai_report_snapshot_draft(draft_id)`，一个草稿只能生成一个最终报告
- IDX：`idx_ai_report_snapshot_user_created(user_id, created_at)`

新增表：`ai_candidate_decision_trace`

- `id`: BIGINT [PK]
- `draft_id`: BIGINT [NOT NULL]
- `report_id`: BIGINT [NULL]
- `program_id`: BIGINT [NULL]
- `school_id`: BIGINT [NULL]
- `tier`: VARCHAR(32) [NULL]
- `decision_source`: VARCHAR(32) [NOT NULL]，`candidate_pool/ai_selected/safety_rejected/user_removed/user_restored/user_requested_alternative`
- `decision_result`: VARCHAR(32) [NOT NULL]，`included/excluded/pending/restored`
- `reason_code`: VARCHAR(64) [NOT NULL]
- `reason_detail`: VARCHAR(512) [NULL]
- `fact_snapshot`: JSON [NULL]
- `created_at`: DATETIME

索引和约束：

- PK：`pk_ai_candidate_decision_trace(id)`
- IDX：`idx_ai_trace_draft_program(draft_id, program_id)`
- IDX：`idx_ai_trace_report(report_id)`
- IDX：`idx_ai_trace_reason(draft_id, reason_code)`

修改表：`recommendation_log`

- 不再作为 AI 推荐主报告的唯一存储；保留旧记录兼容与审计。
- 新最终报告以 `ai_report_snapshot` 为准；如需兼容旧列表，可由服务层合并读取。

关联关系：

- `ai_report_draft.user_id` 关联 `app_user.user_id`。
- `ai_report_snapshot.draft_id` 关联 `ai_report_draft.id`。
- `ai_report_draft_candidate.draft_id` 关联 `ai_report_draft.id`。
- `ai_candidate_decision_trace.draft_id` 关联 `ai_report_draft.id`。
- 候选事实仍来自 `school/college/program/admission_score/admission_plan/admission_result/program_year_data_quality`。

职责边界：

- `ai_report_draft.draft_json`：面向前端的当前草稿展示快照，便于快速读取；不得作为唯一事实来源。
- `ai_report_draft.removed_json`：面向前端和审计的用户调整摘要；稳定移出/加回状态以 `ai_report_draft_candidate.status` 为准。
- `ai_report_draft.trace_json`：面向草稿详情页的轻量追溯摘要；完整逐候选决策链以 `ai_candidate_decision_trace` 为准。
- `ai_report_draft_candidate`：当前草稿候选的规范状态表，负责支持稳定移出、加回、替换、补充、版本重建。
- `ai_candidate_decision_trace`：append-only 决策流水，负责解释某候选为什么进入、被排除、被移出、被加回。
- `ai_report_snapshot`：最终报告不可变快照，生成后不再读取当前 `program/admission_*` 覆盖正文事实。

## 1.3 模块/类设计

| 类名 | 类型 | 职责 | 依赖 | 关联需求 |
|------|------|------|------|----------|
| `AppAiReportDraftController` | Controller | 用户端草稿创建、查询、移出、加回、聊天调整、生成最终报告 | `IAiReportDraftService`, `IAiReportSnapshotService` | F001-F018 |
| `PostgradAiReportTraceController` | Controller | 管理端报告追溯查询 | `IAiReportTraceService` | F019 |
| `IAiReportDraftService` | Service接口 | 新 AI 推荐草稿主编排；替代旧链路主语义 | `IAiCandidatePoolService`, `IAiCandidateFactService`, `IAiDraftAiSelector`, `IAiDraftSafetyService`, `AiReportDraftMapper` | F002-F014,F018 |
| `AiReportDraftServiceImpl` | Service实现 | 执行候选池、事实卡、分档、AI 取舍、安全校验、草稿保存 | 同上 | F002-F014,F018 |
| `IAiCandidateFactService` | Service接口 | 将候选池转换为事实卡，统一事实字段和数据可靠性 | `ProgramMapper`, `AdmissionScoreMapper`, `AdmissionPlanMapper`, `AdmissionResultMapper` | F004,F018 |
| `IAiRiskTierService` | Service接口 | 基于画像和事实卡计算冲刺、稳妥、保底、待核验分档 | `program_year_data_quality` 相关 Mapper | F005,F016,F018 |
| `IAiDraftAiSelector` | AI专项服务接口 | 让 AI 只在事实卡范围内做取舍并输出观点 | `ChatModel` | F006,F014 |
| `IAiDraftSafetyService` | Service接口 | 校验 AI 结果是否在候选池内、是否重复、是否数据不足、是否被用户移除 | `AiRecommendationSafety`, `AiCandidateDecisionTraceMapper` | F007,NFR007 |
| `IAiReportSnapshotService` | Service接口 | 从草稿生成最终报告快照；读取历史报告 | `AiReportSnapshotMapper`, `AiReportDraftMapper` | F015,F016,F017 |
| `IAiReportTraceService` | Service接口 | 查询管理端追溯摘要和候选决策链 | `AiCandidateDecisionTraceMapper`, `AiReportSnapshotMapper` | F019 |
| `AiLegacyRecommendationAdapter` | Service实现/适配器 | 将旧 `analyze/generate-report/bookmarks` 入口转接到新草稿链路 | `IAiReportDraftService`, `IAiReportSnapshotService` | NFR010/回归测试 |
| `AiReportDraftMapper` | Mapper | 草稿表读写 | `ai_report_draft` | F008,F009,F015 |
| `AiReportDraftCandidateMapper` | Mapper | 草稿候选状态表读写 | `ai_report_draft_candidate` | F008,F009,F011,F012,F013 |
| `AiReportSnapshotMapper` | Mapper | 最终报告快照读写 | `ai_report_snapshot` | F015,F017 |
| `AiCandidateDecisionTraceMapper` | Mapper | 候选决策追溯读写 | `ai_candidate_decision_trace` | F007,F019 |

### 新链路模块关系

```
AppAiReportDraftController
  -> IAiReportDraftService
    -> IAiCandidatePoolService
    -> IAiCandidateFactService
    -> IAiRiskTierService
    -> IAiDraftAiSelector
    -> IAiDraftSafetyService
    -> AiReportDraftMapper / AiReportDraftCandidateMapper / AiCandidateDecisionTraceMapper
  -> IAiReportSnapshotService
    -> AiReportSnapshotMapper
```

### 1.3.1 失败降级策略

| 场景 | 草稿状态/返回 | 降级策略 | 关联需求 |
|------|---------------|----------|----------|
| 用户画像缺少预计分数 | `FAILED`，`failureCode=PROFILE_SCORE_REQUIRED`，`retryable=false` | 不生成草稿，提示补充预计分数 | F002 |
| 候选池为空 | `FAILED`，`failureCode=CANDIDATE_POOL_EMPTY`，`retryable=false` | 不调用 AI，不放宽条件补齐，提示调整画像或条件 | F003,NFR005 |
| 候选事实数据不足 | `READY` 或 `FAILED` 取决于主三档是否为空 | 数据不足候选进入 `pending/rejected`，不得进入主三档 | F004,F018 |
| AI 超时 | 若系统分档后存在主三档候选，则 `READY`；否则 `FAILED` | 使用系统风险分档 Top N 生成草稿，标注 `aiUnavailable=true`，AI 观点字段显示“AI 暂不可用，以下为系统分档结果”；不得编造 AI 理由 | F006,NFR006 |
| AI 输出非法或解析失败 | 若系统分档后存在主三档候选，则 `READY`；否则 `FAILED` | 丢弃 AI 输出，使用系统分档候选生成草稿，并记录 `AI_OUTPUT_INVALID` | F006,F007 |
| AI 返回候选池外/重复/已移除候选 | `READY` 或 `FAILED` 取决于校验后主三档是否为空 | 安全网关剔除，记录逐候选排除原因，不补造候选 | F007 |
| 安全校验后主三档为空 | `FAILED`，`failureCode=NO_VALID_MAIN_CANDIDATE`，`retryable=false` | 不生成空白 `READY` 草稿，提示无可信候选 | F007,F018 |
| 草稿为空时生成报告 | 409，`currentStatus` 或 `failureCode=DRAFT_EMPTY` | 不生成最终报告，提示先生成或调整草稿 | F015,F016 |

### 1.3.2 档位裁决规则

- 系统风险分档是主档位裁决依据。
- AI 可以提出 `ai_suggested_tier` 和理由，但不能把系统判为 `pending/rejected` 的候选提升到 `reach/steady/safe`。
- 当 AI 档位建议与系统分档冲突时，以系统分档为准，并在 `ai_report_draft_candidate` 保存 `system_tier`、`ai_suggested_tier`、`adjusted=true` 和 `adjustReason`。
- AI 观点可用于解释“为什么推荐/不推荐”，不能覆盖事实字段、数据可靠性、用户否定状态和安全网关结论。
- 同一学校多个专业方向允许进入时，由系统同校数量限制先裁决；AI 只能在限制后的候选内取舍。

## 1.4 依赖规则（硬约束）

允许调用关系：

- 用户端 Controller 只能调用 Service，不能直接调用 Mapper。
- `IAiReportDraftService` 是新 AI 推荐主链路唯一编排入口。
- `IAiDraftAiSelector` 只能接收系统事实卡与画像摘要，不能自行查询或编造事实字段。
- `IAiDraftSafetyService` 必须在草稿落库前执行。
- `IAiReportSnapshotService` 生成最终报告时只能读取 `ai_report_draft` 当前快照，不能重新调用候选池或 AI 选校。
- 旧入口若保留，只能通过 `AiLegacyRecommendationAdapter` 转调新草稿链路。
- 草稿修改接口必须校验 `snapshotVersion`，冲突返回 409。
- 任何草稿或报告读取必须先校验当前用户归属；管理端追溯必须校验 `postgrad:aiReport:trace` 权限。

禁止项：

- 禁止旧 `AiRecommendationTools.addToReport/removeFromReport` 直接写入最终报告或主草稿状态。
- 禁止 `generateReport(conversationId)` 从聊天记录或书签重新暗选学校生成最终报告。
- 禁止最终报告生成阶段重新调用候选池、AI 取舍或自动补齐。
- 禁止 `FINALIZED/ABANDONED` 草稿继续执行移出、加回、替换、补充、重试。
- 禁止数据不足候选进入 `reach/steady/safe` 主三档。
- 禁止为了凑满 3/4/3 放宽事实可靠性或使用平均值伪造判断。
- 禁止修改 `ruoyi-system/ruoyi-framework/ruoyi-common`。
- 禁止规则筛选链路调用 AI 草稿服务或依赖本次新增表。

## 1.5 架构决策记录（ADR）

ADR-003: AI 推荐报告采用“草稿优先、快照固化”架构

状态：已接受

背景：需求要求 AI 推荐、加入报告、右侧报告草稿、最终报告关系清晰，并保证最终报告不重新暗中选择学校。

决策：引入 `ai_report_draft` 作为报告草稿唯一中间态，最终报告由 `ai_report_snapshot` 固化。草稿可以被移出、加回、替换、补充；最终报告只读取草稿快照生成。

备选方案：继续使用 Redis bookmarks 或 `recommendation_log.result_json` 承载草稿与报告。未选择原因是语义混杂，无法可靠表达用户否定、候选排除原因和历史快照。

影响：编码必须先实现草稿服务，再接入最终报告；旧 report/bookmark 入口需要迁移或废弃。

ADR-004: 废弃旧 AI 推荐主链路，旧入口只能做兼容适配

状态：已接受

背景：旧链路以对话工具书签和会话生成报告为中心，容易让“聊天记录”“收藏夹”“最终报告”混在一起，不满足本次重构目标。

决策：新主链路以 `POST /app/ai-recommend/drafts` 为入口，旧 `analyze/generate-report/bookmarks` 不再承担主推荐语义。保留时必须经 `AiLegacyRecommendationAdapter` 转调新服务。

备选方案：保留旧链路并在前端模拟草稿。未选择原因是业务事实仍在旧服务中分散，最终报告无法保证来自右侧草稿。

影响：前端需要迁移到 `draftId` 驱动；后端测试需要验证旧入口不会绕过草稿与安全校验。

ADR-005: AI 只输出取舍观点，事实字段由后端事实卡提供

状态：已接受

背景：需求明确本地数据库是主推荐依据，AI 不得编造学校、专业、分数、招生人数、年份等事实。

决策：`IAiCandidateFactService` 构造事实卡，`IAiDraftAiSelector` 只输出 programId、档位建议、推荐理由、风险提示、取舍说明。草稿展示和最终报告中的事实字段全部由后端事实卡补齐。

备选方案：让 AI 直接生成完整报告 JSON。未选择原因是事实幻觉风险高，且难以追溯字段来源。

影响：AI 输出解析失败时可降级为系统分档结果，但不得补造 AI 理由或事实字段。

## 1.6 需求追溯矩阵（概要设计列）

| 需求编号 | 用户故事 | 验收标准 | 概要设计对应 | 详细设计 | 代码路径 | 测试方法 |
|----------|----------|----------|-------------|----------|----------|----------|
| F001 | US-001 | AC5 | GET /app/ai-recommend/basis, Module: ai-report-draft |  |  |  |
| F002 | US-001 | AC1, AC2 | POST /app/ai-recommend/drafts, Module: ai-report-draft |  |  |  |
| F003 | US-001 | AC1, AC3 | POST /app/ai-recommend/drafts, Module: candidate-pool |  |  |  |
| F004 | US-001, US-002 | AC4, AC4 | POST /app/ai-recommend/drafts, Module: candidate-fact-card |  |  |  |
| F005 | US-001 | AC2, AC4 | POST /app/ai-recommend/drafts, Module: risk-tier |  |  |  |
| F006 | US-001 | AC3 | POST /app/ai-recommend/drafts, Module: ai-draft-selector |  |  |  |
| F007 | US-001 | AC3, AC4 | POST /app/ai-recommend/drafts, Module: draft-safety |  |  |  |
| F008 | US-002 | AC1, AC2, AC3, AC4 | GET /app/ai-recommend/drafts/{draftId}, Module: ai-report-draft |  |  |  |
| F009 | US-003 | AC1, AC2, AC4 | POST /app/ai-recommend/drafts/{draftId}/remove, Module: ai-report-draft |  |  |  |
| F010 | US-004 | AC1, AC4, AC5 | POST /app/ai-recommend/drafts/{draftId}/adjustments/chat, Module: draft-adjustment |  |  |  |
| F011 | US-003 | AC3 | POST /app/ai-recommend/drafts/{draftId}/candidates/{programId}/restore, Module: ai-report-draft |  |  |  |
| F012 | US-004 | AC3 | POST /app/ai-recommend/drafts/{draftId}/adjustments/chat, Module: draft-adjustment |  |  |  |
| F013 | US-004 | AC3 | POST /app/ai-recommend/drafts/{draftId}/adjustments/chat, Module: draft-adjustment |  |  |  |
| F014 | US-004 | AC2 | POST /app/ai-recommend/drafts/{draftId}/adjustments/chat, Module: candidate-analysis |  |  |  |
| F015 | US-005 | AC1, AC5 | POST /app/ai-recommend/drafts/{draftId}/reports, Module: report-snapshot |  |  |  |
| F016 | US-005 | AC2, AC3 | POST /app/ai-recommend/drafts/{draftId}/reports, Module: report-snapshot |  |  |  |
| F017 | US-006 | AC1, AC2, AC3 | GET /app/ai-recommend/reports, GET /app/ai-recommend/reports/{reportId}, Module: report-snapshot |  |  |  |
| F018 | US-001, US-004 | AC4, AC3 | POST /app/ai-recommend/drafts, Module: candidate-fact-card |  |  |  |
| F019 | US-007 | AC1, AC2, AC3, AC4 | GET /postgrad/ai-recommend/reports/{reportId}/trace, Module: report-trace |  |  |  |

### 1.6.1 规则筛选隔离回归项

需求文档中的 F020 更适合作为链路隔离类 NFR/回归测试项，而不是本次 AI 报告重构的新功能需求。概要设计将其调整为：

- NFR010：规则筛选与 AI 推荐链路分开。
- 回归测试项：现有规则筛选 API、页面入口、筛选结果语义不依赖 `ai_report_draft/ai_report_snapshot/ai_report_draft_candidate`，不调用 AI 草稿服务。
- 架构约束：规则筛选链路禁止依赖本次新增 AI 草稿表和新服务。

## 1.7 ARCHITECTURE.md 更新建议

建议在 `ARCHITECTURE.md` 的 ADR 索引追加：

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| ADR-003 | AI 推荐报告采用草稿优先、快照固化架构 | 已接受 | 2026-06-12 |
| ADR-004 | 废弃旧 AI 推荐主链路，旧入口只能做兼容适配 | 已接受 | 2026-06-12 |
| ADR-005 | AI 只输出取舍观点，事实字段由后端事实卡提供 | 已接受 | 2026-06-12 |

建议新增 P0 架构合规规则：

| 编号 | 规则 | 验证方式 | 对应章节 |
|------|------|----------|----------|
| ARCH-13 | 最终报告生成不得重新调用候选池或 AI 取舍 | ArchUnit 调用链检查 | AI 应用专项约束 |
| ARCH-14 | 旧 AI 推荐入口必须经 `AiLegacyRecommendationAdapter` 转调新草稿链路 | ArchUnit 调用链检查 | ADR-004 |
| ARCH-15 | AI 草稿主三档候选必须经过 `IAiDraftSafetyService` 校验 | ArchUnit 调用链检查 | ADR-003/ADR-005 |
| ARCH-16 | 规则筛选链路不得依赖 AI 草稿服务或新增草稿表 | ArchUnit 包依赖检查 + Mapper SQL 扫描 | NFR010 |
