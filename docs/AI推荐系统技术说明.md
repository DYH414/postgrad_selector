# AI 对话推荐引擎 — 技术说明

> 写完给团队 review 用的，便于讨论改进方向。

## 1. 目的

在现有规则推荐引擎之外，提供 AI 驱动的**对话式择校推荐**。用户筛选出候选学校池后，启动 AI 对话，经过多轮偏好挖掘，最终生成结构化三档推荐报告（冲刺/稳妥/保底）。

## 2. 技术架构

```
┌──────────────┐     ┌─────────────────┐     ┌───────────┐
│  Vue 前端     │────▶│  Spring Boot     │────▶│  Qwen AI   │
│  AiChatPanel │     │  Controller      │     │  qwen-plus │
│  ai-report   │     │  ↓               │     └───────────┘
└──────────────┘     │  Service         │
                      │  ↓        ↓      │
                      │  Redis   RabbitMQ │
                      │  ↓        ↓      │
                      │  MySQL   Consumer │
                      └─────────────────┘
```

| 层 | 技术 |
|---|---|
| AI 模型 | 千问 qwen-plus（langchain4j-community-dashscope） |
| 会话存储 | Redis（30 分钟滑动 TTL，每次交互续期） |
| 异步报告 | RabbitMQ（队列 `ai.report.queue`，1 消费者串行） |
| 持久化 | MySQL `recommendation_log` 表 |
| 前端 | Vue 2.6 + Element UI |

## 3. 完整流程

### 3.1 启动对话 `POST /app/ai-recommend/start`

```
前端传入: { candidateIds: [1,2,3,...], estimatedScore: 300 }

1. 查 user_profile 获取用户画像（本科层次、是否跨考、风险偏好、目标地区、数英水平）
2. 查 RecommendationMapper.selectProgramsByIds → 候选学校池完整数据
3. 拼接 System Prompt（用户画像 + 候选学校摘要）
4. 调千问 qwen-plus 生成开场白
5. 写 Redis:
   - ai:conv:{uuid}  → 对话消息列表 (TTL 1800s)
   - ai:pool:{uuid}  → 候选池完整 JSON (TTL 1800s)
   - ai:owner:{uuid} → userId (TTL 1800s)
6. 返回 { conversationId, message, options: ["985/211光环", "专业排名", ...] }
```

### 3.2 对话交互 `POST /app/ai-recommend/chat`

```
前端传入: { conversationId, message }

1. 鉴权: 校验 ai:owner:{id} == 当前登录 userId
2. 从 Redis 取对话历史，追加 <user_input>新消息</user_input>
3. 调用千问，附带 3 个 Tool（见 3.5）
4. 解析 AI 回复中的 ---OPTIONS--- 分隔符 → options 列表
5. 写回 Redis，EXPIRE 刷新为 1800s
6. 每 6 条消息异步存一份 DB 快照
7. 返回 { message, options }

降级: AI 调用失败 → 重试 1 次 → 仍失败返回 { fallback: true }
```

### 3.3 生成报告 `POST /app/ai-recommend/generate-report`

```
前端传入: { conversationId }

===== 异步路径（RabbitMQ 可用时）=====
1. 鉴权 + 从 System Prompt 解析 estimatedScore
2. 写 recommendation_log (status=PENDING)
3. 投递 RabbitMQ 消息: { reportId, conversationId, userId, estimatedScore }
4. Redis ai:report:{id} = "PENDING"
5. 立即返回 { reportId, status: "PENDING" }

===== Consumer 异步处理 =====
1. 从 Redis 取完整对话历史
2. 拼报告生成 Prompt → 调千问
3. 解析 AI 返回的 JSON（tiers/schools）
4. 后端计算 matchScore + 注入分数/人数/年份/来源等统计字段
5. 写 Redis ai:report:{id} (TTL 7天)
6. 更新 DB recommendation_log.result_json

===== 同步降级（RabbitMQ 不可用时）=====
直接在 HTTP 线程完成上述 AI 调用 → 写 Redis + DB → 返回 DONE
```

### 3.4 查看报告 `GET /app/ai-recommend/report/{id}`

```
1. 查 Redis ai:report:{id}
   - = "PENDING" → 返回 { status: "PENDING" }
   - = JSON 字符串 → 返回 { status: "COMPLETED", ...报告内容 }
2. Redis 没有 → 查 DB fallback
```

前端 PENDING 时每 3 秒轮询，直到 COMPLETED 后渲染报告卡片。

### 3.5 AI 可调用的 Tool（3 个）

所有 Tool 读取 Redis `ai:pool:{conversationId}`，不直接查 DB。conversationId 通过 **ThreadLocal** 静默注入，AI 看不到。

| Tool | 参数 | 功能 |
|---|---|---|
| `getProgramDetail` | programId | 返回单个学校的完整录取数据 |
| `searchPrograms` | {city, tier, minScore, maxScore} | 在候选池内按条件筛选 |
| `comparePrograms` | [programId, ...] | 横向对比多所学校数据 |

### 3.6 System Prompt 结构

```
你是考研408计算机专业择校顾问。

角色: 数据驱动、诚实、简洁。每轮只聚焦一个维度。

用户画像:
- 预估总分: {estimatedScore}
- 本科层次: {undergradTier}
- 跨考: {isCrossMajor}
- 风险偏好: {riskPreference}
- 目标地区: {targetRegions}
- 数学水平: {mathLevel}，英语水平: {englishLevel}

候选学校摘要: [{programId, schoolName, programName, tier, city, avgScore, gap}, ...]

对话节奏:
第1轮: 了解最看重的维度
第2-3轮: 用具体数据讨论 2-3 所目标校
第4-5轮: 确认冲刺/稳妥/保底意向

输出格式: 2-4 句文字 + ---OPTIONS--- + 每行一个快捷选项
```

### 3.7 报告 JSON Schema

```json
{
  "summary": "一句话总结",
  "tiers": [
    {
      "level": "reach",
      "label": "冲刺档",
      "schools": [
        {
          "programId": 1,
          "schoolName": "厦门大学",
          "programName": "计算机科学与技术",
          "reason": "推荐理由（AI 生成）",
          "risk": "high",
          "pros": ["优势1", "优势2"],
          "cons": ["劣势1"],
          "matchScore": 95,
          "scoreLine": 310,
          "avgAdmittedScore": 342,
          "admissionLow": 325,
          "admissionHigh": 368,
          "planCount": 45,
          "admittedCount": 38,
          "dataYear": 2024,
          "dataCompleteness": "A",
          "sourceUrl": "https://nnuo.com/...",
          "sourceOwner": "N诺"
        }
      ]
    }
  ]
}
```

AI 生成部分: `schoolName`, `programName`, `reason`, `risk`, `pros`, `cons`
后端计算注入: `matchScore`, `scoreLine`, `avgAdmittedScore`, `admittedCount`, `planCount`, `dataYear`, `dataCompleteness`, `sourceUrl`, `sourceOwner`

**matchScore 公式:** `max(0, 100 - |预估分 - 录取均分| × weight)`，冲刺档 weight=0.5，其他 weight=0.3

## 4. Redis Key 一览

| Key | 内容 | TTL |
|---|---|---|
| `ai:conv:{id}` | 对话消息列表 JSON | 1800s 滑动（每次对话续期） |
| `ai:pool:{id}` | 候选池完整数据 JSON | 1800s 滑动 |
| `ai:owner:{id}` | userId 字符串 | 1800s 滑动 |
| `ai:report:{id}` | 报告 JSON 或 "PENDING" | 7 天固定 |

## 5. 前端页面

| 页面 | 路由 | 功能 |
|---|---|---|
| AiChatPanel（侧边栏组件） | recommend.vue 内嵌 | 对话界面：气泡消息 + 快捷选项 + 文本输入 + 生成报告按钮 |
| ai-report.vue | `/app/ai-report/:id` | 报告详情：三档卡片、分数统计网格、数据来源链接 |
| ai-history.vue | `/app/ai-history` | 历史报告列表：摘要 + 分档统计 + 日期 |
| AppHeader | 全局导航栏 | "AI 记录" 入口 |

## 6. 成本

单次完整对话（6 轮 + 报告生成），qwen-plus 约 **¥0.02**。月 1000 次 ≈ ¥20。

## 7. 当前已知问题 & 待讨论改进

### 7.1 对话体验

- **Tool 未实际生效**: System Prompt 里声明了 3 个 Tool，但当前 Service 实现用的是 `chatModel.chat(prompt)`（纯文本），没有通过 `AiServices` 注册 Tool，AI 无法真正调用 `getProgramDetail`/`searchPrograms`/`comparePrograms`
- **对话节奏僵硬**: 硬编码 5 轮节奏，AI 不一定会按照这个节奏走，用户也可能提前要求出报告
- **System Prompt 过长**: 候选池有 30 所学校时，摘要层就有 ~1500 token，加上画像 + 指令，初始 prompt 偏大
- **用户输入未做防护**: `<user_input>` 标签包裹是基础防护，但无内容审核/注入检测

### 7.2 报告质量

- **AI 挑学校无约束**: 报告 Prompt 让 AI 从对话历史里选学校，但对话历史可能不包含候选池全貌；AI 可能漏掉更适合的学校
- **matchScore 只管分数**: 当前只基于预估分差距计算，没有考虑学校层次、地域偏好、跨考友好度等因素
- **报告 JSON 偶有格式错误**: AI 偶尔返回非标准 JSON（多余文本、缺字段），当前无 schema 校验，前端 `JSON.parse` 可能失败
- **无报告对比/差异说明**: 无法解释"为什么推荐 A 不推荐 B"

### 7.3 技术层面

- **对话全在 Redis**: 30 分钟过期后对话消失，虽然有每 6 轮的 DB 快照，但恢复体验简陋（"对话已从历史恢复"）
- **RabbitMQ 单消费者**: concurrency=1，高峰期可能积压
- **无 WebSocket 推送**: 报告完成后前端靠轮询（3s），不是实时通知
- **对话与报告无关联**: 报告存了 `result_json` 但没法回溯"这个推荐是基于哪段对话生成的"
- **错误处理粗糙**: AI 调用失败后只返回泛化的 fallback 消息，未区分超时/限流/账号欠费等不同情况

### 7.4 数据层面

- **统计字段只注入不展示利用**: 报告卡片展示了复试线、均分等，但没有在推荐理由中关联这些数据（比如"因均分 342 > 你的 300，归类为冲刺"）
- **数据来源单一**: 只展示了 N诺 URL，未区分官方/第三方/推算
- **无用户反馈闭环**: 用户看不到报告是否有用、是否会再次查看，无法衡量推荐效果

## 8. 数据库表

`recommendation_log` 关键字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 用户 ID |
| `profile_snapshot` | 启动时的用户画像快照 |
| `result_json` | PENDING 状态 / AI 报告 JSON / 对话快照 JSON |
| `rule_version` | `ai-conversation`(报告) / `ai-conversation-state`(对话快照) |
| `data_version` | AI 模型版本标识 |
| `is_paid` | 是否付费 |
| `created_at` | 创建时间 |
