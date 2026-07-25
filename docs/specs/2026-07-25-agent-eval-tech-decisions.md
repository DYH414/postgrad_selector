# Agent 评测服务 · 技术决策与框架复用

**配套**：`2026-07-25-agent-eval-implementation-spec.md`（怎么做）+ `2026-07-25-evaluation-architecture-governance.md`（为什么）。
**本文回答**：每个关键技术节点选什么框架、复用现有的哪个、为什么不重造。所有复用点均已核对现有代码（附文件路径）。

**总原则**：能复用现有 service/模式的绝不新建；新建仅限"现有的会污染领域或引入安全洞"的地方。核查结论——**真正需要新建的只有 3 个文件**，其余全是复用 + 少量扩展。

---

## 一、Python 侧框架选型（新仓库 `python-evaluation-worker/`）

| 节点 | 选型 | 为什么不造轮子 |
| --- | --- | --- |
| Web 框架 | **FastAPI** | 目标即"简历上 FastAPI 经历"；异步原生、Pydantic 集成 |
| 编排 | **LangGraph 1.x** | 扇出/条件边/循环/中断都是它的一等能力，见下方逐项 |
| 并发限流 | **LangGraph `max_concurrency` config** | ✗ 不用 `asyncio.Semaphore`——官方原生，与调度器/checkpoint 协同；外层 Semaphore 会和调度器打架甚至死锁 |
| 多采样 judge | **LangGraph `@task`（Functional API）** | ✗ 不手写 `asyncio.gather`——`@task` 结果被 checkpoint 缓存、resume 不重跑，单节点内并发 3 采样 |
| 状态持久化 | **`AsyncSqliteSaver`（本地）/ `AsyncPostgresSaver`（生产）** | ✗ 不自建状态表——checkpointer 是 interrupt 恢复的官方载体 |
| 人工挂起 | **`interrupt()` + `Command(resume=...)`** | ✗ 不自建"挂起队列"——1.x 官方 HITL 原语 |
| 循环护栏 | **state 计数器 + 条件边**（`reflect_count ≤ 1`）+ `recursion_limit` 兜底 | 官方明确"没有专门循环上限机制，就用 state 计数器"，我们的设计正确 |
| 数据校验 | **Pydantic v2 strict** | ✗ 不手写 JSON 校验——拒额外字段/缺字段/越界一行搞定 |
| HTTP 客户端 | **httpx（async）** | FastAPI 生态标配 |

**Python 依赖红线**：`requirements.txt` 只有 fastapi/uvicorn/pydantic/httpx/langgraph/langgraph-checkpoint-sqlite/aiosqlite/pytest。✗ 禁止 celery/redis/mysql/qdrant/kafka client——Java 是唯一事实源，Python 不碰业务库。

---

## 二、Java 侧复用清单（已核对现有代码）

| 能力 | 决策 | 复用什么 / 现有位置 |
| --- | --- | --- |
| 异步 Run 状态机 | **抄模式，独立类** | `RagEvalRunService` 的 PENDING→CAS `markRunStarted`→RUNNING→`completeRun`、`selectActiveEquivalentRun` 幂等、`cancel_requested` 协作取消、`markRunningInterrupted` 崩溃恢复。新建 `AgentEvalRunService` + 独立表，**照抄这套 SQL 状态机**，不直接调 RagEvalRunService（RAG 专属指标会污染） |
| 内联 trace 聚合 | **直接复用** | `AiObserveService.listRunLlmTraces(runId)` / `listRunToolTraces(runId)` + `RagTraceRecorder.listPersistedByRunId(runId, n)`。前提：Agent 调用透传同一 `runId`，trace 由 `AiInvocationSnapshotListener`（挂在 `AiConfig` ChatModel listeners）自动落库 |
| 数据集 freeze/import/export | **机制复用** | `RagEvalDatasetService` 的 canonical JSONL + SHA-256 freeze + 生命周期。用例字段 ≤2 处差异就扩展 `RagEvalCaseEntity`（加 `agent_spec_json` 一列），差异大才新建 |
| 运行时 model/temperature 真值源 | **直接复用** | `AiConfig` 的 `@Value("${app.ai.deepseek.model-name}")` / `temperature` 就是运行时唯一构造点。AgentConfigVersion 校验时注入同两个 `@Value` 比对 |
| 数据集/用例 CRUD 端点 | **扩展现有 Controller** | `RagEvaluationWorkbenchController`（`/postgrad/rag-evaluation`）按 evaluationType 分派，不新建控制器 |

**只读隔离**复用 claudecode 模式（能力不给 > 给了再拦）：评测下发工具白名单不含写工具（现有 `V2BoundChatTools`/`V2BoundDraftActionTools` 分离，只传前者）。

---

## 三、必须新建的 3 个文件（仅此三处）

| 新建 | 为什么现有的不行 | 复用什么写法 |
| --- | --- | --- |
| `EvaluationWorkerClient`（Java→Python HTTP） | 全项目无共享 HTTP bean；LangChain4j 的 client 只给 OpenAI 兼容端点 | 照抄 `RagConfig` 的 `SimpleClientHttpRequestFactory` 超时构造 + `SiliconFlowReranker` 的 header/body/fastjson2 解析。用 `RestTemplate`（**项目现有约定，不引入 WebClient/RestClient 第三种**） |
| `agentEvalExecutor`（线程池 config） | `ragEvaluationExecutor` 太小（core1/max2/queue4），是 RAG 负载画像；Agent 调 Python 是 I/O 等待型，共用会互抢触发容量耗尽 | 照抄 `RagEvaluationExecutorConfig` 写法（AbortPolicy + TaskRejectedException 转错误码），参数另调 |
| `InternalTokenAuthenticationFilter`（内部端点认证） | `@Anonymous` 是"无凭证全放行"，把内部端点变公开 = 安全洞 | 照抄 `AppAuthenticationFilter`（`OncePerRequestFilter` + `shouldNotFilter` 限定 `/internal/` 前缀 + 校验 `X-Evaluation-Token`），`SecurityConfig.addFilterBefore` 注册 |

新建的 Java 领域类（实体/Mapper/service）不算"轮子"——它们是 AGENT_E2E 的领域模型，本就该独立：`AgentEvalRunService`、`AgentConfigVersion`、`AgentEvalCaseResult`、`ai_agent_run`、`EvaluationAgentInvocationService`、`InternalEvaluationController`。

---

## 四、两个必须在设计阶段处理的隐患（摸底挖出）

1. **`completeRun` 无终态守卫**：现有 SQL 是 `WHERE run_id=?`，靠单线程 `execute()` + `markRunStarted` CAS 保证只调一次。但 AGENT_E2E 引入 **Python 回调并发落库**，直接照搬会让迟到回调覆盖已终结的 run。**必须**给回调落库和 completeRun 加终态守卫 `AND status NOT IN ('SUCCEEDED','FAILED','CANCELLED')`。（实施规格 §4 端点4 的 CAS + §5.6 取消拒收已覆盖，此处强调 SQL 层面。）

2. **judge prompt 内联在 Java 源码**：`RagAnswerEvaluationService.judgeRequest()` 的 prompt 是 Java text block 硬编码——Python 读不到，会造成"Java 一份 + Python 一份"漂移。而且 CLAUDE.md 明确把"嵌 Prompt 在源码里"列为历史屎山、约定"prompt 走 `@Value(classpath:prompts/...)`"，这个类违反了约定。**必须**先把 judge system prompt + 评分维度抽到 classpath（`resources/prompts/agent-eval-judge.txt` + 维度 schema），Java `@Value` 注入、Python 读同一文件。这是治理纲领 §2"judge 定义共享一份"红线的落地点。

---

## 五、文件目录规划

### 5.1 Java 侧（扩展 = 改现有文件；新建 = 本规格新增）

```
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/
├── rag/eval/workbench/
│   ├── domain/
│   │   ├── RagEvalCaseEntity.java              [扩展] +agent_spec_json 字段
│   │   ├── RagEvalDataset.java / RagEvalRun.java [扩展] +evaluationType 等
│   │   ├── AgentConfigVersion.java             [新建] 不可变配置版本
│   │   └── AgentEvalCaseResult.java            [新建] judge/规则结果
│   ├── mapper/RagEvaluationWorkbenchMapper.java + .xml  [扩展] +Agent CRUD/回调幂等 SQL
│   ├── service/
│   │   ├── RagEvalDatasetService.java          [扩展] freeze/import 认 agent 字段
│   │   ├── AgentEvalRunService.java            [新建] AGENT_E2E 状态机(抄 RagEvalRunService 模式)
│   │   ├── AgentConfigVersionService.java      [新建] 配置版本 + 运行时一致性校验
│   │   └── EvaluationWorkerClient.java         [新建] ★ Java→Python HTTP(RestTemplate)
│   ├── config/
│   │   ├── RagEvaluationExecutorConfig.java    [不动]
│   │   └── AgentEvalExecutorConfig.java        [新建] ★ agentEvalExecutor 线程池
│   └── controller/RagEvaluationWorkbenchController.java [扩展] 按 type 分派
├── recommend/
│   ├── service/EvaluationAgentInvocationService.java [新建] 隔离只读调 Agent
│   └── domain/EvaluationAgentInvocationContext.java  [新建] 只读标志
resources/prompts/
└── agent-eval-judge.txt                         [新建] ★★ judge prompt 外置(隐患2)
resources/rag-eval/
└── judge-spec.md                                [新建] Java/Python 共享的 judge 定义

ruoyi-admin/src/main/java/com/ruoyi/
├── web/controller/postgrad/InternalEvaluationController.java [新建] /internal/v1/evaluation/**
└── framework/
    ├── security/filter/InternalTokenAuthenticationFilter.java [新建] ★ 内部 token 认证
    └── config/SecurityConfig.java              [扩展] +addFilterBefore 注册

sql/21_agent_evaluation_mvp.sql                  [新建] 只加不改旧表
```
★ = 四之外真正的"新建工具类"（HTTP 客户端 / 线程池 / filter）；★★ = 隐患修复。

### 5.2 Python 侧（新仓库根目录，不入 Java module）

```
python-evaluation-worker/
├── app/
│   ├── main.py                  FastAPI + lifespan
│   ├── config.py                环境变量严格启动校验
│   ├── api/internal.py          POST /runs（dispatch）+ POST /runs/{id}/resume + token 依赖
│   ├── models/contracts.py      Pydantic：HTTP/Case/Judge/Result（全类型化，不用裸 dict）
│   ├── clients/
│   │   ├── java_control_plane.py  调 Java：agent-invocations（拿内联 trace）+ result 回调
│   │   └── judge_client.py        OpenAI 兼容 judge，temp=0
│   ├── graph/
│   │   ├── main_graph.py         主图：load→map_fanout(Send)→run_case→aggregate→compare→save
│   │   ├── case_graph.py         case 子图（独立 schema）
│   │   └── state.py              RunState / CaseState
│   ├── rules/checker.py          确定性规则纯函数（从内联 trace 算）
│   └── judge/
│       ├── prompt.py             读共享 judge-spec（与 Java 同源，不重写口径）
│       └── parser.py             Pydantic strict parse
├── tests/                        8 个测试文件（见实施规格 §8）
├── requirements.txt
└── README.md
```

### 5.3 本地规划文档（当前 gitignore，不入库）

```
docs/superpowers/specs/2026-07-25-
├── evaluation-architecture-governance.md   为什么
├── agent-eval-implementation-spec.md       怎么做
└── agent-eval-tech-decisions.md            本文：选型+复用+目录
```

---

## 六、一句话总结

Python 侧全用 LangGraph/FastAPI/Pydantic 原生能力，不手写并发/状态机/校验。Java 侧 8 项能力里 5 项复用现有 service/模式，只有 **HTTP 客户端、线程池、内部 filter 3 个真新建**（且都照抄现有写法）。两个隐患——`completeRun` 终态守卫、judge prompt 外置——在写代码前先处理，避免变成新补丁层。


