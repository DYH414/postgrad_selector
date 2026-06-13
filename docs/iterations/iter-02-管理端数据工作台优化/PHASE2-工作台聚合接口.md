# Phase 2：工作台聚合接口

## 1. 阶段结论

Phase 2 已完成代码实现，当前状态为“待浏览器联调确认”。

本阶段新增工作台后端聚合接口，并把前端学校数据工作台从骨架占位推进到真实接口驱动。截图中原本显示的 `20` 是旧 `/postgrad/school/list` 兜底分页数量；新聚合 SQL 对当前库统计到约 `486` 所学校、`933` 个学院、`1958` 个专业方向。

## 2. 已完成文件

### 后端控制器

```text
ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/WorkspaceController.java
```

接口统一放在 `/postgrad/workspace` 下：

```text
GET /postgrad/workspace/stats
GET /postgrad/workspace/schools
GET /postgrad/workspace/school/{id}
```

权限统一使用：

```text
postgrad:workspace:view
```

### 后端服务与 Mapper

```text
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IWorkspaceService.java
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/WorkspaceServiceImpl.java
ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/WorkspaceMapper.java
ruoyi-postgrad/src/main/resources/mapper/postgrad/WorkspaceMapper.xml
```

服务层负责：

- 清洗筛选参数。
- 默认使用当前年份。
- 生成近三年年份范围。
- 组装学校、统计、学院、专业方向、年份完整度矩阵。

Mapper 负责：

- 全局 KPI 聚合。
- 学校列表聚合。
- 单学校工作台聚合。
- 学院统计。
- 专业方向当前年份数据。
- 专业方向近三年完整度状态。

## 3. 完整度口径

工作台完整度遵循全局统一口径：

1. 优先使用 `program_year_data_quality.completeness_level`。
2. 如果质量表没有记录，则按 `admission_score`、`admission_plan`、`admission_result` 动态计算。
3. 不能用 `coalesce(q.completeness_level, 'C')` 把未知数据粗暴归为 C。

当前动态规则：

```text
A：有复试线，且拟录取最低/最高/平均分齐全，并且计划或录取人数至少有一项。
B：有复试线，且拟录取平均/最低分或招生计划任一核心字段存在。
C：只有复试线。
D：没有复试线。
```

## 4. 前端接入

```text
ruoyi-ui/src/api/postgrad/workspace.js
ruoyi-ui/src/views/postgrad/workspace/index.vue
```

前端已接入：

- `listWorkspaceStats`
- `listWorkspaceSchools`
- `getSchoolWorkspace`

页面现在会展示：

- 学校聚合列表。
- KPI 统计。
- 专业方向矩阵。
- 近三年 A/B/C/D 年份状态。
- 当前年份复试线、招生计划、拟录取最低分。

## 5. 验收记录

已执行：

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-postgrad\test\workspace.phase2.test.mjs
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui\test\workspace.phase1.test.mjs
rtk mvn -pl ruoyi-admin -am test -DskipTests
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

结果：

- Phase 2 后端结构测试通过。
- Phase 1 前端结构测试通过。
- 后端编译通过。
- 前端生产构建通过。
- 前端构建仅有 RuoYi 既有资源体积 warning。
- MySQL 只读聚合 SQL 校验通过。

## 6. 待联调事项

- 当前运行中的后端是 IDEA 启动的 `RuoYiApplication`。若页面仍显示旧兜底数据，需要重启或等待 devtools reload 加载新增 `WorkspaceController`。
- 登录管理端后验证三个接口返回 `code=200`。
- 选择一个真实学校，确认中间矩阵展示专业方向。
- 观察浏览器 Network 中 `/postgrad/workspace/stats`、`/postgrad/workspace/schools`、`/postgrad/workspace/school/{id}` 是否都成功。

## 7. 下一阶段建议

进入 Phase 3/4 联动收尾：

- 浏览器联调真实接口。
- 调整学校筛选和选中态。
- 把学院/专业方向矩阵做成更强的扫描视图。
- 再进入 Phase 6 数据可视化。
