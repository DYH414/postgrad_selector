# 详细设计 — AI 推荐报告重构

> 状态：待评审
> 日期：2026-06-12
> 关联需求：REQ-AI推荐报告重构.md

---

## 0. 设计原则

本次是**从零重建**，不是修补。旧 AI 推荐流程代码全部保留不动（仅用于旧历史报告查看），新模块用新类名、新 Redis key 前缀、新 Controller URL，物理隔离。

每个模块遵守单一职责：一个 Service 做一件事，能一句话说清楚。

---

## 1. 目录与文件清单

### 1.1 新增目录

```
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/
├── domain/          # 新 VO / 内部模型
├── service/         # 新 Service 接口
├── service/impl/    # 新 Service 实现
└── config/          # 新模块配置（如有）

ruoyi-postgrad/src/main/resources/prompts/v2/
├── select-reach.txt
├── select-steady.txt
├── select-safe.txt
└── chat-system.txt

user-ui/src/views/ai-recommend-v2/
└── components/

user-ui/src/api/
```

### 1.2 新增文件清单（后端 — ruoyi-postgrad）

| # | 文件 | 类型 | 一句话职责 |
|---|------|------|-----------|
| 1 | `recommend/domain/DraftVO.java` | VO | 草稿完整状态：三档候选 + 画像依据 + 不足说明 |
| 2 | `recommend/domain/CandidateCardVO.java` | VO | 单个候选的事实卡（学校、分数、gap、名额、风险） |
| 3 | `recommend/domain/TierCandidates.java` | 内部模型 | 单档候选集合：档位标签 + 候选列表 + 是否不足 |
| 4 | `recommend/domain/AiSelectionResult.java` | 内部模型 | AI 选校返回：选中候选 ID + 理由 + 风险提示 |
| 5 | `recommend/domain/ReportVO.java` | VO | 最终报告：三档候选 + 摘要 + 生成时间 |
| 6 | `recommend/domain/ReportSummaryVO.java` | VO | 报告列表项：reportId + 摘要 + 生成时间 |
| 7 | `recommend/domain/ChatMessageVO.java` | VO | 对话消息 |
| 8 | `recommend/domain/DraftAction.java` | 内部模型 | AI 对话解析出的草稿操作指令 |
| 9 | `recommend/service/IDraftService.java` | 接口 | 草稿生成、查询、调整 |
| 10 | `recommend/service/IReportService.java` | 接口 | 报告生成、查询、列表 |
| 11 | `recommend/service/IAiChatService.java` | 接口 | 对话式草稿调整 |
| 12 | `recommend/service/ICandidatePoolService.java` | 接口 | 候选池构建与规则分档 |
| 13 | `recommend/service/IAiSelectorService.java` | 接口 | AI 在每档内挑选候选 |
| 14 | `recommend/service/impl/DraftServiceImpl.java` | 实现 | 草稿核心逻辑 + SSE 进度推送 |
| 15 | `recommend/service/impl/ReportServiceImpl.java` | 实现 | 草稿快照 → 最终报告 |
| 16 | `recommend/service/impl/AiChatServiceImpl.java` | 实现 | SSE 流式对话 + 草稿操作意图解析 |
| 17 | `recommend/service/impl/CandidatePoolServiceImpl.java` | 实现 | DB 查询 → 粗筛 → 规则分档 |
| 18 | `recommend/service/impl/AiSelectorServiceImpl.java` | 实现 | 构建事实卡 → 调 LLM → 解析 JSON → 校验 |
| 19 | `recommend/service/impl/SelectionValidator.java` | 工具类 | AI 结果校验：去池外、去重、数据不足降级 |
| 20 | `resources/prompts/v2/select-reach.txt` | 提示词 | 冲刺档选择提示词 |
| 21 | `resources/prompts/v2/select-steady.txt` | 提示词 | 稳妥档选择提示词 |
| 22 | `resources/prompts/v2/select-safe.txt` | 提示词 | 保底档选择提示词 |
| 23 | `resources/prompts/v2/chat-system.txt` | 提示词 | 对话系统提示词 |

### 1.3 新增文件清单（后端 — ruoyi-admin）

| # | 文件 | 类型 | 一句话职责 |
|---|------|------|-----------|
| 24 | `web/controller/postgrad/AppV2RecommendController.java` | Controller | 新 API 端点：参数校验、调用 Service、返回 AjaxResult / SSE |

### 1.4 新增文件清单（前端 — user-ui）

| # | 文件 | 类型 | 一句话职责 |
|---|------|------|-----------|
| 25 | `views/ai-recommend-v2/AiRecommendV2Workspace.vue` | 页面 | 主工作台：画像栏 + 生成按钮 + 草稿面板 + 对话面板 |
| 26 | `views/ai-recommend-v2/components/DraftPanel.vue` | 组件 | 右侧草稿面板：三档候选展示 + 操作按钮 |
| 27 | `views/ai-recommend-v2/components/DraftCandidateCard.vue` | 组件 | 单个候选卡片：事实摘要 + 理由 + 风险 + 移除按钮 |
| 28 | `views/ai-recommend-v2/components/AiChatMiniPanel.vue` | 组件 | 底部/侧边对话面板：SSE 流式对话 + 快捷操作 |
| 29 | `views/ai-recommend-v2/components/ReportView.vue` | 组件 | 报告详情视图 |
| 30 | `views/ai-recommend-v2/components/GenerateDraftButton.vue` | 组件 | 一键生成按钮 + 进度状态 |
| 31 | `api/recommend-v2.js` | API | 前端 API 调用封装 |

### 1.5 修改文件清单

| # | 文件 | 修改内容 | 影响范围 |
|---|------|---------|---------|
| 32 | `user-ui/src/router/index.js` | 新增路由 `/ai-recommend-v2` → `AiRecommendV2Workspace` | 仅新增，不删旧路由 |

**不修改的文件**（旧模块保持原样运行）：

- `AppAiRecommendationController.java` — 保留，旧报告仍可通过旧 API 查看
- `AiRecommendationServiceImpl.java` — 保留不动
- `AiReportBuilderImpl.java` — 保留不动
- `AiCandidatePoolServiceImpl.java` — 保留不动
- `AiRecommendationSafety.java` — 保留不动
- `AiConstants.java` — 保留不动
- `RecommendationMapper.java` / `.xml` — 保留，但新模块直接复用此 Mapper（它是数据访问层，不包含旧业务逻辑）

---

## 2. 模块职责详述

### 2.1 架构图

```
┌──────────────────────────────────────────────────────────┐
│                  AppV2RecommendController                 │
│  只做：参数校验 → 调 Service → 返回 AjaxResult / SseEmitter │
│  不碰：业务逻辑、Redis、DB、LLM                            │
└────┬──────────────┬──────────────┬───────────────────────┘
     │              │              │
┌────▼──────┐ ┌────▼──────┐ ┌────▼──────────────┐
│DraftService│ │ReportService│ │  AiChatService    │
│            │ │             │ │                   │
│ • 生成草稿 │ │ • 生成报告  │ │ • 流式对话         │
│ • 调整草稿 │ │ • 查看报告  │ │ • 解析调整意图     │
│ • 查询草稿 │ │ • 报告列表  │ │ • 返回 DraftAction │
│ • SSE 进度 │ │             │ │   (前端执行)       │
└────┬──────┘ └────┬───────┘ └───────────────────┘
     │              │
     │    ┌─────────▼──────────┐
     │    │  RecommendationMapper │  ← 复用已有 Mapper
     │    │  (数据访问层)         │     CandidateProgramDTO
     │    └────────────────────┘
     │
┌────▼────────────┐   ┌─────────────────┐
│CandidatePoolSvc │   │ AiSelectorSvc    │
│                 │   │                  │
│ • DB 查询候选   │   │ • 构建事实卡     │
│ • 粗筛(地区/gap)│   │ • 调 LLM 挑选    │
│ • 规则分档      │   │ • 解析 JSON 结果 │
│ (纯计算,无AI)   │   │ • 调 Validator   │
└────────────────┘   └────────┬────────┘
                              │
                     ┌────────▼────────┐
                     │SelectionValidator│
                     │                  │
                     │ • 池外候选丢弃   │
                     │ • 重复候选去重   │
                     │ • 数据不足降级   │
                     └─────────────────┘
```

### 2.2 各模块详细说明

#### CandidatePoolService — 候选池构建与规则分档

```
输入：UserProfile（预计分数、地区偏好、层次偏好）
输出：TierCandidates × 3（reach / steady / safe，每档含事实卡列表）
```

**处理步骤：**

1. 从 `RecommendationMapper.selectCandidates()` 查询 408 项目（全量）
2. 粗筛：地区过滤 → score_gap 在 [-30, +30] 范围 → 数据完整度 A/B 优先 → 去重
3. 规则分档（无 AI 参与）：
   - `reach`: gap ≤ 5（分数接近或低于均分）
   - `steady`: gap 6~14（分数有余量）
   - `safe`: gap ≥ 15 且 canBeSafe=true（分数充裕 + 名额充裕）
4. 每档按综合得分排序（数据完整度 × 名额风险 × 学校层次 × gap 适配度）
5. 每档最多取 15 所作为 AI 输入

**关键规则：**
- `canBeSafe` 判断逻辑（独立于此 Service 的工具方法）：
  - 名额 ≤ 3 → false
  - 名额 < 10 且（数据完整度 C 或 无录取区间）→ false
  - 否则 → true

#### AiSelectorService — AI 在每档内挑选

```
输入：TierCandidates（单档候选事实卡列表）+ 档位标识
输出：AiSelectionResult（选中的 programId 列表 + 每所选理由 + 风险提示）
```

**处理步骤：**

1. 为每所候选构建"精简事实卡"（只包含 AI 需要知道的字段）：
   ```
   ID:123 | XX大学 | 计算机技术 | 985 | 北京 | 录取均分345 | 差距+10 | 招生15人 | 名额正常 | 可保底
   ```
2. 加载对应档位的提示词模板，注入事实卡列表
3. 调用 `ChatModel.chat()`（单轮，非对话）
4. 解析 AI 返回的 JSON：
   ```json
   [
     {"programId": 123, "reason": "分数充裕，985平台，招生人数稳定", "risks": ["复试竞争激烈"]},
     ...
   ]
   ```
5. 调用 `SelectionValidator` 校验

**三档分别调用，串行执行（冲→稳→保），每档 3-8 秒，总计约 10-25 秒。**

#### SelectionValidator — 校验器

```
输入：AI 选择的 programId 列表 + 原始候选池
输出：校验后的 AiSelectionResult + 被丢弃的候选列表（用于前端展示"为什么没选XX"）
```

**校验规则（任何一条不通过，丢弃该候选）：**

1. **池外检测**：programId 必须在候选池内 → 不通过则丢弃（AI 幻觉）
2. **重复检测**：同一 programId 只能出现一次 → 保留第一次
3. **数据不足降级**：数据完整度 C 的候选不得进入主档，降为"待核验"
4. **档位上限**：每档最多 4 所（reach 3 / steady 4 / safe 3）

#### DraftService — 草稿管理

```
核心职责：草稿的完整生命周期管理
```

**方法：**

| 方法 | 说明 | SSE |
|------|------|-----|
| `generateDraft(userId)` | 编排完整生成流程，SSE 推送进度 | ✅ |
| `getDraft(userId)` | 从 Redis 读取当前草稿 | ❌ |
| `removeCandidate(userId, programId)` | 从草稿中移除，写回 Redis | ❌ |
| `replaceCandidate(userId, removeId, tier, preference)` | 从同档候选池选一个替代 | ❌ |
| `addBackCandidate(userId, programId)` | 将之前移除的加回草稿 | ❌ |
| `getAlternatives(userId, tier, excludeId)` | 获取同档其他可选候选 | ❌ |

**generateDraft SSE 事件序列：**

```
event: progress
data: {"phase": "loading_profile", "message": "正在加载用户画像..."}

event: progress
data: {"phase": "building_pool", "message": "正在筛选候选学校...", "found": 47}

event: progress
data: {"phase": "ai_selecting", "message": "AI 正在冲刺档挑选合适的学校...", "tier": "reach"}

event: progress
data: {"phase": "ai_selecting", "message": "AI 正在稳妥档挑选合适的学校...", "tier": "steady"}

event: progress
data: {"phase": "ai_selecting", "message": "AI 正在保底档挑选合适的学校...", "tier": "safe"}

event: progress
data: {"phase": "validating", "message": "正在校验 AI 推荐结果..."}

event: done
data: {"draft": {...}, "profileBasis": {...}, "removedCount": 2, "insufficientNotice": "保底档候选不足..."}
```

**Redis 存储：**

```
Key: ai:v2:draft:{userId}
Value: DraftVO JSON
TTL: 7 天
```

#### ReportService — 报告管理

```
核心职责：草稿 → 最终报告的快照生成
```

**generateReport 流程：**

1. 从 Redis 读取当前草稿
2. 校验：草稿至少 1 个可信候选
3. 对草稿中的每个候选，水合完整数据（从 DB 批量查）
4. 生成报告摘要（如"冲到3所、稳妥4所、保底3所"或"保底档暂无可信候选"）
5. 写入 `recommendation_log` 表（`rule_version = 'ai-v2'`）
6. 写入 Redis 缓存（`ai:v2:report:{reportId}`，TTL 7 天）
7. 返回 ReportVO

**关键约束：最终报告的候选集必须与生成时草稿完全一致。不新增、不删除、不替换。**

#### AiChatService — 对话式调整

```
核心职责：SSE 流式对话，解析用户的调整意图，返回 DraftAction 让前端执行
```

**设计决策：对话不直接操作草稿。**

对话只返回结构化的 `DraftAction`，前端读取后调用 DraftService 的 API 执行。这样：
- 对话是纯文本理解 + 意图识别
- 草稿变更逻辑集中在 DraftService
- 前端是唯一的草稿变更发起者

**DraftAction 结构：**

```java
public class DraftAction {
    String type;        // "remove" | "replace" | "analyze" | "none"
    Long programId;     // 操作目标
    String tier;        // 替换时指定档位
    String preference;  // 替换偏好："safer" | "higher_tier" | "closer_region"
    String reason;      // AI 解释为什么要执行这个操作
}
```

**对话流程：**

1. 前端通过 SSE 连接 `/chat/send`
2. 后端加载对话历史 + 当前草稿上下文 → 构建系统提示词
3. AI 流式返回 token
4. AI 在回复末尾嵌入 `---ACTION---` 分隔的 JSON
5. 后端解析出 `DraftAction`，作为 `done` 事件的一部分返回
6. 前端：
   - 渲染 AI 的文本回复
   - 如果 `draftAction.type != "none"`，自动（或提示用户确认后）调用对应 DraftService API

---

## 3. API 设计

所有端点前缀：`/app/ai-recommend-v2`

### 3.1 草稿

| 方法 | 路径 | Content-Type | 说明 |
|------|------|-------------|------|
| `GET` | `/draft` | application/json | 获取当前草稿 |
| `POST` | `/draft/generate` | text/event-stream | SSE 生成草稿 |
| `POST` | `/draft/remove` | application/json | 移除候选 |
| `POST` | `/draft/replace` | application/json | 替换候选 |
| `POST` | `/draft/add-back` | application/json | 加回候选 |
| `GET` | `/draft/alternatives` | application/json | 获取同档替代候选 |

**POST /draft/generate** (SSE)

```
Response (text/event-stream):

event: progress
data: {"phase":"loading_profile","message":"正在加载用户画像..."}

event: progress
data: {"phase":"building_pool","message":"正在筛选候选学校...","found":47}

event: progress
data: {"phase":"ai_selecting","message":"AI 正在冲刺档挑选...","tier":"reach"}

event: progress
data: {"phase":"ai_selecting","message":"AI 正在稳妥档挑选...","tier":"steady"}

event: progress
data: {"phase":"ai_selecting","message":"AI 正在保底档挑选...","tier":"safe"}

event: progress
data: {"phase":"validating","message":"正在校验 AI 推荐结果..."}

event: done
data: {
  "draft": {
    "tiers": [
      {
        "level": "reach",
        "label": "冲刺档",
        "targetCount": 3,
        "candidates": [
          {
            "programId": 123,
            "schoolName": "XX大学",
            "programName": "计算机技术",
            "tier": "985",
            "city": "北京",
            "avgAdmittedScore": 345,
            "scoreGap": -5,
            "quota": 15,
            "quotaRisk": "normal",
            "dataCompleteness": "A",
            "reason": "分数接近，今年可能有回调机会",
            "risks": ["复试竞争激烈", "分数差距-5分有风险"],
            "pros": ["985平台", "招生人数稳定"],
            "cons": ["分数偏低"],
            "status": "selected"
          }
        ],
        "insufficient": false
      },
      {
        "level": "steady",
        "label": "稳妥档",
        "targetCount": 4,
        "candidates": [...],
        "insufficient": false
      },
      {
        "level": "safe",
        "label": "保底档",
        "targetCount": 3,
        "candidates": [
          {
            ...,
            "status": "selected"
          }
        ],
        "insufficient": true,
        "insufficientReason": "当前分数下满足严格保底条件的候选不足，仅找到 1 所。已排除名额过少或数据不足的候选。"
      }
    ],
    "removedCandidates": [],
    "generatedAt": "2026-06-12T15:30:00"
  },
  "profileBasis": {
    "estimatedScore": 330,
    "targetRegions": "北京、上海",
    "undergradTier": "双非",
    "riskPreference": "balanced"
  },
  "removedCount": 1,
  "removedReasons": [
    {"programId": 456, "schoolName": "YY大学", "reason": "候选不在系统候选池内，AI 幻觉已拦截"}
  ]
}

event: error
data: {"message": "用户画像缺少预计分数，请先在个人资料中补充"}
```

**POST /draft/remove**

```
Request:  {"programId": 123}
Response: {"draft": {...}}  // 更新后的 DraftVO
```

**POST /draft/replace**

```
Request:  {"removeProgramId": 123, "tier": "steady", "preference": "higher_tier"}
Response: {"draft": {...}, "replacedWith": {...}}  // 更新后的草稿 + 新加入的候选
```

**POST /draft/add-back**

```
Request:  {"programId": 123}
Response: {"draft": {...}}
```

**GET /draft/alternatives?tier=steady&excludeId=123**

```
Response: {
  "candidates": [
    {"programId": 789, "schoolName": "ZZ大学", ...},
    ...
  ]
}
```

### 3.2 报告

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/report/generate` | 从草稿生成最终报告 |
| `GET` | `/report/{id}` | 查看报告详情 |
| `GET` | `/reports` | 我的 v2 报告列表 |

**POST /report/generate**

```
Response: {
  "reportId": 42,
  "summary": "基于你的画像（预估330分、北京/上海、稳妥优先），在47所候选学校中选出冲刺3所、稳妥4所、保底1所。保底档候选不足，建议继续关注后续年份数据更新。",
  "tiers": [...],  // 与草稿完全一致
  "generatedAt": "2026-06-12T15:30:00",
  "profileBasis": {...}
}
```

**GET /reports**

```
Response: {
  "reports": [
    {"reportId": 42, "summary": "冲刺3所、稳妥4所、保底1所...", "createdAt": "2026-06-12T15:30:00"},
    ...
  ]
}
```

### 3.3 对话

| 方法 | 路径 | Content-Type | 说明 |
|------|------|-------------|------|
| `POST` | `/chat/start` | application/json | 开始/重置对话 |
| `POST` | `/chat/send` | text/event-stream | SSE 流式对话 |
| `GET` | `/chat/resume` | application/json | 恢复对话 |

**POST /chat/send** (SSE)

```
Request:  {"message": "帮我换掉XX大学，想要一个北京211的替代"}

event: token
data: {"text": "好的"}

event: token
data: {"text": "，"}

event: token
data: {"text": "我"}

... (流式 token)

event: done
data: {
  "message": "好的，我帮你看看。北京地区211稳妥档还有 ZZ大学（录取均分340，差距+10），比你当前的XX大学更稳。要换吗？",
  "draftAction": {
    "type": "replace",
    "programId": 123,
    "tier": "steady",
    "preference": "higher_tier"
  }
}
```

### 3.4 异常响应约定

所有非 SSE 端点的错误响应格式：

```json
{
  "code": 500,
  "msg": "用户画像缺少预计分数，请先在个人资料中补充"
}
```

| 场景 | HTTP 状态码 | msg |
|------|-----------|-----|
| 画像缺少预计分数 | 400 | "请先在个人资料中补充预计分数" |
| 候选池为空 | 200 | 正常返回，草稿中三档均为空 + insufficient=true |
| AI 调用超时 | 500 | "AI 服务响应超时，请稍后重试" |
| 草稿不存在（generate 前） | 200 | 返回空草稿 |
| 报告不存在 | 404 | "报告不存在" |
| 无权访问报告 | 403 | "无权访问此报告" |
| 对话过期 | 200 | `{"status":"expired","message":"对话已过期，请开始新对话"}` |
| SSE 中途异常 | event:error | `{"message":"..."}` |

---

## 4. 数据存储设计

### 4.1 Redis Key 设计

所有新 Key 使用 `ai:v2:` 前缀，与旧 `ai:` 前缀物理隔离。

| Key 模式 | 值类型 | TTL | 说明 |
|---------|--------|-----|------|
| `ai:v2:draft:{userId}` | DraftVO JSON | 7 天 | 用户当前草稿 |
| `ai:v2:draft:pool:{userId}` | TierCandidates JSON | 7 天 | 草稿生成时的候选池快照（用于替换/加回操作） |
| `ai:v2:chat:{userId}` | ChatSession JSON | 30 分钟 | 对话会话元数据 |
| `ai:v2:chat:msg:{userId}` | List\<ChatMessage\> JSON | 30 分钟 | 对话消息历史 |
| `ai:v2:report:{reportId}` | ReportVO JSON | 7 天 | 报告缓存 |
| `ai:v2:report:progress:{reportId}` | String | 1 小时 | 报告生成进度 |

**设计理由：**
- 草稿按 userId 而非 conversationId 存储——一个用户同时只有一个草稿，简化管理
- 候选池快照与草稿 TTL 一致——确保替换操作有数据可查
- 对话 TTL 30 分钟——对话是辅助调整工具，不要求长期保存

### 4.2 数据库

**复用 `recommendation_log` 表**，新增 `rule_version = 'ai-v2'` 的记录。

| 字段 | 写入值 | 说明 |
|------|--------|------|
| `user_id` | 用户 ID | |
| `profile_snapshot` | 用户画像 JSON | 用于历史报告展示生成时的画像依据 |
| `result_json` | ReportVO JSON | 报告完整数据 |
| `rule_version` | `'ai-v2'` | 区分新旧报告 |
| `data_version` | `'1.0'` | |
| `is_paid` | `0` | |

**不新建表。** 旧 `recommendation_log` 表结构足够承载新报告数据。

### 4.3 数据流序列

```
生成草稿：
  DB(RecommendationMapper) → CandidatePoolService → 候选池(RAM)
  候选池 → AiSelectorService × 3 → LLM → AiSelectionResult
  AiSelectionResult → SelectionValidator → 校验后结果
  校验后结果 → DraftVO → Redis(ai:v2:draft:{userId})
                    → Redis(ai:v2:draft:pool:{userId})

用户调整：
  Redis(draft) → DraftService.remove/replace/addBack → Redis(draft)

生成报告：
  Redis(draft) → ReportService → 水合完整数据(DB) → ReportVO
    → Redis(ai:v2:report:{reportId})
    → DB(recommendation_log)

对话调整：
  用户消息 + 对话历史(Redis) + 草稿上下文(Redis) → AiChatService → LLM
    → SSE token流 + DraftAction
  前端读取 DraftAction → 调用 DraftService.remove/replace/addBack
```

---

## 5. 技术实现细节

### 5.1 SSE 流式输出

**适用场景：**
- 草稿生成（进度推送 + 最终结果）
- AI 对话（token 流式输出 + DraftAction）

**实现方式（Spring Boot）：**

```java
@PostMapping(value = "/draft/generate", produces = "text/event-stream")
public SseEmitter generateDraft() {
    SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时

    // 使用虚拟线程或线程池异步执行，避免阻塞 Servlet 线程
    Thread.ofVirtual().start(() -> {
        try {
            draftService.generateDraft(userId, new SseProgressEmitter() {
                @Override
                public void progress(String phase, String message, Map<String, Object> extra) {
                    sendSseEvent(emitter, "progress", buildProgressPayload(phase, message, extra));
                }

                @Override
                public void done(DraftVO draft, Map<String, Object> profileBasis, ...) {
                    sendSseEvent(emitter, "done", buildDonePayload(draft, profileBasis, ...));
                    emitter.complete();
                }

                @Override
                public void error(String message) {
                    sendSseEvent(emitter, "error", Map.of("message", message));
                    emitter.complete();
                }
            });
        } catch (Exception e) {
            sendSseEvent(emitter, "error", Map.of("message", e.getMessage()));
            emitter.complete();
        }
    });

    return emitter;
}
```

**为什么不用 MQ 做异步？**

草稿生成是用户点击后等待的操作（等待时间 10-25 秒），SSE 推送进度已经提供了"正在处理中"的反馈。MQ 异步适用于无人等待的后台任务（如旧 analyze 流程的报告生成），但新的设计中没有这种场景——用户始终在等待结果。

**如果未来需要异步（如"生成报告后邮件通知"），再加 MQ 不迟。本次 YAGNI。**

### 5.2 LLM 调用策略

**草稿生成阶段（3 次独立 LLM 调用）：**

每次调用是**单轮 completion**，不是对话：

```
System: 你是考研择校助手。你的任务是从给定的候选学校列表中挑选最合适的。
规则：1) 只从列表中选 2) 不编造数据 3) 给出具体理由 ...

User: ## 冲刺档候选学校
  1. ID:123 | XX大学 | ...
  2. ID:456 | YY大学 | ...

请从上述列表中选择 3 所最适合冲刺的学校，输出 JSON 数组。
```

使用 `ChatModel.chat(SystemMessage.from(prompt), UserMessage.from(facts))`。

**为什么不用 LangChain4j AiServices + Tools？**

旧流程中 AI 通过 Tool Calling 探索候选池，是因为旧流程是"对话驱动"——AI 需要自己决定调用哪些工具。新流程中，候选已经在分档时确定，AI 只需要在限定候选集中"做选择题"，不需要工具调用。简单 system + user message 即可。

**对话阶段（使用 AiServices + Tools）：**

对话阶段 AI 需要工具（如 `getProgramDetail`、`searchPrograms`）来回答用户的问题。使用 LangChain4j AiServices 模式。

### 5.3 并发与线程安全

- **DraftService**：使用 Redis 原子操作（`SET key value NX` + 过期时间），草稿写入是覆盖式，不存在并发竞争。如果用户快速连点"生成"，后一次覆盖前一次。
- **AiSelectorService**：三档串行调用（冲→稳→保），因为每档的上下文可能参考前一档的选择（如"冲刺选了清华，稳妥就不选同一学院的其他方向"）。如果需要性能优化，未来可改为并行调用。
- **AiChatService**：对话是用户驱动的单线程交互，不存在并发问题。

### 5.4 虚拟线程 vs 线程池

Java 21+ 支持虚拟线程，Spring Boot 4.0.3 已内置支持。对于 IO 密集型操作（DB 查询 + LLM 调用），虚拟线程是更好的选择：

```java
// 在 Spring 配置中启用虚拟线程
@Bean
public AsyncTaskExecutor applicationTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
}
```

如果不启用虚拟线程，`CompletableFuture.runAsync()` 使用 ForkJoinPool 也是可以的。

### 5.5 错误处理与降级

```
LLM 调用失败 → 重试 1 次 → 仍失败：
  - 草稿生成中：该档返回空，草稿中标注"AI 服务暂不可用，该档未完成"
  - 对话中：返回错误消息，提示用户稍后重试

候选池为空 → 草稿三档均为空，前端展示"当前条件下没有可信候选"

AI 返回 JSON 解析失败 → 尝试提取 ```json``` 代码块 → 仍失败则改档返回空，记录 warn 日志

用户画像缺失 → 不阻塞，使用默认值（分数 300、地区不限、偏好均衡）
```

---

## 6. 前端设计要点

### 6.1 页面布局

```
┌─────────────────────────────────────────────────────────┐
│  AppHeader (AI 推荐)                                     │
├──────────┬────────────────────────┬─────────────────────┤
│ 画像卡片  │                       │  右侧草稿面板         │
│          │     [生成AI推荐草稿]    │                     │
│ • 预计分  │         ↓             │  冲刺档 (3/3)        │
│ • 地区    │    ⏳ AI 正在挑选中...  │  ┌──────────────┐   │
│ • 偏好    │                       │  │ XX大学  [移除] │   │
│          │     ┌─────────────┐    │  │ 理由: ...     │   │
│          │     │ 对话面板     │    │  │ 风险: ...     │   │
│          │     │ (可收起)    │    │  └──────────────┘   │
│          │     │             │    │                     │
│          │     │ > 换一个... │    │  稳妥档 (4/4)        │
│          │     │             │    │  ...                │
│          │     └─────────────┘    │                     │
│          │                       │  保底档 (1/3) ⚠不足  │
│          │                       │  ...                │
│          │                       │                     │
│          │                       │  [生成最终报告]       │
├──────────┴────────────────────────┴─────────────────────┤
│ 三栏布局：左(画像300px) | 中(对话,可变) | 右(草稿400px)     │
└─────────────────────────────────────────────────────────┘
```

### 6.2 核心交互

**生成草稿：**
1. 用户点击"生成 AI 推荐草稿"
2. 按钮变为加载状态，显示当前阶段文字（"正在筛选候选学校..." → "AI 正在冲刺档挑选..." → ...）
3. 完成后右侧面板滑入，展示三档候选
4. 如果某档不足，显示 ⚠ 标记 + 不足原因

**调整草稿：**
1. 每个候选卡片有"移出"按钮
2. 移出后该档位出现"换一个"占位符
3. 点击"换一个"→ 调 alternatives API → 弹出候选列表 → 用户选择 → 替换
4. 或者打开对话面板："帮我换一个北京211的保底" → AI 回复 + 前端执行操作

**生成报告：**
1. 用户点击"生成最终报告"
2. 确认对话框（如果某档不足，提示"保底档仅 1 所，确定生成？"）
3. 调用 generate API
4. 跳转到报告详情页

### 6.3 SSE 事件消费（前端）

```javascript
// api/recommend-v2.js
export function generateDraft() {
  return fetch('/app/ai-recommend-v2/draft/generate', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${getToken()}` }
  })
  // 返回 ReadableStream，组件自行解析 SSE
}

// 组件中
const eventSource = new EventSource('/app/ai-recommend-v2/draft/generate')
// 注意：EventSource 不支持 POST + custom headers
// 实际方案：使用 fetch + ReadableStream 手动解析 SSE
```

**实际 SSE 解析方案（fetch-based）：**

```javascript
async function generateDraftWithProgress() {
  const response = await fetch('/app/ai-recommend-v2/draft/generate', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${getToken()}`,
      'Accept': 'text/event-stream'
    }
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() // 保留不完整的最后一行

    for (const line of lines) {
      if (line.startsWith('event: ')) {
        // 记录事件类型
      } else if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6))
        // 根据事件类型处理
      }
    }
  }
}
```

---

## 7. 与旧模块的共存策略

| 功能 | 旧（保留） | 新 |
|------|----------|-----|
| 生成推荐 | ❌ 废弃入口 | `/app/ai-recommend-v2/draft/generate` |
| AI 对话 | 旧对话（仅查看历史记录时恢复） | `/app/ai-recommend-v2/chat/*` |
| 生成报告 | 旧 generateReport | `/app/ai-recommend-v2/report/generate` |
| 查看历史报告 | 旧 `/app/ai-recommend/report/{id}` 继续可用 | `/app/ai-recommend-v2/report/{id}` |
| 报告列表 | 旧 `/app/ai-recommend/reports` 继续可用 | `/app/ai-recommend-v2/reports` |

**前端路由：**
- 旧页：`/ai-recommend` → `AiRecommendWorkspace.vue`（保留）
- 新页：`/ai-recommend-v2` → `AiRecommendV2Workspace.vue`（开发期）
- 上线后：`/ai-recommend` 改指新页面，旧页面移到 `/ai-recommend-legacy`

---

## 8. 风险与待定项

| 风险 | 缓解措施 |
|------|---------|
| AI 三档串行调用耗时 10-25 秒，用户体验差 | SSE 进度推送 + 虚拟线程 + 未来可改为并行 |
| AI 返回 JSON 格式不稳定 | 多层解析防御（提取代码块 → 提取花括号 → 兜底空数组）+ 记录 warn 日志 |
| 候选池不够大（<10 所）AI 选择无意义 | 候选池 < 10 所时跳过 AI，直接用规则编排兜底结果 |
| 旧报告数据迁移 | 不需要——旧报告通过旧 API 继续查看，新旧报告在 DB 中通过 rule_version 区分 |
| 对话与草稿状态不一致 | 对话不直接改草稿——DraftAction 由前端执行，前端调用 DraftService，单一变更路径 |

---

## 9. 实施建议

**Phase 1（核心链路）：**
1. `CandidatePoolServiceImpl` + `SelectionValidator` — 候选池构建与校验
2. `AiSelectorServiceImpl` + 3 个提示词文件 — AI 选校
3. `DraftServiceImpl` — 草稿生成 + SSE 进度
4. `AppV2RecommendController` — 基础端点（generate、get、remove、replace）
5. 前端主工作台 + 草稿面板

**Phase 2（报告）：**
6. `ReportServiceImpl` — 报告生成 + 查询
7. Controller 报告端点
8. 前端报告视图

**Phase 3（对话调整）：**
9. `AiChatServiceImpl` — 对话 + DraftAction
10. Controller 对话端点
11. 前端对话面板

---

*设计文档结束。待用户评审后进入实施计划阶段。*
