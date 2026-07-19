USE postgrad_selector;

-- Final-state postgrad admin menu tree. This script is safe to re-run.
-- Super administrators bypass menu authorization. Grant ordinary roles separately after deployment.
-- Retire only the superseded menus and their existing role associations.
DELETE FROM sys_role_menu WHERE menu_id IN (2040, 2041, 2042, 2043, 2044, 2045, 2110, 2111, 2112, 2113, 2114, 2115);
DELETE FROM sys_menu WHERE menu_id IN (2040, 2041, 2042, 2043, 2044, 2045, 2110, 2111, 2112, 2113, 2114, 2115);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (2000, '考研择校', 0, 5, 'postgrad', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'education', 'admin', NOW(), '', NULL, '考研择校业务目录'),

  (2160, '学校数据工作台', 2000, 0, 'workspace', 'postgrad/workspace/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:workspace:view', 'dashboard', 'admin', NOW(), '', NULL, '按学校统一查看学院、专业方向、年份数据完整度和待审核状态'),

  (2130, '基础数据', 2000, 1, 'basic', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'documentation', 'admin', NOW(), '', NULL, '学校、学院、专业、科目和来源'),
  (2001, '学校管理', 2130, 1, 'school', 'postgrad/school/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:school:list', 'school', 'admin', NOW(), '', NULL, '学校基础信息管理'),
  (2002, '学校查询', 2001, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:query', '#', 'admin', NOW(), '', NULL, ''),
  (2003, '学校新增', 2001, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:add', '#', 'admin', NOW(), '', NULL, ''),
  (2004, '学校修改', 2001, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2005, '学校删除', 2001, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2006, '学校导出', 2001, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:school:export', '#', 'admin', NOW(), '', NULL, ''),
  (2010, '学院管理', 2130, 2, 'college', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:college:list', 'tree', 'admin', NOW(), '', NULL, '学院基础信息管理'),
  (2011, '学院查询', 2010, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:college:query', '#', 'admin', NOW(), '', NULL, ''),
  (2012, '学院新增', 2010, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:college:add', '#', 'admin', NOW(), '', NULL, ''),
  (2013, '学院修改', 2010, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:college:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2014, '学院删除', 2010, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:college:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2015, '学院导出', 2010, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:college:export', '#', 'admin', NOW(), '', NULL, ''),
  (2020, '考试科目字典', 2130, 3, 'subject', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:subject:list', 'dict', 'admin', NOW(), '', NULL, '考试科目字典管理'),
  (2021, '科目查询', 2020, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:subject:query', '#', 'admin', NOW(), '', NULL, ''),
  (2022, '科目新增', 2020, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:subject:add', '#', 'admin', NOW(), '', NULL, ''),
  (2023, '科目修改', 2020, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:subject:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2024, '科目删除', 2020, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:subject:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2025, '科目导出', 2020, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:subject:export', '#', 'admin', NOW(), '', NULL, ''),
  (2030, '招生专业管理', 2130, 4, 'program', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:program:list', 'education', 'admin', NOW(), '', NULL, '招生专业管理'),
  (2031, '专业查询', 2030, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:program:query', '#', 'admin', NOW(), '', NULL, ''),
  (2032, '专业新增', 2030, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:program:add', '#', 'admin', NOW(), '', NULL, ''),
  (2033, '专业修改', 2030, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:program:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2034, '专业删除', 2030, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:program:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2035, '专业导出', 2030, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:program:export', '#', 'admin', NOW(), '', NULL, ''),
  (2050, '来源证据管理', 2130, 5, 'dataSource', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:dataSource:list', 'link', 'admin', NOW(), '', NULL, '来源证据管理'),
  (2051, '来源查询', 2050, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataSource:query', '#', 'admin', NOW(), '', NULL, ''),
  (2052, '来源新增', 2050, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataSource:add', '#', 'admin', NOW(), '', NULL, ''),
  (2053, '来源修改', 2050, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataSource:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2054, '来源删除', 2050, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataSource:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2055, '来源导出', 2050, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataSource:export', '#', 'admin', NOW(), '', NULL, ''),

  (2131, '招生数据', 2000, 2, 'admission', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'chart', 'admin', NOW(), '', NULL, '复试线、招生计划和拟录取'),
  (2060, '复试线数据', 2131, 1, 'admissionScore', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:admissionScore:list', 'number', 'admin', NOW(), '', NULL, '历年复试线维护'),
  (2061, '复试线查询', 2060, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionScore:query', '#', 'admin', NOW(), '', NULL, ''),
  (2062, '复试线新增', 2060, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionScore:add', '#', 'admin', NOW(), '', NULL, ''),
  (2063, '复试线修改', 2060, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionScore:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2064, '复试线删除', 2060, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionScore:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2065, '复试线导出', 2060, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionScore:export', '#', 'admin', NOW(), '', NULL, ''),
  (2070, '招生计划数据', 2131, 2, 'admissionPlan', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:admissionPlan:list', 'table', 'admin', NOW(), '', NULL, '历年招生计划维护'),
  (2071, '招生计划查询', 2070, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionPlan:query', '#', 'admin', NOW(), '', NULL, ''),
  (2072, '招生计划新增', 2070, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionPlan:add', '#', 'admin', NOW(), '', NULL, ''),
  (2073, '招生计划修改', 2070, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionPlan:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2074, '招生计划删除', 2070, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionPlan:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2075, '招生计划导出', 2070, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionPlan:export', '#', 'admin', NOW(), '', NULL, ''),
  (2080, '拟录取数据', 2131, 3, 'admissionResult', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:admissionResult:list', 'chart', 'admin', NOW(), '', NULL, '历年拟录取结果维护'),
  (2081, '拟录取查询', 2080, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionResult:query', '#', 'admin', NOW(), '', NULL, ''),
  (2082, '拟录取新增', 2080, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionResult:add', '#', 'admin', NOW(), '', NULL, ''),
  (2083, '拟录取修改', 2080, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionResult:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2084, '拟录取删除', 2080, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionResult:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2085, '拟录取导出', 2080, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:admissionResult:export', '#', 'admin', NOW(), '', NULL, ''),

  (2132, '数据审核', 2000, 3, 'review', NULL, NULL, 'PostgradReviewGroup', 1, 0, 'M', '0', '0', '', 'review', 'admin', NOW(), '', NULL, '数据审核入库'),
  (2120, '数据审核中心', 2132, 1, '', 'postgrad/review/index', NULL, 'PostgradReview', 1, 0, 'C', '0', '0', 'postgrad:staging:list', 'review', 'admin', NOW(), '', NULL, '采集数据审核与入库'),
  (2121, '审核查询', 2120, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:staging:query', '#', 'admin', NOW(), '', NULL, ''),
  (2122, '审核操作', 2120, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:staging:edit', '#', 'admin', NOW(), '', NULL, ''),

  (2133, '数据运维', 2000, 4, 'ops', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', NOW(), '', NULL, '数据体检、采集任务和运行观测'),
  (2090, '数据体检', 2133, 1, 'programYearDataQuality', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:programYearDataQuality:list', 'rate', 'admin', NOW(), '', NULL, '专业年份数据完整度'),
  (2091, '完整度查询', 2090, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:programYearDataQuality:query', '#', 'admin', NOW(), '', NULL, ''),
  (2092, '完整度新增', 2090, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:programYearDataQuality:add', '#', 'admin', NOW(), '', NULL, ''),
  (2093, '完整度修改', 2090, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:programYearDataQuality:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2094, '完整度删除', 2090, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:programYearDataQuality:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2095, '完整度导出', 2090, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:programYearDataQuality:export', '#', 'admin', NOW(), '', NULL, ''),
  (2100, '采集任务', 2133, 2, 'dataCollectionTask', 'postgrad/crud/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:dataCollectionTask:list', 'job', 'admin', NOW(), '', NULL, '定向数据采集任务'),
  (2101, '采集任务查询', 2100, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataCollectionTask:query', '#', 'admin', NOW(), '', NULL, ''),
  (2102, '采集任务新增', 2100, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataCollectionTask:add', '#', 'admin', NOW(), '', NULL, ''),
  (2103, '采集任务修改', 2100, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataCollectionTask:edit', '#', 'admin', NOW(), '', NULL, ''),
  (2104, '采集任务删除', 2100, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataCollectionTask:remove', '#', 'admin', NOW(), '', NULL, ''),
  (2105, '采集任务导出', 2100, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:dataCollectionTask:export', '#', 'admin', NOW(), '', NULL, ''),
  (2134, 'RAG 观测台', 2133, 3, 'rag', 'postgrad/rag/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:rag:view', 'monitor', 'admin', NOW(), '', NULL, 'RAG 状态、索引与最近查询观测'),
  (2135, 'RAG 重新索引', 2134, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:rag:reindex', '#', 'admin', NOW(), '', NULL, 'RAG 管理按钮'),
  (2136, 'AI 观测台', 2133, 4, 'ai-observe', 'postgrad/observe/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:aiObserve:view', 'monitor', 'admin', NOW(), '', NULL, 'AI 调用总量、耗时、token 与最新错误观测')
  ,(2170, 'RAG 评测台', 2133, 5, 'rag-evaluation', 'postgrad/rag-evaluation/index', NULL, '', 1, 0, 'C', '0', '0', 'postgrad:ragEval:view', 'experiment', 'admin', NOW(), '', NULL, 'RAG 数据集、实验、诊断与报告')
  ,(2171, '评测用例维护', 2170, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:ragEval:edit', '#', 'admin', NOW(), '', NULL, '')
  ,(2172, '运行评测', 2170, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:ragEval:run', '#', 'admin', NOW(), '', NULL, '')
  ,(2173, '冻结数据集', 2170, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:ragEval:freeze', '#', 'admin', NOW(), '', NULL, '')
  ,(2174, '对比评测', 2170, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:ragEval:compare', '#', 'admin', NOW(), '', NULL, '')
  ,(2175, '导出评测报告', 2170, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'postgrad:ragEval:export', '#', 'admin', NOW(), '', NULL, '')
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name),
  parent_id = VALUES(parent_id),
  order_num = VALUES(order_num),
  path = VALUES(path),
  component = VALUES(component),
  query = VALUES(query),
  route_name = VALUES(route_name),
  is_frame = VALUES(is_frame),
  is_cache = VALUES(is_cache),
  menu_type = VALUES(menu_type),
  visible = VALUES(visible),
  status = VALUES(status),
  perms = VALUES(perms),
  icon = VALUES(icon),
  remark = VALUES(remark);
