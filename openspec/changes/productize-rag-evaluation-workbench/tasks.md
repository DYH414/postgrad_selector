## 1. Persistence foundation

- [x] 1.1 Add idempotent migration SQL and consolidated runtime-schema definitions for `rag_eval_dataset`, `rag_eval_case`, `rag_eval_run`, and `rag_eval_case_result`, including indexes, lifecycle constraints, and audit columns.
- [x] 1.2 Add evaluation domain objects, enums, MyBatis mappers, XML mappings, and mapper tests for dataset, case, run, and case-result persistence.
- [x] 1.3 Add RuoYi menu and button permissions for evaluation view, case editing, run execution, dataset freezing, comparison, and export.

## 2. Dataset and annotation lifecycle

- [x] 2.1 Implement dataset application services for DEV/READY/FROZEN/SAFETY lifecycle validation, optimistic update protection, pagination, and audit metadata.
- [x] 2.2 Implement case CRUD and validation for expected metadata, relevant evidence conditions, no-answer semantics, categories, difficulty, tags, and reviewer notes.
- [x] 2.3 Implement canonical JSONL serialization, deterministic ordering, SHA-256 manifest generation, and frozen-version immutability.
- [x] 2.4 Implement import of the existing `frozen-v1` and `safety-v1` resources and verify imported checksums and case counts against their manifests.
- [x] 2.5 Add unit and transactional tests covering valid edits, invalid ground truth, freeze rollback, immutable datasets, deterministic checksums, and version copying.

## 3. Persistent evaluation execution

- [x] 3.1 Refactor `RagEvaluationService` only as needed to accept persisted case collections and explicit run parameters while preserving current formulas and `/eval/frozen-v1` compatibility.
- [x] 3.2 Implement a dedicated bounded evaluation executor and run state machine with `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, and `INTERRUPTED` states.
- [x] 3.3 Persist immutable run snapshots containing dataset checksum, Qdrant alias and actual collection, embedding/rerank models, retrieval parameters, Git commit, and timestamps.
- [x] 3.4 Persist per-case results for vector-only and vector-rerank modes, including effective filters, expected/actual evidence, rank/score summaries, stage latency, metrics, and technical errors.
- [x] 3.5 Implement duplicate-run protection, cooperative cancellation between cases, partial-result preservation, progress updates, and startup recovery of interrupted runs.
- [x] 3.6 Add service tests proving evaluation uses `RagRetrievalService`, continues after one technical case failure, isolates executor capacity, and reproduces current frozen metrics from persisted results.

## 4. Diagnosis, feedback, comparison, and reports

- [x] 4.1 Implement case-result diagnosis with supported failure categories, reviewer identity, notes, and timestamps without mutating original machine output.
- [x] 4.2 Implement trace-to-dev-case conversion that pre-fills observed query and filters but requires explicit human ground-truth validation.
- [x] 4.3 Implement compatible-run comparison for metric and latency deltas plus improved, unchanged, and regressed cases; suppress improvement percentages across different dataset checksums.
- [x] 4.4 Implement JSON and Markdown report generation from persisted completed runs, including configuration, metrics, dataset/index identity, and regression summaries.
- [x] 4.5 Add tests for trace conversion authorization, incompatible comparisons, report reproducibility, unfinished-run export rejection, and sensitive-content truncation.

## 5. Administrator APIs and frontend workbench

- [x] 5.1 Add authenticated administrator APIs for dataset/case lifecycle, import/export, run submission/status/cancellation, result diagnosis, comparison, trace conversion, and report download.
- [x] 5.2 Add controller contract tests for validation, permissions, immutable dataset errors, duplicate submissions, pagination, and stable error codes.
- [x] 5.3 Add the RuoYi frontend API module and route for the RAG evaluation workbench without changing existing RAG status, search-test, trace, or reindex contracts.
- [x] 5.4 Build the Datasets tab with version list, lifecycle state, case table, annotation dialog, JSONL import/export, freeze confirmation, and clear empty/error states.
- [x] 5.5 Build the Experiments tab with parameter summary, run submission, polling progress, aggregate metrics, failure filters, and a case-detail drawer showing expected evidence and rank changes.
- [x] 5.6 Build the Compare tab with baseline/candidate selection, compatibility warning, aggregate deltas, latency deltas, and improved/unchanged/regressed case tables.
- [x] 5.7 Add a reviewed “add to dev dataset” action from the existing persistent RAG Trace table and verify it cannot target a frozen dataset.

## 6. Verification and documentation

- [x] 6.1 Run focused mapper, service, evaluator, and controller tests; run the full Maven test/package build and the admin frontend production build.
- [x] 6.2 Execute an end-to-end local acceptance: import frozen-v1/safety-v1, run both evaluation modes, restart the backend, verify persistence, compare runs, annotate a regression, and export JSON/Markdown reports.
- [x] 6.3 Verify online recommendation and RAG search-test latency/functionality remain unchanged while evaluations run and while the evaluation executor is saturated.
- [x] 6.4 Update README and RAG technical documentation with the workbench workflow, metric boundaries, permissions, retention, and the explicit exclusions of BM25, document upload, and LLM-as-judge.
- [x] 6.5 Audit dependencies and confirm the implementation reuses existing LangChain4j retrieval, MyBatis, RuoYi, Jackson, and Qdrant components without adding LangSmith, Ragas, a Python sidecar, or another observability backend.
