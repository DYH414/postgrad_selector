# Agent 评测服务实施规格（FastAPI + LangGraph）

**状态**：可实施
**取代**：`2026-07-25-agent-evaluation-mvp-implementation-spec.md`（旧 MVP spec）与 `2026-07-25-agent-eval-revised-spec.md`（§7 草稿），二者不再单独维护。
**前置**：`2026-07-25-evaluation-architecture-governance.md`（治理纲领：三域边界 / evaluationType 判别轴 / 编排厚·耦合薄 / judge 分阶段）。
**依据**：LangGraph 1.x 官方模式（docs.langchain.com/oss/python/langgraph）+ agent harness 社区复现模式（learn-claude-code，MIT 教学实现，非官方源码）。

---

## 1. 目标与硬边界

**目标**：管理端一个 Python 评审 Agent。用 FastAPI + LangGraph 编排「配置版本 × 冻结用例」的离线评测：调真实 Java Agent → 确定性规则 → LLM-as-judge 多采样投票 → 边界人工挂起 → 版本对比。版本化 Agent 能力（prompt / RAG 变量 / 模型温度快照）以复现 V1/V2/V3。

**明确做**：
- 只评测只读 Route：`KNOWLEDGE_RAG`、`PROGRAM_FACTS`、`HYBRID`、`DRAFT_READ`、`CLARIFY`。
- 每个 Case 独立单轮，不共享对话历史。
- Java 是数据集/配置/Agent 调用/Trace/结果的唯一事实源；Python 只持有 LangGraph checkpoint，不写业务库。

**明确不做**：
- 不评测 `DRAFT_WRITE` / `FALLBACK`（含写副作用的 Route 在进 Agent 前短路为 `UNSUPPORTED_SIDE_EFFECT`）。
- 不用 MQ / Celery / 多 Worker；不自动改 Prompt / 自动发布配置；不在 Python 连业务库（MySQL/Redis/Qdrant）。
- 阶段 1 不动已落地的 `RagAnswerEvaluationService`（Java 的 RAG_ANSWER judge），见 §2 分阶段。

---

## 2. 三域定位与 evaluationType

评测把三个问题分开，别再混进一套 `Mode`：

| evaluationType | 问什么 | 现状 |
| --- | --- | --- |
| `RAG_RETRIEVAL` | 检索准不准 | `RagEvaluationService` + frozen-v3/safety-v3 ✅ |
| `RAG_ANSWER` | 答案忠不忠实 | `RagAnswerEvaluationService` + answer-v1 ✅（Java judge，本期不动） |
| `AGENT_E2E` | Agent 行为对不对 | 本规格新建（Python 编排） |

- `RagEvaluationMode`（VECTOR_ONLY/RERANK）**只属于 RAG_RETRIEVAL**，是它的子维度，`AGENT_E2E` 不碰。
- **judge 分阶段**：阶段 1 Python 只做 `AGENT_E2E`；`RagAnswerEvaluationService` 原样保留。**红线**：两处 judge 的 prompt 与评分口径从现在起共享一份定义（`rag-eval/judge-spec.md`），防漂移。阶段 2 再把 RAG_ANSWER 的 judge 迁进 Python，退掉 Java judge。

---

## 3. 数据模型

### 3.1 Agent Case（冻结 JSONL，嵌套 `agent{}` 不撑顶层）

```json
{
  "id": "agent-program-facts-01",
  "query": "深圳大学计算机专硕近两年录取分数怎样？",
  "expectedRoute": "PROGRAM_FACTS",
  "category": "program_facts",
  "difficulty": "medium",
  "agent": {
    "requiredTools": ["searchPrograms", "getProgramDetail"],
    "forbiddenTools": ["addDraftCandidate"],
    "expectedAbstention": false,
    "expectedClaims": ["必须说明数据年份"],
    "forbiddenClaims": ["保证录取"],
    "referenceEvidence": [{"evidenceKey": "szu-cs-2025", "citationTokens": ["深圳大学", "2025"]}]
  }
}
```
- 空集合写 `[]`，不省略。`expectedClaims`/`forbiddenClaims` 交 Judge 判（自然语言），规则引擎不做语义匹配。
- `citationTokens` 是确定性引用检查的唯一依据：`referenceEvidence` 非空时，答案须含任一 evidence 的全部 token；为空则 `citationPresent=null`，不进聚合分母。

### 3.2 落库：Case 光改 DTO 不够（三处一起改）

`RagEvaluationCase` 加 `agent{}` 后，还须扩持久化，否则导入即丢：
- `RagEvalCaseEntity` 加一列 `agent_spec_json`（**一个 JSON 列 + 一个 `AgentCaseSpec` record 承载**，不加 6 个顶层列——CLAUDE.md 原则 6）。
- `RagEvalDatasetService` 的 `fromEvaluationCase` / `toEvaluationCase` / canonical JSONL 三处映射。
- Mapper XML resultMap + insert/select。

### 3.3 AgentConfigVersion（不可变配置版本）

复现 V1/V2/V3。创建后文本不可改，改 Prompt 须建新版本。

| 字段 | 说明 |
| --- | --- |
| `versionName` | 唯一 |
| `businessPromptText` / `routingPromptText` | 业务 Prompt / 路由 Prompt 原文；服务端算 SHA-256，请求里的 checksum 忽略 |
| `model` / `temperature` | **部署快照**：创建 ACTIVE 版本时必须与 `AiConfig` 当前全局 Bean 实际值一致，否则 `400 AGENT_CONFIG_RUNTIME_MISMATCH`（全局单例模型架构下，不假装动态切模型） |
| `ragConfigSnapshot` | 从当前 `app.ai.rag.*` 有效配置生成，不含密钥 |
| `toolCatalogChecksum` | `AgentToolCatalog.definitions()` 稳定序列化后计算 |
| `status` | ACTIVE（可被新 Run 选）/ ARCHIVED（仅查历史） |

### 3.4 最小建表（`sql/21_agent_evaluation_mvp.sql`，只加不改旧表）

- `rag_eval_dataset` / `rag_eval_run` 加 `evaluation_type VARCHAR(24) DEFAULT 'RAG_RETRIEVAL'`。
- `rag_eval_case` 加 `agent_spec_json JSON NULL`。
- `rag_eval_run` 加 `agent_config_version_id BIGINT NULL` + `agent_config_snapshot_json JSON NULL`。
- 新表 `agent_config_version`、`ai_agent_run`（可观测 Agent 主记录）、`agent_eval_case_result`（Judge/规则结果，与排名表 `rag_eval_case_result` 分开，不破坏其 Rank 语义）。
- 三张 Trace 表**不加** `ai_agent_run` 外键（它们是 fail-open 观测写入，强外键会让观测故障反向影响在线 Agent）。

---

## 4. HTTP 契约（4 端点，编排厚·耦合薄）

内部端点统一 `X-Evaluation-Token`（Java `app.ai.evaluation.internal-token` / Python `EVALUATION_INTERNAL_TOKEN`，两端启动为空即失败，常量时间比较）。JSON 上限 2 MB。错误统一 `{"code","message","requestId"}`。

| # | 方向 | 端点 | 作用 |
| --- | --- | --- | --- |
| 1 | 管理端 → Java | `POST /postgrad/rag-evaluation/runs`（扩 `evaluationType`+`agentConfigVersionId`） | 校验 FROZEN 数据集/ACTIVE 配置/运行时匹配/只读路由 → 建 Run → dispatch Python，返回 `{runId}` |
| 2 | Java → Python | `POST {worker}/runs`（`Idempotency-Key: dispatch:{runId}`） | 下发 cases + configVersions；Worker 建后台图任务返回 202；重复 dispatchId 幂等 |
| 3 | Python → Java | `POST /internal/v1/evaluation/agent-invocations`（`Idempotency-Key: invoke:{runId}:{caseId}`） | 隔离只读跑一次 Agent，**内联返回 answer+route+agentRunId+原始 trace**（无独立 trace 轮询端点，无脱敏——内网可信通道） |
| 4 | Python → Java | `PUT /internal/v1/evaluation/runs/{runId}/cases/{caseId}/result` | 回调写 case 结果；`result_checksum` 幂等：同 checksum 200 replayed，异 checksum 409 不覆盖；`COUNT(*)` 重算 completed，全终态 CAS 转 SUCCEEDED |
| + | 管理端 → Python | `POST {worker}/runs/{runId}/resume` | 人工评审后带裁定恢复挂起的图 |

**端点 3 内联返回 trace 是关键**：评测只读且同步，Agent 跑完 Java 就地组装完整 trace 一起返回。消掉了旧 spec 的独立 trace 轮询端点 + 脱敏矛盾（judge 拿原始检索内容才能真判 faithfulness）。

---

## 5. LangGraph 编排图（核心）

### 5.0 两层图（已按 LangGraph 1.x 官方模式纠正）

```
主图(run级):  load_run → map_fanout ──Send──▶ run_case(普通节点) ──▶ [reducer累加] → aggregate → compare → save
                                          │ 节点内 await case_subgraph.ainvoke(独立schema)
case子图:  invoke_agent → rule_check ─┬─(agent成功&有答案)→ judge_node → decide ─┬─(方差高)→ reflect(≤1次,回judge)
                                       └─(失败/无答案)──────────────────────────────┼─(边界/冲突)→ human_review(interrupt)→ finalize
                                                                                     └─(明确)──────────────────────→ finalize
```

**三处官方纠正（相比初稿）**：
1. **扇出**：`map_fanout` 从 conditional edge 返回 `[Send("run_case", combo) ...]`；`run_case` 是**父图普通节点**，函数体内 `await case_subgraph.ainvoke(sub_input)`，结果映射回父图带 reducer 的聚合 key。**不用** `add_node(compiled_subgraph)` 共享 schema（会撑爆父 state）。
2. **judge 多采样**：`judge_node` **单节点内用 `@task` 并发 3 采样**（temp=0），方差同节点算。不扇出成 3 个图节点。
3. **并发限流**：**删除外部 `asyncio.Semaphore`**，改 invoke 传 `{"max_concurrency": N}`（LangGraph 原生，与 checkpoint/重试协同；外层 Semaphore 会和调度器打架甚至死锁）。

### 5.1 主图节点

| Node | 输入 | 输出 | 失败 |
| --- | --- | --- | --- |
| `load_run` | dispatch payload | 规范化 state + `(config×case)` 组合列表 | payload 非法 → 整 run 技术失败直接 save |
| `map_fanout` | 组合列表 | conditional edge 返回 `[Send("run_case", combo)]` | 空 → 直接 aggregate |
| `run_case` | 单个 combo | 调 case 子图，映射结果到 `case_results`（`Annotated[list, operator.add]`） | 子图内已兜底，此处不抛 |
| `aggregate` | 累加的 case_results | 按 configVersion 分组聚合 | 纯函数 |
| `compare` | 聚合 | V1/V2/V3 的 passRate/routeAcc/faithfulness/latency/token delta + IMPROVED/REGRESSED/UNCHANGED | 仅同 dataset checksum 算 delta |
| `save_run` | 最终结果 | 回调 Java（端点 4） | 可重试 HTTP 重试 3 次 |

### 5.2 case 子图节点

| Node | 输入 | 输出 | 失败 |
| --- | --- | --- | --- |
| `invoke_agent` | query/configId/ids | answer/route/agentRunId/**内联 trace** | 网络重试 2 次；超时 → `agent_status=timeout`，跳 finalize |
| `rule_check` | case/answer/trace | routeAccuracy/requiredToolHit/forbiddenToolCalled/traceComplete/toolSuccessRate/citationPresent/expectedAbstention | 纯函数 |
| `judge_node` | query/answer/**原始检索内容**/claims | `@task` 并发 3 采样 → 多数投票 passed + 三项均值 + 方差 | 单采样重试 1 次；有效采样<2 → 转 human_review |
| `reflect` | 高方差采样 | 追加一次聚焦分歧的重判，回 decide | `reflect_count ≤ 1`，仍高方差 → human_review |
| `human_review` | 边界全上下文 | `interrupt()` 挂起；resume 带人工裁定 | 挂起态持久化 |
| `finalize` | 规则+判定/裁定/技术错误 | 合成 `passed=规则 AND judgePassed`；**必产出带 status 的结果对象** | 纯函数 |

### 5.3 条件边阈值（明确，不靠"感觉"）

- `rule_check → judge_node`：`agentStatus==ok && answer非空`；否则 → `finalize`（技术失败/无答案不判）。
- `decide → reflect`：三采样 faithfulness 极差 > 0.34（跨一档）。reflect 只 1 次。
- `decide → human_review`：任一——① 有效采样<2；② reflect 后仍高方差；③ 规则与判定冲突（judgePassed 但 forbiddenToolCalled，或 route 错但 judgePassed）。
- `decide → finalize`：三采样一致且无冲突（**绝大多数正常 case 走这条，不打扰人**，否则人工评审被淹）。

### 5.4 State（独立 schema，父图不见 case 私有态）

```python
class RunState(TypedDict):
    run_id: str
    dataset_checksum: str
    config_versions: list[dict]
    combos: list[dict]
    case_results: Annotated[list, operator.add]   # reducer 并行累加
    aggregates: dict
    comparison: dict

class CaseState(TypedDict):          # 子图私有，不渗进 RunState
    run_id: str; case: dict; config_version_id: int
    agent_response: dict; agent_status: str        # ok|timeout|http_error|invalid_output
    trace: dict; rule_result: dict
    judge_samples: list; judge_verdict: dict
    reflect_count: int
    human_decision: dict | None
    failure_reasons: list[str]
    final_result: dict           # ← 必回收，见 §5.5 铁律
```

### 5.5 三条铁律（借 claudecode，防扇出场景崩盘）

1. **错误即数据 + 必定回收确定结果**：case 子图无论 Agent 超时/5xx/judge 无定论/取消，`finalize` 都产出一个带明确 `status` 的结果对象回收给主图。**绝不让异常炸掉扇出、绝不让某个 case 消失**。跨语言调用（超时/5xx/非法响应体）在 `invoke_agent` 归一化成 `agent_status`，当数据处理，不抛。
2. **上下文隔离，只回收结论**：每个 case 子图独立 state，case 间不共享可变量；主图只收判定+分数+证据，不收 Agent 完整对话轨迹（省 token、防串味、可复现）。
3. **活跃态/证据两分离 + 熔断**：编排 state 精简；Agent 完整 trace / judge 原始输出落盘为可回溯证据。judge 重采样、Agent 重试、人工挂起都有次数/时间上限熔断，防单 case 卡死拖垮整批。

### 5.6 持久化 / 中断 / 恢复（官方依据）

- `thread_id = {runId}`，**一 run 一 thread**；case 子图靠 `checkpoint_ns`（`run_case:uuid`）天然隔离，**不给子图分独立 thread**。
- checkpointer：本地 `AsyncSqliteSaver`（官方定位实验/本地）；**生产换 `AsyncPostgresSaver`**（thread_id < 255 字符，runId 用 UUID）。可设 `durability="sync"`。
- `interrupt()` + `Command(resume=...)`（1.x 首选，非旧 `interrupt_before`）。**坑**：resume 时节点从头重跑 → `human_review` 里 interrupt **之前**的副作用（写库/通知）必须幂等或后置；并行多 case 同时挂起用 `Command(resume={interrupt_id: value})` map 形式。
- 循环护栏：业务用 `reflect_count ≤ 1` 条件边；`recursion_limit` 当兜底安全阀（1.0.6+ 默认 1000，更早 25，别混）。
- Worker 重启可从 checkpoint 恢复未完成图（比旧 spec「重启不恢复」更进一步）。取消后拒绝迟到回调，挂起图标 CANCELLED 不许 resume。

---

## 6. 确定性规则 + Judge 契约

### 6.1 确定性规则（Python 从内联 trace 算，不来回 Java）

| 指标 | 计算 |
| --- | --- |
| `agentSuccess` | `ai_agent_run.status == SUCCEEDED` |
| `routeAccuracy` | `observedRoute == expectedRoute` |
| `requiredToolHit` | 所有 requiredTools 至少一次 SUCCESS |
| `forbiddenToolCalled` | 任一 Tool 名在 forbiddenTools 中（通过条件为 false） |
| `toolSuccessRate` | SUCCESS 数 / 全部 Tool 数；无 Tool 记 1.0 |
| `citationPresent` | 按 citationTokens 判；referenceEvidence 为空则 null |
| `expectedAbstention` | 标记 true 时答案含任一固定短语（`无法确认`/`暂无可靠数据`/`信息不足` …）|
| `traceComplete` | 有主记录+route+终态+≥1 条 LLM trace；有 Tool 则每条 Tool trace 有终态 |

Java 内部 Trace DTO 必须把三类 trace 按 `occurredAt` 升序返回，Python 不依赖 Mapper 默认排序。

### 6.2 Judge 契约（temp=0，共享 §2 的 judge-spec 定义）

Judge 只判 `faithfulness / answerRelevance / citationCorrectness / passed / reasons / unsupportedClaims`，**不判 Route/Tool 对错**（那是规则的事）。严格输出：

```json
{"faithfulness":0.0,"answerRelevance":0.0,"citationCorrectness":0.0,
 "passed":false,"reasons":["..."],"unsupportedClaims":["..."]}
```
- 三分 `[0,1]`。`passed=true` 唯一规则：三项 ≥0.80 且 `unsupportedClaims=[]`。
- Pydantic strict：拒额外字段/缺字段/越界。解析失败或超时重试 1 次；再失败写 `JUDGE_INVALID_OUTPUT`/`JUDGE_TIMEOUT`，Case 不通过。
- **temp=0 是硬要求**：否则重试时分数漂移 → `result_checksum` 变 → 合法重试被判 409 冲突。
- 多采样：3 次投票取多数 + 报方差（解决 10-15 样本统计不稳）。

### 6.3 Case 总 passed

`agentSuccess && routeAccuracy && requiredToolHit && !forbiddenToolCalled && traceComplete && judgePassed`。`citationPresent`/`expectedAbstention` 仅在非 null 时并入。技术错误 / `UNSUPPORTED_SIDE_EFFECT` / Judge 失败均不通过。

---

## 7. 只读隔离（借 claudecode「执行前拦截」+ 能力不给）

不能直接调 `IAiChatService.chat()`（它加 Redis 锁、读写聊天历史）。新建 `EvaluationAgentInvocationService` + `EvaluationAgentInvocationContext`（`evaluationMode=true, persistConversation=false, readOnly=true`）：

1. **能力不给 > 给了再拦**（claudecode s06：子 Agent 不放 task 工具）：评测下发工具白名单**不含任何写工具**，只传 `V2BoundChatTools`，绝不传 `V2BoundDraftActionTools`。
2. **执行前拦截**（claudecode PreToolUse）：Router 得 `DRAFT_WRITE` → 在建 `V2ChatToolContext`/模型调用前短路 `UNSUPPORTED_SIDE_EFFECT`。
3. **安全不变式**（claudecode：hook allow 不能越权）：Case 级配置只能收紧，绝不能放宽平台级只读策略。
4. **事后兜底**：运行中出现 `draftChanged` / `confirmationRequired` / trace 里任何 WRITE Tool → 终态改 `UNSUPPORTED_SIDE_EFFECT`，不通过。
5. 不调 `ChatRunLockService`、不读写 `ai:v2:chat:*`、不写 `ai_chat_message`；只读加载专用测试用户（`app.ai.evaluation.subject-user-id`）画像/草稿。
6. 生产 `chat()` 不受影响，不接受评测 Config。

---

## 8. Python 项目结构（`python-evaluation-worker/`，独立仓库根目录，不入 Java module）

```
app/
  main.py                     # FastAPI + lifespan
  config.py                   # 环境变量严格启动校验
  api/internal.py             # /runs dispatch + /runs/{id}/resume + token dependency
  models/contracts.py         # Pydantic：HTTP / Case / Judge / Result（全类型化，不用裸 dict）
  clients/java_control_plane.py  # 调 Java：agent-invocations（拿内联 trace）+ result 回调
  clients/judge_client.py     # OpenAI 兼容 Judge，temp=0
  graph/main_graph.py         # 主图：load→map_fanout(Send)→run_case→aggregate→compare→save
  graph/case_graph.py         # case 子图（独立 schema）
  graph/state.py              # RunState / CaseState
  rules/checker.py            # §6.1 确定性规则纯函数
  judge/prompt.py             # 共享 judge-spec 定义（与 Java 同源）
  judge/parser.py             # Pydantic strict parse
tests/
  test_internal_api.py test_main_graph.py test_case_graph.py
  test_rules.py test_judge_parser.py test_java_client.py
  test_concurrency.py test_e2e_mock_java.py
requirements.txt              # fastapi uvicorn pydantic(v2) httpx langgraph
                              # langgraph-checkpoint-sqlite aiosqlite pytest pytest-asyncio
                              # ✗ 不得有 celery / redis / mysql / qdrant / kafka client
README.md
```
必填环境变量：`EVALUATION_INTERNAL_TOKEN` / `JAVA_CONTROL_PLANE_BASE_URL` / `JUDGE_BASE_URL` / `JUDGE_API_KEY` / `JUDGE_MODEL`（须 ≠ 业务模型）/ `CHECKPOINT_DB_PATH`。

---

## 9. 实施顺序

| 步 | 目标 | 验证 |
| --- | --- | --- |
| 1 | schema + 实体 + Mapper（§3.4） | migration 干净跑；Mapper contract test 覆盖新 statement |
| 2 | Case `agent{}` 落库（§3.2） | 导入-导出往返不丢字段；canonical JSONL checksum 稳定 |
| 3 | AgentConfigVersion（§3.3） | V1/V2 不可变；runtime mismatch 被拒 |
| 4 | 只读隔离调用（§7） | 同步返 answer/route/runId；无 Redis chat/history/message 写；写 Tool 绝不执行 |
| 5 | 内部 Trace DTO 内联返回（§4 端点3） | 按 agentRunId 返回时间升序三类 trace |
| 6 | Run dispatch/回调 + `selectActiveEquivalentRun` 补 `modes_json`（顺带修真 bug） | dispatch 202；回调 insert/replay/conflict；取消拒收；聚合 CAS |
| 7 | FastAPI 骨架 + token/dispatch 幂等 | 健康启动 + 幂等测试 |
| 8 | LangGraph 主图+子图（§5，含三处官方纠正） | Send 扇出；`max_concurrency` 限流；judge `@task` 并发；一 run 一 thread |
| 9 | Rules + Judge（§6，temp=0） | 全指标；strict parser；重试；投票+方差 |
| 10 | 端到端 + compare | mock Java e2e；同 checksum V1/V2 delta 正确 |
| 11 | Vue 最小改造（现有 index.vue + api，不新建页） | 建/看/比 Agent Run；不回归 RAG |

---

## 10. 反面清单（claudecode 反面教材，逐条防）

- **停止 ≠ 完成**：Agent 说做完不代表对。judge+规则是独立验证层，**绝不让 Agent 自评**。
- **外部 Semaphore 限流**：用错工具，删掉，用 `max_concurrency`（§5.0）。
- **case 私有态渗进父图 schema**：撑爆 state，用独立 schema + 节点内 invoke（§5.4）。
- **interrupt 前有副作用**：resume 重跑 → 重复执行，必须幂等/后置（§5.6）。
- **judge 不锁 temp**：分数漂移毁 checksum 幂等（§6.2）。
- **异常炸扇出 / case 消失**：错误即数据，必回收带 status 结果（§5.5）。
- **Python 连业务库**：违背 Java 唯一事实源，依赖树禁止 MySQL/Redis/Qdrant client（§8）。
- **裸 dict/Map 传结果**：用 Pydantic/record 类型化（CLAUDE.md 原则 6）。
- **两个 judge prompt 漂移**：阶段 1 起共享一份定义（§2）。

---

## 附：与旧 MVP spec 的关键差异

| 旧 spec | 本规格 | 原因 |
| --- | --- | --- |
| Python 编排全流程（invoke/trace/rule/judge/save），5 端点 | 编排厚在图、耦合薄：4 端点，trace 内联 | 消脱敏矛盾 + trace 轮询时序 |
| 每 case 平坦图，无扇出/循环/HITL | 主图 Send 扇出 + 子图 + reflect 循环 + interrupt | 版本对比是编排核心；简历深度 |
| 外部 Semaphore(3) | `max_concurrency` config | LangGraph 官方；外层会死锁 |
| judge 单次 | `@task` 并发 3 采样 + 方差 | 10-15 样本统计稳健 |
| checkpoint 不恢复 | thread=runId + 重启可恢复 + HITL 挂起 | interrupt 使其必要 |
| §2.2 修 frozen-v1/v2 基线 | 已作废：v3 迁移已合并入 master | 现状变了 |



