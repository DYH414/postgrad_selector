## Context

The project already has a single retrieval path through `RagRetrievalService`, deterministic metric calculation in `RagEvaluationService`, frozen/safety JSONL loaders with checksum verification, persistent RAG traces, Qdrant alias metadata, MyBatis persistence, and a RuoYi administrator frontend. The missing layer is an operator-facing control plane for curating datasets and retaining comparable experiment results.

LangChain4j 1.15 provides model, embedding-store, and content-retriever listeners plus experimental Micrometer observation modules. These are useful for runtime telemetry but do not provide a Spring Boot dataset registry, human annotation queue, frozen dataset publishing, experiment database, or comparison UI. LangSmith provides those product capabilities as an external SaaS, but adopting it would duplicate the project's current trace storage, externalize potentially sensitive queries, and add a second identity and operational surface. Ragas primarily targets Python evaluation workflows and would require a sidecar runtime for metrics the project already computes in Java.

The implementation therefore keeps the evaluation control plane deliberately thin and reuses existing project components rather than rebuilding retrieval or adding a new platform dependency.

## Goals / Non-Goals

**Goals:**

- Make RAG evaluation repeatable, persistent, manually maintainable, and comparable from the administrator UI.
- Preserve Git-auditable frozen JSONL datasets while adding MySQL-backed interactive development datasets.
- Reuse the current retrieval path and deterministic metrics so online and offline behavior do not drift.
- Turn persistent production traces into reviewed regression cases.
- Produce reproducible JSON and Markdown reports suitable for engineering review and interview evidence.

**Non-Goals:**

- Adding BM25, sparse vectors, query rewriting, document upload, OCR, or a knowledge-base CMS.
- Adding LLM-as-judge or full Agent trajectory evaluation in this change.
- Replacing persistent project traces with LangSmith, Langfuse, Phoenix, or an OpenTelemetry collector.
- Allowing evaluation experiments to edit production retrieval configuration.
- Reimplementing Qdrant retrieval, BGE reranking, or existing metric formulas.

## Decisions

### 1. Use MySQL for the workbench and JSONL for frozen interchange

Four primary tables will be added:

- `rag_eval_dataset`: logical dataset/version, type, state, checksum, case count, source version, and audit fields.
- `rag_eval_case`: query, category, expected metadata JSON, relevant conditions JSON, source references JSON, no-answer flag, tags, and review fields.
- `rag_eval_run`: status, mode set, immutable parameter snapshot, dataset checksum, alias/collection, models, Git commit, aggregate metrics, progress, and timestamps.
- `rag_eval_case_result`: run/case/mode, effective filters, candidate ranking summary, selected evidence, metrics, latency, technical error, human failure category, and review note.

MySQL supports normal RuoYi CRUD, filtering, pagination, and durable history. JSONL and manifests remain the canonical portable representation for frozen snapshots. A canonical serializer sorts cases by stable case key and JSON object keys before hashing.

Alternative considered: keep all datasets in repository files. Rejected because administrators cannot safely annotate or curate them through the running application. Alternative considered: make MySQL the only source. Rejected because frozen datasets would lose simple Git review and checksum reproducibility.

### 2. Adapt the existing evaluation engine instead of creating a second engine

`RagEvaluationService` remains responsible for request construction and metric calculation. A new orchestration service loads persisted cases, converts them to `RagEvaluationCase`, invokes the existing evaluator, and persists normalized outputs. Metric logic must be extracted only where necessary to accept a dataset and a result sink; formulas are not duplicated in controller or mapper code.

`RagRetrievalService` remains the only retrieval implementation. Evaluation run parameters are passed explicitly through the existing evaluation request DTO and cannot mutate shared configuration beans.

Alternative considered: integrate LangSmith or Ragas as the primary runner. Rejected for this phase because the current deterministic Java evaluator already covers retrieval ranking and safety metrics, while external integration would add operational complexity without replacing the required domain-specific ground truth.

### 3. Separate editable datasets from immutable snapshots

Dataset state transitions are:

```text
DRAFT DEV -> READY DEV -> FROZEN
                 |          |
                 +-> copy -> new DRAFT DEV version
```

`SAFETY` is a dataset type, not a ranking category, and is evaluated with abstention metrics. Freezing creates a new immutable record and copied cases in one database transaction, then writes the portable export. Failure to write the export rolls back the freeze transaction or leaves the new version unpublished.

### 4. Persist raw-enough evidence for diagnosis without duplicating full documents

Case results store chunk/source identifiers, short previews, expected/actual ranks, vector/rerank scores, effective filters, and stage latencies. They do not copy complete Chunk bodies or vectors. Full text remains in Qdrant or the document source and may be resolved on demand when available.

This keeps reports explainable while controlling table growth and sensitive-content exposure.

### 5. Run evaluations asynchronously with bounded capacity

A dedicated Spring `ThreadPoolTaskExecutor` with a small configurable pool and queue runs experiments. Submission persists `PENDING` before scheduling. Workers atomically transition to `RUNNING`, write each case result independently, update progress in batches, and finish as `SUCCEEDED`, `FAILED`, `CANCELLED`, or `INTERRUPTED`.

The UI polls run status; SSE is unnecessary for a first version because runs are administrative background jobs and polling is simpler and more robust. Cancellation is cooperative between cases.

### 6. Build the UI as one focused workbench

The administrator page uses three tabs:

1. **Datasets**: versions, lifecycle, case CRUD, import/export, freeze action.
2. **Experiments**: start run, view progress and metrics, filter failures, open case details.
3. **Compare**: select baseline/candidate runs and inspect aggregate plus per-case deltas.

Trace-to-case is initiated from the existing RAG Trace table and opens a pre-filled case dialog in the workbench. Existing RuoYi permission directives protect view, edit, run, freeze, and export actions separately.

### 7. Reuse framework features only where they reduce project code

- MyBatis and the existing pagination helpers handle persistence and listing.
- RuoYi security, audit annotations, exception handling, upload/download response helpers, and Element UI tables/forms are reused.
- Jackson handles canonical DTO serialization; no handwritten JSON parsing is introduced.
- Existing LangChain4j retrieval components remain untouched. Its listeners may later feed Micrometer timing, but experimental listener APIs are not made a hard dependency of this workbench.
- Existing Qdrant Alias/Collection resolution is captured in each run snapshot.

## Risks / Trade-offs

- [Evaluation tables grow with every run] -> Store bounded previews rather than full chunks, index run/dataset/case keys, and add explicit retention for non-frozen exploratory runs.
- [Frozen database rows and exported JSONL diverge] -> Generate both from one canonical snapshot transaction and verify checksum on import and before every frozen run.
- [Administrative runs contend with online inference] -> Use a dedicated bounded executor, reject excess submissions, and cap per-run concurrency.
- [Human labels are inconsistent] -> Require categories and structured relevant conditions, record reviewer identity, and retain notes without silently modifying historical results.
- [Framework experimental APIs change] -> Do not couple core evaluation persistence to experimental LangChain4j observability listeners.
- [Comparisons mislead across different datasets] -> Compute deltas only for identical dataset checksums and display incompatible runs without improvement percentages.

## Migration Plan

1. Add idempotent migration SQL and consolidated runtime-schema definitions for the four evaluation tables and menu permissions.
2. Import current `frozen-v1` and `safety-v1` resources as initial immutable dataset versions; retain resources as the audited source and rollback artifact.
3. Deploy backend APIs and verify existing `/postgrad/rag/eval/frozen-v1` remains compatible.
4. Deploy the administrator workbench behind new permissions.
5. Run the imported frozen dataset and compare its persisted metrics with the existing JSON report before enabling dataset freezing.

Rollback removes the menu entry and disables new endpoints while leaving evaluation tables intact. Existing file-based evaluation and online RAG continue to work independently.

## Open Questions

None blocking. Initial retention defaults and executor sizes remain configuration values and will be documented with conservative defaults.
