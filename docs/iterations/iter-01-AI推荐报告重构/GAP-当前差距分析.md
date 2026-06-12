# 差距分析 — AI 推荐报告重构（截至 2026-06-13）

## 1. 已实现 vs 待实现

### 1.1 已完成（✅）

| 模块 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 候选池构建 | `CandidatePoolServiceImpl` | ✅ 已实现 | DB查询→去重→gap计算→三档分档→综合得分排序 |
| AI 选校 | `AiSelectorServiceImpl` | ✅ 已实现 | 事实卡构建→LLM调用→JSON解析→调Validator |
| 结果校验 | `SelectionValidator` | ✅ 已实现 | 池外检测、去重、数据不足降级、上限裁剪 |
| 草稿生成 | `DraftServiceImpl` | ✅ 已实现 | 编排生成流程→Redis持久化→进度回调推送 |
| 任务化SSE | `DraftGenerationTaskServiceImpl` | ✅ Codex新增 | POST创建任务→GET/EventSource订阅进度流 |
| 草稿调整 | `DraftAdjustServiceImpl` | ✅ 已实现 | 移除、替换、加回候选 |
| 生成锁 | `DraftServiceImpl` | ✅ 已实现 | Redis SET NX防止重复点击 |
| 分档边界 | `CandidatePoolServiceImpl` | ✅ 已修复 | reach: -15~5, steady: 6~14, safe: ≥15+canBeSafe |
| Controller | `AppV2RecommendController` | ✅ 已实现 | SSE+REST端点，task/stream双重模式 |
| 前端主页面 | `AiRecommendV2Workspace.vue` | ✅ 已实现 | 三栏布局+EventSource SSE消费 |
| 前端组件 | `DraftPanel/CandidateCard/ProfileSidebar/GenerateButton/ChatPanel/ReportView` | ✅ 已实现 | Element Plus主题一致 |
| 前端API | `recommend-v2.js` | ✅ 已实现 | task/EventSource模式 |
| 前端工具 | `event-source.js` | ✅ Codex新增 | EventSource URL构建工具 |
| 路由 | `router/index.js` | ✅ 已修改 | `/ai-recommend`指新页面 |
| Vite代理 | `vite.config.js` | ✅ 已配置 | SSE流代理配置 |

### 1.2 待实现（❌）

| 模块 | 文件 | TODO数 | 优先级 |
|------|------|--------|--------|
| **最终报告生成** | `ReportServiceImpl.generateReport()` | — | **P0** |
| **报告详情查询** | `ReportServiceImpl.getReport()` | — | **P0** |
| **历史报告列表** | `ReportServiceImpl.listReports()` | — | **P0** |
| AI对话流式 | `AiChatServiceImpl.chat()` | 7 | P1 |
| AI对话启动 | `AiChatServiceImpl.startChat()` | 2 | P1 |
| AI对话恢复 | `AiChatServiceImpl.resumeChat()` | 1 | P1 |
| 工具：getProgramDetail | `V2ChatTools` | 1 | P1 |
| 工具：searchPrograms | `V2ChatTools` | 1 | P1 |
| 工具：comparePrograms | `V2ChatTools` | 1 | P1 |
| 工具：getDraftContext | `V2ChatTools` | 1 | P1 |
| 工具：loadPoolSnapshot | `V2ChatTools` | 1 | P1 |
| 工具：loadDraft | `V2ChatTools` | 1 | P1 |

## 2. Codex 架构变更评估

Codex 引入了任务化的 SSE 模式，替代原先的直接推送模式。

**变更内容：**

| 新增 | 职责 |
|------|------|
| `DraftGenerationTaskVO` | taskId + streamToken |
| `DraftGenerationTaskState` | status + phase + message + draftJson + errorMessage |
| `IDraftGenerationTaskService` | start / getState / validateStreamToken |
| `DraftGenerationTaskServiceImpl` | Redis存储任务状态 + 异步生成 + 轮询推送 |
| `event-source.js` | EventSource URL构建 + 兼容baseURL |

**架构评价：** 这个变更是合理的。原先 POST+fetch+ReadableStream 需要携带 Authorization header，而 EventSource 不能自定义 header。task/streamToken 模式解决了这个问题，同时保留了 getState 的独立查询能力。未增加耦合。✅

**但是，原先的 `POST /draft/generate` 端点（fetch+ReadableStream模式）还在 Controller 里并存，建议后续清理掉其中一种。**

## 3. P0 详细任务

### 3.1 实现 `ReportServiceImpl.generateReport()`

```
输入：userId
处理：
  1. 从 Redis ai:v2:draft:{userId} 读取当前 DraftVO
  2. 校验：至少 1 个 tier 中有候选（totalCount > 0）
  3. 报告快照：深拷贝 DraftVO.tiers，不受后续草稿修改影响
  4. 插入 recommendation_log：
     - user_id = userId
     - profile_snapshot = JSON(profileBasis)
     - result_json = JSON(ReportVO)
     - rule_version = 'ai-v2'
     - data_version = '1.0'
     - is_paid = 0
  5. 缓存 Redis：ai:v2:report:{reportId} = ReportVO JSON, TTL 7天
  6. 返回 ReportVO(reportId, summary, tiers, profileBasis, createdAt)
输出：ReportVO
```

**关键约束：** 最终报告的候选集必须与生成时草稿完全一致。不新增、不删除、不替换。

### 3.2 实现 `ReportServiceImpl.getReport()`

```
输入：userId, reportId
处理：
  1. 先查 Redis ai:v2:report:{reportId}
  2. 未命中：查 DB recommendation_log WHERE id=reportId AND user_id=userId
  3. 校验所有权（user_id匹配）
  4. 反序列化 result_json → ReportVO
  5. 返回 ReportVO
输出：ReportVO
```

### 3.3 实现 `ReportServiceImpl.listReports()`

```
输入：userId
处理：
  1. 查询 recommendation_log WHERE user_id=userId AND rule_version='ai-v2'
     ORDER BY created_at DESC LIMIT 20
  2. 每条记录解析 result_json 提取 summary 字段
  3. 返回 List<ReportSummaryVO>
输出：List<ReportSummaryVO>
```

## 4. P1 详细任务

### 4.1 AI 对话最小可用版

目标：AI 能解释草稿内容，不做工具调用。

```
AiChatServiceImpl.chat() 实现：
  1. 加载对话历史（Redis）
  2. 构建系统提示词（用户画像 + 草稿摘要）
  3. 使用 streamingChatModel 流式返回
  4. 解析 ---ACTION--- 分隔的 DraftAction
  5. 通过 ChatStreamCallback 推送 token/done/error
```

### 4.2 V2ChatTools 实现（按优先级）

| 优先级 | 工具 | 原因 |
|--------|------|------|
| 1 | `getDraftContext` | AI 必须先知道草稿里有什么 |
| 2 | `getProgramDetail` | 解释为什么推荐某所学校 |
| 3 | `comparePrograms` | 对比两个候选 |
| 4 | `searchPrograms` | 搜索替代候选 |

## 5. 端到端闭环验证清单

| # | 场景 | 预期 | 当前 |
|---|------|------|------|
| 1 | 点击"生成草稿" | SSE进度→三档候选卡片展示 | ✅ 已通 |
| 2 | 移除候选 | 卡片滑出→已移除列表出现 | ✅ 已通 |
| 3 | 替换候选 | 新候选替换旧候选 | ✅ 已通 |
| 4 | 加回候选 | 候选回到草稿 | ✅ 已通 |
| 5 | 重复点击生成 | 提示"正在生成" | ✅ 已加锁 |
| 6 | 生成最终报告 | 报告快照→跳转详情页 | ❌ TODO |
| 7 | 查看报告详情 | 展示三档候选+画像依据+摘要 | ❌ TODO |
| 8 | 历史报告列表 | 显示已生成的报告列表 | ❌ TODO |
| 9 | 问AI分析候选 | AI流式解释推荐理由 | ❌ TODO |
| 10 | AI对话调整草稿 | 移除/替换草稿候选 | ❌ TODO |

## 6. 建议执行顺序

```
第1步（P0）：实现 ReportServiceImpl 三方法（~200行，2小时）
第2步（P0）：前端联调：草稿→生成报告→跳转详情→返回历史
第3步（P0）：端到端验证闭环 #6~#8
第4步（P1）：实现 AiChatServiceImpl + V2ChatTools（~500行，3小时）
第5步（P1）：前端联调：AI对话→解释草稿→调整草稿
第6步（P1）：端到端验证闭环 #9~#10
```

## 7. 代码健康指标

| 指标 | 当前 | 评估 |
|------|------|------|
| recommend包文件数 | 38 | ✅ |
| 最大文件行数 | CandidatePoolServiceImpl 375行 | ✅ ≤400 |
| 裸Map残留 | 0 | ✅ 全强类型 |
| SseEmitter穿透Service层 | 0 | ✅ DraftGenerationCallback桥接 |
| 旧代码耦合 | 0 | ✅ 不复用旧AiRecommendationTools |
| 垃圾桶命名 | 0 | ✅ |
| 编译状态 | 通过 | ✅ |
| TODO残留 | ReportService(8) + AiChatService(9) + V2ChatTools(11) | ⚠ P0+P1待实现 |
