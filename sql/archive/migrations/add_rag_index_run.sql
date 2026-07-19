-- ARCHIVED: Historical SQL. Do not use for fresh installation or batch production execution.
-- Current entry scripts are in the sql/ root directory.

-- sql/add_rag_index_run.sql
CREATE TABLE IF NOT EXISTS `rag_index_run` (
    `run_id`             VARCHAR(36)    NOT NULL COMMENT '批次UUID',
    `status`             VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/VALIDATING/SWITCHING/SUCCEEDED/FAILED/INTERRUPTED',
    `candidate_collection` VARCHAR(128)  NULL COMMENT '候选版本Collection名',
    `previous_collection`  VARCHAR(128)  NULL COMMENT '切换前活动版本',
    `embedding_model`    VARCHAR(64)    NOT NULL DEFAULT '' COMMENT 'Embedding模型',
    `embedding_dimension` INT           NOT NULL DEFAULT 0 COMMENT '向量维度',
    `start_year`         INT            NULL COMMENT '数据起始年份',
    `end_year`           INT            NULL COMMENT '数据结束年份',
    `general_document_count` INT       NOT NULL DEFAULT 0 COMMENT '通用文档数',
    `database_document_count` INT      NOT NULL DEFAULT 0 COMMENT '数据库文档数',
    `chunk_count`        INT            NOT NULL DEFAULT 0 COMMENT '预期Chunk总数',
    `indexed_chunk_count` INT           NOT NULL DEFAULT 0 COMMENT '已写入Chunk数',
    `progress_stage`     VARCHAR(32)    NULL COMMENT '当前阶段 assembling/embedding/validating/switching',
    `progress_percent`   INT            NOT NULL DEFAULT 0 COMMENT '进度百分比0-100',
    `error_code`         VARCHAR(32)    NULL COMMENT '脱敏错误码',
    `error_summary`      VARCHAR(512)   NULL COMMENT '脱敏错误摘要',
    `started_at`         DATETIME       NULL COMMENT '开始时间',
    `finished_at`        DATETIME       NULL COMMENT '结束时间',
    `created_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`run_id`),
    INDEX `idx_status_created` (`status`, `created_at`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG索引构建批次';
