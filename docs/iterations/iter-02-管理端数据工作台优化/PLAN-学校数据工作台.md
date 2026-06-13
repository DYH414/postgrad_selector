# 学校数据工作台实施计划

> 面向管理端前端优化第一阶段。目标是把当前按表分散的 RuoYi CRUD，升级为按“学校 -> 学院 -> 专业方向 -> 年份数据”组织的统一数据工作台。

## 1. 背景

当前管理端已经具备学校、学院、专业、复试线、招生计划、拟录取、数据完整度、采集任务、审核中心等基础 CRUD 能力，但主要问题是：

- 数据管理入口按数据库表拆分，管理员无法围绕一个学校统一查看和维护数据。
- 学校详情页已有学院、专业、统计信息，但只是弹窗级别，不适合作为日常工作台。
- 审核中心已有待审核列表和通过/驳回能力，但没有和学校、专业方向、数据完整度形成联动。
- 数据可视化能力已有 ECharts 基础，但尚未用于 postgrad 管理端。

第一阶段不删除旧 CRUD。旧 CRUD 保留为“原始数据维护 / 高级维护”兜底，新建“学校数据工作台”作为主入口。

## 2. 当前可复用基础

### 前端

- `ruoyi-ui/src/views/postgrad/school/index.vue`
  - 已有学校列表、学校详情弹窗、学院列表、专业列表、统计卡片。
- `ruoyi-ui/src/views/postgrad/crud/index.vue`
  - 已有通用 CRUD 表格、查询、表单、远程下拉。
- `ruoyi-ui/src/views/postgrad/crud/moduleConfig.js`
  - 已有 `college`、`program`、`admissionScore`、`admissionPlan`、`admissionResult`、`programYearDataQuality`、`dataCollectionTask` 等模块配置。
- `ruoyi-ui/src/views/postgrad/review/index.vue`
  - 已有审核列表、统计、通过、驳回、跳过、批量通过、一键通过目录数据。
- `ruoyi-ui/src/views/dashboard/*.vue`
  - 已有 ECharts 用法示例，可复用图表初始化和 resize 方式。

### 后端

- `GET /postgrad/school/list`
- `GET /postgrad/school/{id}`
- `GET /postgrad/school/{id}/overview`
- `GET /postgrad/review/list`
- `GET /postgrad/review/stats`
- `POST /postgrad/review/{id}/approve`
- `POST /postgrad/review/{id}/reject`
- `POST /postgrad/review/batch-approve`
- 各 postgrad CRUD 接口：`/postgrad/{module}/list`、`/postgrad/{module}`。

## 3. 第一版目标

第一版完成后，管理员应能在一个页面内完成：

- 按学校搜索和筛选数据。
- 查看学校下所有学院和专业方向。
- 查看每个专业方向近年数据覆盖情况。
- 查看复试线、招生计划、拟录取、数据来源、完整度是否齐全。
- 查看学校级数据完整度分布和覆盖率图表。
- 选中专业方向后查看详情和历年趋势。
- 从工作台跳转到原始 CRUD 或审核中心处理具体数据。

第一版暂不强求：

- 不做复杂内联编辑。
- 不重构所有 CRUD。
- 不删除旧菜单。
- 不把审核中心完全重做。

## 4. 信息架构

目标页面：`学校数据工作台`

推荐布局：

```text
学校数据工作台
├─ 顶部筛选区
│  ├─ 学校 / 学院 / 专业方向搜索
│  ├─ 省份
│  ├─ 学校层次
│  ├─ 年份
│  ├─ 408
│  └─ 完整度 A/B/C/D
│
├─ KPI 总览
│  ├─ 学校数
│  ├─ 学院数
│  ├─ 专业方向数
│  ├─ 408 专业数
│  ├─ A 级完整度
│  ├─ 待审核
│  └─ 缺失任务
│
├─ 左侧学校列表
│  ├─ 学校名称
│  ├─ 地区
│  ├─ 学校层次
│  ├─ 专业数
│  ├─ 完整度
│  └─ 待处理数量
│
├─ 中间学院/专业方向矩阵
│  ├─ 学院分组
│  ├─ 专业代码
│  ├─ 专业名称
│  ├─ 研究方向
│  ├─ 408 标记
│  ├─ 近年数据覆盖
│  └─ 完整度状态
│
└─ 右侧详情面板
   ├─ 基础信息
   ├─ 复试线趋势
   ├─ 招生计划
   ├─ 拟录取
   ├─ 数据来源
   ├─ 数据完整度
   └─ 审核入口
```

## 5. 阶段计划

### Phase 0：需求冻结与接口盘点

**状态：已完成**

**阶段产物**

- `docs/iterations/iter-02-管理端数据工作台优化/PHASE0-需求冻结与接口盘点.md`

**目标**

明确第一版页面只做“统一查看 + 快速跳转维护”，不做重型内联编辑。

**工作内容**

- 确认工作台菜单位置。
- 确认旧 CRUD 保留为高级维护入口。
- 确认第一版需要展示的年份范围，建议默认近三年：`2024`、`2025`、`2026`。
- 确认完整度采用全局统一口径：`program_year_data_quality` 优先，无记录时按 score/plan/result 动态计算。
- 确认工作台第一版只读取审核数量，不直接审批。

**产出**

- 本计划文档确认通过。
- 页面范围不再新增“内联编辑、完整审核中心重构、采集调度重构”等需求。

**验收方式**

- 人工评审本文档。
- 明确第一版验收口径：能统一看、能定位问题、能跳转处理。

---

### Phase 1：菜单与前端页面骨架

**状态：已完成**

**阶段产物**

- `docs/iterations/iter-02-管理端数据工作台优化/PHASE1-菜单与前端页面骨架.md`
- `ruoyi-ui/src/views/postgrad/workspace/index.vue`
- `ruoyi-ui/src/api/postgrad/workspace.js`
- `ruoyi-ui/test/workspace.phase1.test.mjs`
- `sql/postgrad_workspace_menu.sql`

**目标**

新增学校数据工作台页面，先完成页面结构、筛选区、占位状态和基础交互。

**计划改动**

- 新增：`ruoyi-ui/src/views/postgrad/workspace/index.vue`
- 新增：`ruoyi-ui/src/api/postgrad/workspace.js`
- 新增：`sql/postgrad_workspace_menu.sql`

**页面能力**

- 顶部筛选区。
- KPI 总览区。
- 左侧学校列表。
- 中间学院/专业方向矩阵。
- 右侧详情面板。
- 空状态、加载态、错误提示。

**验收方式**

- 管理端菜单能看到“学校数据工作台”。
- 点击菜单可进入新页面，不影响原学校管理、审核中心、CRUD 页面。
- 页面在无接口或接口失败时有可读空状态，不白屏。
- 浏览器控制台无明显 Vue 渲染错误。
- `npm run build:prod` 能通过。

---

### Phase 2：工作台聚合接口

**状态：已完成，待浏览器联调确认**

**阶段产物**

- `docs/iterations/iter-02-管理端数据工作台优化/PHASE2-工作台聚合接口.md`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/WorkspaceController.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IWorkspaceService.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/WorkspaceServiceImpl.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/WorkspaceMapper.java`
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/WorkspaceMapper.xml`
- `ruoyi-postgrad/test/workspace.phase2.test.mjs`

**目标**

后端提供前端工作台直接可用的数据结构，避免前端拼接大量 CRUD 响应。

**计划改动**

- 新增：`ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/WorkspaceController.java`
- 新增或扩展：`ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IWorkspaceService.java`
- 新增或扩展：`ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/WorkspaceServiceImpl.java`
- 新增或扩展：`ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/WorkspaceMapper.java`
- 新增：`ruoyi-postgrad/src/main/resources/mapper/postgrad/WorkspaceMapper.xml`

**接口设计**

```text
GET /postgrad/workspace/stats
GET /postgrad/workspace/schools
GET /postgrad/workspace/school/{id}
```

**`/postgrad/workspace/stats` 返回内容**

- 学校数
- 学院数
- 专业方向数
- 408 专业数
- 完整度 A/B/C/D 分布
- 待审核数量
- 缺失任务数量
- 复试线/招生计划/拟录取覆盖率

**`/postgrad/workspace/schools` 返回内容**

- 学校 ID
- 学校名称
- 省份
- 城市
- 学校层次
- 学院数
- 专业方向数
- 408 专业数
- 完整度摘要
- 待审核数
- 缺失任务数

**`/postgrad/workspace/school/{id}` 返回内容**

- 学校基础信息
- 学院列表
- 专业方向列表
- 每个专业方向近年数据状态
- 数据完整度
- 缺失字段
- 待审核数量
- 采集任务数量

**验收方式**

- 登录后台后，请求三个接口返回 `code=200`。
- 任意选择一个有专业数据的学校，`/postgrad/workspace/school/{id}` 能返回学院和专业方向。
- 每个专业方向至少能判断：
  - 是否有复试线
  - 是否有招生计划
  - 是否有拟录取
  - 完整度等级
- SQL 不只 `coalesce(q.completeness_level, 'C')`，无质量记录时必须动态计算完整度。
- 后端单元测试或接口级测试覆盖：
  - 有 `program_year_data_quality` 记录时使用记录值。
  - 无记录但 score/plan/result 齐全时计算为 A。
  - 无记录且缺 result 时不能误判为 A。

---

### Phase 3：真实数据接入与学校选择体验

**状态：已完成**

当前前端已经接入 Phase 2 接口：全局统计、学校列表、选中学校后的专业方向矩阵和年份完整度均走 `/postgrad/workspace/*`。已补充学校摘要、失败态提示和上下文跳转参数。

**阶段产物**

- `docs/iterations/iter-02-管理端数据工作台优化/PHASE3-4-真实数据接入与矩阵体验.md`

**目标**

前端接入真实接口，实现学校列表、筛选、选中学校加载工作台数据。

**计划改动**

- 修改：`ruoyi-ui/src/views/postgrad/workspace/index.vue`
- 修改：`ruoyi-ui/src/api/postgrad/workspace.js`

**页面能力**

- 默认加载全局统计。
- 默认加载学校列表。
- 点击学校后加载学校工作台详情。
- 支持按学校名称、省份、层次、408、完整度过滤。
- 学校列表显示完整度和待处理数量。

**验收方式**

- 登录管理端，进入工作台后能看到真实学校列表。
- 点击一所学校后，中间区域展示该校学院和专业方向。
- 筛选学校名称能正确收敛结果。
- 切换学校时右侧详情能同步刷新。
- 网络较慢时显示 loading，失败时显示错误提示。

---

### Phase 4：学院/专业方向矩阵

**状态：已完成基础版**

当前已具备按学校查看专业方向、按学院过滤、近三年 A/B/C/D 年份状态、score/plan/result 三件套标识和右侧专业详情联动。后续可在 Phase 6 接入图表，把矩阵里的状态进一步可视化。

**目标**

把一个学校下的学院、专业方向、年份数据状态组织成可扫描的矩阵。

**页面能力**

- 按学院分组展示专业方向。
- 专业方向行展示：
  - 专业代码
  - 专业名称
  - 研究方向
  - 学习方式
  - 学位类型
  - 408 标记
  - 近三年数据状态
  - 完整度等级
- 年份状态用颜色表达：
  - 绿色：score/plan/result 齐全
  - 蓝色：核心数据基本齐全
  - 橙色：缺关键字段
  - 红色：严重缺失或冲突
- 支持点击专业方向，右侧详情面板刷新。

**验收方式**

- 一个学校下多个学院能按学院折叠或分组展示。
- 专业方向过多时页面仍能滚动查看，表头或关键信息不丢失。
- 408 专业有清晰标记。
- A/B/C/D 完整度颜色与全局规则一致。
- 点击任意专业方向，右侧展示对应数据，不出现错位。

---

### Phase 5：右侧专业详情与维护跳转

**目标**

选中专业方向后，管理员能快速看到这个专业的关键数据和处理入口。

**页面能力**

- 基础信息：学校、学院、专业代码、专业名称、研究方向、考试科目。
- 历年复试线：总分线、单科线。
- 招生计划：总计划、推免、统考名额。
- 拟录取：录取人数、最低分、平均分、最高分、调剂情况。
- 数据来源：来源类型、来源标题、URL、可信状态。
- 完整度：A/B/C/D、缺失字段、最后检查时间。
- 维护入口：
  - 编辑专业基础信息
  - 维护复试线
  - 维护招生计划
  - 维护拟录取
  - 查看审核记录
  - 创建采集任务

**验收方式**

- 选中专业方向后，右侧信息能和中间选中行一致。
- 维护入口能跳转到对应 CRUD 页面，并尽可能带上 `schoolId`、`collegeId`、`programId`、`year` 查询条件。
- 没有某类数据时显示缺失原因和维护入口，而不是空白。
- 来源 URL 可点击打开新页面。

---

### Phase 6：数据可视化

**目标**

增加能帮助管理员发现数据问题的可视化，而不是装饰性图表。

**计划组件**

- 完整度分布环图：A/B/C/D。
- 数据覆盖率柱图：复试线、招生计划、拟录取、来源。
- 复试线趋势图：选中专业方向后展示近年复试线趋势。
- 待审核/缺失任务小指标：用于提示后续处理。

**验收方式**

- 图表使用真实接口数据。
- 切换学校后图表同步刷新。
- 切换专业方向后复试线趋势同步刷新。
- 图表容器在侧边栏收起、窗口缩放后能自适应，不重叠、不空白。
- 图表颜色与完整度状态一致，不使用杂乱主题色。

---

### Phase 7：审核中心联动

**目标**

学校数据工作台先和审核中心打通，不在第一版内重做审核中心。

**页面能力**

- 学校列表显示待审核数量。
- 专业方向详情显示该专业相关待审核数量。
- 点击待审核数量跳转到审核中心。
- 跳转时携带筛选参数：

```text
schoolName
programCode
year
matchStatus
status=pending
```

**验收方式**

- 从学校工作台点击“待审核”能进入审核中心。
- 审核中心能根据 URL query 或页面参数自动筛选对应数据。
- 审核通过或驳回后，回到工作台刷新能看到待审核数量变化。

---

### Phase 8：视觉统一与可用性打磨

**目标**

让管理端从默认 RuoYi CRUD 风格，升级为更符合教育数据平台主题的运营工作台。

**视觉规范**

- 背景：`#F8FAFC`
- 主色：`#1E40AF`
- 辅助蓝：`#3B82F6`
- 警告色：`#F59E0B`
- 完整：绿色
- 缺失/冲突：红色
- 卡片圆角不超过 `8px`
- 管理端保持信息密度，不做营销式 hero。

**验收方式**

- 页面主视觉和参考图方向一致。
- 没有卡片套卡片造成的层级混乱。
- 文本在 1366x768 和 1920x1080 下不溢出。
- 表格、标签、按钮、图表颜色含义一致。
- 与现有 RuoYi 侧边栏、顶部栏不冲突。

---

### Phase 9：联调、测试与交付

**目标**

完成从登录、进入工作台、筛选学校、查看专业、跳转维护、跳转审核的闭环验证。

**验证清单**

- 后端编译通过。
- 前端构建通过。
- 管理端登录成功。
- 菜单可见。
- 工作台可进入。
- 学校列表可加载。
- 学校详情可加载。
- 专业方向可选择。
- 图表可渲染。
- 维护跳转可用。
- 审核中心联动可用。

**建议命令**

```powershell
rtk mvn -pl ruoyi-admin -am test
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

**浏览器验收**

- 使用管理端账号登录。
- 访问“学校数据工作台”。
- 搜索一个真实学校。
- 选择学校。
- 选择一个专业方向。
- 查看完整度、年份状态、复试线趋势。
- 点击维护入口。
- 点击待审核入口。

## 6. 第一版完成标准

以下条件全部满足，视为第一版完成：

- 新增“学校数据工作台”菜单。
- 能按学校统一查看学院和专业方向。
- 能看到专业方向近年 score/plan/result 覆盖状态。
- 能看到 A/B/C/D 完整度。
- 能看到至少两个真实数据图表。
- 能从工作台跳转到原始 CRUD 维护数据。
- 能从工作台跳转到审核中心处理待审核数据。
- 原有学校管理、审核中心、CRUD 页面不被破坏。
- 前端构建通过。
- 后端关键接口测试通过。

## 7. 风险与处理

### 风险 1：聚合接口 SQL 复杂

处理方式：

- 第一版只聚合近三年数据。
- 先按学校维度聚合，不做全库复杂大宽表。
- 必要时分成 stats、schools、school workspace 三个接口。

### 风险 2：完整度口径不一致

处理方式：

- 后端统一封装完整度计算。
- SQL 查询不得只使用 `coalesce(q.completeness_level, 'C')`。
- 无质量记录时按 score/plan/result 动态计算。

### 风险 3：前端页面过重

处理方式：

- 第一版不做内联编辑。
- 右侧详情只展示和跳转。
- 学校切换后按需加载详情。

### 风险 4：旧 CRUD 菜单仍然很多

处理方式：

- 第一版不删旧菜单。
- 第二阶段再做菜单分组：工作台、审核中心、原始数据维护、采集任务。

### 风险 5：中文编码问题

处理方式：

- 修改前确认相关 Vue 文件编码。
- 新增文件统一使用 UTF-8。
- 避免在乱码文件里做大面积文案重写。

## 8. 后续阶段

学校数据工作台完成后，再进入：

- 审核中心升级：审核队列、字段差异对比、正式库已有数据对比、批量审批。
- 数据录入体验优化：抽屉式录入、字段校验、来源绑定。
- 采集任务中心优化：按学校和缺失字段创建任务。
- 原始 CRUD 菜单收敛：保留高级维护入口，减少管理员日常菜单负担。
