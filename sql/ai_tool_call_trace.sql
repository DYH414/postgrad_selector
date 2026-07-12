CREATE TABLE IF NOT EXISTS ai_tool_call_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    call_id VARCHAR(36) NOT NULL,
    parent_call_id VARCHAR(36) NULL,
    run_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    operation_key VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    tool_category VARCHAR(24) NOT NULL,
    budget_group VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_code VARCHAR(64) NULL,
    retryable TINYINT(1) NOT NULL DEFAULT 0,
    confirmation_required TINYINT(1) NOT NULL DEFAULT 0,
    arguments_summary VARCHAR(1000) NULL,
    result_summary VARCHAR(1000) NULL,
    result_token_estimate INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    confirmation_expires_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_call_id (call_id),
    KEY idx_tool_run_time (run_id, started_at),
    KEY idx_tool_status_time (status, started_at),
    KEY idx_tool_user_time (user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Tool 调用追踪';

DELETE FROM ai_tool_call_trace
WHERE started_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
