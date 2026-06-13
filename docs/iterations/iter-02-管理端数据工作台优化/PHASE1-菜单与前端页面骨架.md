# Phase 1：菜单与前端页面骨架

## 1. 阶段结论

Phase 1 已完成。当前已经新增学校数据工作台的前端入口骨架，包括：

- 工作台页面。
- 工作台前端 API 封装。
- 独立菜单 SQL。
- Phase 1 结构测试。

本阶段没有改动旧学校管理、通用 CRUD、审核中心业务逻辑。

## 2. 已完成文件

### 新增页面

```text
ruoyi-ui/src/views/postgrad/workspace/index.vue
```

页面包含：

- 顶部标题和操作区。
- 筛选区：关键词、省份、层次、年份、408、完整度。
- KPI 总览区。
- 左侧学校列表。
- 中间学院 / 专业方向矩阵区域。
- 右侧专业方向详情区域。
- 加载态、空状态、接口待接入提示。

### 新增 API

```text
ruoyi-ui/src/api/postgrad/workspace.js
```

预留接口：

```text
listWorkspaceStats(params)
listWorkspaceSchools(params)
getSchoolWorkspace(id, params)
```

对应后续 Phase 2 接口：

```text
GET /postgrad/workspace/stats
GET /postgrad/workspace/schools
GET /postgrad/school/{id}/workspace
```

### 新增菜单 SQL

```text
sql/postgrad_workspace_menu.sql
```

新增菜单：

```text
menu_name: 学校数据工作台
parent_id: 2000
order_num: 1
path: workspace
component: postgrad/workspace/index
perms: postgrad:workspace:view
icon: dashboard
```

### 新增结构测试

```text
ruoyi-ui/test/workspace.phase1.test.mjs
```

验证：

- 页面文件存在。
- API 文件存在。
- 菜单 SQL 存在。
- 页面包含 `学校数据工作台` 和 `workspace-shell`。
- API 暴露三个工作台方法。
- 菜单 SQL 指向 `postgrad/workspace/index`。
- 菜单 SQL 定义 `postgrad:workspace:view`。

## 3. 当前页面行为

由于 Phase 2 聚合接口还未实现，页面当前采用以下策略：

- 优先请求未来的工作台聚合接口。
- 如果 `/postgrad/workspace/stats` 不存在，显示“聚合统计接口待接入”提示。
- 如果 `/postgrad/workspace/schools` 不存在，兜底请求已有 `GET /postgrad/school/list`。
- 如果 `/postgrad/school/{id}/workspace` 不存在，中间矩阵显示“学校工作台聚合接口待接入”空状态。

这样做的目标是：

- Phase 1 页面不白屏。
- 后续 Phase 2 接口接入后无需重做页面结构。
- 管理员可以先看到工作台入口和整体布局。

## 4. 验收结果

### 结构测试

命令：

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui\test\workspace.phase1.test.mjs
```

结果：

```text
Phase 1 workspace structure test passed
```

### 前端构建

命令：

```powershell
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

结果：

```text
DONE  Build complete. The dist directory is ready to be deployed.
```

构建仍有两个资源体积 warning，属于当前 RuoYi 项目既有体积提示，不是本阶段新增错误：

- asset size limit
- entrypoint size limit

## 5. 下一阶段进入条件

Phase 2 可以开始。

Phase 2 重点不再继续堆前端静态结构，而是实现后端聚合接口：

```text
GET /postgrad/workspace/stats
GET /postgrad/workspace/schools
GET /postgrad/school/{id}/workspace
```

其中完整度计算必须继续遵守 Phase 0 冻结口径：

- 有 `program_year_data_quality` 记录时优先使用记录值。
- 无记录时按 score / plan / result 动态计算。
- 不允许退回 `coalesce(q.completeness_level, 'C')`。

