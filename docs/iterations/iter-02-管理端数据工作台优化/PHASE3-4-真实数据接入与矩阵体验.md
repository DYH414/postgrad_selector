# Phase 3/4：真实数据接入与矩阵体验

## 1. 阶段结论

Phase 3 已完成，Phase 4 已完成基础版。

学校数据工作台现在不再只是页面骨架，已经可以通过 `/postgrad/workspace/*` 接口加载真实数据，并在页面内完成“学校 -> 学院 -> 专业方向 -> 年份完整度”的钻取。

## 2. 已完成能力

### 真实数据接入

- 全局 KPI 调用 `GET /postgrad/workspace/stats`。
- 学校列表调用 `GET /postgrad/workspace/schools`。
- 选中学校后调用 `GET /postgrad/workspace/school/{id}`。
- 接口失败时保留学校列表兜底，不白屏。
- 统计接口失败文案改为真实失败态，不再误导为“待接入”。

### 学校选择体验

- 左侧学校列表显示地区、层次、专业数、待审核数。
- 选中学校后显示学校摘要条。
- 摘要条展示学院数、专业数、408 专业数、待审数。
- 切换学校时自动重置学院筛选和右侧选中专业。

### 学院/专业方向矩阵

- 中间矩阵展示学院、专业代码、专业方向、408 标记。
- 新增 score/plan/result 三件套状态：
  - `线`：复试线
  - `计`：招生计划
  - `录`：拟录取
- 新增学院筛选。
- 新增 A/B/C/D 完整度图例。
- 年份状态按 A/B/C/D 着色。
- 切换学院后，右侧详情自动选中当前学院的第一个专业方向。

### 专业方向详情

- 展示当前年份复试线、招生计划、拟录取最低分。
- 展示待审核数、缺失任务数、缺失字段。
- 维护入口跳转时携带当前上下文参数：
  - `schoolId`
  - `collegeId`
  - `programId`
  - `year`

## 3. 接口验收

已使用管理端 `admin` 登录并获取 token，实际请求三个工作台接口。

验收结果：

```text
GET /postgrad/workspace/stats?year=2026
HTTP 200, code=200

GET /postgrad/workspace/schools?year=2026
HTTP 200, code=200, dataLength=80

GET /postgrad/workspace/school/177?year=2026
HTTP 200, code=200
school=中国科学院大学
colleges=45
programs=102
programYears=306
```

示例专业方向：

```text
programCode=085400
programName=电子信息
is408=true
completenessLevel=D
hasScore=0
hasPlan=0
hasResult=0
```

## 4. 构建验收

已执行：

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui\test\workspace.phase1.test.mjs
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-postgrad\test\workspace.phase2.test.mjs
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

结果：

- 前端结构测试通过。
- 后端工作台结构测试通过。
- 前端生产构建通过。
- 构建仅有 RuoYi 既有资源体积 warning。

## 5. 当前限制

- 当前阶段还没有接入真实 ECharts 图表。
- 右侧详情里的趋势图仍是占位，留到 Phase 6。
- 维护跳转已经带 query，但各 CRUD 页面是否消费这些 query 需要后续单独打通。
- 审核中心能跳转，但自动按 URL query 筛选留到 Phase 7。

## 6. 下一阶段建议

优先进入 Phase 6 数据可视化：

- 完整度 A/B/C/D 分布图。
- score/plan/result 覆盖率柱图。
- 选中专业方向的近年复试线趋势图。

再进入 Phase 7 审核中心联动：

- 审核中心读取 URL query 自动筛选。
- 工作台待审入口跳转到指定学校、专业、年份。
