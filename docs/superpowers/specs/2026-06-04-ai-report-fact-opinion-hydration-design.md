# AI 推荐报告事实与观点分层设计

## 背景

当前 AI 推荐报告的学校卡片直接消费报告 JSON 中的字段，例如学院、专业、分数、招生人数和推荐理由。这个结构把两类职责混在一起：

- 事实数据：学校、学院、专业、分数线、录取区间、招生人数、数据年份、来源和完整度。
- 观点判断：为什么推荐、适合冲稳保哪一档、有哪些取舍、需要注意什么。

这会导致 AI 选中了正确的 `programId`，但报告展示字段可能缺失或与数据库不一致，例如学院显示为 `-`。新的设计目标是让 AI 只负责观点判断，事实数据始终由后端数据库补全。

## 设计目标

1. AI 报告中的事实字段全部来自后端数据库或后端计算结果。
2. AI 只输出候选 `programId`、分档、推荐理由、取舍说明和风险观点。
3. 后端校验 AI 输出的 `programId` 必须来自本次候选池。
4. 后端根据 `programId` 回表补全页面所需详情，生成稳定的报告快照。
5. 前端继续作为结构化报告渲染器，并在 UI 上区分事实数据和 AI 观点。
6. 同步降级路径和 MQ 异步路径必须调用同一个报告构建/水合组件，避免 prompt 和 schema 分叉。
7. 用户画像页需要先收集基础择校偏好，AI 对话只补充本次推荐里的临时取舍。

## 非目标

- 不让 AI 直接查询任意学校或生成任意 ID。
- 不使用单一线性总分替代综合择校判断。
- 不在前端逐卡请求详情来补全报告主体。
- 不要求本次重做筛选结果页或备选页的整体 UI。

## 核心原则

**事实由后端负责，观点由 AI 负责。**

后端可以计算均分差、最低分差、招生规模、地区匹配、专业匹配、数据完整度等事实和事实衍生指标，但不把这些指标强行压成一个唯一线性分数。它们作为证据材料交给 AI。AI 根据用户画像中的偏好观点进行综合取舍，输出人类顾问式的推荐判断。

报告生成存在两条入口：`AiRecommendationServiceImpl` 的同步降级路径，以及 `AiReportConsumer` 的 MQ 异步路径。两条路径不得各自维护 prompt、fallback 和数据注入逻辑。新的共享组件放在 `ruoyi-postgrad` 模块，由 `ruoyi-admin` 依赖并调用。

## 数据流

```text
用户画像 + 搜索条件
        |
        v
后端筛选候选池
        |
        v
后端生成事实摘要 factSnapshot
        |
        v
AI 根据 factSnapshot + preferenceProfile 输出 opinion
        |
        v
后端校验 programId 属于候选池
        |
        v
后端按 programId 回表水合完整展示字段
        |
        v
保存最终报告 result_json
        |
        v
前端渲染事实数据 + AI 观点
```

## 用户画像观点层

用户画像不只保存分数和硬条件，还需要表达用户的择校取向。推荐初期可以使用枚举型字段，避免复杂权重带来的解释成本。

```json
{
  "riskPreference": "balanced",
  "priorityPreference": "success_rate",
  "schoolTierPreference": "no_strict_requirement",
  "regionStrategy": "no_limit",
  "dataReliabilityPreference": "medium"
}
```

字段含义：

- `riskPreference`：风险偏好，例如 `conservative`、`balanced`、`aggressive`。该字段已存在，继续保留。
- `priorityPreference`：最看重什么，例如 `success_rate`、`school_tier`、`developed_region`、`major_strength`。
- `schoolTierPreference`：学校层次倾向，例如 `must_211_or_better`、`prefer_211_or_better`、`no_strict_requirement`。
- `regionStrategy`：地区策略，例如 `no_limit`、`developed_regions`、`specific_regions`、`near_home`。
- `dataReliabilityPreference`：数据可靠性要求，例如 `strict`、`medium`、`loose`。

本次改造需要在画像页提供轻量选择项，避免 AI 每次重新追问稳定偏好。对话仍然可以覆盖本次推荐里的临时取舍。新增字段为空时使用默认值：均衡风险、上岸概率优先、地区不限、学校层次不强求、数据可靠性中等。

画像页控件不使用复杂权重滑杆，而使用 3-4 个分段按钮或下拉选择，降低填写成本。

## 后端事实摘要

后端传给 AI 的不是完整数据库记录，而是适合判断的事实摘要。事实摘要可以包含学校展示字段，但 AI 输出时不需要回传这些字段。

```json
{
  "programId": 123,
  "facts": {
    "schoolName": "北京信息科技大学",
    "collegeName": "计算机学院",
    "programName": "计算机科学与技术",
    "province": "北京",
    "city": "北京",
    "schoolTier": "普通本科",
    "examCombo": "11408",
    "dataYear": 2025,
    "avgAdmittedScore": 295,
    "avgScoreGap": 5,
    "admissionLow": 270,
    "admissionLowGap": 30,
    "admissionHigh": 331,
    "scoreLine": 273,
    "scoreLineGap": 27,
    "unifiedExamQuota": 28,
    "planCount": 33,
    "dataCompleteness": "C",
    "sourceOwner": "N诺"
  },
  "signals": {
    "scoreSafety": "medium",
    "quotaScale": "medium",
    "regionMatch": "matched",
    "majorMatch": "high",
    "dataReliability": "low"
  }
}
```

`signals` 是后端对事实的离散化表达，用于帮助 AI 稳定理解数据，但它不是最终推荐结论。

## AI 输出 Schema

AI 只输出观点结果，不输出学校、学院、专业等事实字段。

```json
{
  "summary": "基于稳妥优先和发达地区偏好，推荐以稳妥档为主，少量配置冲刺。",
  "tiers": [
    {
      "level": "steady",
      "label": "稳妥档",
      "schools": [
        {
          "programId": 123,
          "judgement": "steady",
          "risk": "medium",
          "decision": "适合作为主力稳妥候选",
          "reason": "分数与历史拟录取均分接近，地区符合发达地区偏好，招生规模适中。",
          "pros": ["分数匹配度较高", "地区符合偏好", "招生规模不算小"],
          "cons": ["学校层次不是优势", "数据完整度为 C，需要复核官网"],
          "tradeoffs": ["上岸稳定性优先于学校层次"],
          "recommendedAction": "建议进入备选，并核验官网招生目录和拟录取名单"
        }
      ]
    }
  ]
}
```

约束：

- `programId` 必须来自候选池。
- `level` 只允许 `reach`、`steady`、`safe`。
- `judgement` 只表达 AI 对该专业方向的观点判断。
- `reason`、`pros`、`cons`、`tradeoffs` 不得编造数据库中不存在的事实。
- AI 不需要输出 `schoolName`、`collegeName`、`programName`、分数或招生数字。

## 后端水合报告

后端在 AI 输出后执行 `hydrateReportPrograms`：

1. 收集 AI 输出里的所有 `programId`。
2. 校验每个 ID 是否属于本次候选池。
3. 对合法 ID 调用现有推荐详情数据源，例如 `RecommendationMapper.selectProgramForRecommendation` 或复用 `ProgramRecommendationServiceImpl` 的规范化逻辑。
4. 生成 `factSnapshot`，包含页面展示所需字段。
5. 将 `factSnapshot` 和 AI 的 `opinion` 合并到最终报告。
6. 丢弃非法 ID、重复 ID 或缺少详情数据的项目，并在 `meta` 中记录原因。

最终报告中的单个学校结构建议为：

```json
{
  "programId": 123,
  "schoolName": "北京信息科技大学",
  "collegeName": "计算机学院",
  "programName": "计算机科学与技术",
  "province": "北京",
  "examCombo": "11408",
  "dataYear": 2025,
  "avgAdmittedScore": 295,
  "avgScoreGap": 5,
  "admissionLow": 270,
  "admissionHigh": 331,
  "admissionRange": "270-331",
  "unifiedExamQuota": 28,
  "planCount": 33,
  "dataCompleteness": "C",
  "sourceUrl": "...",
  "sourceOwner": "N诺",
  "opinion": {
    "judgement": "steady",
    "risk": "medium",
    "decision": "适合作为主力稳妥候选",
    "reason": "分数与历史拟录取均分接近，地区符合发达地区偏好，招生规模适中。",
    "pros": ["分数匹配度较高", "地区符合偏好", "招生规模不算小"],
    "cons": ["学校层次不是优势", "数据完整度为 C，需要复核官网"],
    "tradeoffs": ["上岸稳定性优先于学校层次"],
    "recommendedAction": "建议进入备选，并核验官网招生目录和拟录取名单"
  }
}
```

为了兼容现有前端，后端也可以把 `opinion.reason`、`opinion.pros`、`opinion.cons` 映射到顶层旧字段，但新的语义边界以 `opinion` 为准。

## 前端展示

AI 报告页应保持和筛选结果页一致的事实展示风格：

- 卡片顶部展示学校、学院、专业、地区、考试组合和数据年份。
- 分数区展示拟录取均分、均分差距、最低录取分、拟录取区间、招生人数。
- 数据质量展示完整度、来源和官网核验提示。
- AI 观点区展示推荐依据、需要注意、取舍说明和行动建议。

页面文案上区分：

- “录取数据”“招生信息”“数据来源”属于事实。
- “AI 推荐依据”“需要注意”“取舍判断”属于观点。

如果旧报告缺少 `opinion`，前端可以继续读取旧字段作为兼容 fallback；如果旧报告缺少学院等事实字段，前端只展示“详情待补充”，不再把它当作 AI 观点问题。

## 错误处理

- AI 返回非 JSON：使用规则 fallback，并进入同一水合流程。
- AI 返回候选池外 ID：丢弃该项，记录 `meta.invalidProgramIds`。
- AI 返回重复 ID：保留第一次出现，记录 `meta.duplicateProgramIds`。
- 数据库查不到详情：丢弃该项或标记 `hydrationStatus: missing_detail`。
- 某一档为空：允许为空，但报告 `summary` 应提示候选不足。
- 候选池为空：直接返回无推荐报告，不调用 AI。

## 与现有代码的关系

- `AiRecommendationServiceImpl.buildReportPrompt`：调整 prompt，要求 AI 只输出 `programId` 和观点字段。
- `AiRecommendationServiceImpl.validateAndNormalizeReport`：继续负责合法性校验，并增加候选池 ID 校验。
- `AiRecommendationServiceImpl.injectMatchScores`：升级为水合流程，或者新增独立 `hydrateReportPrograms` 替代当前只拷贝部分字段的逻辑。
- `ProgramRecommendationServiceImpl.programDetail` / `RecommendationMapper.selectProgramForRecommendation`：作为水合事实来源。
- `user-ui/src/utils/aiReport.js`：兼容新旧结构，将 `opinion` 规范化为页面可读字段。
- `user-ui/src/views/AiReport.vue`：展示事实卡片和 AI 观点区，避免依赖 AI 直接输出学院等字段。

## 测试策略

后端测试：

- AI 只返回 `programId` 时，最终报告能补全 `collegeName`、`programName`、分数和招生字段。
- 候选池外 `programId` 会被丢弃。
- 重复 `programId` 会去重。
- fallback 报告也会进入同一水合流程。
- 缺失详情的数据会被记录到 `meta`。

前端测试：

- 新结构 `opinion` 能正常显示推荐依据、风险和取舍。
- 旧结构报告仍能显示旧字段。
- 缺少学院的旧报告不会误把 `-` 当成正常事实。
- 卡片事实字段和筛选结果页字段名称保持一致。

## 实施顺序建议

1. 后端先新增报告水合函数，并让现有 AI 输出也经过水合。
2. 调整 AI prompt 和 schema，让 AI 不再输出事实字段。
3. 更新前端归一化逻辑兼容 `opinion`。
4. 微调 AI 报告页 UI，将事实和观点视觉区分。
5. 增加后端和前端测试。

这个顺序可以先解决学院缺失和事实不一致问题，再逐步收紧 AI 输出结构。
