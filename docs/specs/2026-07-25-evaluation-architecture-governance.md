# 评测架构治理决定（一页纲领）

**状态：待你拍板** · **作用：作为 Agent Evaluation MVP spec 的前置纲领，spec 的 §5-7 按本文改写**

---

## 0. 为什么会乱

评测这块把**三个本质不同的问题**塞进了同一套 `RagEvaluation*` 命名空间，还用一个只表达"检索配置"的 `Mode` 枚举去承载它们。三个问题各自需要的东西完全不同：

| # | 问题 | 需要什么 | 现状 |
|---|---|---|---|
| ① | 检索准不准（找对 chunk 没） | 纯离线，无 LLM | `RagEvaluationService` + frozen-v3/safety-v3 ✅ |
| ② | 答案好不好（忠实/引用/拒答） | LLM judge | `RagAnswerEvaluationService` + answer-v1 ✅ **已存在** |
| ③ | Agent 行为对不对（路由+工具+端到端） | 真实调 Agent + judge | 未建（spec 目标） |

关键事实：**② 已经是一个能跑的 Java LLM-as-judge**（faithfulness/relevance/citation/abstention 各 0-2 分，judge 跑 2 遍，严格解析）。

---

## 1. 三域边界（别再混）

```
① 运行时（被评测对象）  AiChatServiceImpl / Router / ToolScope / Runtime / 16 Tool / RAG
② 可观测（发生了什么）  3 张 trace 表 + AiObserveController，按 runId 聚合
③ 评测（好不好）        ← 治理只发生在这里
```
①② 清晰、稳定，不动。治理只在 ③。

---

## 2. 判别轴：用 `evaluationType`，不用 `Mode`

```
Dataset.evaluationType / Run.evaluationType =
  RAG_RETRIEVAL   → Case 带 relevantConditions/contentHash；Run 带 RetrievalMode(VECTOR_ONLY/RERANK)
  RAG_ANSWER      → Case 带 requiredClaims/forbiddenClaims/expectedAbstention；Run 走 LLM judge
  AGENT_E2E       → Case 带 agent{...}；Run 带 AgentConfigVersion
```

- `RagEvaluationMode`（VECTOR_ONLY/RERANK）**只属于 RAG_RETRIEVAL**，是它的子维度。
- `AGENT_E2E` 不碰 Mode。spec §4 的 `evaluation_type` 列方向正确。
- 共享 `rag_eval_dataset/case/run` 骨架保留；**不为改名去动 20 个文件**（纯 churn）。Agent 专属字段用嵌套 `agent{}` + 一个 `AgentCaseSpec` record 承载，别加 6 个顶层 JSON 列。

---

## 3. Java / Python 接缝：编排厚、耦合薄

**目标是简历上一个可信的 FastAPI + LangGraph 经历**，所以 Python 吃下整个"评测编排"，Java 只做"跑一次 Agent"。跨语言耦合收敛到一处调用。

**Python / LangGraph（编排半场，评测图的全部逻辑）：**
```
                         ┌─→ judge_sample_1 ─┐
load_case → invoke_agent ─┼─→ judge_sample_2 ─┼─→ vote+variance ──→ 方差过高? ─┐
(调Java一次,拿answer+trace) └─→ judge_sample_3 ─┘                │            │
                                                                 │      reflect+重判(循环)
                                          边界case → human_review(interrupt挂起)
                                                                 │
                                              admin UI 评审后 resume → aggregate → compare(V1/V2/V3)
```
- **扇出**：config 版本 × case 并行（版本化 prompt/rag 变量的对比评测）
- **条件边**：agent 失败跳过 judge；方差过高走 reflect 循环
- **human-in-the-loop**：边界 case 用 LangGraph `interrupt` 挂起，管理端评审后 `resume`（接现有 workbench review 接口）
- **checkpointer**：长跑 + 人工挂起需要恢复，SQLite checkpointer 这里**是必要的**（和薄方案相反）

**Java（执行半场，只有"跑一次 Agent"）：**
- `EvaluationAgentInvocationService.invoke()` — 隔离只读调真实 Agent（spec §3.2 保留）
- **关键：invoke 一次性返回 answer + route + 原始 trace（内联）**，不设独立 trace 轮询端点。评测只读且同步，Agent 跑完后 Java 就地组装完整 trace 一起返回。

**收敛后的 HTTP 契约（比 spec 的 5 端点少一半，且无脱敏矛盾）：**
| 方向 | 端点 | 作用 |
|---|---|---|
| Java → Python | `POST /runs` | dispatch：下发 cases + config 版本，返回 202 |
| Python → Java | `POST /agent-invocations` | 跑一次 Agent，**内联返回 answer + trace**（原始内容，内网可信通道，无需脱敏） |
| Python → Java | `PUT /runs/{id}/results` | 回调：写 case 结果 |
| admin → Python | `POST /runs/{id}/resume` | 人工评审后恢复挂起的图 |

确定性规则（路由/工具/trace 完整）在 Python 侧从 invoke 返回的 trace 算——它已经在编排，顺手算，不必来回。

---

## 4. Java 已有一个 judge —— 分阶段收敛，不现在动它

`RagAnswerEvaluationService`（②）**已经在 Java 里做 LLM judge**（2 遍投票、严格解析、prompt 完整），刚合并、测试全绿。既然编排厚在 Python，长期终态应是 **Python 成为唯一 judge**，但**现在就把它的 judge 拆出来委托 Python 是对刚落地代码的 churn**，违反「不要为改而改」。

**采用分阶段，不是三选一：**

- **阶段 1（本次 MVP）**：Python/LangGraph 只建 `AGENT_E2E` 的评测图。`RagAnswerEvaluationService` **原样不动**，继续管 `RAG_ANSWER`。短期两个 judge 并存是可接受的——因为有明确收敛计划。
- **阶段 2（后续，可选）**：把 `RAG_ANSWER` 的 judge 调用也迁进 Python 图（`RagAnswerEvaluationService` 保留检索+生成，judge 委托 Python），退掉 Java judge。届时 Python 图同时服务两种 `evaluationType`，判断逻辑收敛为一份，Python 项目也更厚（简历更强）。

**红线**：两个 judge 的 prompt/评分口径此刻起就必须**共享同一份定义**（放 `rag-eval/judge-spec.md` 或常量），否则阶段 1 就开始漂移，阶段 2 收敛成本翻倍。这是 CLAUDE.md「第二个补丁是警告」的预防。

---

## 5. 顺带修的两处

- **`selectActiveEquivalentRun` 漏 `modes_json`**（真 bug）：加 AGENT_E2E run-key 时一起改成 `(datasetId, checksum, evaluationType, agentConfigVersionId, modes_json)`，一处 XML 带走。
- **Case 落库**：光改 `RagEvaluationCase` 不够，Entity/Mapper/DB 三处都要扩，否则 agent 字段导入即丢。

---

## 6. 已定的决策（本纲领基线）

1. **目标**：管理端一个 Python 评审 Agent，FastAPI + LangGraph 编排评测、版本化 agent 能力（prompt/rag 变量）。简历导向。
2. **接缝**：编排厚在 Python（扇出/多采样/反思循环/human-in-the-loop/版本对比），Java 只暴露"跑一次 Agent 并内联返回 trace"。收敛为 4 个端点。
3. **judge 冲突**：分阶段——阶段 1 Python 只建 `AGENT_E2E`，Java `RagAnswerEvaluationService` 原样保留；阶段 2 再把 `RAG_ANSWER` judge 迁进 Python。judge prompt 此刻起共享一份定义防漂移。
4. **判别轴**：`evaluationType`（RAG_RETRIEVAL / RAG_ANSWER / AGENT_E2E），`Mode` 降为 RAG_RETRIEVAL 子维度。
5. **顺带修**：`selectActiveEquivalentRun` 补 `modes_json`；Case 落库扩 Entity/Mapper/DB。

## 7. 下一步

按本纲领改写 spec 的 §5-7（编排图、4 端点契约、Python 文件结构），其余章节（§1-4 边界/schema、§3.2 只读隔离、§9.1 modes_json）保留。改写后即可交编码。
