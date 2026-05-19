USE postgrad_selector;

-- 考研择校业务目录
DELETE FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2006;

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
    (2000, '考研择校', 0, 5, 'postgrad', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', NULL, '考研择校业务目录'),
    (2001, '学校管理', 2000, 1, 'school', 'postgrad/school/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:school:list', 'school', 'admin', sysdate(), '', NULL, '学校基础信息管理'),
    (2002, '学校查询', 2001, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:query', '#', 'admin', sysdate(), '', NULL, ''),
    (2003, '学校新增', 2001, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:add', '#', 'admin', sysdate(), '', NULL, ''),
    (2004, '学校修改', 2001, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:edit', '#', 'admin', sysdate(), '', NULL, ''),
    (2005, '学校删除', 2001, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:remove', '#', 'admin', sysdate(), '', NULL, ''),
    (2006, '学校导出', 2001, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:export', '#', 'admin', sysdate(), '', NULL, '');
