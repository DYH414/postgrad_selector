# Phase 7：右侧内联编辑面板实施计划

## 1. 目标

把学校数据工作台右侧从“详情展示 + 跳转维护”升级为“当前专业方向的内联维护面板”，管理员可以在当前页面直接查看、编辑和新增当前专业当前年份的数据。

## 2. 第一版范围

第一版只处理当前选中的专业方向和当前筛选年份，不做批量编辑。

支持模块：

- 专业基础信息：`program`
- 复试线：`admissionScore`
- 招生计划：`admissionPlan`
- 拟录取：`admissionResult`
- 数据质量：`programYearDataQuality`

每个模块独立加载、独立编辑、独立保存。保存成功后刷新当前学校详情、矩阵、右侧趋势图。

## 3. 架构

### 新增组件

`ruoyi-ui/src/views/postgrad/workspace/components/WorkspaceEditorPanel.vue`

职责：

- 接收 `selectedProgram`、`selectedSchool`、`year`、`programYears`。
- 渲染右侧 Tab。
- 根据 `moduleConfig.js` 生成表单字段。
- 用 `listCrud/getCrud/addCrud/updateCrud/optionselectCrud` 读写数据。
- 保存成功后向父组件发出 `saved` 事件。

### 工作台主页面

`ruoyi-ui/src/views/postgrad/workspace/index.vue`

职责保持为：

- 加载学校、专业矩阵和统计。
- 维护当前选中的学校、学院、专业方向。
- 渲染图表。
- 接收右侧面板 `saved` 事件并刷新当前学校详情。

## 4. 数据流

```text
选中专业方向
  -> WorkspaceEditorPanel 接收 selectedProgram + year
  -> program 直接 GET /postgrad/program/{id}
  -> admissionScore/list?programId=&year=
  -> admissionPlan/list?programId=&year=
  -> admissionResult/list?programId=&year=
  -> programYearDataQuality/list?programId=&year=
  -> 找到记录则 PUT，没找到则 POST
  -> 保存成功 emit saved
  -> 父组件 reload 当前学校 workspace
```

## 5. 表单规则

- 不展示 `virtual`、`readonly`、`queryOnly` 字段。
- `program` 不允许改所属学院，第一版避免误把专业挪到别的学院。
- admission 类数据自动带入 `programId` 和 `year`。
- 数据质量自动带入 `programId` 和 `year`。
- 缺失记录时显示“新增当前年份数据”状态。
- 保存按钮按当前 Tab 单独保存。

## 6. 验收

结构测试：

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui\test\workspace.phase1.test.mjs
```

构建测试：

```powershell
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\ruoyi-ui'; npm run build:prod"
```

浏览器验收：

- 进入 `http://localhost:8081/postgrad/workspace`。
- 选中一个专业方向。
- 右侧出现 Tab：概览、专业基础信息、复试线、招生计划、拟录取、数据质量。
- 每个数据 Tab 可进入编辑态。
- 右侧可直接切换维护年份，切换后同步刷新当前专业的年度数据。
- 右侧编辑面板在宽屏下保留约 `520px` 宽度，表单采用单列输入，避免数字框和下拉框被压缩。
- 已存在记录显示“保存修改”。
- 不存在记录显示“新增当前年份数据”。
- 保存成功后右侧退出编辑态，页面刷新当前学校详情。
