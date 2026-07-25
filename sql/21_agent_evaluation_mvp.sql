-- =============================================================================
-- 21_agent_evaluation_mvp.sql
-- AGENT_E2E 评测：只加不改。在 20_ai_runtime_schema.sql 之后导入。
-- 复用 rag_eval_dataset/case/run 骨架，仅追加 evaluationType 判别轴与 Agent 专属列/表。
-- 幂等：ALTER 用存在性判断包裹，可重复执行。
-- =============================================================================

-- ---- 1. 扩展现有表（evaluationType 判别轴 + Agent 字段）----------------------

-- rag_eval_dataset：加 evaluation_type
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_eval_dataset' AND COLUMN_NAME = 'evaluation_type');
SET @ddl := IF(@col = 0,
    'ALTER TABLE rag_eval_dataset ADD COLUMN evaluation_type VARCHAR(24) NOT NULL DEFAULT ''RAG_RETRIEVAL'' COMMENT ''RAG_RETRIEVAL/RAG_ANSWER/AGENT_E2E'' AFTER lifecycle_state, ADD KEY idx_rag_eval_dataset_eval_type_state (evaluation_type, lifecycle_state)',
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- rag_eval_case：加 agent_spec_json（一列承载 requiredTools/forbiddenTools/expectedClaims/referenceEvidence 等）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_eval_case' AND COLUMN_NAME = 'agent_spec_json');
SET @ddl := IF(@col = 0,
    'ALTER TABLE rag_eval_case ADD COLUMN agent_spec_json JSON NULL COMMENT ''AGENT_E2E 用例期望（工具/断言/证据），非 Agent 用例为 NULL'' AFTER expected_route',
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- rag_eval_run：加 evaluation_type + agent_config_version_id + agent_config_snapshot_json
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rag_eval_run' AND COLUMN_NAME = 'evaluation_type');
SET @ddl := IF(@col = 0,
    'ALTER TABLE rag_eval_run ADD COLUMN evaluation_type VARCHAR(24) NOT NULL DEFAULT ''RAG_RETRIEVAL'' AFTER status, ADD COLUMN agent_config_version_id BIGINT NULL AFTER evaluation_type, ADD COLUMN agent_config_snapshot_json JSON NULL AFTER parameter_snapshot_json, ADD KEY idx_rag_eval_run_type_status_created (evaluation_type, status, created_at)',
    'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---- 2. 新表：AgentConfigVersion（不可变配置版本）---------------------------

CREATE TABLE IF NOT EXISTS agent_config_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_name VARCHAR(64) NOT NULL,
    business_prompt_text MEDIUMTEXT NOT NULL,
    business_prompt_checksum CHAR(64) NOT NULL,
    routing_prompt_text MEDIUMTEXT NOT NULL,
    routing_prompt_checksum CHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    temperature DECIMAL(4,3) NOT NULL,
    tool_catalog_checksum CHAR(64) NOT NULL,
    rag_config_snapshot_json JSON NOT NULL,
    git_commit VARCHAR(64) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE/ARCHIVED',
    created_by VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_config_version_name (version_name),
    KEY idx_agent_config_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable evaluation Agent configuration';

-- ---- 3. 新表：ai_agent_run（可观测 Agent 主记录）-----------------------------

CREATE TABLE IF NOT EXISTS ai_agent_run (
    run_id VARCHAR(36) NOT NULL,
    invocation_key VARCHAR(128) NOT NULL,
    evaluation_run_id VARCHAR(36) NULL,
    case_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    evaluation_mode TINYINT(1) NOT NULL DEFAULT 0,
    query_text VARCHAR(2000) NOT NULL,
    route VARCHAR(32) NULL,
    route_reason VARCHAR(512) NULL,
    final_answer MEDIUMTEXT NULL,
    agent_config_version_id BIGINT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED/UNSUPPORTED_SIDE_EFFECT/TIMED_OUT',
    duration_ms BIGINT NULL,
    draft_changed TINYINT(1) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_ai_agent_run_invocation (invocation_key),
    UNIQUE KEY uk_ai_agent_run_eval_case (evaluation_run_id, case_id),
    KEY idx_ai_agent_run_status_time (status, started_at),
    KEY idx_ai_agent_run_eval_time (evaluation_run_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Observable Chat Agent run master';

-- ---- 4. 新表：agent_eval_case_result（Judge/规则结果，与排名表分离）-----------

CREATE TABLE IF NOT EXISTS agent_eval_case_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(36) NOT NULL,
    case_id BIGINT NOT NULL,
    agent_run_id VARCHAR(36) NULL,
    passed TINYINT(1) NOT NULL DEFAULT 0,
    agent_success TINYINT(1) NOT NULL DEFAULT 0,
    route_accuracy TINYINT(1) NULL,
    required_tool_hit TINYINT(1) NULL,
    forbidden_tool_called TINYINT(1) NULL,
    tool_success_rate DECIMAL(8,6) NULL,
    citation_present TINYINT(1) NULL,
    expected_abstention TINYINT(1) NULL,
    trace_complete TINYINT(1) NOT NULL DEFAULT 0,
    faithfulness DECIMAL(5,4) NULL,
    answer_relevance DECIMAL(5,4) NULL,
    citation_correctness DECIMAL(5,4) NULL,
    judge_variance DECIMAL(5,4) NULL,
    judge_passed TINYINT(1) NULL,
    rule_result_json JSON NOT NULL,
    judge_result_json JSON NULL,
    failure_reasons_json JSON NOT NULL,
    latency_ms BIGINT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    result_checksum CHAR(64) NOT NULL,
    technical_error_code VARCHAR(64) NULL,
    technical_error_summary VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_eval_result_run_case (run_id, case_id),
    KEY idx_agent_eval_result_run_passed (run_id, passed),
    KEY idx_agent_eval_result_agent_run (agent_run_id),
    CONSTRAINT fk_agent_eval_result_run FOREIGN KEY (run_id) REFERENCES rag_eval_run(run_id),
    CONSTRAINT fk_agent_eval_result_case FOREIGN KEY (case_id) REFERENCES rag_eval_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-case Agent E2E evaluation outcome';

-- 注：三张 trace 表（ai_tool_call_trace/ai_llm_call_trace/rag_retrieval_trace）
-- 不加 ai_agent_run 外键——它们是 fail-open 观测写入，强外键会让观测故障反向影响在线 Agent。

