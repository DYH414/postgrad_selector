# Phase 7：审核中心联动完成记录

## 1. 阶段结论

Phase 7 已完成基础版。

学校数据工作台已经具备跳转审核中心的入口；审核中心现在会读取 URL query，并自动把 query 映射到审核列表筛选参数，解决“从工作台能跳过去，但审核中心仍显示全量待审核数据”的断点。

本阶段不重做审核中心，只完成第一版要求的联动闭环。

## 2. 本次改动

### 审核中心读取 URL query

修改文件：

- `ruoyi-ui/src/views/postgrad/review/index.vue`

新增能力：

- 页面创建时先执行 `applyRouteQuery()`，再请求列表。
- 监听 `$route.query`，路由参数变化时重新应用筛选并刷新列表。
- 支持从 URL query 映射以下字段：
  - `schoolName`
  - `programCode`
  - `year`
  - `status`
  - `confidence`
  - `sourceType`
  - `matchStatus`
  - `is408`

### 工作台跳转参数保持不变

复核文件：

- `ruoyi-ui/src/views/postgrad/workspace/index.vue`

当前 `jumpReview()` 会跳转到：

```text
/postgrad/review?schoolName=...&programCode=...&year=...&status=pending
```

审核中心现在可以消费这些参数。

### 新增结构验收测试

新增文件：

- `ruoyi-ui/test/review.query-link.test.mjs`

测试覆盖：

- 工作台存在 `jumpReview()`。
- 工作台跳转路径为 `/postgrad/review`。
- 工作台携带 `schoolName`、`programCode`、`status=pending`。
- 审核中心存在 `applyRouteQuery()`。
- 审核中心读取 `$route.query`。
- 审核中心支持 `schoolName`、`programCode`、`matchStatus`、`is408` 等筛选字段。

## 3. 验收记录

已执行：

```powershell
rtk node test/review.query-link.test.mjs
```

结果：

```text
review query link checks passed
```

已执行：

```powershell
rtk npm run build:prod
```

结果：

- 前端生产构建通过。
- 构建过程仍有 RuoYi 既有资源体积 warning，不影响本阶段功能。

已执行：

```powershell
rtk mvn -pl ruoyi-admin -am -DskipTests compile
```

结果：

- 后端编译通过。
- 仍有既有 `Jackson2JsonMessageConverter` 过期 warning，不影响本阶段功能。

## 4. 可上线验收标准

本阶段满足以下上线标准：

- 从学校数据工作台点击待审核入口能进入审核中心。
- 审核中心能根据 URL query 自动筛选学校、专业代码、年份、状态。
- 审核中心自身刷新、直接打开带 query 的 URL 时，也能保留筛选。
- 原审核中心的审批、驳回、批量审批、一键通过目录数据逻辑未改动。
- 原工作台跳转参数未改动，只补齐审核中心消费能力。
- 前端构建通过。
- 后端编译通过。

## 5. 剩余事项

以下内容不属于 Phase 7 基础版，建议放到后续阶段：

- 审核中心升级为独立审核工作台，展示字段 diff、来源证据、冲突等级。
- 审核通过或驳回后，工作台页面自动感知并局部刷新待审核数量。
- 浏览器端完整 E2E：登录管理端、进入工作台、选择学校、跳转审核、执行审批、返回工作台复核数量变化。
- AI 批量数据采集 Agent 与人工审核中心的证据链打通。
