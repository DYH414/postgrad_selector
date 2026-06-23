-- AI 推荐 12 表联查性能优化索引
-- 覆盖 RecommendationMapper.selectCandidates 核心查询

-- admission_score: program_id + year + score_line 复合索引
ALTER TABLE admission_score ADD INDEX idx_program_year_score (program_id, year, score_line);

-- admission_plan: program_id + year 复合索引
ALTER TABLE admission_plan ADD INDEX idx_program_year (program_id, year);

-- admission_result: program_id + year 复合索引
ALTER TABLE admission_result ADD INDEX idx_program_year (program_id, year);

-- program_year_data_quality: program_id + year 复合索引
ALTER TABLE program_year_data_quality ADD INDEX idx_program_year (program_id, year);
