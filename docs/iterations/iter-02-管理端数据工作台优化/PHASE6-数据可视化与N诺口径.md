# Phase 6：数据可视化与 N诺口径

## 1. 阶段结论

Phase 6 已完成基础版。

学校数据工作台已从全库混合统计调整为 N诺来源优先口径，并接入真实 ECharts 图表。默认视角为 `2025` 年、`只看 408`，用于贴近当前 N诺数据维护与择校推荐数据质量检查场景。

## 2. 口径调整

### N诺来源范围

工作台统计、学校列表、学校详情均限定为 N诺来源数据：

- 复试线：`admission_score` 关联 `data_source.source_owner like 'N诺%'`
- 招生计划：`admission_plan` 关联 `data_source.source_owner like 'N诺%'`
- 拟录取：`admission_result` 关联 `data_source.source_owner like 'N诺%'`
- 年份完整度：基于同一 N诺来源的 score/plan/result 动态计算

不再把其他第三方来源混入工作台统计，避免 A 级完整度被非目标数据源稀释。

### 默认筛选

- 默认年份：`2025`
- 默认 408：`只看 408`
- 年份选项：`2023`、`2024`、`2025`

## 3. 已完成能力

### 全局可视化

- 新增 `N诺完整度分布` 图表：
  - 展示 A/B/C/D 专业方向占比。
  - 图表数据来自 `/postgrad/workspace/stats`。
- 新增 `N诺覆盖率` 图表：
  - 展示复试线、招生计划、拟录取三类数据覆盖率。
  - 覆盖率基于 N诺来源数据计算。

### 专业方向趋势

- 右侧详情新增 `近年复试线趋势` 图表。
- 选中专业方向后，按年份展示复试线总分。
- 切换学校、学院、专业方向后图表自动刷新。

### 页面联动

- 筛选条件变化后同步刷新 KPI、学校列表和图表。
- 选中学校后同步刷新学院/专业方向矩阵和右侧详情。
- 图表监听窗口 resize，避免侧栏变化或窗口缩放后空白。

## 4. 当前验收数据

后端重启后，已通过管理端页面和接口验证当前 N诺口径。

```text
GET /postgrad/workspace/stats?year=2025&is408=1
HTTP 200, code=200

schoolCount=135
collegeCount=355
programCount=853
program408Count=853
aLevelCount=687
bLevelCount=98
cLevelCount=36
dLevelCount=32
aLevelRate=0.8054
scoreReadyCount=849
planReadyCount=831
resultReadyCount=782
```

页面验收：

```text
URL: http://localhost:8081/postgrad/workspace
N诺学校数: 135
N诺 A 级完整度: 81%
默认年份: 2025
默认 408: 只看 408
canvas 数量: 3
```

三个图表均已渲染：

- `N诺完整度分布`
- `N诺覆盖率`
- `近年复试线趋势`

## 5. 验收方式

已执行：

```powershell
rtk mvn -pl ruoyi-admin -am test -DskipTests
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui\test\workspace.phase1.test.mjs
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-postgrad\test\workspace.phase2.test.mjs
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

结果：全部通过。前端生产构建仍有 RuoYi 既有资源体积 warning，不影响本阶段功能验收。

## 6. 后续缺口

Phase 6 仍保持为基础可视化版本，后续可在 Phase 7/8 中继续增强：

- 审核中心读取工作台 URL query 自动筛选。
- 待审核、缺失任务从占位 KPI 升级为可点击问题队列。
- 图表点击后联动筛选专业方向矩阵。
- 维护入口落到具体 CRUD 页面后自动带入 schoolId、collegeId、programId、year。
