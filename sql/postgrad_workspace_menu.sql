USE postgrad_selector;

-- Phase 1: add the school data workspace as the primary postgrad admin entry.
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (2140, '学校数据工作台', 2000, 1, 'workspace', 'postgrad/workspace/index', NULL, NULL,
   1, 0, 'C', '0', '0', 'postgrad:workspace:view', 'dashboard',
   'admin', NOW(), '', NULL, '按学校统一查看学院、专业方向、年份数据完整度和待审核状态');
