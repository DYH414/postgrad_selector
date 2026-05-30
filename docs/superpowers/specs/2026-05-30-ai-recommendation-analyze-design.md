# AI 择校「快速推荐」模式 — 设计规格

2026-05-30 | status: draft

---

## 概述

在现有「AI 对话推荐」模式之外，新增「快速推荐」模式：用户一次请求，后端基于画像 SQL 筛选全量 408 学校数据，构造含完整录取数据的 Prompt，调千问直接输出推荐报告。

两种模式并存：
- **快速推荐** — POST /analyze → 异步 MQ 生成 → 轮询报告，无需多轮对话
- **AI 对话推荐** — 保留现有 start/chat/generate-report 流程

---

## 新增接口

### POST /app/ai-recommend/analyze

- **无需请求体**，后端从 token 中获取 userId
- 返回 `{ reportId, msg }`
- 后端逻辑在 Controller 层完成：查画像 → SQL 筛选 → 存 PENDING 记录 → 发 MQ → 返回 reportId

```
POST /app/ai-recommend/analyze
Authorization: Bearer <token>

Response:
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "reportId": 42,
    "msg": "报告生成中，请稍候"
  }
}
```

---

## 后端数据流

### Controller: AppAiRecommendationController

新增方法 `analyze()`：

1. 获取当前 AppLoginUser → userId
2. 调 `userProfileMapper.selectUserProfileByUserId(userId)` 查画像
3. 提取 `estimatedScore`、`targetRegions`
4. 调 `recommendationMapper.selectForAnalysis(estimatedScore, regions, estimatedScore-20, estimatedScore+20)` 查学校
5. 对结果分层抽样：保底/稳妥/冲刺各约 17 所，上限 50 所
6. 构造 Prompt（画像 + 学校数据表格 + 输出 JSON 格式）
7. 插 `recommendation_log`（status=PENDING, user_id=userId）
8. 发 MQ 消息：`{ reportId, estimatedScore, prompt, poolJson }`
9. 返回 `{ reportId }`

**待确认**：分层抽样逻辑与候选人池服务（`AiCandidatePoolServiceImpl`）的结构相似。可以复用一个方法，也可以内联到 `buildAnalysisPool`。建议在 `IAiCandidatePoolService` 新增 `buildAnalysisPool(profile, estimatedScore)` 方法，避免重复代码。

### Mapper: RecommendationMapper

新增方法 `selectForAnalysis`：

```sql
SELECT
    p.id AS programId, s.name AS schoolName, s.tier AS schoolTier,
    s.province AS province, s.city AS city,
    c.name AS collegeName, p.program_name AS programName,
    p.degree_type AS degreeType,
    subj.subject_codes AS subjectCodes,
    sc.year AS dataYear, sc.score_line AS scoreLine,
    ar.admitted_count AS admittedCount,
    ar.min_admitted_score AS admissionLow,
    ar.avg_admitted_score AS avgAdmittedScore,
    ar.max_admitted_score AS admissionHigh,
    ap.total_plan AS planCount,
    ap.retest_count AS retestCount,
    COALESCE(q.completeness_level, 'C') AS dataCompleteness,
    COALESCE(ar_ds.url, sc_ds.url, ap_ds.url) AS sourceUrl,
    COALESCE(ar_ds.source_owner, sc_ds.source_owner, ap_ds.source_owner, 'N诺') AS sourceOwner
FROM program p
JOIN college c ON p.college_id = c.id
JOIN school s ON c.school_id = s.id
LEFT JOIN (subquery: subject_codes) subj ON subj.program_id = p.id
LEFT JOIN admission_score sc ON sc.program_id = p.id
  AND sc.year = (SELECT MAX(year) FROM admission_score sc2
    WHERE sc2.program_id = p.id AND sc2.score_line IS NOT NULL)
LEFT JOIN admission_plan ap ON ap.program_id = p.id AND ap.year = sc.year
LEFT JOIN admission_result ar ON ar.program_id = p.id AND ar.year = sc.year
LEFT JOIN program_year_data_quality q ON q.program_id = p.id AND q.year = sc.year
LEFT JOIN data_source sc_ds ON sc_ds.id = sc.source_id
LEFT JOIN data_source ap_ds ON ap_ds.id = ap.source_id
LEFT JOIN data_source ar_ds ON ar_ds.id = ar.source_id
WHERE p.status = 'active' AND s.status = 'active' AND p.is_408 = 1
  AND p.study_mode = 'full_time'
  AND subj.subject_codes IN ('101,204,302,408', '101,201,301,408')
  AND (sc.score_line IS NOT NULL OR ar.avg_admitted_score IS NOT NULL)
  AND ar.avg_admitted_score >= #{minScore}
  AND ar.avg_admitted_score <= #{maxScore}
  AND s.province IN <foreach regions>   -- 仅当 regions 非空时生效
ORDER BY ABS(CAST(ar.avg_admitted_score AS SIGNED) - #{estimatedScore}) ASC
LIMIT 300
```

### Prompt 构建

在 `AiReportConsumer`（或共享工具类）中新增 `buildAnalysisPrompt`：

```text
你是考研择校顾问。请基于以下用户画像和候选学校数据，直接输出一份择校推荐报告。

## 用户画像
- 预估总分: {score}
- 本科层次: {undergradTier}
- 跨考: {isCrossMajor}
- 目标地区: {targetRegions}

## 推荐要求
1. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校
2. 学校选择需综合考虑：录取均分、差距、招生人数、报录比、复试线
3. 差距 ≥ 5 分优先稳妥/保底档，差距 ≤ -6 分优先冲刺档
4. 差距 > -5 且 < 5 时可归入稳妥档
5. 不要推荐差距 < -10 分的学校（难度过高）
6. 推荐理由必须引用具体数据（均分、招生人数等），不要只说"分数合适"

## 候选学校数据
格式: ID | 学校 | 专业 | 层次 | 城市 | 均分 | 差距 | 复试线 | 招生 | 录取 | 报录比 | 数据年份

{学校数据行，每行一条}

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

学校数据行格式（`buildAnalysisSchoolRows`）：

```
{idx}. ID:{programId} | {schoolName} | {programName} | {schoolTier} | {city} | 均分:{avg} | 差距:{gap} | 复试线:{scoreLine} | 招生:{planCount} | 录取:{admittedCount} | 报录比:{ratio} | {dataYear}年
```

**说明**：报录比 = `planCount / admittedCount`（若两者都有值），在 Java 层计算后写入行。

---

## MQ Consumer 改造

### AiReportConsumer 适配

当前 consumer 监听 `ai.report.queue`，接收 `{ reportId, conversationId, estimatedScore }`。

**改造方案**：conversation 模式不变，analyze 模式新增可选字段 `prompt`。若消息含 `prompt`，跳过对话读取/裁剪，直接用传入的 prompt。

```
消息格式:
{
  "reportId": 42,
  "estimatedScore": 300,
  "prompt": "你是考研择校顾问...",    // analyze 模式: 预构建的完整 prompt
  "poolJson": "[{...}]"              // 学校数据, 用于 injectFullData
}
```

Consumer 处理逻辑：
1. 若 `msg.prompt` 存在 → 走 analyze 路径，跳过 Redis 对话读取
2. 调 `chatModel.chat(prompt)` → `parseReportJson()` → `injectFullData()`
3. 存 Redis（`ai:report:{id}`, 7 天）+ DB UPDATE

### injectFullData（替换现有 injectMatchScores）

现有 `injectMatchScores` 已注入部分字段。扩展为 `injectFullData`：

```java
private void injectFullData(JSONObject report, int estimatedScore, Map<Long, Map<String,Object>> poolMap) {
    for (tier in report.tiers) {
        String level = tier.level; // "reach" | "steady" | "safe"
        for (school in tier.schools) {
            long pid = school.programId;
            Map<String,Object> stats = poolMap.get(pid);
            if (stats == null) continue;

            // 注入全部数据库字段
            inject(school, stats, "scoreLine");
            inject(school, stats, "avgAdmittedScore");
            inject(school, stats, "admissionLow");
            inject(school, stats, "admissionHigh");
            inject(school, stats, "planCount");
            inject(school, stats, "admittedCount");
            inject(school, stats, "retestCount");
            inject(school, stats, "dataYear");
            inject(school, stats, "dataCompleteness");
            inject(school, stats, "sourceUrl");
            inject(school, stats, "sourceOwner");

            // 计算 gap
            Object avgObj = stats.get("avgAdmittedScore");
            if (avgObj instanceof Number) {
                double gap = estimatedScore - ((Number) avgObj).doubleValue();
                school.put("gap", (int) gap);
            }

            // 计算报录比
            Object plan = stats.get("planCount");
            Object admitted = stats.get("admittedCount");
            if (plan instanceof Number && admitted instanceof Number) {
                double p = ((Number) plan).doubleValue();
                double a = ((Number) admitted).doubleValue();
                if (a > 0) school.put("retestRatio", String.format("%.1f:1", p / a));
            }

            // 匹配度
            if (avgObj instanceof Number) {
                double gap = Math.abs(estimatedScore - ((Number) avgObj).doubleValue());
                double weight = "reach".equals(level) ? 0.5 : 0.3;
                school.put("matchScore", (int) Math.max(0, 100 - gap * weight));
            }
        }
    }
}
```

---

## 前端改动

### AiRecommend.vue

将原有单一「开始 AI 推荐」按钮改为两个入口按钮：

- **快速推荐**（primary 按钮）：调 `postAiAnalyze()` → 拿到 reportId → `router.push('/ai-report/' + reportId)`
- **AI 对话推荐**（secondary 按钮）：`panelOpen = true` 打开 `AiChatPanel`

在 intro-band 下方增加一行说明文字，解释两种模式的区别。

### api/ai.js

新增：
```js
export function postAiAnalyze() {
  return request({ url: '/app/ai-recommend/analyze', method: 'post' })
}
```

### AiReport.vue

无需改动。PENDING 轮询、COMPLETED 展示、操作按钮均复用。

---

## 涉及文件

| 类型 | 文件 | 操作 |
|------|------|------|
| Mapper | `RecommendationMapper.java` | 新增 `selectForAnalysis` 方法签名 |
| Mapper XML | `RecommendationMapper.xml` | 新增 `selectForAnalysis` SQL |
| Service | `IAiCandidatePoolService.java` | 新增 `buildAnalysisPool` 方法签名 |
| Service Impl | `AiCandidatePoolServiceImpl.java` | 实现 `buildAnalysisPool` |
| Service | `IAiRecommendationService.java` | 新增 `analyze` 方法签名 |
| Service Impl | `AiRecommendationServiceImpl.java` | 实现 `analyze`（查画像→查学校→存PENDING→发MQ→返回reportId） |
| Controller | `AppAiRecommendationController.java` | 新增 `POST /analyze` 端点 |
| Consumer | `AiReportConsumer.java` | 支持 prompt 模式 + `injectFullData` |
| Frontend | `AiRecommend.vue` | 双按钮布局 |
| Frontend API | `api/ai.js` | 新增 `postAiAnalyze` |

---

## 指定不做

- 不新建独立的分析服务类 — 逻辑放在现有 Service/Consumer 中
- 不改 `AiReport.vue` 报告页 — 复用
- 不改 `startConversation`/`chat` — 保留不变
- 不新增 MQ Queue — 复用 `ai.report.queue`
- 不新增 Redis key 约定 — 复用 `ai:report:{id}`

---

## 优先级与依赖

| 步骤 | 内容 | 依赖 |
|------|------|------|
| ① | `selectForAnalysis` Mapper + XML | 无 |
| ② | `buildAnalysisPool` 分层抽样 | ① |
| ③ | `analyze` 方法（Controller + Service） | ② |
| ④ | Consumer prompt 模式 + `injectFullData` | ③ |
| ⑤ | 前端双按钮 + API | ③ |
| ⑥ | 集成测试 | ④⑤ |
