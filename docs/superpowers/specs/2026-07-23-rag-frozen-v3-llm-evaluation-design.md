# RAG Frozen V3 And LLM Evaluation Design

## Goal

Create a reproducible RAG benchmark that separates retrieval ranking, abstention, and answer-generation quality. Run it against the real Qdrant alias and configured LLM, and retain enough runtime metadata and per-case evidence to reproduce every reported metric.

## Scope

The change reuses the existing RAG evaluation workbench, `RagRetrievalService`, JSONL checksum verification, Qdrant alias metadata, and configured `ChatModel`.

It adds:

- `frozen-v3`: 36 positive retrieval cases.
- `safety-v3`: 12 no-answer cases.
- `answer-v1`: 12 answer-generation cases selected from the retrieval and safety sets.
- Correct relevance-unit deduplication for ranking metrics.
- A deterministic answer-evaluation rubric plus repeated LLM-as-judge scoring.
- A report containing dataset checksums, Git commit, Qdrant alias and collection, model names, retrieval parameters, aggregate metrics, and per-case outputs.

It does not change the user-facing recommendation flow or replace the existing administrator workbench.

## Dataset Design

### Frozen V3 Ranking

`frozen-v3` contains 36 positive cases:

| Segment | Count | Coverage |
| --- | ---: | --- |
| General knowledge | 12 | Exact, paraphrase, terminology, policy, composite questions |
| Admission guides | 24 | Tuition, duration, eligibility, scholarships, aliases, paraphrases and composite questions |

Ground truth is derived from the indexed Markdown evidence before retrieval output is inspected. Each positive case points to an exact reviewed `content_hash`, not merely a source filename, and carries stable IDs, difficulty, tags, and expected metadata filters.

The active architecture intentionally keeps score lines, enrollment plans, and other structured facts in MySQL-backed Tools. Those facts require a separate Agent/Tool benchmark and are not counted as Qdrant retrieval cases.

### Safety V3

`safety-v3` contains 12 cases split across:

- nonexistent school or program;
- schools missing from the current indexed corpus;
- off-topic questions;
- ambiguous or underspecified references.

Safety cases do not contribute to MRR, Hit@K, Recall, Precision, or nDCG. They contribute only to No-answer Accuracy and False Positive Rate.

### Answer V1

`answer-v1` contains 12 cases:

- four general-knowledge questions;
- four school admission-guide questions;
- two hybrid evidence questions;
- two expected-abstention questions.

Each case stores expected facts, forbidden claims, expected source prefixes, and whether the answer must abstain.

## Metric Semantics

Positive retrieval cases use:

- `MRR = mean(1 / first relevant rank)`;
- `Hit@K = cases with a relevant result in top K / positive cases`;
- `Recall@K = unique relevant units retrieved / expected relevant units`;
- `Precision@K = unique relevant units retrieved / K`;
- `nDCG@K = DCG / IDCG`, bounded to `[0, 1]`;
- paired Vector Only versus Vector + Rerank improvements, ties and regressions;
- P50 and P95 latency.

A relevance unit is the exact `content_hash` of a reviewed evidence chunk. Multiple copies of the same relevance unit count once for Recall and nDCG.

Filter Accuracy is reported with an evaluated-case count. Empty retrievals do not silently count as filter successes.

Safety cases use:

- `No-answer Accuracy = correct abstentions / safety cases`;
- `False Positive Rate = non-empty unsupported retrievals / safety cases`.

## LLM Evaluation

The answer stage uses the configured production `ChatModel` to produce an answer from the retrieved evidence only. The answer prompt requires citations and explicit abstention when evidence is insufficient.

Each answer is judged twice with a strict JSON rubric:

- faithfulness;
- answer relevance;
- citation correctness;
- abstention correctness.

The two judgments and their reasons are retained. The report exposes agreement and mean scores but never merges LLM-as-judge scores into deterministic retrieval metrics. Raw evidence, answer, citations and judge output remain available for human review.

The same configured model may act as generator and judge in the local benchmark. Reports must label this limitation and must not describe LLM-as-judge scores as human ground truth.

## Reproducibility

Every run records:

- dataset name, version, case count and SHA-256;
- Git commit and dirty-worktree flag;
- Qdrant alias and resolved collection;
- embedding, rerank, generator and judge model names;
- recall Top N, final Top K and rerank mode;
- UTC start and finish time;
- aggregate, segment and per-case metrics;
- evidence previews, generated answers and judge reasons;
- technical errors separately from quality failures.

## Reliability

- One technical case failure does not abort the run.
- Dataset checksum or case-count mismatch blocks the run.
- LLM output that cannot be parsed is recorded as a judge technical error, not a quality score.
- API or database failures are retained in the report without being converted to zero-quality answers.
- Existing `frozen-v1` and `safety-v1` resources remain unchanged.

## Acceptance Criteria

1. Metric tests prove duplicate chunks cannot produce `nDCG > 1`.
2. Positive and safety denominators are independent.
3. All three datasets pass case-count, identity and checksum validation.
4. A real run resolves `postgrad_rag_active` to its concrete collection.
5. Vector Only and Vector + Rerank run against exactly the same positive and safety cases.
6. Twelve answer cases produce persisted raw answers and two judge outputs per successful case.
7. The final Markdown and JSON reports contain enough metadata to reproduce the run.
8. Resume metrics are taken only from the generated report and include their sample size.
