# RAG Document Directory And Metadata Design

## Background

The current database-backed RAG output is generated under:

```text
ruoyi-postgrad/src/main/resources/rag-generated/program-year/2025/
```

Each file represents one school program in one year, using `doc_type: program_year_summary`.
This is a useful first step, but the recommendation product needs a stronger evidence layer:

1. explain why a program was recommended;
2. show historical trend evidence;
3. cite the source behind report conclusions;
4. support metadata-filtered Qdrant retrieval;
5. keep MySQL as the source of structured truth.

The new design treats RAG documents as recommendation evidence documents, not raw table exports.

## Goals

1. Define a stable `resources/rag-generated/` directory layout.
2. Introduce a metadata schema that supports Qdrant filtering and citation traceability.
3. Prioritize `program-history` documents because recommendation questions are usually trend-based.
4. Preserve `program-year` documents for exact yearly facts.
5. Add a `data-quality` document family for source and missing-field risk explanations.
6. Keep the first implementation small enough to build and test quickly.

## Non-Goals

1. Do not redesign candidate pool SQL, tier classification, or selection validation.
2. Do not let RAG replace MySQL facts for exact score, plan, and admission values.
3. Do not introduce one Qdrant collection per school or per document family.
4. Do not build an editorial CMS for hand-authored RAG documents.
5. Do not add AI chat tools in this design; that should be a later implementation spec.

## Recommended Approach

Use a business-object-oriented document model:

```text
MySQL structured facts
  -> Markdown evidence documents
  -> semantic sections
  -> structured chunks with metadata
  -> Qdrant payload filters
  -> AI chat evidence and report citations
```

The recommended first version includes three document families:

1. `program-history`
2. `program-year`
3. `data-quality`

`school-profile` is useful, but it can wait until the recommendation evidence layer is stable.

## Directory Layout

All generated RAG documents should live under the Java resources tree:

```text
ruoyi-postgrad/src/main/resources/rag-generated/
  README.md

  program-history/
    program_history_{schoolId}_{programId}.md

  program-year/
    2023/
      program_year_{schoolId}_{programId}_2023.md
    2024/
      program_year_{schoolId}_{programId}_2024.md
    2025/
      program_year_{schoolId}_{programId}_2025.md

  data-quality/
    program_quality_{schoolId}_{programId}.md

  eval/
    rag_eval_recommendation.jsonl
    rag_eval_citation.jsonl
```

The `README.md` should explain that files are generated from MySQL and should not be edited by hand.

## Document Families

### program-history

Purpose:

1. recommendation reasoning;
2. historical score and admission trend explanation;
3. report citation for selected programs;
4. risk explanation when a program has unstable data.

Grain:

```text
one program across all available years
```

File name:

```text
program_history_{schoolId}_{programId}.md
```

Recommended sections:

```text
basic_info
exam_subjects
score_trend
admission_trend
plan_trend
risk_factors
source_quality
```

### program-year

Purpose:

1. exact yearly facts;
2. citation for a specific year;
3. precise answers such as "2025 retest line" or "2024 admitted average".

Grain:

```text
one program in one year
```

File name:

```text
program_year_{schoolId}_{programId}_{year}.md
```

Recommended sections:

```text
basic_info
exam_subjects
admission_score
admission_plan
admission_result
source_quality
```

### data-quality

Purpose:

1. explain missing years and missing fields;
2. surface official-source coverage;
3. warn the report when evidence is weak;
4. support admin and evaluation checks.

Grain:

```text
one program across all available years
```

File name:

```text
program_quality_{schoolId}_{programId}.md
```

Recommended sections:

```text
coverage_summary
missing_years
missing_fields
source_conflicts
recommendation_warning
```

### school-profile

This family is intentionally deferred.

When added later, it should cover:

```text
school_overview
location_tier
college_programs
discipline_features
source_quality
```

## Metadata Schema

All chunks should use the same metadata vocabulary. Values should be serialized as strings unless there is a proven need for typed Qdrant filtering.

Required metadata:

```yaml
schema_version: "rag-doc-v2"
doc_type: "program_history"
doc_id: "program_history_1_148"
chunk_id: "program_history:1:148:score_trend:a1b2c3d4e5f6"
chunk_index: "1"
section: "score_trend"
section_title: "历年分数趋势"

school_id: "1"
school_name: "广州大学"
province: "广东"
city: "广州"
school_tier: "PUBLIC_REGULAR"

college_id: "128"
college_name: "网络空间安全学院"
program_id: "148"
program_code: "083900"
program_name: "网络空间安全"
degree_type: "academic"
study_mode: "full_time"
exam_type: "GENERAL_500"
is_408: "true"

year: ""
year_start: "2023"
year_end: "2025"
years_available: "2023,2024,2025"

source_owner: "N诺考研择校"
source_name: "N诺考研择校-广州大学"
source_url: "https://noobdream.com/schoolinfo/135/"
source_priority: "secondary"
has_official_source: "false"

quality_level: "B"
missing_fields: "score"
has_conflict: "false"
data_completeness: "partial"

content_hash: "..."
generated_at: "2026-07-07T00:00:00"
embedding_version: "bge-m3-v1"
```

## doc_type Values

The new vocabulary should be concise and stable:

```text
program_history
program_year
data_quality
school_profile
methodology
policy
```

The current `program_year_summary` value should be treated as legacy. New generated documents should use `program_year`.

## section Values

Recommended `program_history` sections:

```text
basic_info
exam_subjects
score_trend
admission_trend
plan_trend
risk_factors
source_quality
```

Recommended `program_year` sections:

```text
basic_info
exam_subjects
admission_score
admission_plan
admission_result
source_quality
```

Recommended `data_quality` sections:

```text
coverage_summary
missing_years
missing_fields
source_conflicts
recommendation_warning
```

## program-history Template

```markdown
---
schema_version: "rag-doc-v2"
doc_type: "program_history"
doc_id: "program_history_1_148"
school_id: "1"
school_name: "广州大学"
program_id: "148"
program_name: "网络空间安全"
year_start: "2023"
year_end: "2025"
years_available: "2023,2024,2025"
quality_level: "B"
missing_fields: "score"
source_owner: "N诺考研择校"
source_url: "https://noobdream.com/schoolinfo/135/"
embedding_version: "bge-m3-v1"
---

# 广州大学 网络空间安全 历年招生录取事实

## 基本信息
学校、地区、学院、专业代码、培养方式、考试科目。

## 历年分数趋势
| 年份 | 复试线 | 最低录取分 | 平均录取分 | 最高录取分 |
|---|---:|---:|---:|---:|
| 2023 | - | - | - | - |
| 2024 | - | - | - | - |
| 2025 | - | - | - | - |

## 历年招生与录取
| 年份 | 招生计划 | 统考名额 | 复试人数 | 录取人数 |
|---|---:|---:|---:|---:|

## 推荐风险提示
- 如果录取均分高于用户估分较多，说明存在分数风险。
- 如果统考名额较少，说明波动风险较高。
- 如果年份数据缺失，报告中应提示数据可信度有限。

## 来源与质量
来源、链接、缺失字段、是否官方来源、是否冲突。
```

## Chunking Rules

Chunking should follow semantic sections rather than fixed character length.

Rules:

1. One section should normally become one chunk.
2. Tables should stay attached to their section heading.
3. `score_trend`, `admission_trend`, and `plan_trend` should not be split by year in the first version because trend questions need adjacent years.
4. Very long sections may be split by subsection, but each split must keep the same `doc_id` and use a stable `chunk_index`.
5. `content_hash` should be calculated from normalized chunk text.

## Retrieval Rules

The future `getProgramEvidence(programId, question)` service should use payload filters first, then vector search, then rerank.

Examples:

```text
Question: "这个专业风险是什么？"
Filters:
  program_id = 148
  doc_type in program_history,data_quality
  section in score_trend,admission_trend,plan_trend,risk_factors,source_quality
```

```text
Question: "2025 年复试线是多少？"
Filters:
  program_id = 148
  doc_type = program_year
  year = 2025
  section = admission_score
```

```text
Question: "为什么推荐它？"
Filters:
  program_id = 148
  doc_type in program_history,program_year
  section in score_trend,admission_trend,plan_trend,risk_factors
```

## Qdrant Payload Indexes

The existing single-collection strategy should remain:

```text
postgrad_rag_main
```

The most useful payload indexes are:

```text
school_id
program_id
doc_type
year
section
quality_level
```

## Integration With Recommendation Flow

This design keeps RAG out of hard filtering and validation.

Do not use RAG in:

1. candidate SQL selection;
2. tier classification;
3. `SelectionValidator`;
4. exact DB snapshot hydration.

Use RAG in:

1. post-selection evidence enrichment;
2. AI chat evidence lookup;
3. final report citations;
4. admin retrieval testing and evaluation.

## Evaluation Dataset

The first evaluation set should live under:

```text
ruoyi-postgrad/src/main/resources/rag-generated/eval/
```

Recommended files:

```text
rag_eval_recommendation.jsonl
rag_eval_citation.jsonl
```

Each row should contain:

```json
{
  "query": "广州大学网络空间安全近几年录取趋势怎么样？",
  "expected_program_id": "148",
  "expected_doc_type": "program_history",
  "expected_sections": ["score_trend", "admission_trend"]
}
```

## Rollout Plan

1. Add `program-history` document generation from MySQL.
2. Normalize new metadata to `rag-doc-v2`.
3. Keep existing `program-year` generation, but rename new `doc_type` to `program_year`.
4. Add `data-quality` generation for missing fields and source warnings.
5. Reindex Qdrant using the new document families.
6. Add retrieval tests that verify metadata-filtered recall.
7. Only after that, connect evidence retrieval to AI chat and final report citations.

## Acceptance Criteria

1. Generated files are grouped by document family under `resources/rag-generated/`.
2. `program-history` exists for each program with at least two available yearly records, or with one record plus a clear data-quality warning.
3. All chunks include `schema_version`, `doc_type`, `doc_id`, `chunk_id`, `program_id`, `school_id`, `section`, `content_hash`, and `embedding_version`.
4. `program_year_summary` is not used by newly generated documents.
5. Metadata-filtered search can restrict results by `program_id`, `doc_type`, `year`, and `section`.
6. Report citation work can consume these chunks without changing the directory design again.
