# 考研择校推荐引擎 — 数据结构与字段参考

> 生成时间: 2026-05-22 | 数据库: postgrad_selector | 当前数据年份: 2025

---

## 一、数据流概览

```
school ──┐
          ├──> program ──> program_subject ──> subject
college ─┘        │
                  │ (LEFT JOIN, 只取 verified)
                  ├──> admission_score   (复试线)
                  ├──> admission_plan    (招生计划+复试人数)
                  ├──> admission_result  (拟录取分数统计)
                  └──> program_year_data_quality (完整度)
```

**推荐引擎入口:** `RecommendationServiceImpl.fetchCandidates()`

**数据准入条件:**
- `program.is_408 = 1`
- `program.status = 'active'` / `school.status = 'active'`
- `admission_score.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')`
- `admission_plan.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')`
- `admission_result.verify_status IN ('OFFICIAL_VERIFIED', 'MANUAL_VERIFIED')`

---

## 二、核心表结构

### 2.1 school — 学校基础信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(100) | 学校全称 |
| province | VARCHAR(30) | 省份 |
| city | VARCHAR(30) | 城市 |
| tier | ENUM | 学校层次 |
| is_985 | TINYINT(1) | 是否985 |
| is_211 | TINYINT(1) | 是否211 |
| is_double_first | TINYINT(1) | 是否双一流 |
| is_public | TINYINT(1) | 是否公办 |

**tier 枚举值:**
| 值 | 含义 |
|----|------|
| `985` | 985工程 |
| `211` | 211工程 |
| `DOUBLE_FIRST` | 双一流（非985/211） |
| `PUBLIC_REGULAR` | 普通公办 |
| `PRIVATE` | 民办 |
| `INDEPENDENT` | 独立学院 |
| `RESEARCH_INSTITUTE` | 科研院所 |
| `OTHER` | 其他 |

**当前数据:** 341 校 · 28 省 · 75 市 · 985:39 / 211:68 / 双一流:6 / 普通公办:108 / 其他:120

**推荐引擎使用:** `tier` 用于分层分类，`province` 用于地区筛选，`is_985/is_211` 用于展示标签。

---

### 2.2 college — 学院信息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| school_id | BIGINT | 关联 school.id |
| name | VARCHAR(120) | 学院名称 |

**推荐引擎使用:** 通过 `program.college_id` 关联，用于展示学院名称。

---

### 2.3 subject — 初试科目字典

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR(20) | 科目代码 (101/201/204/301/302/408) |
| name | VARCHAR(100) | 科目名称 |
| subject_type | ENUM('public','professional') | 公共课 / 专业课 |

**种子数据 (408相关):**
| code | name | type |
|------|------|------|
| 101 | 思想政治理论 | public |
| 201 | 英语（一） | public |
| 204 | 英语（二） | public |
| 301 | 数学（一） | public |
| 302 | 数学（二） | public |
| 408 | 计算机学科专业基础 | professional |

---

### 2.4 program — 专业方向 (推荐核心)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| college_id | BIGINT | 关联 college.id |
| program_code | VARCHAR(20) | 专业代码 |
| program_name | VARCHAR(120) | 专业名称 |
| research_direction | VARCHAR(200) | 研究方向 |
| study_mode | ENUM | 学习方式 |
| degree_type | ENUM | 学位类型 |
| exam_type | ENUM | 考试类型 |
| score_scale | SMALLINT | 考试满分 (500/300) |
| is_408 | TINYINT(1) | 是否408专业 (关键筛选标签) |
| protects_first_choice | TINYINT(1) | 是否保护一志愿 |
| is_joint_program | TINYINT(1) | 是否联培/中外合作 |
| status | ENUM | 状态 |

**ENUM 值:**

| 字段 | 可选值 |
|------|--------|
| study_mode | `full_time` (全日制) / `part_time` (非全日制) |
| degree_type | `academic` (学硕) / `professional` (专硕) |
| exam_type | `GENERAL_500` / `MANAGEMENT_300` / `LAW_MASTER_500` / `MEDICAL_500` / `ART_500` / `OTHER` |
| status | `active` / `suspended` / `pending` |

**当前数据:** 1,808 个活跃专业 · is_408=1,084 · 10 种专业代码

**408 专业按学位:**
| study_mode | degree_type | 数量 |
|------------|-------------|------|
| full_time | professional | 660 |
| full_time | academic | 344 |
| part_time | professional | 80 |

**推荐引擎使用:**
- `is_408=1` 作为候选池硬筛选条件
- `study_mode` / `degree_type` 用于用户偏好过滤
- `protects_first_choice` 用于风险标记（=0 时警告）
- `is_joint_program` 用于联培项目特殊标记

---

### 2.5 program_subject — 专业-科目关联

| 字段 | 类型 | 说明 |
|------|------|------|
| program_id | BIGINT | 关联 program.id |
| subject_id | BIGINT | 关联 subject.id |
| subject_order | TINYINT | 科目顺序 (1-4) |

**推荐引擎使用:** 当前推荐以 `is_408` 快速筛选为主，`program_subject` 作为长期精确匹配的依据。当用户指定科目要求时使用。

---

## 三、数据表（推荐引擎直接消费）

### 3.1 admission_score — 历年复试线

| 字段 | 类型 | 说明 |
|------|------|------|
| program_id | BIGINT | 关联 program.id |
| year | SMALLINT | 招生年份 |
| **score_line** | SMALLINT | **复试总分线** |
| single_politics | SMALLINT | 政治单科线 |
| single_english | SMALLINT | 英语单科线 |
| single_math | SMALLINT | 数学单科线 |
| single_professional | SMALLINT | 专业课单科线 |
| verify_status | ENUM | 可信度状态 |
| source_id | BIGINT | 来源 (关联 data_source) |

**verify_status:**
| 值 | 含义 | 推荐引擎是否使用 |
|----|------|:--:|
| `OFFICIAL_VERIFIED` | 官方来源已验证 | ✓ |
| `MANUAL_VERIFIED` | 人工审核通过 | ✓ |
| `THIRD_PARTY` | 第三方未审核 | ✗ |
| `OCR_PENDING` | OCR待审核 | ✗ |
| `CONFLICT` | 数据冲突 | ✗ |
| `MISSING` | 数据缺失 | ✗ |

**当前数据 (MANUAL_VERIFIED):**
- 174 条 · 分数范围 250~370 · 均分 298

**推荐引擎使用:**
- `score_line` 是有效分数计算的核心基准
- 取 `MAX(year)` 的最新一年数据
- 无已审核复试线的专业 → 排除出推荐池 (不推荐)
- 单科线当前未参与计算，预留用于单科薄弱用户的风险评估

---

### 3.2 admission_plan — 历年招生计划

| 字段 | 类型 | 说明 |
|------|------|------|
| program_id | BIGINT | 关联 program.id |
| year | SMALLINT | 招生年份 |
| **total_plan** | SMALLINT | **总招生计划** |
| recommended_exemption_plan | SMALLINT | 推免计划 |
| **unified_exam_quota** | SMALLINT | **统考名额** |
| **retest_count** | SMALLINT | **一志愿进入复试人数** |
| verify_status | ENUM | 可信度状态 |

**当前数据 (MANUAL_VERIFIED):**
- 188 条 · 均计划招生 66 人 · 均复试人数 53 人

**推荐引擎使用:**
- `unified_exam_quota < 10` → 风险警告 "统考名额较少"
- `retest_count / admitted_count` → 复录比，用于有效分数修正
  - 复录比 > 2.0 → effective_score +20
  - 复录比 > 1.5 → effective_score +10

---

### 3.3 admission_result — 历年拟录取统计

| 字段 | 类型 | 说明 |
|------|------|------|
| program_id | BIGINT | 关联 program.id |
| year | SMALLINT | 招生年份 |
| admitted_count | SMALLINT | 实际录取人数 |
| first_choice_admitted_count | SMALLINT | 一志愿录取人数 |
| **min_admitted_score** | SMALLINT | **拟录取最低分** |
| **avg_admitted_score** | DECIMAL(5,1) | **拟录取平均分** |
| max_admitted_score | SMALLINT | 拟录取最高分 |
| has_transfer | TINYINT(1) | 是否有调剂录取 |

**当前数据 (MANUAL_VERIFIED):**
- 174 条 · 最低分均 309 · 平均分均 335.4 · 最高分字段大部分为 NULL

**推荐引擎使用:**
- 有 `min_admitted_score` 时: `effective_score = max(score_line, min_admitted_score)`
- 无 `min_admitted_score` 时: `effective_score = score_line` (标记"暂无录取分数据")
- `avg_admitted_score` 展示给用户作为参考难度
- `max_admitted_score` 字段当前大部分为空，未参与计算

---

### 3.4 program_year_data_quality — 数据完整度

| 字段 | 类型 | 说明 |
|------|------|------|
| program_id | BIGINT | 关联 program.id |
| year | SMALLINT | 年份 |
| has_score | TINYINT(1) | 是否有复试线 |
| has_plan | TINYINT(1) | 是否有招生计划 |
| has_result | TINYINT(1) | 是否有拟录取统计 |
| has_official_source | TINYINT(1) | 是否有官网来源 |
| completeness_level | ENUM('A','B','C','D','E') | 完整度等级 |

**推荐引擎使用:** `completeness_level` 用于数据质量标记，当前不直接影响推荐决策（有数据就用，没数据就排除）。后续可用于按完整度排序。

---

## 四、推荐规则配置

### 4.1 recommendation_rule

| 字段 | 默认值 | 说明 |
|------|--------|------|
| rule_scope | `all` | 规则范围 (all / 具体专业代码如 085404) |
| **steady_gap_min** | **20** | 重点稳妥: 预估分-有效复试线 ≥ 20 |
| **focus_gap_min** | **5** | 重点关注: 预估分-有效复试线 ≥ 5 |
| **reach_gap_min** | **-10** | 略冲: 预估分-有效复试线 ≥ -10 |
| small_plan_threshold | 10 | 小招生规模阈值 (< 10 触发降档) |
| high_score_gap_threshold | 20 | 最低分高于复试线多少触发下调 |
| wave_threshold | 30 | 复试线波动阈值 |
| retest_ratio_warning | 1.50 | 复录比预警阈值 (> 1.5 触发风险警告) |

---

## 五、推荐分档算法

### 5.1 有效分数计算

```
effective_score = max(score_line, min_admitted_score)  // 取复试线和录取最低分的较大值

复录比修正:
  复录比 = retest_count / admitted_count
  复录比 > 2.0 → effective_score += 20
  复录比 > 1.5 → effective_score += 10

无 min_admitted_score 时:
  effective_score = score_line  // 标记"暂无录取分数据"，仅用复试线评估
```

### 5.2 分档逻辑

| 档位 | 条件 | 说明 |
|------|------|------|
| **steady** (重点稳妥) | gap ≥ 20 | 分数大幅超过复试线，录取概率高 |
| **focus** (重点关注) | 20 > gap ≥ 5 | 分数略超复试线，需要关注竞争 |
| **reach** (略冲) | 5 > gap ≥ -10 | 分数略低于复试线，有一定风险 |
| **notRecommended** | gap < -10 | 差距较大，不推荐 |
| **insufficient** | 无已审核复试线 | 数据不足，无法评估 |

其中 `gap = 用户预估分 - effective_score`

### 5.3 风险降档

当 `unified_exam_quota < 10` (统考名额较少) 时自动降一档:
- steady → focus
- focus → reach
- reach → notRecommended

### 5.4 风险标记

| 标记条件 | 警告文本 |
|----------|----------|
| 无 min_admitted_score | "暂无已审核录取分数据，基于复试线评估，实际录取分可能更高" |
| unified_exam_quota < 10 | "统考名额较少（N人），录取不确定性高" |
| 复录比 > 2.0 | "复试竞争激烈（复录比 X.X）" |
| protects_first_choice = 0 | "该院校一志愿保护机制较弱" |
| gap ∈ [-3, 3] | "分数与估算线持平，录取风险极高" (warning_level=extreme) |

---

## 六、用户画像 (推荐输入)

### 6.1 user_profile

| 字段 | 类型 | 说明 |
|------|------|------|
| **estimated_score** | SMALLINT | **初试预估总分 (100-500)** |
| **target_regions** | JSON | **目标省市列表** |
| accept_part_time | TINYINT(1) | 是否接受非全日制 |
| accept_transfer | TINYINT(1) | 是否接受调剂 |
| accept_academic | TINYINT(1) | 是否接受学硕 |
| accept_joint | TINYINT(1) | 是否接受联培/中外合作 |
| **risk_preference** | ENUM | **风险偏好** |
| undergrad_tier | ENUM | 本科院校层次 |
| is_cross_major | TINYINT(1) | 是否跨考 |
| math_level | ENUM | 数学基础 |
| english_level | ENUM | 英语基础 |
| cs_level | ENUM | 专业课基础 |

**risk_preference:** `conservative` (保守) / `balanced` (均衡) / `aggressive` (激进)

---

## 七、推荐输出结构

### 7.1 RecommendationItem (单条推荐)

| 字段 | 来源 | 说明 |
|------|------|------|
| schoolName | s.name | 学校名 |
| province | s.province | 省份 |
| city | s.city | 城市 |
| tier | s.tier (归一化) | 学校层次 |
| is985 / is211 / isDoubleFirst | s.is_985/211/double_first | 标签 |
| collegeName | c.name | 学院名 |
| programCode | p.program_code | 专业代码 |
| programName | p.program_name | 专业名 |
| studyMode / degreeType | p.study_mode/degree_type | 学习方式/学位 |
| **effectiveScore** | 计算 | **有效复试线 (算法核心)** |
| **scoreGap** | 计算 | **预估分-有效复试线** |
| scoreBasis | 计算 | 分数计算依据说明 |
| scoreLine | ascore.score_line | 原始复试线 |
| planCount | ap.total_plan | 招生计划 |
| minAdmittedScore | ar.min_admitted_score | 录取最低分 |
| avgAdmittedScore | ar.avg_admitted_score | 录取平均分 |
| completenessLevel | pydq.completeness_level | 数据完整度 |
| **tierLabel** | 计算 | **推荐档位** |
| warnings | 计算 | **风险标记列表** |
| warningLevel | 计算 | 风险等级 (extreme/normal) |

### 7.2 RecommendationResult (推荐结果)

| 字段 | 说明 |
|------|------|
| steady | 重点稳妥列表 (每个 ≤15条) |
| focus | 重点关注列表 (每个 ≤15条) |
| reach | 略冲列表 (每个 ≤15条) |
| notRecommended | 不推荐列表 |
| insufficient | 数据不足列表 |
| overflow | 溢出列表 (分数远高于复试线) |
| totalCandidates | 候选总数 |

---

## 八、当前数据覆盖 (2026-05-22)

| 表 | MANUAL_VERIFIED | OFFICIAL_VERIFIED | 总计可用 |
|------|:--:|:--:|:--:|
| admission_score | 174 | 0 | **174** |
| admission_plan | 188 | 1,802 | **1,990** |
| admission_result | 174 | 0 | **174** |

**数据来源分布:**
- `OFFICIAL_VERIFIED`: 研招网官方目录 (admission_plan 1802 条，只有招生计划)
- `MANUAL_VERIFIED`: N诺第三方数据经审核通过 (174 条复试线 + 188 条计划 + 174 条录取分)

**覆盖学校:** 150 所 (N诺数据) + 研招网目录学校

**数据缺口:**
- 大部分专业的 `admission_score` 和 `admission_result` 仍为 MISSING/THIRD_PARTY 状态
- `max_admitted_score` 字段大部分为空 (N诺无此字段，只有 min/avg/range)
- `program_year_data_quality` 表未自动更新 (需要触发器或定时任务)
- 多年度历史数据缺失 (当前只有 2025 年)
