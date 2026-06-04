-- 考研择校决策平台 MySQL schema
-- Version: MVP 1.0
-- Date: 2026-05-18
-- Target: MySQL 8.x

CREATE DATABASE IF NOT EXISTS postgrad_selector
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE postgrad_selector;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS recommendation_log;
DROP TABLE IF EXISTS recommendation_rule;
DROP TABLE IF EXISTS user_favorite_program;
DROP TABLE IF EXISTS user_target_program;
DROP TABLE IF EXISTS user_profile;
DROP TABLE IF EXISTS app_user;
DROP TABLE IF EXISTS program_year_data_quality;
DROP TABLE IF EXISTS data_collection_task;
DROP TABLE IF EXISTS staging;
DROP TABLE IF EXISTS admission_result;
DROP TABLE IF EXISTS admission_plan;
DROP TABLE IF EXISTS admission_score;
DROP TABLE IF EXISTS data_source;
DROP TABLE IF EXISTS program_subject;
DROP TABLE IF EXISTS program;
DROP TABLE IF EXISTS subject;
DROP TABLE IF EXISTS college;
DROP TABLE IF EXISTS school;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE school (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(100) NOT NULL COMMENT '学校全称',
  short_name VARCHAR(30) NULL COMMENT '学校简称',
  province VARCHAR(30) NOT NULL COMMENT '省份',
  city VARCHAR(30) NOT NULL COMMENT '城市',
  tier ENUM('985','211','DOUBLE_FIRST','PUBLIC_REGULAR','PRIVATE','INDEPENDENT','RESEARCH_INSTITUTE','OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '学校层次',
  is_985 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否985',
  is_211 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否211',
  is_double_first TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否双一流',
  is_public TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否公办',
  website VARCHAR(255) NULL COMMENT '学校官网或研招官网',
  status ENUM('active','inactive') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_school_name (name),
  KEY idx_school_region (province, city),
  KEY idx_school_tier (tier),
  KEY idx_school_flags (is_985, is_211, is_double_first)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校基础信息';

CREATE TABLE college (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  school_id BIGINT UNSIGNED NOT NULL COMMENT '学校ID',
  name VARCHAR(120) NOT NULL COMMENT '学院名称',
  website VARCHAR(255) NULL COMMENT '学院官网',
  graduate_url VARCHAR(255) NULL COMMENT '学院研招信息页',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_college_school_name (school_id, name),
  KEY idx_college_school (school_id),
  CONSTRAINT fk_college_school
    FOREIGN KEY (school_id) REFERENCES school(id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学院信息';

CREATE TABLE subject (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  code VARCHAR(20) NOT NULL COMMENT '科目代码，如408/101/201',
  name VARCHAR(100) NOT NULL COMMENT '科目名称',
  subject_type ENUM('public','professional') NOT NULL COMMENT '公共课/专业课',
  exam_category VARCHAR(30) NULL COMMENT '学硕/专硕/通用等',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_code (code),
  KEY idx_subject_type (subject_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='初试科目字典';

CREATE TABLE program (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  college_id BIGINT UNSIGNED NOT NULL COMMENT '学院ID',
  program_code VARCHAR(20) NOT NULL COMMENT '专业代码',
  program_name VARCHAR(120) NOT NULL COMMENT '专业名称',
  research_direction VARCHAR(200) NULL COMMENT '研究方向',
  discipline_category VARCHAR(30) NULL COMMENT '学科门类',
  first_discipline VARCHAR(30) NULL COMMENT '一级学科',
  study_mode ENUM('full_time','part_time') NOT NULL COMMENT '学习方式',
  degree_type ENUM('academic','professional') NOT NULL COMMENT '学位类型',
  exam_type ENUM('GENERAL_500','MANAGEMENT_300','LAW_MASTER_500','MEDICAL_500','ART_500','OTHER') NOT NULL DEFAULT 'GENERAL_500' COMMENT '考试类型，用于全专业扩展',
  score_scale SMALLINT UNSIGNED NOT NULL DEFAULT 500 COMMENT '考试满分，如500/300',
  retest_subjects VARCHAR(255) NULL COMMENT '复试科目描述',
  is_408 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否408快捷标签，长期筛选以program_subject为准',
  protects_first_choice TINYINT(1) NULL COMMENT '是否保护一志愿',
  is_joint_program TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否联培/中外合作',
  status ENUM('active','suspended','pending') NOT NULL DEFAULT 'active' COMMENT '状态',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_program_college_code_mode_dir (college_id, program_code, study_mode, degree_type, research_direction),
  KEY idx_program_code (program_code),
  KEY idx_program_college_408 (college_id, is_408),
  KEY idx_program_mode_degree (study_mode, degree_type),
  KEY idx_program_exam_type (exam_type, score_scale),
  KEY idx_program_status (status),
  CONSTRAINT fk_program_college
    FOREIGN KEY (college_id) REFERENCES college(id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业方向';

CREATE TABLE program_subject (
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  subject_id BIGINT UNSIGNED NOT NULL COMMENT '科目ID',
  subject_order TINYINT UNSIGNED NOT NULL COMMENT '科目顺序，1-4',
  PRIMARY KEY (program_id, subject_id),
  UNIQUE KEY uk_program_subject_order (program_id, subject_order),
  KEY idx_program_subject_subject (subject_id),
  CONSTRAINT fk_program_subject_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_program_subject_subject
    FOREIGN KEY (subject_id) REFERENCES subject(id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业-初试科目关联';

CREATE TABLE data_source (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  source_type ENUM('OFFICIAL','THIRD_PARTY','MANUAL','OCR') NOT NULL COMMENT '来源类型',
  url TEXT NULL COMMENT '原始页面或文件URL',
  title VARCHAR(255) NULL COMMENT '来源标题',
  source_owner VARCHAR(100) NULL COMMENT '来源主体，如学校/学院/第三方网站',
  publish_date DATE NULL COMMENT '发布日期',
  local_file_path VARCHAR(500) NULL COMMENT '本地文件路径',
  file_hash VARCHAR(64) NULL COMMENT '文件SHA256',
  page_hash VARCHAR(64) NULL COMMENT '页面SHA256',
  fetched_at TIMESTAMP NULL COMMENT '最近抓取时间',
  robots_checked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否检查robots',
  robots_allowed TINYINT(1) NULL COMMENT 'robots是否允许',
  terms_checked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否检查服务条款',
  commercial_use_risk ENUM('low','medium','high','unknown') NOT NULL DEFAULT 'unknown' COMMENT '商业使用风险',
  copyright_risk ENUM('low','medium','high','unknown') NOT NULL DEFAULT 'unknown' COMMENT '版权风险',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_data_source_type (source_type),
  KEY idx_data_source_file_hash (file_hash),
  KEY idx_data_source_page_hash (page_hash),
  KEY idx_data_source_publish_date (publish_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据来源证据';

CREATE TABLE admission_score (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  year SMALLINT UNSIGNED NOT NULL COMMENT '招生年份',
  score_line SMALLINT UNSIGNED NULL COMMENT '复试总分线',
  single_math SMALLINT UNSIGNED NULL COMMENT '数学单科线',
  single_english SMALLINT UNSIGNED NULL COMMENT '英语单科线',
  single_politics SMALLINT UNSIGNED NULL COMMENT '政治单科线',
  single_professional SMALLINT UNSIGNED NULL COMMENT '专业课单科线',
  verify_status ENUM('OFFICIAL_VERIFIED','MANUAL_VERIFIED','THIRD_PARTY','OCR_PENDING','CONFLICT','MISSING') NOT NULL DEFAULT 'THIRD_PARTY' COMMENT '可信度状态',
  source_id BIGINT UNSIGNED NULL COMMENT '来源ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admission_score_program_year (program_id, year),
  KEY idx_admission_score_year (year),
  KEY idx_admission_score_status (verify_status),
  KEY idx_admission_score_source (source_id),
  CONSTRAINT fk_admission_score_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_admission_score_source
    FOREIGN KEY (source_id) REFERENCES data_source(id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历年复试线';

CREATE TABLE admission_plan (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  year SMALLINT UNSIGNED NOT NULL COMMENT '招生年份',
  total_plan SMALLINT UNSIGNED NULL COMMENT '总招生计划',
  recommended_exemption_plan SMALLINT UNSIGNED NULL COMMENT '推免计划',
  unified_exam_quota SMALLINT UNSIGNED NULL COMMENT '统考名额',
  retest_count SMALLINT UNSIGNED NULL COMMENT '一志愿进入复试人数',
  verify_status ENUM('OFFICIAL_VERIFIED','MANUAL_VERIFIED','THIRD_PARTY','OCR_PENDING','CONFLICT','MISSING') NOT NULL DEFAULT 'THIRD_PARTY' COMMENT '可信度状态',
  source_id BIGINT UNSIGNED NULL COMMENT '来源ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admission_plan_program_year (program_id, year),
  KEY idx_admission_plan_year (year),
  KEY idx_admission_plan_status (verify_status),
  KEY idx_admission_plan_source (source_id),
  CONSTRAINT fk_admission_plan_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_admission_plan_source
    FOREIGN KEY (source_id) REFERENCES data_source(id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历年招生计划';

CREATE TABLE admission_result (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  year SMALLINT UNSIGNED NOT NULL COMMENT '招生年份',
  admitted_count SMALLINT UNSIGNED NULL COMMENT '实际录取人数',
  first_choice_admitted_count SMALLINT UNSIGNED NULL COMMENT '一志愿录取人数',
  min_admitted_score SMALLINT UNSIGNED NULL COMMENT '拟录取最低分',
  avg_admitted_score DECIMAL(5,1) NULL COMMENT '拟录取平均分',
  max_admitted_score SMALLINT UNSIGNED NULL COMMENT '拟录取最高分',
  has_transfer TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有调剂录取',
  transfer_count SMALLINT UNSIGNED NULL COMMENT '调剂录取人数',
  verify_status ENUM('OFFICIAL_VERIFIED','MANUAL_VERIFIED','THIRD_PARTY','OCR_PENDING','CONFLICT','MISSING') NOT NULL DEFAULT 'THIRD_PARTY' COMMENT '可信度状态',
  source_id BIGINT UNSIGNED NULL COMMENT '来源ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admission_result_program_year (program_id, year),
  KEY idx_admission_result_year (year),
  KEY idx_admission_result_status (verify_status),
  KEY idx_admission_result_source (source_id),
  CONSTRAINT fk_admission_result_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_admission_result_source
    FOREIGN KEY (source_id) REFERENCES data_source(id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历年拟录取统计结果';

CREATE TABLE staging (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT UNSIGNED NULL COMMENT '采集任务ID，可为空',
  source_id BIGINT UNSIGNED NULL COMMENT '来源ID，可在审核后绑定data_source',
  matched_program_id BIGINT UNSIGNED NULL COMMENT '已匹配的主库专业ID，入库前可为空',
  source_type ENUM('YZ_CHSI','OFFICIAL_HTML','OFFICIAL_PDF','IMAGE','THIRD_PARTY','MANUAL','OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '采集来源类型',
  school_name VARCHAR(120) NOT NULL COMMENT '学校名称，入库前匹配用',
  college_name VARCHAR(120) NULL COMMENT '学院名称',
  city VARCHAR(30) NULL COMMENT '城市',
  program_code VARCHAR(20) NULL COMMENT '专业代码',
  program_name VARCHAR(120) NULL COMMENT '专业名称',
  exam_subjects VARCHAR(500) NULL COMMENT '初试科目原始描述，如101/204/302/408',
  year SMALLINT UNSIGNED NULL COMMENT '年份',
  score_line SMALLINT UNSIGNED NULL COMMENT '复试线',
  single_math SMALLINT UNSIGNED NULL COMMENT '数学单科线',
  single_english SMALLINT UNSIGNED NULL COMMENT '英语单科线',
  single_politics SMALLINT UNSIGNED NULL COMMENT '政治单科线',
  single_professional SMALLINT UNSIGNED NULL COMMENT '专业课单科线',
  min_admitted SMALLINT UNSIGNED NULL COMMENT '最低录取分',
  avg_admitted DECIMAL(6,2) NULL COMMENT '平均录取分',
  plan_count SMALLINT UNSIGNED NULL COMMENT '招生计划',
  retest_count SMALLINT UNSIGNED NULL COMMENT '复试人数',
  admitted_count SMALLINT UNSIGNED NULL COMMENT '录取人数',
  confidence ENUM('high','medium','low','unknown') NOT NULL DEFAULT 'unknown' COMMENT '抽取置信度',
  source_url TEXT NULL COMMENT '原始来源URL',
  raw_text LONGTEXT NULL COMMENT 'OCR/PDF/HTML提取出的原始文本',
  extract_json JSON NULL COMMENT 'Agent/Gemini原始结构化结果，保留完整证据包',
  status ENUM('seed','pending','approved','rejected','skipped') NOT NULL DEFAULT 'pending' COMMENT '审核状态，seed表示名单骨架数据',
  error_message TEXT NULL COMMENT '采集或字段校验错误信息',
  reviewer_id BIGINT UNSIGNED NULL COMMENT '审核人ID',
  reviewed_at TIMESTAMP NULL COMMENT '审核时间',
  review_note VARCHAR(500) NULL COMMENT '审核备注',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_staging_status_created (status, created_at),
  KEY idx_staging_school_program_year (school_name, program_code, year),
  KEY idx_staging_task (task_id),
  KEY idx_staging_source (source_id),
  KEY idx_staging_matched_program (matched_program_id),
  KEY idx_staging_source_type_confidence (source_type, confidence),
  CONSTRAINT fk_staging_source
    FOREIGN KEY (source_id) REFERENCES data_source(id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_staging_matched_program
    FOREIGN KEY (matched_program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽取暂存表';

CREATE TABLE data_collection_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  task_type ENUM('SCORE','PLAN','RESULT','RETEST','SOURCE') NOT NULL COMMENT '采集任务类型',
  target_year SMALLINT UNSIGNED NOT NULL COMMENT '目标年份',
  priority TINYINT UNSIGNED NOT NULL DEFAULT 50 COMMENT '优先级，数值越大越优先',
  status ENUM('PENDING','RUNNING','STAGING','REVIEWED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  source_hint_url TEXT NULL COMMENT '来源提示URL',
  failure_reason VARCHAR(500) NULL COMMENT '失败原因',
  created_by ENUM('SYSTEM','ADMIN','USER') NOT NULL DEFAULT 'SYSTEM' COMMENT '任务创建来源',
  assigned_to BIGINT UNSIGNED NULL COMMENT '处理人ID',
  started_at TIMESTAMP NULL COMMENT '开始时间',
  finished_at TIMESTAMP NULL COMMENT '完成时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_task_status_priority (status, priority, created_at),
  KEY idx_task_program_year_type (program_id, target_year, task_type),
  KEY idx_task_assigned_to (assigned_to),
  CONSTRAINT fk_task_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定向数据采集任务';

ALTER TABLE staging
  ADD CONSTRAINT fk_staging_task
  FOREIGN KEY (task_id) REFERENCES data_collection_task(id)
  ON UPDATE CASCADE ON DELETE SET NULL;

CREATE TABLE program_year_data_quality (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '专业ID',
  year SMALLINT UNSIGNED NOT NULL COMMENT '年份',
  has_score TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有复试线',
  has_plan TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有招生计划',
  has_result TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有拟录取统计',
  has_official_source TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有官网来源',
  has_conflict TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否存在数据冲突',
  completeness_level ENUM('A','B','C','D','E') NOT NULL DEFAULT 'D' COMMENT '完整度等级',
  missing_fields JSON NULL COMMENT '缺失字段列表',
  last_checked_at TIMESTAMP NULL COMMENT '最近检查时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quality_program_year (program_id, year),
  KEY idx_quality_level (completeness_level),
  KEY idx_quality_missing (has_score, has_plan, has_result),
  CONSTRAINT fk_quality_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业年份数据完整度';

CREATE TABLE app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  phone_hash VARCHAR(128) NULL COMMENT '手机号HMAC或哈希值，不存明文',
  email_hash VARCHAR(128) NULL COMMENT '邮箱HMAC或哈希值，不存明文',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码BCrypt哈希',
  role ENUM('user','admin') NOT NULL DEFAULT 'user' COMMENT '角色',
  consent_at TIMESTAMP NULL COMMENT '隐私政策同意时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_at TIMESTAMP NULL COMMENT '软删除时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_phone_hash (phone_hash),
  UNIQUE KEY uk_user_email_hash (email_hash),
  KEY idx_user_role (role),
  KEY idx_user_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号';

CREATE TABLE user_profile (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  estimated_score SMALLINT UNSIGNED NOT NULL COMMENT '初试预估总分',
  target_regions JSON NULL COMMENT '目标省市列表',
  accept_part_time TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接受非全日制',
  accept_transfer TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接受调剂',
  accept_academic TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接受学硕',
  accept_joint TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接受联培/中外合作',
  risk_preference ENUM('conservative','balanced','aggressive') NOT NULL DEFAULT 'balanced' COMMENT '风险偏好',
  school_tier_preference ENUM('must_211_or_better','prefer_211_or_better','no_strict_requirement') NULL COMMENT '院校层次取舍',
  region_strategy ENUM('developed_priority','developed_balanced','no_strict_requirement','target_regions_only') NULL COMMENT '地区取舍',
  undergrad_tier ENUM('985','211','DOUBLE_FIRST','PUBLIC_REGULAR','PRIVATE','JUNIOR_COLLEGE','OTHER') NULL COMMENT '本科院校层次',
  undergraduate_major VARCHAR(100) NULL COMMENT '本科专业',
  is_cross_major TINYINT(1) NULL COMMENT '是否跨考',
  math_level ENUM('weak','medium','strong') NULL COMMENT '数学基础',
  english_level ENUM('weak','medium','strong') NULL COMMENT '英语基础',
  cs_level ENUM('weak','medium','strong') NULL COMMENT '专业课基础',
  daily_study_hours DECIMAL(3,1) NULL COMMENT '每日学习时间',
  review_progress VARCHAR(100) NULL COMMENT '当前复习进度',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_profile_user (user_id),
  KEY idx_profile_score (estimated_score),
  KEY idx_profile_risk (risk_preference),
  CONSTRAINT fk_profile_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像';

CREATE TABLE user_target_program (
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  program_code VARCHAR(20) NOT NULL COMMENT '目标专业代码',
  PRIMARY KEY (user_id, program_code),
  KEY idx_user_target_program_code (program_code),
  CONSTRAINT fk_user_target_program_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户目标专业';

CREATE TABLE user_favorite_program (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  program_id BIGINT UNSIGNED NOT NULL COMMENT '收藏的专业方向ID',
  note VARCHAR(255) NULL COMMENT '用户备注，如关注原因、复试风险、待补资料',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_user_program (user_id, program_id),
  KEY idx_favorite_user_created (user_id, created_at),
  KEY idx_favorite_program (program_id),
  CONSTRAINT fk_favorite_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_favorite_program
    FOREIGN KEY (program_id) REFERENCES program(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏专业方向';

CREATE TABLE recommendation_rule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_scope VARCHAR(50) NOT NULL DEFAULT 'all' COMMENT '规则范围，如all/085404',
  steady_gap_min SMALLINT NOT NULL DEFAULT 20 COMMENT '重点稳妥最低分差',
  focus_gap_min SMALLINT NOT NULL DEFAULT 5 COMMENT '重点关注最低分差',
  reach_gap_min SMALLINT NOT NULL DEFAULT -10 COMMENT '略冲最低分差',
  small_plan_threshold SMALLINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '小招生规模阈值',
  high_score_gap_threshold SMALLINT NOT NULL DEFAULT 20 COMMENT '最低分高于复试线多少触发下调',
  wave_threshold SMALLINT UNSIGNED NOT NULL DEFAULT 30 COMMENT '复试线波动阈值',
  retest_ratio_warning DECIMAL(4,2) NOT NULL DEFAULT 1.50 COMMENT '复录比预警阈值',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_scope_active (rule_scope, is_active),
  KEY idx_rule_scope (rule_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐规则配置';

CREATE TABLE recommendation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  profile_snapshot JSON NOT NULL COMMENT '生成时用户画像快照',
  result_json JSON NOT NULL COMMENT '推荐结果JSON',
  rule_version VARCHAR(50) NULL COMMENT '规则版本',
  data_version VARCHAR(50) NULL COMMENT '数据版本',
  is_paid TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否付费报告',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (id),
  KEY idx_recommendation_user_created (user_id, created_at),
  KEY idx_recommendation_paid (is_paid),
  CONSTRAINT fk_recommendation_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐结果快照';

-- Seed common 408-related subjects.
INSERT INTO subject (code, name, subject_type, exam_category) VALUES
('101', '思想政治理论', 'public', '通用'),
('201', '英语（一）', 'public', '通用'),
('204', '英语（二）', 'public', '通用'),
('301', '数学（一）', 'public', '通用'),
('302', '数学（二）', 'public', '通用'),
('408', '计算机学科专业基础', 'professional', '计算机');

-- Default recommendation rule.
INSERT INTO recommendation_rule (
  rule_scope,
  steady_gap_min,
  focus_gap_min,
  reach_gap_min,
  small_plan_threshold,
  high_score_gap_threshold,
  wave_threshold,
  retest_ratio_warning,
  is_active
) VALUES (
  'all',
  20,
  5,
  -10,
  10,
  20,
  30,
  1.50,
  1
);
