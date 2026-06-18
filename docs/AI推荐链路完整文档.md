# V2 AI 推荐 — 完整链路文档

> 从用户画像到最终草稿，每一步的数据变换、决策逻辑、兜底策略。

---

## 架构总览

系统维护四层数据结构，各司其职：

```
Universe（全集）     →  最宽的候选池，gap ≥ -30 全进，存 Redis
  ↓ 过滤+排序+diversityTrim
Workspace（工作集）  →  每档 30 所，去同校，给 AI 做选择空间
  ↓ 更进一步过滤+排序
Pool（精选池）       →  每档 15 所，backend 打分排序，供 mutations 用
  ↓ AI 选择（3 档并行）
Draft（草稿）        →  最终 3/4/3 = 10 所，用户可见
```

四层都持久化到 Redis（7 天 TTL），刷新不丢。

| 结构 | 每档数量 | 总计 | Redis Key |
|------|---------|------|-----------|
| CandidatePool | ~15 | ~45 | `ai:v2:draft:pool:{userId}` |
| CandidateUniverse | 无限（gap≥-30） | ~200-500 | `ai:v2:universe:{userId}` |
| CandidateWorkspace | 30 | ~90 | `ai:v2:workspace:{userId}` |
| Draft | 3/4/3 | 10 | `ai:v2:draft:{userId}` |

---

## Step 1: 用户画像 → ProfileBasisVO

**代码：** `DraftServiceImpl.buildProfileBasis()`

```
UserProfile (MySQL)
  ├── estimatedScore: 300
  ├── targetRegions: ["浙江", "福建"]
  ├── undergradTier: "PUBLIC_REGULAR"
  ├── isCrossMajor: true
  ├── riskPreference: "balanced"
  ├── schoolTierPreference: "prefer_211_or_better"
  └── regionStrategy: "developed_priority"

        ↓

ProfileBasisVO {
  estimatedScore: 300,
  targetRegions: "浙江、福建",
  undergradTier: "双非一本",
  isCrossMajor: "跨考",
  riskPreference: "平衡兼顾",
  schoolTierPreference: "优先211及以上",
  regionStrategy: "优先发达地区",
  candidateScope: ""  // 后续由 Universe 实际数量填入
}
```

---

## Step 2: 候选池构建

**代码：** `CandidatePoolServiceImpl.buildPool()`

### 2.1 SQL 查询

两轮分别查 11408（英一数一）和 22408（英二数二），按 `programId` 去重合并。

```sql
SELECT ...
FROM program p
JOIN college c ON p.college_id = c.id
JOIN school s ON c.school_id = s.id
LEFT JOIN (子查询: program_subject + subject, GROUP_CONCAT 拼科目)
LEFT JOIN admission_score sc (子查询: 取最新年份)
LEFT JOIN admission_plan ap (同年)
LEFT JOIN admission_result ar (同年)
LEFT JOIN program_year_data_quality q (同年)
LEFT JOIN data_source × 3
WHERE p.status='active' AND s.status='active' AND p.is_408=1
  AND p.study_mode='full_time'
  AND subj.subject_codes = '101,201,301,408'  -- 或 '101,204,302,408'
  AND s.province IN (...)                     -- 可选
  AND p.program_code IN (...)                 -- 可选
  AND estimatedScore >= avgAdmittedScore - 30 -- gap ≥ -30
LIMIT 300
```

### 2.2 构建 SchoolFact

`SchoolFact.fromRow(row)` 逐字段提取：

| 分组 | 字段 | 来源 |
|------|------|------|
| 标识 (2) | `programId`, `schoolId` | DB |
| 学校 (4) | `schoolName`, `schoolTier`(中文标签), `city`, `province` | DB + `tierLabel()` |
| 学院/专业 (5) | `collegeName`, `programName`, `programCode`, `degreeType`, `examCombo` | DB |
| 分数 (5) | `scoreLine`, `avgAdmittedScore`, `admissionLow`, `admissionHigh`, `admissionRange`("345-360") | DB + 派生 |
| 招生 (4) | `planCount`, `unifiedExamQuota`, `admittedCount`, `retestCount` | DB |
| 数据质量 (4) | `dataYear`, `dataCompleteness`(A/B/C), `sourceUrl`, `sourceOwner` | DB |
| 后端计算 (6) | `scoreGap`, `gapLabel`, `quotaLabel`, `quotaRisk`, `canBeSafe`, `safeBlockReason` | 后端 |

### 2.3 计算字段规则

#### scoreGap

```
scoreGap = estimatedScore - avgAdmittedScore
例: 300 - 302 = -2（你比均分低 2 分）
```

#### gapLabel

```
≥ 0  → "+N"  例: "+10"（你比均分高 10 分）
< 0  → "-N"  例: "-5"（你比均分低 5 分）
```

#### quotaLabel

| 名额 | 标签 |
|------|------|
| ≤ 0 | 名额未知 |
| ≤ 3 | 名额极少 |
| < 10 | 名额偏少 |
| < 20 | 名额正常 |
| ≥ 20 | 名额充裕 |

#### quotaRisk

| 名额 | 风险等级 |
|------|---------|
| ≤ 0 | unknown |
| ≤ 3 | very_high |
| < 10 | high |
| < 20 | medium |
| ≥ 20 | normal |

#### canBeSafe — 保底资格审查

```java
public static boolean canBeSafe(int quota, String completeness,
                                Integer admissionLow, Integer admissionHigh) {
    if (quota <= 3) return false;           // 招 ≤3 人，不足保底
    if (quota < 10) {                       // 招 4-9 人，需要额外条件
        boolean hasRange = admissionLow != null || admissionHigh != null;
        if ("C".equals(completeness) || !hasRange)
            return false;                   // 数据不全或无录取区间 → 拒绝
    }
    return true;                            // 招 ≥10 人 → 自动通过
}
```

### 2.4 档位分类（系统唯一真相来源）

```java
// SchoolFact.classifyTier()
public static String classifyTier(int gap, Boolean canBeSafe) {
    if (gap < -15)  return null;            // 差距太大，不入档
    if (gap >= -15 && gap <= 5)             // -15 ~ +5
        return "reach";                     // → 冲刺档
    if (gap <= 14)                          // +6 ~ +14
        return "steady";                    // → 稳妥档
    if (gap >= 15 && canBeSafe)             // ≥ +15 且通过保底审查
        return "safe";                      // → 保底档
    return "steady";                        // ≥ +15 但不满足保底条件 → 降级
}
```

### 2.5 复合评分排序

每档内按 `compositeScore` 降序排列，四个因子：

| 因子 | 取值 | 权重 |
|------|------|------|
| 数据完整度 | A=30, B=20, C=10 | 最高 |
| 名额风险 | normal=30, medium=20, high/very_high=10 | 高 |
| 学校层次 | 985=25, 211/双一流=18, 其他=10 | 中 |
| 分差适配度 | `max(0, 15 - abs(实际gap - 理想gap))` | 辅助 |

**学校层次偏好加成：** 当用户选择 `must_211_or_better` 或 `prefer_211_or_better` 时：
- 985: 25 → 30
- 211: 18 → 22
- 其他: 10 → 5

**理想 gap：** reach=0, steady=10, safe=20

最终每档取 Top **15**，封装为 `CandidateCardVO`（仅 `fact` 层，暂无 `opinion`）。

### 2.6 持久化

```java
// Redis
redisTemplate.opsForValue().set(
    "ai:v2:draft:pool:{userId}",
    JSON.toJSONString(poolFlatList),
    Duration.ofDays(7)
);
```

---

## Step 3: 候选全集构建

**代码：** `CandidateUniverseServiceImpl.buildUniverse()`

与 Pool 使用**同一 SQL 查询**，但：

| 差异 | Pool | Universe |
|------|------|----------|
| gap 过滤阈值 | ≥ -15（不入档的不要） | ≥ -30（更宽，存着备用） |
| 数量限制 | 每档 15 所 | 不限 |
| 用途 | AI 选择的直接来源 | mutations 替换/填充的来源 |

持久化：`ai:v2:universe:{userId}`

---

## Step 4: 工作集构建

**代码：** `CandidateWorkspaceServiceImpl.buildWorkspace()`

从 Universe 中进一步筛选：

```
① 对每个候选：SchoolFact.classifyTier() → 分三档
② 过滤：dataCompleteness = "C" 的丢弃（数据太差不给 AI 看）
③ policyScore 排序：
   - 与 Pool 相同的四个因子
   - 额外：发达地区优先（city 在 24 城列表中 → +10）
④ diversityTrim：同学校只保留得分最高的专业
⑤ 每档取 Top 30
```

**diversityTrim 逻辑：**

```java
// 同一档内，每个 schoolId 只保留 compositeScore 最高的那个
Set<Long> seen = new HashSet<>();
for (CandidateCardVO c : candidates) {
    if (seen.add(c.getFact().getSchoolId())) {
        kept.add(c);  // 第一次出现 → 保留
    }
    // 第二次出现 → 丢弃
}
```

**效果：** 同一档内不会有"杭师大 CS"和"杭师大 AI"同时出现。跨档可以出现（如杭师大稳档+ 杭师大保底档）。

持久化：`ai:v2:workspace:{userId}`

---

## Step 5: AI 并行选择

**代码：** `AiSelectorServiceImpl.select()`

### 5.1 提前退出

```
candidates ≤ limit (reach=3, steady=4, safe=3)
  → 跳过 AI，直接全选（不需要 AI 做选择）
```

### 5.2 拼 Facts 文本

```java
// buildFactsText(candidates)
// 注意：只给原始数据，不给"名额充裕""可保底"等预判标签
// 让 AI 自己从数据中判断，避免后端偏见污染 AI 决策
```

输出格式：

```
1. ID:2854 | 杭州电子科技大学 | 网安方向 | 其他 | 杭州 | 均分302 | 差距-2 | 招生58
2. ID:3012 | 华南农业大学 | 人工智能 | 双一流 | 广州 | 均分295 | 差距+5 | 招生35
3. ID:1789 | 厦门大学 | 计算机科学与技术 | 985 | 厦门 | 均分320 | 差距-20 | 招生12
...
```

### 5.3 加载 Prompt

| 档位 | Prompt 文件 | 上限 | 偏好指令 |
|------|-----------|------|---------|
| 冲刺 | `select-reach.txt` | 3 | 可接受一定风险，关注潜力 |
| 稳妥 | `select-steady.txt` | 4 | 优先名额充裕、匹配度好的 |
| 保底 | `select-safe.txt` | 3 | 优先 gap ≥ 15、名额稳定的 |

### 5.4 LLM 调用

```java
// 单轮对话，不流式
chatModel.chat(
    SystemMessage.from(prompt),    // ~1KB
    UserMessage.from(factsText)   // ~8KB
);
// → DeepSeek 返回 JSON 数组
```

### 5.5 JSON 解析 — 多层防御

```java
// parseAiResponse(raw)
// 第 1 层: 提取 ```json ... ``` 代码块
// 第 2 层: 没有 → 提取 ``` ... ``` 裸代码块
// 第 3 层: 没有 → 提取 [ 到 ] 之间的内容
// 第 4 层: fastjson2 JSON.parseArray → List<SelectedItem>
// 失败 → selectAll fallback
```

**SelectedItem 结构：**

```java
{
    programId: Long,      // 必须
    reason: String,       // AI 给出的选择理由
    risks: List<String>,  // AI 分析的风险
    pros: List<String>,   // AI 分析的优势
    cons: List<String>    // AI 分析的劣势
}
```

### 5.6 Fallback 链

```
LLM 调用抛异常       → selectAll(top N, 按原排序)
LLM 返回空/空白       → selectAll(top N, 按原排序)
JSON 解析返回空列表   → selectAll(top N, 按原排序)
```

**selectAll 逻辑：** 按候选池的原始排序（compositeScore 降序），取前 N 个，不附加 AI opinion。

### 5.7 三档并行

```java
CompletableFuture<AiSelectionResult> futureReach  = ...  // 冲刺
CompletableFuture<AiSelectionResult> futureSteady = ...  // 稳妥
CompletableFuture<AiSelectionResult> futureSafe   = ...  // 保底

// 顺序 join（先完成的先处理），每完成一个立即增量持久化
futureReach.join()   → buildPartialDraft() → saveDraft()
futureSteady.join()  → buildPartialDraft() → saveDraft()
futureSafe.join()    → buildPartialDraft() → saveDraft()
```

---

## Step 6: 校验器兜底

**代码：** `SelectionValidator.validate()`

```
对每个 AI 选中的 SelectedItem：

① programId 为空？
   → blocked: "programId 为空"

② 不在这个档的候选池里？
   → blocked: "候选不在系统候选池内，AI 幻觉已拦截"  ← 最关键的一步

③ 这个 programId 已经选过了？
   → blocked: "候选重复，已去除"

④ dataCompleteness = "C"？
   → blocked: "数据完整度为 C，不具备作为主推荐的条件"

⑤ 通过的数量超过档位上限？
   → 超出部分 blocked: "档位上限已满（3/5），该候选未入选"
```

返回：

```java
AiSelectionResult {
    selected: [通过校验的 SelectedItem],
    blocked:  [{programId, schoolName, blockReason}, ...]
}
```

---

## Step 7: 草稿组装与增量持久化

**代码：** `DraftServiceImpl.mergeSelection()` + `buildPartialDraft()`

### 7.1 合并 AI 选择结果

```java
// mergeSelection(tier, aiResult)
for (CandidateCardVO candidate : workspaceTier.candidates) {
    if (aiResult 的 selected 中包含 candidate.programId) {
        // 创建 AiOpinion，附上 AI 生成的理由/风险/优势/劣势
        AiOpinion opinion = new AiOpinion();
        opinion.setReason(selectedItem.reason);
        opinion.setRisks(selectedItem.risks);
        opinion.setPros(selectedItem.pros);
        opinion.setCons(selectedItem.cons);

        candidate.setOpinion(opinion);
        candidate.setStatus("selected");     // 状态：已选中
        candidate.setFinalJudgement(tier);    // 最终档位
        candidate.setAdjusted(false);         // 非人工调整
        kept.add(candidate);
    }
    // 不在 AI 选中列表 → 丢弃
}

// 按 scoreGap 降序排列（分差最大的排最前）
kept.sort(byScoreGapDesc);
```

### 7.2 增量持久化

每完成一个档位，立即构建部分草稿并写入 Redis：

```java
// buildPartialDraft(doneTiers, blocked, basis, wsSummary)
// 已完成的档位 → 填入真实数据
// 未完成的档位 → insufficient=true, reason="AI 正在[...] 挑选合适的学校..."
```

**效果：** 用户刷新页面时能看到已完成的档位，未完成的显示"生成中"。前端通过 SSE 流式接收 `onTierComplete` 事件更新 UI。

### 7.3 最终草稿结构

```json
{
  "tiers": [
    {
      "level": "reach",
      "label": "冲刺",
      "targetCount": 3,
      "candidates": [
        {
          "fact": {                          // ← SchoolFact（30 字段）
            "programId": 2854,
            "schoolName": "杭州电子科技大学",
            "avgAdmittedScore": 302,
            "scoreGap": -2,
            "quotaRisk": "normal",
            ...
          },
          "opinion": {                      // ← AiOpinion（AI 生成）
            "reason": "杭电位居杭州，IT产业发达...",
            "risks": ["均分略高于你的预估分"],
            "pros": ["招生名额充裕", "地理位置好"],
            "cons": ["学校层次一般"]
          },
          "tier": "reach",                  // ← 后端裁决
          "status": "selected",
          "adjusted": false
        }
      ],
      "insufficient": false
    },
    ...  // steady + safe
  ],
  "removedCandidates": [],
  "blockedCandidates": [
    {"programId": 9999, "schoolName": "未知", "blockReason": "AI 幻觉已拦截"}
  ],
  "profileBasis": {...},
  "generatedAt": "2026-06-18T15:30:00",
  "workspaceSummary": {"reach": 28, "steady": 30, "safe": 30}
}
```

持久化：

```java
redisTemplate.opsForValue().set(
    "ai:v2:draft:{userId}",
    JSON.toJSONString(draft),
    Duration.ofDays(7)
);
```

---

## Step 8: 草稿变更（Mutations）

**代码：** `DraftMutationServiceImpl`

### 8.1 removeCandidate(userId, programId, workspace)

```
① 在草稿中找到 candidate → 确定其所属档位
② 从该档位移除 → 加入 removedCandidates
③ 记录到 Redis ai:v2:excluded:{userId}
④ 调 refillPolicyService.evaluate() 判断填充策略
   - "auto": 自动从 workspace 选最优替补填充
   - "confirm": 返回几个候选让用户选
   - "none": 不填充
```

### 8.2 addCandidate(userId, programId, tier, workspace)

```
① 在 workspace 中按档位查找 candidate
② 去重检查（该档是否已有此学校）
③ 加入草稿对应档位
④ 从 removedCandidates 中删除（如有）
```

### 8.3 replaceCandidate(userId, removeProgramId, addProgramId, tier, workspace)

```
= removeCandidate() + addCandidate()
```

### 8.4 fillTier(userId, tier, workspace)

```
needed = targetCount - currentCount
从 workspace 中选最优 needed 个（未被 excluded 的）
  保底档偏好：gap 更大的
  冲刺档偏好：学校层次更高的
  稳妥档偏好：平衡
```

---

## Step 9: 报告生成

**代码：** `ReportServiceImpl.generateReport()`

用户确认草稿后：

```
① 从 Redis 读取最终 DraftVO
② 收集所有 programId，批量查询 DB 做最终数据快照
③ 新建 SchoolFact（来自 DB 最新数据），但保留草稿中的：
   - 计算字段（scoreGap, quotaRisk, canBeSafe 等）
   - AI Opinion（理由、风险、优劣势）
   - 后端裁决（tier, status, adjusted）
④ 构建摘要文本：
   "基于你的画像，在候选池中选出冲刺 2 所、稳妥 4 所、保底 3 所，共 9 所学校。"
⑤ 写入 MySQL recommendation_log
   - profile_snapshot = JSON(用户画像)
   - result_json = JSON(完整报告)
   - rule_version = "ai-v2"
⑥ 缓存到 Redis ai:v2:report:{reportId}（7 天）
```

---

## 完整数据流图

```
UserProfile (MySQL)
  │
  ▼
┌─────────────────────────────────────────┐
│  SQL: selectCandidates                  │  ← 2 轮（11408 + 22408），去重
│  JOIN 9 表，gap ≥ -30                   │
└─────────────────────────────────────────┘
  │
  ├──→ CandidateUniverse (gap≥-30, 不限量)   → Redis ai:v2:universe:{userId}
  │      │
  │      ├──→ CandidateWorkspace (30/档)     → Redis ai:v2:workspace:{userId}
  │      │      │
  │      │      ├── filter: dataCompleteness ≠ "C"
  │      │      ├── diversityTrim: 同校保留最高分
  │      │      ├── policyScore: 含发达地区加成
  │      │      └── 每档 Top 30
  │      │
  │      └──→ Draft Mutations 替换/填充来源 ← 读 Workspace
  │
  └──→ CandidatePool (15/档, ~45 总)         → Redis ai:v2:draft:pool:{userId}
         │
         ├── filter: gap ≥ -15
         ├── compositeScore: 完整度 + 名额 + 层次 + 适配度
         └── 每档 Top 15
              │
              ▼
┌─────────────────────────────────────────┐
│  AiSelector × 3 (CompletableFuture 并行) │
│  facts 文本 → Prompt → DeepSeek → JSON   │
│  解析失败 → selectAll fallback           │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  SelectionValidator                     │
│  ① programId 非空                        │
│  ② programId 在候选池内（幻觉拦截）      │
│  ③ programId 未重复（去重）             │
│  ④ dataCompleteness ≠ C                 │
│  ⑤ 不超过档位上限                       │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  mergeSelection → 附 AiOpinion           │
│  每档完成 → buildPartialDraft → 存 Redis │  ← 增量持久化
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│  DraftVO (3/4/3 = 10 所)                │  → Redis ai:v2:draft:{userId}
│  ┌ fact: SchoolFact (铁打的数据)        │
│  ├ opinion: AiOpinion (AI 可质疑)       │
│  └ 顶层: tier / status / adjusted       │
└─────────────────────────────────────────┘
              │
              │  用户可手动调整
              ▼
┌─────────────────────────────────────────┐
│  DraftMutationServiceImpl               │
│  remove / add / replace / fillTier       │
│  RefillPolicy: auto / confirm / none    │
└─────────────────────────────────────────┘
              │
              │  用户确认
              ▼
┌─────────────────────────────────────────┐
│  ReportServiceImpl.generateReport()     │
│  DB 最新快照 + 保留 opinion + 计算字段   │
│  存 MySQL recommendation_log            │
│  缓存 Redis ai:v2:report:{id}           │
└─────────────────────────────────────────┘
```

---

## 关键设计决策

| 决策 | 做法 | 原因 |
|------|------|------|
| AI 做什么 | 阅读理解，不是打分排序 | AI 读 20-30 所学校描述，自己判断最合适的 |
| facts 文本怎么写 | 纯数据，不预判 | 不给 AI 灌"名额充裕""可保底"等后端标签 |
| 三档并行 | CompletableFuture × 3 | 减少总等待时间 |
| 增量持久化 | 每档完成即存 Redis | 支持页面刷新恢复、SSE 流式展示 |
| 多层 fallback | LLM挂了/疯了/解析失败 → selectAll | 确保不可能空返回 |
| 事实与观点分离 | `fact`(铁) + `opinion`(可质疑) | AI 幻觉最多污染 opinion，永远碰不到 fact |
| 校验器 | 5 层拦截 | 幻觉检测 > 去重 > C级剔除 > 上限裁剪 |
| 同校多专业 | 档内去重(diversityTrim) | 同一档内每个学校只保留一个专业 |
| 草稿持久化 | Redis（非 MySQL） | 中间态数据，无需 ACID，需要快速读写 |

---

## 相关文件

| 文件 | 职责 |
|------|------|
| `DraftServiceImpl.java` | 生成流程编排、增量持久化、锁管理 |
| `DraftMutationServiceImpl.java` | 草稿变更（移除/添加/替换/填充） |
| `CandidatePoolServiceImpl.java` | 候选池构建、SQL查询、SchoolFact计算 |
| `CandidateUniverseServiceImpl.java` | 候选全集构建（宽阈值） |
| `CandidateWorkspaceServiceImpl.java` | 工作集构建、diversityTrim、policyScore |
| `AiSelectorServiceImpl.java` | AI 调用、facts文本拼装、Prompt加载、JSON解析 |
| `SelectionValidator.java` | 5层校验（幻觉拦截/去重/C级剔除/上限裁剪） |
| `ReportServiceImpl.java` | 报告生成、DB快照、Redis缓存 |
| `SchoolFact.java` | 30字段事实层、档位分类、保底资格审查 |
| `CandidateCardVO.java` | 候选卡片（fact + opinion + 顶层裁决） |
| `AiOpinion.java` | AI 生成的分析（理由/风险/优劣势） |
| `DraftVO.java` | 草稿根对象 |
| `TierCandidates.java` | 单档容器 |
| `ProfileBasisVO.java` | 用户画像快照 |
| `prompts/v2/select-reach.txt` | 冲刺档 AI Prompt |
| `prompts/v2/select-steady.txt` | 稳妥档 AI Prompt |
| `prompts/v2/select-safe.txt` | 保底档 AI Prompt |
| `mapper/postgrad/RecommendationMapper.xml` | 核心 SQL（selectCandidates） |
