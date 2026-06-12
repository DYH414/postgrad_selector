-- Refresh program-year data quality from score, plan, and admission-result tables.
-- Safe to run repeatedly.

INSERT INTO program_year_data_quality (
  program_id,
  year,
  has_score,
  has_plan,
  has_result,
  has_official_source,
  has_conflict,
  completeness_level,
  missing_fields,
  last_checked_at
)
SELECT
  py.program_id,
  py.year,
  IF(ascore.program_id IS NULL, 0, 1) AS has_score,
  IF(ap.program_id IS NULL, 0, 1) AS has_plan,
  IF(ar.program_id IS NULL, 0, 1) AS has_result,
  0 AS has_official_source,
  0 AS has_conflict,
  CASE
    WHEN ascore.score_line IS NOT NULL
      AND ar.min_admitted_score IS NOT NULL
      AND ar.max_admitted_score IS NOT NULL
      AND ar.avg_admitted_score IS NOT NULL
      AND (ap.total_plan IS NOT NULL OR ap.unified_exam_quota IS NOT NULL OR ar.admitted_count IS NOT NULL)
      THEN 'A'
    WHEN ascore.score_line IS NOT NULL
      AND (ar.avg_admitted_score IS NOT NULL
        OR ar.min_admitted_score IS NOT NULL
        OR ap.total_plan IS NOT NULL
        OR ap.unified_exam_quota IS NOT NULL)
      THEN 'B'
    WHEN ascore.score_line IS NOT NULL THEN 'C'
    ELSE 'D'
  END AS completeness_level,
  JSON_ARRAYAGG(missing.field_name) AS missing_fields,
  NOW() AS last_checked_at
FROM (
  SELECT program_id, year FROM admission_score
  UNION
  SELECT program_id, year FROM admission_plan
  UNION
  SELECT program_id, year FROM admission_result
) py
LEFT JOIN admission_score ascore ON ascore.program_id = py.program_id AND ascore.year = py.year
LEFT JOIN admission_plan ap ON ap.program_id = py.program_id AND ap.year = py.year
LEFT JOIN admission_result ar ON ar.program_id = py.program_id AND ar.year = py.year
JOIN JSON_TABLE(
  JSON_ARRAY(
    IF(ascore.program_id IS NULL, 'score', NULL),
    IF(ap.program_id IS NULL, 'plan', NULL),
    IF(ar.program_id IS NULL, 'result', NULL),
    'official_source'
  ),
  '$[*]' COLUMNS (field_name VARCHAR(32) PATH '$')
) missing
WHERE missing.field_name IS NOT NULL
GROUP BY
  py.program_id,
  py.year,
  ascore.program_id,
  ap.program_id,
  ar.program_id
ON DUPLICATE KEY UPDATE
  has_score = VALUES(has_score),
  has_plan = VALUES(has_plan),
  has_result = VALUES(has_result),
  has_official_source = VALUES(has_official_source),
  has_conflict = VALUES(has_conflict),
  completeness_level = VALUES(completeness_level),
  missing_fields = VALUES(missing_fields),
  last_checked_at = VALUES(last_checked_at);
