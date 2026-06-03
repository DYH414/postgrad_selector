# AI 对话推荐引擎 — 技术报告

2026-06-03 | 版本 3.0

---

## 1. 概述

### 1.1 目的

在规则推荐引擎之外，提供 AI 驱动的**对话式择校推荐**。用户启动 AI 对话后，经过多轮偏好挖掘，生成包含**冲刺档 / 稳妥档 / 保底档**的结构化推荐报告。

### 1.2 技术栈

| 层 | 技术 | 说明 |
|---|---|---|
| AI 模型 | DeepSeek V4 Pro | 通过 OpenAI 兼容接口 `api.deepseek.com/v1` 调用 |
| AI 框架 | langchain4j 1.15.0 | AiServices + @Tool 注解 + ChatMemory |
| 会话存储 | Redis | 30 分钟滑动 TTL，每次对话续期 |
| 异步队列 | RabbitMQ | 报告生成走队列异步处理 |
| 持久化 | MySQL `recommendation_log` | 对话快照 + 最终报告 |
| 后端 | Spring Boot 4.0.3 + MyBatis | 若依 v3.9.2 框架 |
| 前端 | Vue 2.6 + Element UI | 侧边栏聊天 + 报告卡片 + 历史列表 |

---

## 2. 系统架构

```
用户画像 (UserProfile)
     │
     ▼
候选池构建 (IAiCandidatePoolService)
     │
     ├──→ Redis: ai:pool:{conversationId}  (完整候选学校数据, TTL 30min)
     │
     ▼
AI 对话引擎 (AiRecommendationServiceImpl)
     │  ┌─ SYSTEM_PROMPT (含画像+候选池摘要+工具定义)
     │  ├─ LangChain4j AiServices (chat/stream)
     │  ├─ AiRecommendationTools (getProgramDetail, searchPrograms, comparePrograms, queryDatabase)
     │  └─ Redis: ai:conv:{conversationId} (对话历史, TTL 30min)
     │
     ▼
报告生成 (用户说"出报告")
     │
     ├──→ 同步降级路径 (RabbitMQ 不可用时)
     │       └─ AiRecommendationServiceImpl.generateReport()
     │           ├─ 构建 reportPrompt
     │           ├─ AI 生成 JSON
     │           ├─ injectMatchScores() 注入录取详情
     │           └─ validateAndNormalizeReport() 标准化
     │
     └──→ 异步 MQ 路径 (生产环境)
             └─ RabbitMQ "ai.report.queue"
                 └─ AiReportConsumer.onMessage()
                     ├─ handleConversationMessage()  对话报告
                     │   ├─ 取 ai:conv:{id} + ai:pool:{id}
                     │   ├─ buildReportPrompt() → AI 生成 JSON
                     │   ├─ injectFullData() 注入录取详情
                     │   └─ normalizeReport() 标准化
                     │
                     └─ handleAnalyzeMessage()  快速推荐报告
                         ├─ 取 ai:agent:pool:{reportId}
                         ├─ buildAnalysisPrompt() → AI 生成 JSON
                         ├─ injectFullData() 注入录取详情
                         └─ normalizeReport() 标准化
```

---

## 3. 数据流

### 3.1 启动对话 `POST /app/ai-recommend/start`

```
前端传入: { candidateIds: [1,2,3,...] }

1. loadUserProfile(userId) → 查 user_profile 表
   - estimatedScore: 从请求参数或画像取, 默认 300
   - undergradTier: 本科层次, 默认"双非"
   - isCrossMajor: 是否跨考, 默认"否"
   - targetRegions: 目标地区 JSON 数组, 默认"不限"

2. aiCandidatePoolService.buildPool(request, profile, estimatedScore)
   → 构建最多 50 个具备录取数据的 408 项目作为 AI 初始候选池

3. buildSummaryList(pool, estimatedScore) 
   → 保存完整字段到 Redis（programId, schoolName, schoolTier, city, province,
      programName, collegeName, degreeType, avgAdmittedScore, gap,
      scoreLine, admissionLow, admissionHigh, planCount, admittedCount,
      retestCount, dataYear, dataCompleteness, sourceUrl, sourceOwner）

4. buildSummaryText(summaryList) 
   → 拼成 System Prompt 中的候选学校摘要（含均分和差距）

5. 构建 AiServices:
   - ChatModel: OpenAiChatModel (DeepSeek OpenAI 兼容模式)
   - Tools: aiRecommendationTools (getProgramDetail/searchPrograms/comparePrograms/queryDatabase)
   - ChatMemory: MessageWindowChatMemory (max 20 条)
   - systemMessageProvider: 动态 System Prompt

6. 调用 assistant.chat("开始择校对话...") → AI 生成开场白

7. 写入 Redis:
   ai:conv:{uuid}  → 对话消息列表 JSON (TTL 1800s 滑动)
   ai:pool:{uuid}  → 候选池完整 JSON (TTL 1800s 滑动)
   ai:owner:{uuid} → userId (TTL 1800s 滑动)

8. 返回 { conversationId, message, options, profileBasis, candidateCount }
```

### 3.2 流式对话 `POST /app/ai-recommend/chat/stream`

```
前端传入: { conversationId, message }

1. 鉴权: ai:owner:{id} == 当前登录 userId

2. 从 Redis 读取 ai:conv:{id} → 重建 ChatMemory

3. 构建 AiServices (StreamRecommendationAssistant):
   - streamingChatModel: OpenAiStreamingChatModel (DeepSeek)
   - onPartialResponse → callback.onToken(token)
   - onCompleteResponse → persistStreamConversation → callback.onComplete(result)
   - onError → callback.onError(error)

4. 前端通过 SSE 接收 token 流, 实时渲染到气泡中
```

### 3.3 生成报告 `POST /app/ai-recommend/generate-report`

```
===== 异步路径 (RabbitMQ 可用时) =====

1. 鉴权 + 从 System Prompt 解析 estimatedScore

2. 写 recommendation_log: result_json = {"status":"PENDING"}

3. 投递 RabbitMQ: { reportId, conversationId, userId, estimatedScore }

4. Redis ai:report:{id} = "PENDING"
5. 返回 { reportId, status: "PENDING" }

   ┌─ AiReportConsumer.handleConversationMessage ──────┐
   │ 1. 读 Redis ai:conv:{id} + ai:pool:{id}           │
   │ 2. stripTailExchange() 裁剪最后两轮对话            │
   │ 3. buildReportPrompt() → 拼候选列表+对话历史+格式   │
   │ 4. AI 生成报告 JSON                                │
   │ 5. parseReportJson() 解析 + 重试机制               │
   │ 6. injectFullData() 注入录取详情                   │
   │ 7. normalizeReport() 标准化                        │
   │ 8. 写 Redis ai:report:{id} (TTL 7天)               │
   │ 9. updateReportResult → 更新 DB                    │
   └────────────────────────────────────────────────────┘

===== 同步降级 (RabbitMQ 不可用时) =====

直接在 HTTP 线程完成 AI 调用 → injectMatchScores → validateAndNormalizeReport → 返回
```

### 3.4 查看报告 `GET /app/ai-recommend/report/{id}`

```
1. 查 Redis ai:report:{id}
   ├─ = "PENDING" → 返回 { status: "PENDING" }
   ├─ = JSON 字符串 → 返回 { status: "COMPLETED", ...报告内容 }
   └─ 不存在 → 查 DB fallback
```

前端 PENDING 时每 3 秒轮询。

---

## 4. System Prompt（完整版）

```
你是独立的 AI 择校顾问。当前对话主要依据用户画像和系统自动候选池，
不依赖筛选页或对比页的临时条件。回复简洁（2-4句），不自我介绍，不讲客套话。每轮聚焦一个问题。

## 用户画像
- 预估总分: {estimatedScore}
- 本科层次: {undergradTier}
- 跨考: {isCrossMajor}
- 目标地区: {targetRegions}

## 分数差距与上岸率规则（重要）
候选学校中的「差距」= 用户预估分 - 学校录取均分。正数越大上岸率越高。
| 差距 | 分类 | 上岸率 |
| ≥ +15 | 保底 | 高，推荐给看重上岸率的用户 |
| +5 ~ +14 | 稳妥 | 中高 |
| -10 ~ +4 | 可冲刺 | 中等，需努力 |
| < -10 | 难度高 | 低，风险大 |

当用户说「看重上岸率」时，必须优先推荐差距 ≥ +5 的学校（稳妥/保底档）。
讨论学校时必须明确说出其录取均分和差距，不要只说学校名字。

## 地区规则
- 目标地区为"不限"时：只在候选池内推荐，不主动提及候选池外的城市
- 目标地区有具体城市时：优先推荐该城市学校，其他城市只在用户主动询问时才讨论

## 候选学校摘要（每行含均分和差距，差距越大上岸率越高）
{candidateSummary}

## 可用工具（必须使用）
- getProgramDetail(programId): 获取指定学校的完整录取数据
  （复试线、小分、招生计划、录取均分等）
- searchPrograms(filters): 在候选池内按城市、学校层次、分数范围等条件筛选。
  filters 为 JSON: {"city":"上海","tier":"211","minScore":290,"maxScore":310}
- comparePrograms(ids): 横向对比多所学校的详细录取数据
- queryDatabase(filters): 直接查询数据库中所有院校数据，不受候选池限制。
  filters 为 JSON，支持 keyword/tier/province/minScore/maxScore/limit。
  例如查"浙江所有211"用 {"province":"浙江","tier":"211"}

## 展示规则
回复中绝对不要出现学校的 programId 或任何数字 ID，用户只需要看到学校名称。

## 工具使用规则
1. 讨论具体学校时，必须先调用 getProgramDetail 获取真实数据再回复
2. 用户要求筛选/过滤/列清单时，必须调用 searchPrograms
3. 对比学校时，必须调用 comparePrograms 获取详细对比数据
4. 回复中引用数据时，确保数据来自工具返回结果，不要编造数字
5. 每次推荐学校时，必须说明该校的录取均分和差距

## 对话节奏
第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）
第2-3轮: 按用户偏好筛选推荐（每次只分析1-2所）
第4-5轮: 确认冲刺/稳妥/保底意向

## 输出格式
每轮回复含简短文字(2-4句)。
回复末尾附 2-3 个快捷选项，用 "---OPTIONS---" 分隔，每行一个选项。

## 快捷选项规则（重要）
- 必须是用户偏好/决策类："看重上岸率""愿意冲刺""稳妥为主""优先211"
- 不要在用户明确选择城市维度前，生成具体的城市限定选项
- 选项应顺着分析结论往前推进，不要重复已讨论过的内容
- 禁止将工具调用作为快捷选项（"查看XX学校数据" 等）
- 用户说"出报告"时，只回复"好的，正在为你生成报告..."，不要附带选项
```

### 候选池摘要格式（System Prompt 中注入）

```
1. ID:39 | 中国矿业大学 | 计算机科学与技术 | 211 | 徐州 | 均分:291 | 差距:+9分（稳妥）
2. ID:46 | 南昌大学 | 智能科学与技术 | 211 | 南昌 | 均分:291 | 差距:+9分（稳妥）
...
```

---

## 5. AI 可调用工具

### 5.1 getProgramDetail

```
@Tool("获取指定学校的完整录取数据，包括近三年复试线、小分、招生计划、录取均分")
String getProgramDetail(long programId)
```

- 从 Redis `ai:pool:{conversationId}` 读取
- 返回单校完整 JSON（含所有录取字段）
- 预算扣除 800 token

### 5.2 searchPrograms

```
@Tool("在候选池内按城市、学校层次、分数范围等条件筛选")
String searchPrograms(String filters)
```

- filters JSON: `{"city":"上海","tier":"211","minScore":290,"maxScore":310}`
- 从候选池 Redis 读取 + 按条件过滤
- 预算扣除 1200 token
- 最多返回 10 条

### 5.3 comparePrograms

```
@Tool("横向对比多所学校的详细录取数据")
String comparePrograms(long[] ids)
```

- 批量获取多所学校数据
- 预算扣除 600 × N token

### 5.4 queryDatabase

```
@Tool("直接查询数据库中所有院校数据，不受候选池限制")
String queryDatabase(String filters)
```

- 直查 MySQL 的 program 表
- 支持 keyword/tier/province/minScore/maxScore/limit
- 预算扣除 1500 token

### 5.5 安全机制

| 威胁 | 对策 |
|---|---|
| AI 查候选池外学校 | getProgramDetail/searchPrograms 只读 `ai:pool:{id}` |
| conversationId 泄露 | ThreadLocal 注入，AI 不可见 |
| Prompt 注入篡改 | 用户输入 `<user_input>` 包裹 |

---

## 6. 报告 Prompt 模板

### 6.1 对话报告模式（conversation）

```
这不是对话。请直接输出推荐报告JSON，不要回复"好的""正在生成"或其他确认语。

## 完整候选学校列表（请从这里选学校）
{candidatePoolLines}

## 对话历史（用户偏好参考）
{convJson}

## 要求
1. 从上面的候选列表中选学校，不要推荐列表之外的学校
2. programId 必须与候选列表中的 ID 一致
3. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校

## 输出格式（严格 JSON）
{
  "summary": "一句话总结",
  "tiers": [
    {
      "level": "reach",
      "label": "冲刺档",
      "schools": [
        {
          "programId": 1,
          "schoolName": "学校名",
          "programName": "专业名",
          "reason": "推荐理由",
          "risk": "high",
          "pros": ["优势1"],
          "cons": ["劣势1"]
        }
      ]
    },
    {"level": "steady", "label": "稳妥档", "schools": [...]},
    {"level": "safe", "label": "保底档", "schools": [...]}
  ]
}
```

**对话历史裁剪**: `stripTailExchange()` 移除最后两轮（用户"出报告" + AI"好的..."）。

### 6.2 快速推荐模式（analyze）

```
你是考研择校顾问。请基于以下用户画像和候选学校数据，直接输出一份择校推荐报告。

## 用户画像
- 预估总分: {estimatedScore}
- 本科层次: {undergradTier}
- 跨考: {isCrossMajor}
- 目标地区: {targetRegions}

## 推荐要求（重要）
1. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校
2. 学校选择需综合考虑：录取均分、差距、招生人数、报录比、复试线
3. 差距 ≥ 5 分优先稳妥/保底档，差距 ≤ -6 分优先冲刺档
4. 差距 > -5 且 < 5 时可归入稳妥档
5. 不要推荐差距 < -10 分的学校（难度过高）
6. 推荐理由必须引用具体数据（均分、招生人数等）
7. programId 必须从候选列表中的 "ID:" 值精确复制
8. 输出学校时必须使用 judgement 枚举: safe/steady/steady_reach/small_reach/high_risk_reach/data_insufficient_pending
9. verificationStatus 必须是: official/third_party/local_data_only/verification_failed/pending
10. 不要输出 matchScore。推荐理由写入 evidence 和 risks

## 候选学校数据
候选池共 {N} 条，以下为前 {PROMPT_POOL_ROW_LIMIT=120} 条代表行：
格式: ID | 学校 | 专业 | 层次 | 城市 | 均分 | 差距 | 复试线 | 招生 | 录取 | 报录比 | 数据年份

## 输出格式（严格 JSON）
{
  "summary": "一句话总结",
  "tiers": [
    {
      "level": "reach",
      "label": "冲刺档",
      "schools": [
        {
          "programId": 1,
          "schoolName": "学校名",
          "programName": "专业名",
          "judgement": "steady",
          "verificationStatus": "local_data_only",
          "evidence": ["推荐依据，须引用均分、招生人数等数据"],
          "risks": ["需要核验的风险点"]
        }
      ]
    },
    ...
  ]
}
```

**候选池截断**: 超过 120 行时截断，仅发送前 120 条。

---

## 7. 报告 JSON Schema

```json
{
  "summary": "根据您的预估分数和偏好，为您推荐了冲刺、稳妥及保底三个档次的学校。",
  "tiers": [
    {
      "level": "reach",
      "label": "冲刺档",
      "schools": [
        {
          "programId": 39,
          "schoolName": "中国矿业大学",
          "programName": "计算机科学与技术",
          "reason": "211层次高校，专业实力较强",
          "risk": "high",
          "pros": ["211高校", "专业实力强"],
          "cons": ["录取均分较高，竞争激烈"],

          // === 以下由 injectFullData/injectMatchScores 后端注入 ===
          "scoreLine": 278,
          "avgAdmittedScore": 291.0,
          "admissionLow": 268,
          "admissionHigh": 310,
          "planCount": 30,
          "admittedCount": 25,
          "retestCount": 35,
          "dataYear": 2025,
          "dataCompleteness": "A",
          "sourceUrl": "https://noobdream.com/schoolinfo/...",
          "sourceOwner": "N诺考研",
          "gap": 9,
          "retestRatio": "1.2:1",
          "matchScore": 55
        }
      ]
    },
    {"level": "steady", "label": "稳妥档", "schools": [...]},
    {"level": "safe", "label": "保底档", "schools": [...]}
  ]
}
```

### 字段来源

| 字段 | 来源 | 说明 |
|---|---|---|
| `schoolName` `programName` `reason` `risk` `pros` `cons` | AI 生成 | 基于候选池 + 对话偏好 |
| `scoreLine` `avgAdmittedScore` `admissionLow` `admissionHigh` | 后端注入 | 从候选池复制 |
| `planCount` `admittedCount` `retestCount` | 后端注入 | 同上 |
| `dataYear` `dataCompleteness` | 后端注入 | 同上 |
| `sourceUrl` `sourceOwner` | 后端注入 | N诺数据来源 |
| `gap` | 后端计算 | `estimatedScore - avgAdmittedScore` |
| `retestRatio` | 后端计算 | `planCount / admittedCount` |
| `matchScore` | 后端计算（同步路径） | 移除（MQ 路径不保留） |

---

## 8. AI 容错机制

### 8.1 报告生成重试

```
AI 第一次调用
  ├─ 成功 + 有 tiers → 返回
  ├─ 成功 + 无 tiers → 重试（修正 prompt）
  │   ├─ 成功 → 返回
  │   └─ 失败 → ruleBasedFallback 降级
  └─ 异常/超时 → ruleBasedFallback 降级
```

### 8.2 ruleBasedFallback 降级规则

- 从候选池按 gap 自动分档
- gap ≥ 15 → 保底档
- gap ≥ 5 → 稳妥档
- gap ≥ -10 → 冲刺档
- gap < -10 → 跳过
- reason = "自动分配（AI 报告生成失败）"

### 8.3 工具调用异常

- 候选池 Redis 过期 → 返回空 JSON `{}`
- API 调用失败 → 重试 1 次 → 仍失败返回 `{ fallback: true }`

---

## 9. Redis Key 设计

| Key | 内容 | TTL | 写入时机 | 读取时机 |
|---|---|---|---|---|
| `ai:conv:{id}` | 对话消息 JSON 数组 | 1800s 滑动 | start / chat | chat / generateReport / resume |
| `ai:pool:{id}` | 候选池完整数据 JSON | 1800s 滑动 | start | Tool / injectFullData / buildPrompt |
| `ai:owner:{id}` | userId 字符串 | 1800s 滑动 | start | chat 鉴权 / generateReport 鉴权 |
| `ai:report:{id}` | "PENDING" 或完整报告 JSON | 7 天固定 | generateReport / Consumer | getReport 轮询 |
| `ai:agent:pool:{reportId}` | analyze 模式候选池 | 1 小时固定 | analyze | Consumer |
| `ai:analyze:pool:{reportId}` | analyze 模式候选池（兼容） | 1 小时固定 | analyze | Consumer |

---

## 10. 前端页面

| 页面/组件 | 路由 | 功能 |
|---|---|---|
| `AiChatPanel.vue` | recommend.vue 内嵌 | 侧边栏对话：气泡消息 + SSE 流式 + 快捷选项 + 生成报告 |
| `AiReport.vue` | `/app/ai-report/:id` | 报告详情：三档卡片 + 录取均分/分数差/最低录取 + 推荐依据 + 详情抽屉 |
| `Recommend.vue` | `/recommend` | 筛选页 + AI 对话面板 |
| `Results.vue` | `/results` | 筛选结果 + 对比 + 备选管理 |
| `Profile.vue` | `/app/profile` | 画像：预估分/目标地区/本科层次 + 备选列表 + AI 报告历史 |
| `AppHeader.vue` | 全局 | 顶部导航 |

---

## 11. 工具调用预算

| 工具 | 单次预算 | 说明 |
|------|:---:|------|
| `getProgramDetail` | 800 | 查询单所学校 |
| `searchPrograms` | 1200 | 条件筛选 |
| `comparePrograms` | 600 × N | 对比 N 所学校 |
| `queryDatabase` | 1500 | 直查数据库 |
| **对话总预算** | 8000 | 超出后禁止工具调用 |
| **报告总预算** | 40000 | 超出后标记 explorationLimited |

---

## 12. 接口清单

| Method | Path | 说明 |
|--------|------|------|
| `POST` | `/app/ai-recommend/start` | 启动 AI 对话 |
| `POST` | `/app/ai-recommend/chat` | 发送消息（非流式） |
| `POST` | `/app/ai-recommend/chat/stream` | 发送消息（SSE 流式） |
| `POST` | `/app/ai-recommend/generate-report` | 生成报告（MQ 异步） |
| `GET` | `/app/ai-recommend/report/{id}` | 获取报告结果 |
| `GET` | `/app/ai-recommend/reports` | 获取历史报告列表 |
| `POST` | `/app/ai-recommend/resume` | 恢复过期对话 |
| `POST` | `/app/ai-recommend/analyze` | 快速推荐（不对话） |
