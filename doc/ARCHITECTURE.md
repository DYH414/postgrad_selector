# 考研择校决策平台 — 架构技术文档

> 生成日期: 2026-06-04 | 基于若依 v3.9.2 (Spring Boot 4.0.3 + JDK 17 + Vue 2/3)

---

## 目录

1. [项目概览](#1-项目概览)
2. [技术栈](#2-技术栈)
3. [模块架构](#3-模块架构)
4. [择校业务架构](#4-择校业务架构)
5. [AI 推荐系统架构](#5-ai-推荐系统架构)
6. [AI 推荐完整流程](#6-ai-推荐完整流程)
7. [系统提示词设计](#7-系统提示词设计)
8. [数据库设计](#8-数据库设计)
9. [前端架构](#9-前端架构)
10. [安全与护栏](#10-安全与护栏)

---

## 1. 项目概览

本项目是一个基于 **RuoYi (若依) v3.9.2** 定制开发的 **考研择校决策平台**，为 408 计算机统考考生提供智能化择校辅助服务。

### 项目组成

| 部分 | 技术栈 | 说明 |
|------|--------|------|
| `ruoyi-admin` | Spring Boot 4.0.3 | Web 入口，Controller 层 |
| `ruoyi-postgrad` | Java 17 | 核心业务模块（择校 + AI 推荐） |
| `ruoyi-framework` | Spring Security + JWT | 安全认证、AOP、数据源 |
| `ruoyi-system` | MyBatis + Redis | 系统管理（用户/角色/菜单） |
| `ruoyi-common` | 通用工具 | 注解、常量、异常、工具类 |
| `ruoyi-quartz` | Quartz | 定时任务管理 |
| `ruoyi-generator` | Velocity | 代码生成器 |
| `ruoyi-ui` | Vue 2.6 + Element UI | 管理后台前端 |
| `user-ui` | Vue 3 + Vite + Pinia | C 端用户应用 |

### 两条业务线

```
┌──────────────────────────────────────────────────────────────┐
│                      考研择校决策平台                          │
├────────────────────────┬─────────────────────────────────────┤
│  管理后台 (ruoyi-ui)    │   用户端 (user-ui)                   │
│  · 学校/学院/专业 CRUD   │   · 用户画像 (Profile)               │
│  · 分数线/招生/录取数据   │   · 规则推荐 (Recommendation)        │
│  · 数据来源审核          │   · AI 对话推荐 (AiChat)             │
│  · 数据采集任务管理       │   · AI 报告 (AiReport)               │
│  · 数据质量监控          │   · 收藏/对比/历史                    │
└────────────────────────┴─────────────────────────────────────┘
```

---

## 2. 技术栈

### 后端

| 组件 | 版本/实现 | 用途 |
|------|----------|------|
| Spring Boot | 4.0.3 | 应用框架 |
| JDK | 17 | 运行环境 |
| MyBatis | 4.0.1 | ORM / 数据访问 |
| Apache Druid | 1.2.28 | 数据库连接池 |
| Spring Security | 6.x | 认证授权 |
| JWT | 0.9.1 | 无状态 Token 认证 |
| Redis | - | 会话/缓存/对话存储 |
| RabbitMQ | spring-boot-starter-amqp | 异步报告生成 |
| FastJSON 2 | 2.0.61 | JSON 序列化 |
| PageHelper | - | 物理分页 |
| SpringDoc | 3.0.2 | OpenAPI 文档 |

### AI 集成

| 组件 | 版本 | 用途 |
|------|------|------|
| langchain4j-core | 1.15.0-beta25 | AI 服务框架 (`@Tool` 注解) |
| langchain4j | 1.15.0-beta25 | AiServices + ChatMemory |
| langchain4j-open-ai | 1.15.0-beta25 | OpenAI 兼容客户端（对接 DeepSeek） |
| langchain4j-community-dashscope | 1.15.0-beta25 | 阿里千问模型支持 |
| DeepSeek API | deepseek-v4-pro | 实际使用的 LLM 模型 |

### 前端

| 组件 | 用户端 (user-ui) | 管理端 (ruoyi-ui) |
|------|-----------------|-------------------|
| 框架 | Vue 3 (Composition API) | Vue 2.6 (Options API) |
| 构建 | Vite | Vue CLI 4 (Webpack) |
| UI 库 | Element Plus | Element UI 2.15 |
| 状态管理 | Pinia | Vuex |
| 路由 | Vue Router 4 | Vue Router 3 |

---

## 3. 模块架构

### 3.1 Maven 模块依赖

```
pom.xml (root, packaging=pom)
├── ruoyi-admin        ← 入口 (RuoYiApplication)
│   └── depends on: ruoyi-framework, ruoyi-postgrad, ruoyi-system
├── ruoyi-postgrad     ← 【核心】择校业务 (Service/Mapper/Domain/Tool)
│   └── depends on: ruoyi-common
├── ruoyi-framework    ← Spring Security, JWT, AOP, 数据权限
│   └── depends on: ruoyi-system
├── ruoyi-system       ← 系统管理 Service/Mapper (若依原始)
│   └── depends on: ruoyi-common
├── ruoyi-common       ← 基础工具 (AjaxResult, BaseController, 注解)
├── ruoyi-quartz       ← 定时任务
│   └── depends on: ruoyi-common
└── ruoyi-generator    ← 代码生成器
    └── depends on: ruoyi-common
```

### 3.2 项目包结构

```
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/
│
├── domain/           (26 个实体类)
│   ├── School.java, College.java, Program.java, Subject.java
│   ├── AdmissionScore.java, AdmissionPlan.java, AdmissionResult.java
│   ├── DataSource.java, CollectionTask.java, DataQuality.java, Staging.java
│   ├── AppUser.java, UserProfile.java, UserFavoriteProgram.java
│   ├── RecommendationLog.java, RecommendationRule.java
│   ├── AiRecommendationSafety.java    ← AI 安全护栏
│   ├── AiReportSupport.java           ← 报告标准化
│   ├── AiToolBudget.java              ← 工具预算控制
│   ├── AiToolTrace.java               ← 工具调用追踪
│   └── RowMap.java, ModuleMeta.java 等 CRUD 元数据
│
├── mapper/           (18 个 Mapper + 19 个 XML)
│   ├── SchoolMapper, CollegeMapper, ProgramMapper... (业务表)
│   ├── AiDatabaseToolMapper           ← AI 数据库查询
│   ├── RecommendationMapper           ← 推荐专用复杂查询
│   └── ProgramSearchMapper            ← 专业搜索
│
├── service/          (20 个接口 + 19 个实现)
│   ├── ISchoolService → SchoolServiceImpl
│   ├── IAiRecommendationService → AiRecommendationServiceImpl ★核心
│   ├── AiReportBuilder → AiReportBuilderImpl ★报告生成
│   ├── IAiCandidatePoolService → AiCandidatePoolServiceImpl ★候选池
│   ├── IProgramRecommendationService → ProgramRecommendationServiceImpl
│   └── ...
│
└── tool/             (工具类)
    ├── AiRecommendationTools.java      ← AI 工具集 (@Tool 注解)
    └── RecommendationDecisionEngine.java ← 五维评分决策引擎
```

### 3.3 Controller 层

```
ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/
│
├── SchoolController.java              # 学校 CRUD
├── CollegeController.java             # 学院 CRUD
├── ProgramController.java             # 专业 CRUD
├── SubjectController.java             # 科目 CRUD
├── AdmissionScoreController.java      # 复试线 CRUD
├── AdmissionPlanController.java       # 招生计划 CRUD
├── AdmissionResultController.java     # 录取结果 CRUD
├── CollectionTaskController.java      # 采集任务管理
├── DataSourceController.java          # 数据来源管理
├── DataQualityController.java         # 数据质量
├── ReviewController.java              # 数据审核
│
├── AppAuthController.java             # C端认证
├── AppProfileController.java          # C端画像
├── AppProgramController.java          # C端专业查询
├── AppFavoriteController.java         # C端收藏
├── AppRecommendationController.java   # C端规则推荐
├── AppAiRecommendationController.java # C端 AI 推荐 ★核心
└── AiReportConsumer.java              # MQ 报告消费者 ★核心
```

---

## 4. 择校业务架构

### 4.1 数据层

系统管理 15 张核心业务表，按数据流分四层：

```
第一层：基础信息
  school → college → program → program_subject
                                      ↓
第二层：考试科目                       subject
                                      
第三层：录取数据
  admission_score (复试线)
  admission_plan  (招生计划)
  admission_result (录取结果)
  data_source     (数据来源)
  
第四层：用户数据
  app_user → user_profile → user_favorite_program
  recommendation_log (推荐快照)
  recommendation_rule (推荐规则)
  
辅助表：
  staging（数据审核暂存）
  data_collection_task（采集任务）
  program_year_data_quality（数据完整度）
```

### 4.2 推荐等级体系

系统定义了 5 级判断标准：

| 判断等级 | 英文标识 | 中文标签 | 条件 |
|---------|---------|---------|------|
| 保底 | `safe` | 保底 | gap≥15, 竞争温和, 数据≥B级 |
| 稳妥 | `steady` | 稳妥 | gap 5-14, 数据可接受 |
| 稳妥偏冲 | `steady_reach` | 稳妥偏冲 | 稳定基础上稍有风险 |
| 小冲 | `small_reach` | 小冲 | gap -10~4, 建议有备选 |
| 高风险冲刺 | `high_risk_reach` | 高风险冲刺 | gap<-10, 数据不足 |
| 数据不足 | `data_insufficient_pending` | 待核验 | 缺少关键数据字段 |

### 4.3 五维评分引擎 (`RecommendationDecisionEngine`)

纯后端计算，不依赖 AI。对每个候选学校计算五维评分：

| 维度 | 键值 | 评分逻辑 |
|------|------|---------|
| 分数匹配度 | `scoreMatch` | gap≥15→90分, gap≥5→75分, gap≥-10→55分, 更低→30分 |
| 竞争程度 | `competition` | 复试/录取比<1.2→85分(温和), <2.0→65分, <3.0→45分, ≥3.0→30分(激烈) |
| 地区匹配 | `regionScore` | 一线城市匹配→90分, 一线不匹配→65分, 偏远→35分 |
| 学校层次 | `schoolTier` | 985→95分, 211→80分, 双一流→70分, 普本→50分 |
| 数据完整度 | `dataCompleteness` | A级→90分, B级→70分, C级及以下→50分 |

加权公式：`weightedScore = Σ(维度分 × 权重因子)`，其中 `high=2.0`, `medium=1.0`, `low=0.3`

---

## 5. AI 推荐系统架构

### 5.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            user-ui (Vue 3)                               │
│                                                                          │
│  AiRecommend.vue  ──→ AiChatPanel.vue                                    │
│  (入口页面)            (对话侧面板)                                        │
│    │                    │                                                │
│    │ 快速推荐            │ AI 对话推荐                                     │
│    ▼                    ▼                                                │
│  /analyze        /chat/stream (SSE)                                      │
└────────┬────────────────┬────────────────────────────────────────────────┘
         │                │
┌────────┴────────────────┴────────────────────────────────────────────────┐
│                      ruoyi-admin (Controller 层)                         │
│                                                                          │
│  AppAiRecommendationController                                           │
│  ├── POST /start          → 创建对话 + 候选池                             │
│  ├── POST /chat           → 非流式对话                                    │
│  ├── POST /chat/stream    → SSE 流式对话 (120s 超时)                      │
│  ├── POST /generate-report → 异步生成报告 (RabbitMQ / 同步降级)           │
│  ├── GET  /report/{id}    → 获取报告                                     │
│  ├── GET  /reports        → 报告列表                                     │
│  ├── POST /resume         → 恢复对话                                     │
│  └── POST /analyze        → 一键快速分析                                  │
│                                                                          │
│  AiReportConsumer (RabbitMQ Listener)                                    │
│  └── 监听 ai.report.queue → 消费消息 → 调用 AI 生成报告                   │
└────────┬─────────────────────────────────────────────────────────────────┘
         │
┌────────┴─────────────────────────────────────────────────────────────────┐
│                    ruoyi-postgrad (Service 层)                            │
│                                                                          │
│  ★ AiRecommendationServiceImpl                                           │
│  ├── startConversation()  → 构建 System Prompt + 候选池摘要 + Redis 存储  │
│  ├── chat()               → langchain4j AiServices + @Tool 非流式对话     │
│  ├── chatStream()         → langchain4j TokenStream SSE 流式对话          │
│  ├── generateReport()     → RabbitMQ 异步 / 同步降级                      │
│  ├── analyze()            → 一键快速分析 (宽候选池 + MQ)                   │
│  └── resumeConversation() → Redis 恢复对话                                │
│                                                                          │
│  ★ AiReportBuilderImpl                                                   │
│  ├── buildConversationReport() → 基于对话历史的报告生成                    │
│  ├── buildAnalyzeReport()     → 基于画像的一键分析报告                     │
│  ├── hydrateReportPrograms()  → 数据库补全事实字段 + 安全降级              │
│  └── ruleBasedFallback()      → AI 解析失败时的兜底规则推荐                │
│                                                                          │
│  ★ AiCandidatePoolServiceImpl                                            │
│  ├── buildPool()          → 构建对话候选池 (≤50 个)                        │
│  ├── buildAnalysisPool()  → 构建分析候选池 (分层抽样)                      │
│  └── buildAgentPool()     → 构建 Agent 用宽候选池 (≤500 个)               │
│                                                                          │
│  ★ AiRecommendationTools (@Tool)                                          │
│  ├── getProgramDetail()   → 学校完整录取数据 (带对话级缓存)                │
│  ├── searchPrograms()     → 候选池内筛选 (含 facets 聚合)                  │
│  ├── comparePrograms()    → 多校横向对比                                   │
│  ├── queryDatabase()      → 直接查询 MySQL (突破候选池限制)                 │
│  ├── expandCandidatePool()→ 扩展候选池                                     │
│  └── verifyOfficialInfo() → 官网信息核验 (Phase1 仅本地)                    │
└────────┬─────────────────────────────────────────────────────────────────┘
         │
┌────────┴─────────────────────────────────────────────────────────────────┐
│                        外部服务 & 基础设施                                 │
│                                                                          │
│  DeepSeek API (deepseek-v4-pro)                                          │
│  └── baseUrl: https://api.deepseek.com/v1                                │
│  └── auth: DEEPSEEK_API_KEY 环境变量                                      │
│                                                                          │
│  Redis                                                                   │
│  ├── ai:conv:{id}    → 对话历史 JSON (TTL 30min)                          │
│  ├── ai:pool:{id}    → 候选池 JSON (TTL 30min)                           │
│  ├── ai:owner:{id}   → 对话所有者 userId (TTL 30min)                     │
│  ├── ai:agent:pool:{reportId} → 快速分析候选池 (TTL 1h)                   │
│  ├── ai:analyze:pool:{reportId} → 快速分析候选池备用键 (TTL 1h)           │
│  └── ai:report:{id}  → 报告 JSON (TTL 7天)                               │
│                                                                          │
│  RabbitMQ (可选，默认启用)                                                 │
│  └── Queue: ai.report.queue (durable)                                    │
│  └── Consumer: AiReportConsumer (concurrency=1)                           │
│                                                                          │
│  MySQL (postgrad_selector)                                                │
│  └── AiDatabaseToolMapper → AI 直接查询数据库                              │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.2 核心技术：langchain4j 集成

```java
// 1. 构建模型
ChatModel chatModel = OpenAiChatModel.builder()
    .baseUrl("https://api.deepseek.com/v1")
    .apiKey(System.getenv("DEEPSEEK_API_KEY"))
    .modelName("deepseek-v4-pro")
    .build();

// 2. 构建 AI 服务（注入工具 + 内存 + 系统提示）
RecommendationAssistant assistant = AiServices.builder(RecommendationAssistant.class)
    .chatModel(chatModel)
    .tools(aiRecommendationTools)          // @Tool 注解的 Bean
    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
    .systemMessageProvider(ignored -> systemPrompt)
    .build();

// 3. 调用
String response = assistant.chat("开始择校对话...");
```

### 5.3 AI 工具集 (`AiRecommendationTools`)

每个工具方法使用 `@Tool` 注解，AI 可自主决定调用时机：

| 工具 | 预算消耗 | 说明 |
|------|---------|------|
| `getProgramDetail(programId)` | 800 | 获取学校完整数据，有对话级缓存避免重复查询 |
| `searchPrograms(filters)` | 1000 | 在候选池内筛选，返回摘要+facets+分页提示 |
| `comparePrograms(ids)` | 1200 | 多校横向对比，返回完整字段 |
| `queryDatabase(filters)` | 500 | 直接查 MySQL，突破候选池限制 |
| `expandCandidatePool(filters)` | 600 | 从 DB 查询新学校合并到候选池并写回 Redis |
| `verifyOfficialInfo(input)` | 500 | Phase 1 仅返回本地状态，暂不联网 |

**工具预算** (`AiToolBudget`)：
- 报告生成默认 20 次总调用
- 对话轮次默认 8 次
- 超预算返回 `tool_budget_exceeded` 错误

**工具追踪** (`AiToolTrace`)：
- 记录每次工具调用的参数、结果和时间
- 用于报告生成后的验证和审计

### 5.4 流式对话 (SSE)

```
Browser (fetch + ReadableStream)  ←→  Server (SseEmitter)
────────────────────────────────────────────────────────
POST /app/ai-recommend/chat/stream
Content-Type: application/json
Response: text/event-stream

event: thinking
data: {"text":"正在查询学校详细数据..."}

event: token
data: {"text":"根据"}

event: token
data: {"text":"查询"}

...

event: done
data: {"message":"...","options":["..."],"conversationId":"..."}

event: error
data: {"message":"AI 对话暂不可用，请稍后重试"}
```

**前后端降级机制**：
- 流式失败 → 前端自动降级重试 `postAiChat()`（非流式）
- 非流式失败 → 后端重试一次
- 彻底失败 → 返回 "AI 服务暂时不可用" + 空选项

---

## 6. AI 推荐完整流程

### 6.1 流程一：AI 对话推荐

```
用户进入 /ai-recommend
  │
  ▼
Step 1: 展示用户画像 (AiRecommend.vue)
  · 读取 user_profile 表
  · 展示: 预估分、目标地区、本科层次、跨考、偏好设置
  · 缺失字段提示用户补充
  │
  ▼
Step 2: 用户点击「AI 对话推荐」
  │
  ▼
Step 3: POST /start 创建对话
  ├── 加载用户画像 → 提取 estimatedScore, undergradTier, isCrossMajor, targetRegions
  ├── 构建候选池 (AiCandidatePoolService.buildPool)
  │   · 查询 408 考试组合的院校数据
  │   · 按分数差距分层: 保底(gap≥15) / 稳妥(gap 5-14) / 可冲(gap -10~4) / 高难(gap<-10)
  │   · 每层采样 ≤50 个
  ├── 生成候选池摘要文本
  │   · 每行: 学校名 | 专业 | 层次 | 均分 | 差距 | 招生 | 完整度 | canBeSafe | quotaRisk
  ├── 拼接 System Prompt (见第7节)
  │   · 注入: 预估总分、本科层次、跨考情况、目标地区、候选池摘要
  ├── 创建 LangChain4j AiServices 实例
  │   · ChatModel: DeepSeek deepseek-v4-pro
  │   · Tools: AiRecommendationTools (@Tool 方法)
  │   · ChatMemory: MessageWindowChatMemory(maxMessages=20)
  │   · SystemMessageProvider: 返回拼接好的系统提示
  ├── 发送初始消息: "开始择校对话。不要自我介绍，直接询问用户最看重哪个维度"
  ├── 存储到 Redis:
  │   · ai:conv:{conversationId} → 对话历史 (TTL 30min)
  │   · ai:pool:{conversationId} → 候选池 (TTL 30min)
  │   · ai:owner:{conversationId} → userId (TTL 30min)
  └── 返回: conversationId + AI 首轮回复 + 偏好选项
  │
  ▼
Step 4: 对话循环 (AiChatPanel.vue)
  ├── 用户输入消息或点击快捷选项
  ├── POST /chat/stream (SSE 流式)
  │   ├── 从 Redis 恢复对话历史 → 重建 ChatMemory
  │   ├── 重建 AiServices (含 SystemPrompt + Tools)
  │   ├── 调用 assistant.chat(message)
  │   │   └── 包裹在 <user_input>message</user_input> 中
  │   ├── AI 自主调用工具:
  │   │   ├── getProgramDetail → 前端显示 "正在查询学校详细数据..."
  │   │   ├── searchPrograms → 前端显示 "正在搜索符合条件的学校..."
  │   │   ├── comparePrograms → 前端显示 "正在对比学校数据..."
  │   │   └── queryDatabase → 前端显示 "正在查询数据库..."
  │   ├── 流式返回 token → 前端逐字显示
  │   ├── 对话节奏 (由 System Prompt 控制):
  │   │   第1轮: 了解最看重的维度
  │   │   第2-3轮: 根据偏好搜索/分析候选学校
  │   │   第4-5轮: 确认冲刺/稳妥/保底意向
  │   └── 每轮附带 2-3 个快捷选项
  │   ├── 写回 Redis 更新对话历史
  │   └── 返回: message + options
  │
  ▼
Step 5: 用户点击「生成推荐报告」(≥4轮后出现)
  ├── POST /generate-report
  ├── 创建 recommendation_log (status=PENDING)
  ├── 投递 RabbitMQ (优先异步) 或 同步降级
  │   └── 异步: AiReportConsumer 消费 → 调用 AI 生成报告
  │   └── 同步: 直接调用 AiReportBuilder.buildConversationReport()
  ├── 前端跳转 /ai-report/{reportId}
  └── AiReport.vue 3 秒轮询直到报告完成
  │
  ▼
Step 6: 报告展示 (AiReport.vue)
  ├── PENDING 状态: 加载动画 (打字机效果、分析步骤进度)
  ├── COMPLETED 状态:
  │   ├── 概览卡片 (冲刺/稳妥/保底数量)
  │   ├── 每档学校卡片
  │   │   ├── 学校名、专业、层次、地区
  │   │   ├── 录取均分、差距、最低录取分、拟录取区间
  │   │   ├── 招生名额、数据完整度、风险等级
  │   │   ├── AI 推荐理由 (pros/cons/tradeoffs)
  │   │   └── 数据来源链接
  │   └── 操作: 查看详情 / 加入对比 / 收藏
  └── FAILED 状态: 错误提示 + 重试
```

### 6.2 流程二：一键快速推荐

```
用户进入 /ai-recommend
  │
  ▼
Step 1: 用户点击「快速推荐」
  │
  ▼
Step 2: POST /analyze
  ├── 加载用户画像
  ├── 构建宽候选池 (AiCandidatePoolService.buildAgentPool)
  │   · 查询范围更大 (≤500 个)
  │   · 不限地区 (或按用户画像地区)
  │   · 包含更宽分数范围
  ├── 候选池存入 Redis (TTL 1h)
  ├── 创建 recommendation_log (status=PENDING)
  ├── 投递 RabbitMQ 消息 (mode=analyze)
  └── 返回 reportId
  │
  ▼
Step 3: AiReportConsumer.handleAnalyzeMessage()
  ├── 从 Redis 读取候选池
  ├── 构建 ChatModel (DeepSeek)
  ├── 调用 AiReportBuilder.buildAnalyzeReport()
  │   ├── 拼接分析 Prompt (候选池 + 用户画像偏好)
  │   ├── 调用 chatModel.chat(prompt) → 获取 JSON 报告
  │   ├── parseReportJson → 解析 AI 返回
  │   ├── hydrateReportPrograms → 数据库补全事实字段
  │   │   ├── 验证 programId 有效性
  │   │   ├── 去重检查
  │   │   ├── 安全降级 (canBeSafe=false 的保底校 → 降级为稳妥)
  │   │   └── 注入 opinion 字段 (judgement/risk/decision/reason/pros/cons)
  │   └── 存入 Redis: ai:report:{reportId} (TTL 7天)
  └── 更新 recommendation_log.resultJson
  │
  ▼
Step 4: 前端轮询 /report/{id} → 展示报告
```

### 6.3 候选池分层策略

```
用户预估分: 300 分

┌─────────────────────────────────────────┐
│            候选池分层 (≤50 个)            │
├──────────┬──────────┬────────┬─────────┤
│  保底层   │  稳妥层   │  可冲层  │  高难层  │
│ gap≥15   │ gap 5-14 │ gap-10~4│ gap<-10 │
│ 均分≤285 │ 均分286-295│均分296-310│均分>310│
│          │          │          │         │
│ ~15个    │ ~15个     │ ~15个    │ ~5个    │
└──────────┴──────────┴──────────┴─────────┘
         各层按加权总分排序后采样
```

---

## 7. 系统提示词设计

### 7.1 对话系统提示词 (完整)

位于 `AiRecommendationServiceImpl.java:48-97`，运行时通过 `String.format()` 动态注入：

```
你是独立的 AI 择校顾问。当前对话主要依据用户画像和系统自动候选池，
不依赖筛选页或对比页的临时条件。回复简洁（2-4句），不自我介绍，
不讲客套话。每轮聚焦一个问题。

## 用户画像
- 预估总分: %d          ← 从 user_profile.estimated_score 注入
- 本科层次: %s           ← 从 user_profile.undergrad_tier 注入
- 跨考: %s               ← 从 user_profile.is_cross_major 注入
- 目标地区: %s           ← 从 user_profile.target_regions 注入

## 多维择校规则（重要）
候选学校中的「差距」= 用户预估分 - 学校录取均分。差距只是分数安全维度，
不能单独决定冲刺/稳妥/保底。
必须综合判断：分数差距、统考/计划招生名额、拟录取区间、数据完整度、
学校层次、地区偏好、专业方向匹配。
如果 canBeSafe=false，禁止称为保底；即使差距很大，也只能说
"分数有余量但存在明显风险/只能作稳妥或线索"。
招生名额极少是强风险信号：≤3 人不能作为保底；
4-9 人若数据不完整、没有拟录取区间或分数优势不足，也不能作为保底。
...

## 候选学校摘要（每行含分数、招生、数据和保底边界）
%d. ID:xxx | 学校名 | 专业:xxx | 学院:xxx | 地区:xxx | 层次:xxx | 
均分:xxx | 差距:xxx | 招生:xxx | 完整度:A | canBeSafe:true | quotaRisk:low
...                     ← 运行时动态生成候选池摘要文本

## 可用工具（必须使用）
- getProgramDetail(programId): 获取指定学校的完整录取数据
- searchPrograms(filters): 在候选池内按条件筛选，filters 为 JSON
- comparePrograms(ids): 横向对比多所学校的详细录取数据
- queryDatabase(filters): 直接查询数据库中所有院校数据，不受候选池限制

## 展示规则
回复中绝对不要出现学校的 programId 或任何数字 ID，
用户只需要看到学校名称。

## 工具使用规则
1. 讨论具体学校时，必须先调用 getProgramDetail 获取真实数据再回复
2. 用户要求筛选/过滤/列清单时，必须调用 searchPrograms
3. 对比学校时，必须调用 comparePrograms
4. 回复中引用数据时，确保数据来自工具返回结果，不要编造数字
5. 每次推荐学校时，必须说明该校的录取均分、差距、招生名额和关键风险
6. 工具返回 canBeSafe=false 时，不得把该校描述为保底或绝对稳妥

## 对话节奏
第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）
第2-3轮: 根据偏好搜索/分析候选学校
第4-5轮: 确认冲刺/稳妥/保底意向

## 输出格式
每轮回复含简短文字(2-4句)。
回复末尾附 2-3 个快捷选项，用 "---OPTIONS---" 分隔，每行一个选项。

## 快捷选项规则（重要）
快捷选项必须是用户偏好/决策类。
禁止将工具调用作为快捷选项。
用户说"出报告"时，只回复"好的，正在为你生成报告..."，不要附带选项。
```

### 7.2 报告生成提示词

位于 `AiReportBuilderImpl.java:49-69`，与对话提示词结构不同：

```
这不是对话。请直接输出推荐报告 JSON，不要回复确认语。

## preferenceProfile
{...}  ← 用户偏好 JSON

## 候选学校事实摘要
1. ID:xxx | 学校名 | 专业 | 地区 | 层次 | 均分 | 差距 | 招生 | 
   完整度 | canBeSafe | quotaRisk
...

## 要求
1. 只能从候选列表中选学校，programId 必须与候选列表一致
2. 按冲刺/稳妥/保底三档推荐，每档 1-3 所
3. AI 只输出观点字段，事实字段由后端数据库补全
4. 不要输出 schoolName、collegeName、programName、分数、招生人数等事实字段
5. 推荐理由必须基于候选事实摘要和 preferenceProfile 的取舍
6. canBeSafe=false 是事实硬约束，禁止放入保底档

## 输出格式（严格 JSON）
{"summary":"一句话总结","tiers":[
  {"level":"reach","label":"冲刺档","schools":[
    {"programId":1,"judgement":"small_reach","risk":"high",
     "decision":"适合作为冲刺候选","reason":"推荐理由",
     "pros":["优势"],"cons":["风险"],"tradeoffs":["取舍"],
     "recommendedAction":"行动建议"}]},
  {"level":"steady","label":"稳妥档","schools":[]},
  {"level":"safe","label":"保底档","schools":[]}
]}
```

### 7.3 提示词设计要点

1. **角色定义**: "独立的 AI 择校顾问"，不是助手/客服，强调独立性
2. **数据锚定**: 通过候选池摘要文本，将 AI 限定在系统已有数据范围内
3. **工具强制**: 明确禁止编造数据，必须先调工具再回复
4. **安全约束**: canBeSafe=false 是硬约束，AI 不能绕过的护栏
5. **对话节奏**: 预设 5 轮对话节奏，防止 AI 一次性输出过多信息
6. **输出控制**: 2-4 句简洁回复 + 快捷选项，保持对话聚焦
7. **前后端职责分离**: 报告生成时 AI 只输出观点（judgement/risk/reason），事实字段由后端 hydrate

---

## 8. 数据库设计

### 8.1 核心表 ER 关系

```
school ──→ college ──→ program ──→ program_subject ←── subject
                          │
                          ├──→ admission_score (1:N, 按年份)
                          ├──→ admission_plan  (1:N, 按年份)
                          ├──→ admission_result(1:N, 按年份)
                          └──→ data_source     (1:N)

app_user ──→ user_profile (1:1)
              │
              └──→ user_favorite_program (1:N → program)

recommendation_log (user_id → app_user)
recommendation_rule (全局规则表)
```

### 8.2 关键字段说明

**program 表**:
- `is_408`: 是否考察 408 统考（核心筛选条件）
- `exam_type`: 初试科目组合方式
- `study_mode`: full_time / part_time
- `degree_type`: academic(学硕) / professional(专硕)
- `protects_first_choice`: 是否保护第一志愿

**admission_score** (复试线):
- `score_line`: 复试分数线
- `single_math/english/politics/professional`: 各科单科线
- `verify_status`: 数据核验状态

**admission_plan** (招生计划):
- `total_plan`: 计划招生总数
- `recommended_exemption_plan`: 推免人数
- `unified_exam_quota`: 统考名额 = total_plan - exemption
- `retest_count`: 复试人数

**admission_result** (录取结果):
- `admitted_count`: 实际录取人数
- `first_choice_admitted_count`: 一志愿录取人数
- `min/max/avg_admitted_score`: 录取分数统计

**user_profile** (用户画像):
- `estimated_score`: 预估初试总分
- `target_regions`: 目标地区 (JSON 数组)
- `risk_preference`: 风险偏好 (conservative/balanced/aggressive)
- `school_tier_preference`: 学校层次偏好
- `region_strategy`: 地区策略

---

## 9. 前端架构

### 9.1 路由设计

**user-ui (C 端)**:

| 路由 | 组件 | 说明 |
|------|------|------|
| `/` | Home.vue | 首页，重定向到 /recommend |
| `/login` | Login.vue | 登录 |
| `/register` | Register.vue | 注册 |
| `/recommend` | Recommend.vue | 规则筛选推荐 |
| `/results` | Results.vue | 推荐结果 |
| `/ai-recommend` | AiRecommend.vue | AI 推荐入口 |
| `/ai-report/:id` | AiReport.vue | AI 推荐报告 |
| `/ai-history` | AiHistory.vue | AI 报告历史 |
| `/favorites` | Favorites.vue | 收藏 |
| `/history` | History.vue | 推荐历史 |
| `/history/:id` | HistoryDetail.vue | 历史详情 |
| `/profile` | Profile.vue | 用户画像 |

**ruoyi-ui (管理端)**:
- 动态路由：后端 `/getRouters` 返回菜单树，前端 `filterAsyncRouter` 转换为 Vue Router 路由
- 权限指令 `v-hasPermi` 控制按钮可见性

### 9.2 状态管理

**user-ui (Pinia)**:
- `useUserStore`: token, userId, role, profile
- AI 对话状态通过组件内部状态管理（非全局）
- 对话历史持久化到 `localStorage`（keyed by conversationId）

**ruoyi-ui (Vuex)**:
- `permission`: 动态路由 + 权限列表
- `user`: 用户信息 + 角色 + 权限
- `settings`: 主题、侧边栏、tagsView 等

### 9.3 AI 对话前端组件交互

```
AiRecommend.vue
  │
  ├── 画像展示区（来自 userStore.profile）
  ├── 「快速推荐」→ postAiAnalyze() → 跳转 /ai-report/:id
  └── 「AI 对话推荐」→ 打开 AiChatPanel（滑出面板）
        │
        ├── 创建对话: postAiStart()
        │     └── 存储: conversationId, options
        │
        ├── 对话循环:
        │   ├── 用户输入 → postAiChatStream(data, handlers)
        │   │   ├── onThinking: 更新 thinking 状态文字
        │   │   ├── onToken: 流式追加 token 到消息
        │   │   ├── onDone: 完成，更新 options
        │   │   └── onError: 降级重试 postAiChat()
        │   │
        │   └── 点击快捷选项 → 同上
        │
        ├── 消息渲染:
        │   ├── 技术文本清理 (cleanTechnicalText)
        │   │   └── canBeSafe/quotaRisk/programId/sourceUrl → 中文
        │   ├── Markdown 去除 (stripMarkdown)
        │   ├── 智能分段 (messageParagraphs)
        │   ├── 洞察标签提取 (extractInsightChips)
        │   │   └── 提取均分/差距/招生/风险/层次
        │   └── 快捷选项解析 (parseOptions)
        │
        └── 生成报告:
            ├── postAiGenerateReport({conversationId})
            └── 跳转 /ai-report/:id
```

---

## 10. 安全与护栏

### 10.1 AI 推荐安全护栏 (`AiRecommendationSafety`)

```java
// 保底资格判断
safeEligibility(row, estimatedScore)

规则 1: 统考名额 ≤3 → canBeSafe=false, 理由:"录取波动极大"
规则 2: 统考名额 4-9 且 (gap<35 或 数据C级 或 无拟录取区间) 
        → canBeSafe=false
规则 3: 名额未知 + C级 + 无拟录取区间 → canBeSafe=false
```

**名额风险等级** (`quotaRisk`):
| 名额 | 风险 |
|------|------|
| ≤3 | very_high |
| 4-9 | high |
| 10-19 | medium |
| ≥20 | normal |
| null | unknown |

### 10.2 报告安全降级

`AiReportBuilderImpl.hydrateReportPrograms()` 中的安全处理：

1. **无效 ID 过滤**: programId 不在候选池中 → 丢弃
2. **重复检测**: 同一 programId 出现在多档 → 丢弃重复
3. **数据缺失**: 数据库查不到详情 → 丢弃
4. **保底降级**: AI 放入保底档但 canBeSafe=false → 自动降级到稳妥档，标注"不宜作为保底，降级为稳妥待核验"

### 10.3 API 安全

- 所有 API 需要 JWT Bearer Token 认证
- 对话所有权验证: `ai:owner:{conversationId}` 与当前 userId 比对
- Redis TTL 自动过期防止数据泄漏
- Controller 层无业务逻辑，仅做参数验证和响应封装

### 10.4 降级策略

| 场景 | 降级方案 |
|------|---------|
| RabbitMQ 不可用 | 同步生成报告 |
| SSE 流式失败 | 前端自动降级为非流式 `postAiChat()` |
| 非流式失败 | 重试一次，仍失败返回 fallback 提示 |
| AI 返回非 JSON | 使用 `ruleBasedFallback()` 兜底生成（取候选池前 6 所学校按顺序分档） |
| 对话过期 (TTL) | 提示用户开始新对话 |

---

## 附录：关键文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| AI 服务实现 | `ruoyi-postgrad/.../impl/AiRecommendationServiceImpl.java` | 核心：System Prompt、流式对话、报告生成 |
| AI 工具集 | `ruoyi-postgrad/.../tool/AiRecommendationTools.java` | @Tool 方法、工具预算、缓存 |
| 报告生成器 | `ruoyi-postgrad/.../impl/AiReportBuilderImpl.java` | 对话报告/分析报告、安全降级 |
| 决策引擎 | `ruoyi-postgrad/.../tool/RecommendationDecisionEngine.java` | 五维评分、分档排序 |
| 候选池服务 | `ruoyi-postgrad/.../impl/AiCandidatePoolServiceImpl.java` | 分层采样、候选池构建 |
| 安全护栏 | `ruoyi-postgrad/.../domain/AiRecommendationSafety.java` | 保底资格判断 |
| 报告标准化 | `ruoyi-postgrad/.../domain/AiReportSupport.java` | 判断标准化、排序 |
| AI Controller | `ruoyi-admin/.../AppAiRecommendationController.java` | REST API + SSE |
| MQ 消费者 | `ruoyi-admin/.../AiReportConsumer.java` | RabbitMQ 异步报告 |
| AI 对话组件 | `user-ui/src/components/AiChatPanel.vue` | 前端对话面板 (SSE 流式) |
| AI 入口页 | `user-ui/src/views/AiRecommend.vue` | 推荐入口（快速/对话） |
| AI 报告页 | `user-ui/src/views/AiReport.vue` | 报告展示 |
| AI API | `user-ui/src/api/ai.js` | 前端 API 封装 (SSE fetch) |
