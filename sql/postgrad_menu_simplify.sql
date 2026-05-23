-- ==========================================
-- 考研择校菜单简化 — 13项扁平 → 4组分层
-- ==========================================
USE postgrad_selector;

-- Step 1: Delete redundant menus (and their button children)
-- 专业初试科目 (2040-2045): low-value junction table CRUD
DELETE FROM sys_menu WHERE menu_id BETWEEN 2040 AND 2045;
-- 抽取暂存审核 (2110-2115): redundant with review center
DELETE FROM sys_menu WHERE menu_id BETWEEN 2110 AND 2115;

-- Step 2: Insert 4 directory menus
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
(2130, '基础数据', 2000, 1, 'basic', NULL, 1, 0, 'M', '0', '0', '', 'documentation', 'admin', NOW(), '学校·学院·专业·科目·来源'),
(2131, '招生数据', 2000, 2, 'admission', NULL, 1, 0, 'M', '0', '0', '', 'chart', 'admin', NOW(), '复试线·招生计划·拟录取'),
(2132, '数据审核', 2000, 3, 'review', NULL, 1, 0, 'M', '0', '0', '', 'review', 'admin', NOW(), 'N诺数据审核入库'),
(2133, '数据运维', 2000, 4, 'ops', NULL, 1, 0, 'M', '0', '0', '', 'tool', 'admin', NOW(), '数据体检·采集任务');

-- Step 3: Re-parent existing menus under new directories
-- 基础数据 (2130)
UPDATE sys_menu SET parent_id = 2130, order_num = 1 WHERE menu_id = 2001;  -- 学校管理
UPDATE sys_menu SET parent_id = 2130, order_num = 2 WHERE menu_id = 2010;  -- 学院管理
UPDATE sys_menu SET parent_id = 2130, order_num = 3 WHERE menu_id = 2020;  -- 考试科目字典
UPDATE sys_menu SET parent_id = 2130, order_num = 4 WHERE menu_id = 2030;  -- 招生专业管理
UPDATE sys_menu SET parent_id = 2130, order_num = 5 WHERE menu_id = 2050;  -- 来源证据管理

-- 招生数据 (2131)
UPDATE sys_menu SET parent_id = 2131, order_num = 1 WHERE menu_id = 2060;  -- 复试线数据
UPDATE sys_menu SET parent_id = 2131, order_num = 2 WHERE menu_id = 2070;  -- 招生计划数据
UPDATE sys_menu SET parent_id = 2131, order_num = 3 WHERE menu_id = 2080;  -- 拟录取数据

-- 数据审核 (2132)
UPDATE sys_menu SET parent_id = 2132, order_num = 1 WHERE menu_id = 2120;  -- 数据审核中心

-- 数据运维 (2133)
UPDATE sys_menu SET parent_id = 2133, order_num = 1 WHERE menu_id = 2090;  -- 数据体检
UPDATE sys_menu SET parent_id = 2133, order_num = 2 WHERE menu_id = 2100;  -- 采集任务
