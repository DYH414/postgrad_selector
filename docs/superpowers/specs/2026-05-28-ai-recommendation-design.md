# AI 对话推荐引擎 — 设计规格

2026-05-28 | status: draft

## 概述

在现有规则推荐引擎基础上，新增 AI 驱动的对话推荐模式。用户通过表单筛选出候选池后，启动侧边栏 AI 对话，经过多轮偏好挖掘和数据讨论，生成结构化推荐报告。

不替代现有推荐，与现有「规则推荐」并存。

## 用户流程

```
筛选页 (recommend.vue)
  │
  ├─ [填写表单] → [获取候选列表] → 左侧候选列表展示
  │
  ├─ [AI 推荐] 按钮 → 右侧滑出 AiChatPanel
  │     │
  │     ├─ AI: "你的候选池有 30 所学校，你更在意什么？"
  │     │   [985/211光环] [专业排名] [城市/地域]
  │     │
  │     ├─ 用户点选 → AI 深入讨论 → 2-5 轮
  │     │
  │     └─ [生成报告] → 跳转报告页
  │
  └─ 报告页 (ai-report.vue)
       ├─ 冲刺档 / 稳妥档 / 保底档 三组卡片
       ├─ 每校详情：推荐理由、匹配度、风险、优缺点
       └─ [收藏] [分享] [重新推荐]
```

## 架构

### 新增文件

```
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/
├── service/
│   ├── IAiRecommendationService.java
│   └── impl/AiRecommendationServiceImpl.java
└── tool/AiRecommendationTools.java

ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/
└── AppAiRecommendationController.java        # /app/ai-recommend/*

ruoyi-ui/src/views/postgrad/app/
├── components/AiChatPanel.vue                # 侧边栏聊天面板
└── ai-report.vue                             # 推荐报告页

sql/postgrad_ai_menu.sql                      # 菜单权限（如需要）
```

### API 端点

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/app/ai-recommend/start` | 传入 candidateIds + profile → 返回 conversationId + AI 开场白 |
| POST | `/app/ai-recommend/chat` | { conversationId, message } → AI 回复 + options |
| POST | `/app/ai-recommend/generate-report` | { conversationId } → reportId（异步，走 RabbitMQ） |
| GET | `/app/ai-recommend/report/{id}` | 查看报告详情 |
| GET | `/app/ai-recommend/reports` | 用户历史报告列表 |
| POST | `/app/ai-recommend/resume` | 尝试恢复过期对话 |

## 数据流

### AI 对话流程

```
Controller.start(candidateIds, profile)
  │
  ├─ 1. 查 DB → 候选学校摘要 + 完整数据
  ├─ 2. Redis:
  │     ai:conv:{id}  → [{system prompt + 开场白}]
  │     ai:pool:{id}  → 候选池完整 JSON
  │     各 TTL = 1800s, 每次交互续期
  ├─ 3. 返回 { conversationId, message, options }

Controller.chat(conversationId, userMessage)
  │
  ├─ 0. 鉴权: 当前登录 userId 必须是 conversationId 的创建者
  │       （Redis ai:conv:{id} 存储时带 userId 字段，校验失败 → 403）
  ├─ 1. 读 ai:conv:{id} → 追加 userMessage
  ├─ 2. AiRecommendationTools.setConversationId(id)  // ThreadLocal
  ├─ 3. ChatModel.chat(messages, tools)
  │       AI 可调用: getProgramDetail(id)、searchPrograms(filters)、
  │                 comparePrograms([ids])
  │       参数 conversationId 由 ThreadLocal 静默注入, 不暴露给 AI
  ├─ 4. 写回 ai:conv:{id}，EXPIRE 1800
  ├─ 5. 每 3 轮异步落 DB (recommendation_log.conversation_state JSON)
  ├─ 6. 前端同步写 localStorage
  └─ 7. 返回 { message, options }

Controller.generateReport(conversationId)
  │
  ├─ 1. 鉴权（同 /chat 的 userId 绑定校验）
  ├─ 2. 取完整对话历史 + 深入过的学校完整数据
  ├─ 3. 投递 RabbitMQ 消息: { conversationId, convData }
  ├─ 4. 返回 reportId（状态 PENDING，前端轮询 /report/{id}）
  │
  ▼ (异步 Consumer)
AiReportConsumer.onMessage(msg)
  │
  ├─ 1. ChatModel.chat → 生成三档推荐 JSON
  ├─ 2. 后端计算 matchScore（规则算法，非 AI 生成）
  ├─ 3. 保存 recommendation_log (profile_snapshot + result_json)
  ├─ 4. Redis: ai:report:{id} (TTL 7天)
  ├─ 5. 更新 ai_batch_task / ai_search_result 状态为 DONE
  └─ 6. WebSocket 推送通知前端
```

### Token 优化：两阶段注入

| 阶段 | 注入内容 | Token 量 |
|---|---|---|
| Layer 1 摘要层（每轮必带） | System prompt + 用户画像 + 学校摘要（名称/城市/层次/均分/分差） | ~1,500 |
| Layer 2 详细层（Tool 按需） | getProgramDetail → 单校完整录取数据 | +600/次 |
| 报告生成 | 完整对话历史 + 深入过的学校完整数据 | ~4,000 |

### 降级策略

```
千问 API 故障（超时 / 5xx）
  │
  ├─ 第 1 次失败 → 重试
  ├─ 第 2 次失败 → 返回降级标记 { fallback: true }
  └─ 前端收到 fallback → 调现有规则引擎 POST /app/recommendation/generate
        → 展示规则推荐结果
        → 顶部提示："AI 对话暂不可用，已为你展示规则推荐结果"
```

## System Prompt

```
你是考研408计算机专业择校顾问。

## 你的角色
帮助考生从候选学校池中挑选最匹配的目标院校。
风格：数据驱动、诚实、简洁。不画饼，不说"努力就能上"。
每轮只聚焦一个维度。

## 用户画像
- 预估总分: {estimatedScore}
- 本科层次: {undergradTier}
- 跨考: {isCrossMajor}
- 风险偏好: {riskPreference}
- 目标地区: {targetRegions}
- 数学水平: {mathLevel}，英语水平: {englishLevel}

## 候选学校摘要
{id, name, tier, city, avgScore, gap}

## 可用工具
- getProgramDetail(programId): 获取完整录取数据
- searchPrograms(filters): 在候选池内筛选
- comparePrograms(ids): 横向对比多校

## 对话节奏
第1轮: 了解最看重的维度
第2-3轮: 用具体数据讨论 2-3 所目标校
第4-5轮: 确认冲刺/稳妥/保底意向

## 输出格式
每轮回复含简短文字(2-4句) + 2-3个快捷选项。
用户说"出报告"时,只回复"好的,正在生成..."
```

## 报告 JSON Schema

```json
{
  "summary": "一句话总结",
  "tiers": [
    {
      "level": "reach|steady|safe",
      "label": "冲刺档|稳妥档|保底档",
      "schools": [
        {
          "programId": 1,
          "schoolName": "...",
          "programName": "...",
          "reason": "推荐理由",
          "matchScore": 65,
          "risk": "high|medium|low",
          "pros": ["优势1", "优势2"],
          "cons": ["劣势1", "劣势2"]
        }
      ]
    }
  ]
}
```

**matchScore 计算规则：** matchScore 不由 AI 生成，由后端规则算法计算。AI 只负责选出学校和写推荐理由，报告返回后后端补充分数：

```
matchScore = max(0, 100 - |estimatedScore - avgAdmittedScore| * weight)
  weight = 0.5 if avgAdmittedScore > estimatedScore (冲刺), 0.3 otherwise
  最终 clamp 到 [0, 100]
```

同一 programId + 同一预估分永远得到相同 matchScore，保证一致性。

## 报告生成方式

| 威胁 | 对策 |
|---|---|
| Prompt 注入篡改 conversationId | conversationId 通过 ThreadLocal 注入 Tool，不暴露给 AI |
| 用户要求查候选池外学校 | Tool 只读 Redis `ai:pool:{id}` 内数据 |
| 用户注入指令修改 System Prompt | 用户输入包在 `<user_input>` 标签，System Prompt 用 `###` 分隔 |
| 对话泄露到公共电脑 | localStorage 不清除时前端提供「退出清除」机制 |

## 成本估算

| 项目 | 单次费用 |
|---|---|
| 摘要层 6 轮 × ~1500t | ¥0.007 |
| Tool 调用 2-3 次 × ~1000t | ¥0.002 |
| 报告生成 × ~4000t | ¥0.003 |
| **单次对话总成本** | **≈ ¥0.02** |

月 1000 次对话 ≈ ¥20（千问 qwen-plus 定价）。

## Redis Key 设计

| Key | 内容 | TTL |
|---|---|---|
| `ai:conv:{id}` | 对话消息 JSON | 1800s 滑动 |
| `ai:pool:{id}` | 候选池完整数据 JSON | 1800s 滑动 |
| `ai:report:{id}` | 生成报告 JSON | 7 天固定 |

## 对话恢复策略

```
恢复优先级: Redis → localStorage(仅展示) → DB快照 → 重新开始
```

| 场景 | 体验 |
|---|---|
| 离开 < 30min | Redis 有，无感恢复 |
| 离开 > 30min, localStorage 有 | 展示历史，后端重建上下文继续 |
| 全过期 | 友好提示 + 「用原筛选条件重新开始」按钮 |

## 测试

- [ ] 单次对话 golden path: start → 3轮 chat → generate-report
- [ ] 候选池隔离: 两个 conversationId 互不干扰
- [ ] ThreadLocal 清理: AI 调用异常后不泄漏
- [ ] Tool 拒绝候选池外 programId
- [ ] 降级: 千问不可用 → 回退规则引擎
- [ ] 对话恢复: Redis 过期 + localStorage 有数据
- [ ] 滑动 TTL: 发消息后 TTL 续期
- [ ] 报告 JSON schema 校验
- [ ] 会话鉴权: 用 A 的 conversationId 在 B 账号下调用 /chat → 403
- [ ] matchScore 一致性: 同一 programId + 同一预估分，多次生成报告分数不变
