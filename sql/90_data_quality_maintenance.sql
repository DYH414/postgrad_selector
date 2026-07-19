-- Refresh program-year data quality from score, plan, and admission-result facts.
-- Safe to run repeatedly; this script only maintains derived quality summaries.

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
  facts.program_id,
  facts.year,
  facts.has_score,
  facts.has_plan,
  facts.has_result,
  facts.has_official_source,
  0 AS has_conflict,
  CASE
    WHEN facts.score_line IS NOT NULL
      AND facts.min_admitted_score IS NOT NULL
      AND facts.max_admitted_score IS NOT NULL
      AND facts.avg_admitted_score IS NOT NULL
      AND (facts.total_plan IS NOT NULL
        OR facts.unified_exam_quota IS NOT NULL
        OR facts.admitted_count IS NOT NULL)
      THEN 'A'
    WHEN facts.score_line IS NOT NULL
      AND (facts.avg_admitted_score IS NOT NULL
        OR facts.min_admitted_score IS NOT NULL
        OR facts.total_plan IS NOT NULL
        OR facts.unified_exam_quota IS NOT NULL)
      THEN 'B'
    WHEN facts.score_line IS NOT NULL THEN 'C'
    ELSE 'D'
  END AS completeness_level,
  JSON_MERGE_PRESERVE(
    IF(facts.has_score = 0, JSON_ARRAY('score'), JSON_ARRAY()),
    IF(facts.has_plan = 0, JSON_ARRAY('plan'), JSON_ARRAY()),
    IF(facts.has_result = 0, JSON_ARRAY('result'), JSON_ARRAY()),
    IF(facts.has_official_source = 0, JSON_ARRAY('official_source'), JSON_ARRAY())
  ) AS missing_fields,
  NOW() AS last_checked_at
FROM (
  SELECT
    py.program_id,
    py.year,
    IF(ascore.program_id IS NULL, 0, 1) AS has_score,
    IF(ap.program_id IS NULL, 0, 1) AS has_plan,
    IF(ar.program_id IS NULL, 0, 1) AS has_result,
    COALESCE(official_source.has_official_source, 0) AS has_official_source,
    ascore.score_line,
    ap.total_plan,
    ap.unified_exam_quota,
    ar.admitted_count,
    ar.min_admitted_score,
    ar.avg_admitted_score,
    ar.max_admitted_score
  FROM (
    SELECT program_id, year FROM admission_score
    UNION
    SELECT program_id, year FROM admission_plan
    UNION
    SELECT program_id, year FROM admission_result
  ) py
  LEFT JOIN admission_score ascore
    ON ascore.program_id = py.program_id AND ascore.year = py.year
  LEFT JOIN admission_plan ap
    ON ap.program_id = py.program_id AND ap.year = py.year
  LEFT JOIN admission_result ar
    ON ar.program_id = py.program_id AND ar.year = py.year
  LEFT JOIN (
    SELECT
      source_facts.program_id,
      source_facts.year,
      MAX(source_facts.is_official) AS has_official_source
    FROM (
      SELECT
        ascore.program_id,
        ascore.year,
        IF(ds.source_type = 'OFFICIAL', 1, 0) AS is_official
      FROM admission_score ascore
      LEFT JOIN data_source ds ON ds.id = ascore.source_id

      UNION ALL

      SELECT
        ap.program_id,
        ap.year,
        IF(ds.source_type = 'OFFICIAL', 1, 0) AS is_official
      FROM admission_plan ap
      LEFT JOIN data_source ds ON ds.id = ap.source_id

      UNION ALL

      SELECT
        ar.program_id,
        ar.year,
        IF(ds.source_type = 'OFFICIAL', 1, 0) AS is_official
      FROM admission_result ar
      LEFT JOIN data_source ds ON ds.id = ar.source_id
    ) source_facts
    GROUP BY source_facts.program_id, source_facts.year
  ) official_source
    ON official_source.program_id = py.program_id
    AND official_source.year = py.year
) facts
ON DUPLICATE KEY UPDATE
  has_score = VALUES(has_score),
  has_plan = VALUES(has_plan),
  has_result = VALUES(has_result),
  has_official_source = VALUES(has_official_source),
  has_conflict = VALUES(has_conflict),
  completeness_level = VALUES(completeness_level),
  missing_fields = VALUES(missing_fields),
  last_checked_at = VALUES(last_checked_at);
