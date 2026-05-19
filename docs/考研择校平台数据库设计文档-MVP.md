# 考研择校平台数据库设计文档（MVP版）

版本：1.0  
日期：2026-05-18  
适用阶段：单人开发 MVP  
数据库：MySQL 8.x

## 1. 设计目标

本数据库用于支撑考研择校决策平台的核心能力。平台长期目标是面向全专业、全国性考研择校；MVP 阶段以 408 计算机相关方向验证数据导入、候选召回、数据采集、审核入库和动态推荐闭环。

- 从研招网等来源导入学校、学院、专业、考试科目基础目录。
- 支持按考试科目、考试类型、地区、专业代码、学习方式、学校层次召回候选学校。
- 支持用 `exam_type` 和 `score_scale` 区分 500 分制、管理类 300 分制、法硕、医学、艺术等不同考试类型。
- 存储复试线、招生计划、拟录取统计结果。
- 记录数据来源、可信度和采集任务。
- 支持用户画像、动态推荐、历史推荐报告。
- 支持数据缺口识别，避免盲目全量采集。

本版本遵循一个原则：

```text
先跑通择校闭环，后续再做更细的数据工程拆分。
```

因此，本版本不单独拆 `raw_document`、`extract_task`、`review_record`、`source_compliance_record` 等复杂表，而是先用 `data_source`、`staging`、`data_collection_task` 支撑采集与审核闭环。

注意：`408` 是第一阶段业务标签，不是系统长期边界。长期筛选应以 `program_subject`、`exam_type`、`score_scale`、专业代码和专业类别体系共同判断。

## 2. 核心数据策略

系统采用：

```text
基础目录全量导入 + 深度数据按需采集
```

流程如下：

```text
研招网全量专业目录
-> 批量导入 school / college / program / subject / program_subject
-> 用户填写画像
-> SQL 召回候选学校
-> 检查 program_year_data_quality
-> 生成 data_collection_task
-> Agent/人工定向采集
-> 入 staging，保留字段映射结果、raw_text 和 extract_json
-> 人工核验
-> 写入 admission_score / admission_plan / admission_result
-> 推荐引擎重新计算
```

## 3. 表分组

本版本共 18 张表。

### 3.1 基础数据层

| 表名 | 作用 |
|---|---|
| `school` | 学校基础信息 |
| `college` | 学院信息 |
| `subject` | 初试科目字典 |
| `program` | 学校-学院下的专业方向 |
| `program_subject` | 专业与考试科目多对多关联 |

### 3.2 招生数据层

| 表名 | 作用 |
|---|---|
| `admission_score` | 历年复试线 |
| `admission_plan` | 历年招生计划和复试人数 |
| `admission_result` | 历年拟录取统计结果 |
| `data_source` | 数据来源证据 |

### 3.3 采集审核层

| 表名 | 作用 |
|---|---|
| `staging` | Agent/人工抽取结果暂存表，保存 `extract_json` 和审核状态，审核后才入主库 |
| `data_collection_task` | 定向采集任务队列 |
| `program_year_data_quality` | 专业-年份数据完整度表 |

### 3.4 用户与推荐层

| 表名 | 作用 |
|---|---|
| `app_user` | 用户账号 |
| `user_profile` | 用户画像 |
| `user_target_program` | 用户目标专业代码 |
| `user_favorite_program` | 用户收藏的专业方向 |
| `recommendation_rule` | 推荐规则配置 |
| `recommendation_log` | 推荐结果快照 |

## 4. 核心关系

```text
school 1 - n college
college 1 - n program
program n - n subject，通过 program_subject
program 1 - n admission_score
program 1 - n admission_plan
program 1 - n admission_result
admission_* n - 1 data_source
program + year 1 - 1 program_year_data_quality
program + year + task_type 1 - n data_collection_task
app_user 1 - 1 user_profile
app_user n - n target program code，通过 user_target_program
app_user n - n program，通过 user_favorite_program
app_user 1 - n recommendation_log
```

## 5. 关键设计说明

### 5.1 为什么保留 `program_subject`

408、数学一、英语一、管理类联考、法硕联考等初试科目不能用字符串字段模糊匹配。`program_subject` 能支持标准 JOIN 查询。

例如：

```sql
SELECT p.*
FROM program p
JOIN program_subject ps ON ps.program_id = p.id
JOIN subject s ON s.id = ps.subject_id
WHERE s.code = '408';
```

`program.is_408` 只作为第一阶段快捷标签和索引优化字段，不能替代 `program_subject` 关系。后续扩展到 MPAcc、法硕、医学等专业时，系统应通过专业代码、考试科目组合、`exam_type` 和 `score_scale` 进行筛选。

### 5.2 为什么暂不拆 `program_exam_scheme`

长期看，考试科目应支持年份变化。但 MVP 第一阶段主要面向当前 408 样板场景，先用 `program_subject` 降低复杂度。后续如果需要记录某专业 2025 自命题、2026 改 408，或 MPAcc/法硕等专业不同年份考试方案变化，再升级为 `program_exam_scheme`。

### 5.3 为什么新增 `data_collection_task`

系统的数据采集不是全量乱爬，而是定向补缺。`data_collection_task` 用来记录：

- 哪个学校/专业/年份缺数据
- 缺什么类型的数据
- 任务优先级
- 任务当前状态
- 采集来源提示
- 是否已经进入 staging

这是后续 Agent 或人工工作台的任务入口。

### 5.4 为什么新增 `program_year_data_quality`

每个学校专业每年可能缺不同数据。推荐系统不能装作数据完整，必须明确标记：

- 是否有复试线
- 是否有招生计划
- 是否有拟录取统计
- 是否有官网来源
- 数据完整度等级

该表用于生成数据缺口列表，也用于推荐时显示“数据不足”。

### 5.5 为什么 `staging` 暂时不拆

长期可以拆成原始文档、抽取任务、抽取记录、人工审核记录。MVP 阶段为降低维护成本，先用一张 `staging` 存储抽取候选数据、原始识别文本、Agent 原始结构化结果和审核状态。

`staging` 是单人开发阶段的核心保险机制：所有 Codex/Gemini/脚本/人工录入产生的数据先进入暂存表，不直接写入 `admission_score`、`admission_plan`、`admission_result` 等主库表。只有人工审核为 `approved` 后，才允许写入主库。

关键字段设计：

| 字段 | 作用 |
|---|---|
| `source_type` | 标记来源类型，如研招网、学校官网 HTML、PDF、图片、第三方或人工录入 |
| `source_url` | 原始页面、PDF、图片或截图 URL |
| `raw_text` | OCR、PDF 解析或 HTML 抽取得到的原始文本 |
| `extract_json` | Agent/Gemini 原始结构化输出，完整保留识别结果、rows、warnings、confidence 等信息 |
| `confidence` | Agent 或程序给出的抽取置信度，辅助决定抽查还是人工校验 |
| `status` | 审核状态，支持 `seed`、`pending`、`approved`、`rejected`、`skipped` |
| `matched_program_id` | 审核或自动匹配后指向主库 `program`，减少同名学校/学院误入库 |
| `error_message` | 保存采集失败、字段校验失败或 JSON 解析失败原因 |

其中 `extract_json` 不替代业务字段。业务字段用于列表、筛选和审核页快速展示；`extract_json` 用于保留 Agent 当时的完整理解，便于后续重新映射、补字段、排错和评估抽取质量。

### 5.6 为什么 SQL 表名使用 `app_user`

`user` 在数据库和权限系统中容易与关键字或系统表产生歧义，因此 SQL 中使用 `app_user`，业务上仍称为用户表。

### 5.7 为什么在 `program` 中加入 `exam_type` 和 `score_scale`

第一阶段虽然以 408 验证闭环，但系统未来要扩展到全专业全国性考研择校。不同专业的考试满分和推荐逻辑不同，例如：

- 408 计算机、法硕、医学等多为 500 分制。
- MPAcc、审计、图书情报等管理类联考为 300 分制。
- 艺术、医学、教育等专业的复试和录取判断规则也不同。

因此，`program` 表加入：

- `exam_type`：考试类型，如 `GENERAL_500`、`MANAGEMENT_300`、`LAW_MASTER_500` 等。
- `score_scale`：考试满分，如 500 或 300。

这样后续扩展专业时，不会把 MPAcc 的 230 分和 408 的 230 分混用，也能为不同专业配置不同推荐规则。

### 5.8 为什么收藏使用 `user_favorite_program`

考研择校场景中，用户真正关注的通常不是单独的学校，而是具体的学校、学院、专业代码、学习方式和学位类型组合。例如同一所学校下可能同时存在计算机技术、软件工程、人工智能、非全日制或联培方向，录取难度和风险并不相同。

因此 MVP 使用 `user_favorite_program` 记录用户收藏的专业方向，并允许用户填写简单备注。这样后续做学校对比、推荐报告和 Agent 分析时，可以直接围绕具体报考方向展开，而不是停留在学校层面。

## 6. 推荐计算依赖字段

推荐引擎至少依赖：

- `user_profile.estimated_score`
- `user_profile.target_regions`
- `user_profile.risk_preference`
- `program.study_mode`
- `program.degree_type`
- `program.exam_type`
- `program.score_scale`
- `program.is_408`
- `admission_score.score_line`
- `admission_plan.unified_exam_quota`
- `admission_plan.retest_count`
- `admission_result.admitted_count`
- `admission_result.min_admitted_score`
- `admission_result.avg_admitted_score`
- `program_year_data_quality.completeness_level`

初始有效难度分：

```text
effective_score = max(近三年复试线中位数, 近三年拟录取最低分中位数)
```

档位判断：

```text
score_gap = 用户预估分 - effective_score

score_gap >= 20：重点稳妥
score_gap >= 5 且 < 20：重点关注
score_gap >= -10 且 < 5：略冲
score_gap < -10：不建议
关键数据缺失：数据不足
```

## 7. 数据可信度状态

统一使用以下状态：

| 状态 | 含义 |
|---|---|
| `OFFICIAL_VERIFIED` | 来自学校或学院官网，已人工核验 |
| `MANUAL_VERIFIED` | 人工录入并核验，来源有据可查 |
| `THIRD_PARTY` | 来自大学生必备网等第三方，未与官网交叉验证 |
| `OCR_PENDING` | 图片或 PDF 提取，尚未人工校对 |
| `CONFLICT` | 多来源数据冲突，需人工裁决 |
| `MISSING` | 数据缺失 |

## 8. 数据采集任务状态

`data_collection_task.status` 使用：

| 状态 | 含义 |
|---|---|
| `PENDING` | 待处理 |
| `RUNNING` | 采集中 |
| `STAGING` | 已进入暂存表 |
| `REVIEWED` | 已审核并写入主库 |
| `FAILED` | 采集失败 |
| `CANCELLED` | 已取消 |

## 9. 数据完整度等级

`program_year_data_quality.completeness_level` 使用：

| 等级 | 含义 |
|---|---|
| `A` | 复试线、招生计划、拟录取统计齐全，且有官网或人工核验来源 |
| `B` | 复试线、招生计划齐全，拟录取统计缺失或待核验 |
| `C` | 只有复试线或第三方核心线索 |
| `D` | 只有基础专业目录，无有效招生结果数据 |
| `E` | 数据严重缺失或冲突 |

## 10. MVP 查询示例

### 10.1 按考试科目召回候选专业，以 408 为例

```sql
SELECT
  s.id AS school_id,
  s.name AS school_name,
  s.province,
  s.city,
  c.id AS college_id,
  c.name AS college_name,
  p.id AS program_id,
  p.program_code,
  p.program_name,
  p.study_mode,
  p.degree_type
FROM program p
JOIN college c ON c.id = p.college_id
JOIN school s ON s.id = c.school_id
JOIN program_subject ps ON ps.program_id = p.id
JOIN subject sub ON sub.id = ps.subject_id
WHERE sub.code = '408'
  AND p.study_mode = 'full_time'
  AND p.status = 'active'
  AND s.province IN ('北京', '上海', '江苏', '浙江', '广东', '福建');
```

后续扩展到其他专业时，可以替换 `sub.code`、`program_code`、`exam_type` 或 `score_scale` 条件。例如 MPAcc 可以使用管理类联考科目和 `exam_type = 'MANAGEMENT_300'` 进行召回。

### 10.2 查询候选池数据缺口

```sql
SELECT
  p.id AS program_id,
  s.name AS school_name,
  p.program_code,
  p.program_name,
  q.year,
  q.has_score,
  q.has_plan,
  q.has_result,
  q.completeness_level
FROM program_year_data_quality q
JOIN program p ON p.id = q.program_id
JOIN college c ON c.id = p.college_id
JOIN school s ON s.id = c.school_id
WHERE p.id IN (/* candidate program ids */)
ORDER BY q.completeness_level DESC, s.name;
```

### 10.3 生成缺失数据采集任务

```sql
INSERT INTO data_collection_task
  (program_id, task_type, target_year, priority, status, created_by)
VALUES
  (?, 'RESULT', 2026, 80, 'PENDING', 'SYSTEM');
```

## 11. 后续扩展方向

当 408 样板 MVP 跑通后，可逐步扩展：

- 将 `program_subject` 升级为 `program_exam_scheme`，支持考试科目按年份变化。
- 按 `exam_type` 增加不同专业的有效难度分计算策略，例如 408、MPAcc、法硕、医学分别使用不同规则。
- 拆分 `raw_document`、`extract_task`、`extract_record_staging`，增强数据工程能力。
- 新增 `source_compliance_record`，强化商业化合规审计。
- 新增 `privacy_consent` 和 `user_data_delete_request`，强化隐私治理。
- 新增更多专业规则模板，扩展到自命题计算机、MPAcc、法硕、教育学、金融专硕、医学、新闻传播、心理学、艺术设计等方向。
