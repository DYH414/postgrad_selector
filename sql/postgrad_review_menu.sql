-- 数据审核中心菜单
-- 需要在考研择校(2000)目录下

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(2120, '数据审核中心', 2000, 12, 'postgrad/review/index', 'postgrad/review/index', NULL, NULL,
  1, 0, 'C', '0', '0', 'postgrad:staging:list', 'review', 'admin', NOW(), '', NULL, ''),
(2121, '审核查询', 2120, 1, '', '', NULL, NULL,
  1, 0, 'F', '0', '0', 'postgrad:staging:query', '#', 'admin', NOW(), '', NULL, ''),
(2122, '审核操作', 2120, 2, '', '', NULL, NULL,
  1, 0, 'F', '0', '0', 'postgrad:staging:edit', '#', 'admin', NOW(), '', NULL, '');
