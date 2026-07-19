## Why

The project can already run deterministic RAG retrieval evaluations, but datasets and reports are primarily file- and endpoint-driven. Administrators need a persistent workbench to curate cases, repeat experiments, inspect regressions, and promote validated datasets without editing source files or relying on transient report artifacts.

## What Changes

- Add administrator-managed RAG evaluation datasets with editable `dev`, immutable `frozen`, and independent `safety` dataset types.
- Preserve JSONL plus SHA-256 manifests as the Git-auditable interchange and frozen snapshot format while using MySQL for interactive management and run history.
- Persist evaluation runs, parameter snapshots, aggregate metrics, and per-case outcomes produced by the existing `RagEvaluationService`.
- Add human annotation for expected metadata, relevant evidence, no-answer behavior, failure classification, and reviewer notes.
- Add experiment comparison, regression drill-down, and Markdown/JSON report export.
- Allow eligible production RAG traces to be copied into an editable dev dataset after explicit administrator review.
- Reuse the existing retrieval service, metric implementation, persistent trace model, MyBatis, RuoYi permissions, and Element UI components; do not introduce an external evaluation SaaS.
- Exclude BM25, document upload, LLM-as-judge, and production configuration mutation from this change.

## Capabilities

### New Capabilities
- `rag-evaluation-workbench`: Persistent dataset curation, evaluation execution, human annotation, experiment comparison, trace-to-dataset feedback, and report export for the existing RAG retrieval pipeline.

### Modified Capabilities

None. Existing search-test and trace-observability behavior remains compatible.

## Impact

- Backend: new evaluation domain entities, MyBatis mappers, application services, asynchronous run orchestration, and authenticated admin APIs in `ruoyi-postgrad` and `ruoyi-admin`.
- Database: new tables for datasets, cases, runs, and case results, added to the consolidated AI runtime schema and an idempotent migration.
- Admin frontend: a dedicated evaluation workbench view using the existing RuoYi/Element UI stack and API conventions.
- Existing code reused: `RagEvaluationService`, `RagRetrievalService`, `RagEvaluationCase`, `RagEvaluationResult`, `RagEvaluationRunReport`, dataset checksum logic, Qdrant alias metadata, and persistent RAG traces.
- Dependencies: no required external SaaS or Python runtime. LangChain4j observability listeners may complement runtime telemetry but do not replace the evaluation workbench.
