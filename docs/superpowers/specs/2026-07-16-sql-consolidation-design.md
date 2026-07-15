# SQL 脚本收敛设计

## 目标

将当前混合了全量初始化、历史迁移、菜单演进和一次性数据修复的 SQL 文件，整理为少量职责清晰的入口脚本。新环境能够按固定顺序初始化，已有环境不会误执行带 `DROP TABLE` 的脚本，历史修复记录仍可追溯。

## 核心原则

- 全新部署脚本与存量数据库维护脚本分离。
- 入口脚本表达当前最终结构，不重放历史演进过程。
- 不删除历史 SQL；失去现行入口价值的脚本移动到归档目录。
- 不把数据保留策略、一次性清理或特定数据源口径混入 DDL。
- 菜单初始化不自动向普通角色授权；角色授权由部署环境决定。
- 不修改现有 Java 实体、Mapper 或业务行为；SQL 必须与当前代码契约一致。

## 最终目录

```text
sql/
├── 00_platform_bootstrap.sql
├── 10_postgrad_core_schema.sql
├── 20_ai_runtime_schema.sql
├── 30_postgrad_menu_permissions.sql
├── 90_data_quality_maintenance.sql
└── archive/
    ├── migrations/
    └── one-off/
```

## 入口脚本职责

### 00_platform_bootstrap.sql

合并 RuoYi 基础表和 Quartz 表。脚本包含破坏性 `DROP TABLE`，只允许用于空库初始化或明确批准的全量重建。它不包含考研业务表、AI 表或菜单扩展。

### 10_postgrad_core_schema.sql

保存当前考研业务表的完整基线。用户画像偏好字段直接写入 `user_profile` 最终定义，不再依赖后续 `ALTER TABLE`。

索引以当前基线为准：不重复创建已被唯一键覆盖的 `(program_id, year)` 普通索引。只有能由当前查询契约证明必要且不重复的索引才进入基线。

该脚本同样只用于空库初始化或明确批准的业务库重建。

### 20_ai_runtime_schema.sql

合并以下纯 DDL：

- AI 会话与消息持久化；
- RAG 索引构建批次；
- Tool 调用 Trace。

脚本使用 `CREATE TABLE IF NOT EXISTS`，不包含历史数据删除。Tool Trace 的 30 天清理语句不属于 Schema 初始化，应交给独立运维任务。

### 30_postgrad_menu_permissions.sql

直接生成当前最终菜单树，不再依次执行“创建、删除、重挂载”的五段历史菜单脚本。

脚本需要满足：

- 可以重复执行，不因菜单主键已存在而失败；
- 采用最终的 `path`、`component`、父子层级和排序；
- 清理已经废弃的菜单 ID；
- 只维护 `sys_menu`，不自动写入 `sys_role_menu`；
- 在文件头明确说明超级管理员可直接访问，普通角色需要管理员另行授权。

### 90_data_quality_maintenance.sql

保存可重复执行的数据质量同步逻辑，面向已有业务数据运行。统一按当前全部有效招生事实计算质量标记，不沿用特定事故清理脚本的“N诺来源限定”口径。

`has_official_source` 必须根据真实来源字段计算，不能无条件写为 `0`。该脚本不删除招生事实，不创建事故备份表。

## 归档策略

### archive/migrations

存放已被最终基线吸收的增量迁移和菜单演进脚本，包括：

- 用户画像偏好字段迁移；
- 推荐索引增量脚本；
- 五个历史菜单脚本。

文件保留原名，顶部增加“历史归档，不用于新环境初始化”的说明。

### archive/one-off

存放带日期的一次性数据清理脚本。此类脚本保留审计价值，但不得被 README 或安装流程列为常规执行步骤。

`codex-backups/` 中的 SQL 备份保持原位，不参与初始化或归档迁移。

## README 与执行顺序

README 的数据库初始化步骤调整为：

```text
00_platform_bootstrap.sql
→ 10_postgrad_core_schema.sql
→ 20_ai_runtime_schema.sql
→ 30_postgrad_menu_permissions.sql
```

`90_data_quality_maintenance.sql` 单独列为导入业务数据后的维护操作，不属于建库必需步骤。

README 必须标明前两个脚本包含破坏性 DDL，禁止直接用于生产升级。

## 风险控制

- 合并时保留原脚本的字符集、引擎、主键、唯一键和初始化数据。
- 对重复列、重复索引及菜单 ID 冲突逐项检查，不机械拼接文件。
- 不自动运行任何 SQL，不连接或修改本地、测试或生产数据库。
- 不删除或覆盖用户当前未提交的 README、文档及其他工作区改动。

## 验收标准

1. `sql/` 根目录只保留五个现行入口脚本和 `archive/`。
2. 所有原 SQL 均能在入口或归档目录中找到，不丢失历史记录。
3. 新环境安装文档只引用四个初始化入口，顺序明确。
4. 初始化 DDL 不包含 Tool Trace 清理和污染数据清理操作。
5. 业务基线不存在重复画像字段迁移及明显重复索引。
6. 菜单脚本可重复执行，并且不自动修改普通角色权限。
7. 维护脚本不包含破坏性招生事实删除。
8. 通过静态 SQL 检查、路径引用检查和 Git diff 检查。
