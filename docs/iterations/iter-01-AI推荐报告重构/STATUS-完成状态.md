# AI 推荐报告重构 — 完成状态

> 截至 2026-06-13

## 需求覆盖：14/14 ✅

| 编号 | 功能 | 状态 |
|------|------|------|
| F001 | 生成 AI 推荐草稿 | ✅ DraftServiceImpl + SSE 进度 |
| F002 | 展示推荐依据 | ✅ ProfileBasisVO + 报告详情页 |
| F003 | 候选事实卡 | ✅ SchoolFact(31字段) + DraftCandidateCard |
| F004 | AI 候选选择 | ✅ AiSelectorService → LLM → JSON 解析 |
| F005 | AI 结果校验 | ✅ SelectionValidator 四条规则 |
| F006 | 右侧报告草稿 | ✅ DraftPanel + TierCandidates |
| F007 | 移出候选 | ✅ DraftAdjustService.removeCandidate |
| F008 | 加回候选 | ✅ DraftAdjustService.addBackCandidate |
| F009 | 替换候选 | ✅ DraftAdjustService.replaceCandidate |
| F010 | 候选分析 | ✅ AiChatService + Markdown 渲染 |
| F011 | 生成最终报告 | ✅ ReportService.generateReport(快照) |
| F012 | 不满 3/4/3 也可生成 | ✅ insufficient 标记 + 确认弹窗 |
| F013 | 查看历史报告 | ✅ ReportService.listReports/getReport |
| F014 | 旧流程废弃 | ✅ 新模块独立包，旧代码保留不动 |

## NFR 覆盖：7/7 ✅

| 编号 | 要求 | 实现 |
|------|------|------|
| NFR001 | AI 不得生成事实字段 | SchoolFact/AiOpinion 分离 |
| NFR002 | 推荐必须有理由 | AI 选择必须返回 reason |
| NFR003 | 草稿和最终报告一致 | ReportService 深拷贝快照 |
| NFR004 | 不强行凑满 3/4/3 | insufficient + insufficientReason |
| NFR005 | 操作简单 | 一键生成 → 调整 → 报告 |
| NFR006 | 生成过程有状态提示 | SSE progress 事件 |
| NFR007 | 废弃边界 | 新 Redis key `ai:v2:*` 前缀隔离 |

## 代码规模

| 层 | 文件数 | 总行数 | 最大文件 |
|----|--------|--------|---------|
| domain + dto | 20 | ~1200 | CandidateCardVO |
| service 接口 | 8 | ~120 | — |
| service 实现 | 8 | 2163 | CandidatePoolServiceImpl 383行 |
| tool | 2 | 308 | V2ChatTools 258行 |
| Controller | 1 | ~450 | AppV2RecommendController |
| 前端 | 8 | ~1200 | AiRecommendV2Workspace |

## 已知改进项（非阻塞）

| # | 项 | 优先级 |
|---|-----|--------|
| 1 | `toSchoolFact`(CandidatePoolServiceImpl) 和 `rowToSchoolFact`(ReportServiceImpl) 有 ~20 行重复映射代码 | 低 |
| 2 | Controller 中 `sendSseEvent` 和 `safeSend` 两套 SSE 发送方法并存 | 低 |
| 3 | 流式 markdown 渲染时，不完整的标记（如 `**bold` 未闭合）会短暂显示为源码 | 低 |
| 4 | AiChatService 系统提示词只含草稿上下文，未注入用户画像（分数/偏好） | 中 |
| 5 | 对话恢复时，用户画像如果更新了，系统提示词不会刷新 | 低 |

## 结论

**重构目标已达到。14 个功能需求、7 个非功能需求全部覆盖。零 TODO 残留。**
