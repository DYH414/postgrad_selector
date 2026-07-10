## 1. Baseline Audit and Test Targets

- [x] 1.1 Confirm current backend endpoints for manual reindex, database generation, database reindex, search-test, traces, and evaluation.
- [x] 1.2 Confirm current admin frontend routes and API wrappers for RAG and AI observability.
- [x] 1.3 Confirm current user frontend report/chat components and whether `ragCitations` is rendered.
- [x] 1.4 Add or identify focused failing tests for metadata search filters, evidence retrieval, report citations, and user citation rendering.

## 2. Backend RAG Evidence Service

- [x] 2.1 Define a program evidence request/response contract containing query, program id, optional school/year/doc type/section filters, selected chunks, and citation summaries.
- [x] 2.2 Implement a `RagEvidenceService` or equivalent service that builds metadata filters before vector retrieval.
- [x] 2.3 Reuse existing embedding, Qdrant, rerank, and chunk result mapping instead of duplicating retrieval code.
- [x] 2.4 Implement controlled fallback behavior for empty strict-filter results and record whether fallback was used.
- [x] 2.5 Add unit tests for program-scoped retrieval, doc type/section filters, empty result fallback, and citation mapping from selected chunks.

## 3. Recommendation Chain Integration

- [x] 3.1 Add an AI chat tool such as `getProgramEvidence(programId, question)` that calls the evidence service.
- [x] 3.2 Update the chat system prompt so program-specific recommendation explanations use structured draft data plus evidence retrieval when appropriate.
- [x] 3.3 Integrate RAG evidence retrieval into report generation so `ragCitations` can be built from selected chunks, not only `SchoolFact`.
- [x] 3.4 Preserve MySQL facts as authoritative for candidate identities, scores, plans, years, and source URLs.
- [x] 3.5 Add graceful report behavior when evidence retrieval is unavailable or returns no chunks.
- [x] 3.6 Add focused backend tests for chat evidence tool wiring and report citation population.

## 4. Admin Backend and Observability

- [x] 4.1 Ensure `/postgrad/rag/generate-documents` and `/postgrad/rag/reindex-database` return operator-friendly summaries.
- [x] 4.2 Extend status output if needed to distinguish manual and database RAG last-run modes.
- [x] 4.3 Record RAG traces for recommendation evidence retrieval with filters, fallback mode, retrieved count, selected count, top sources, and outcome.
- [x] 4.4 Ensure trace responses expose enough metadata for admin UI operation labels without leaking unnecessary internals.
- [x] 4.5 Add tests for trace recording in evidence success, fallback, and failure paths.

## 5. Admin Frontend Integration

- [x] 5.1 Add `generateDatabaseDocuments` and `reindexDatabaseRag` API wrappers in `ruoyi-ui/src/api/postgrad/rag.js`.
- [x] 5.2 Add database RAG controls to the RAG management page: start year, end year, limit, generate documents, and database reindex.
- [x] 5.3 Relabel existing manual reindex controls so operators understand the difference between `rag-documents/` and `rag-generated/`.
- [x] 5.4 Add metadata filter controls to search-test UI for school id, program id, year, document type, and section.
- [x] 5.5 Expand search-test result columns to show database RAG metadata and active filters.
- [x] 5.6 Update trace UI labels for search-test, AI chat, recommendation evidence, and database operations.
- [ ] 5.7 Verify medium-width admin layout remains readable for KPI cards and result tables.

## 6. User Frontend Integration

- [x] 6.1 Add report citation rendering in `user-ui` for backend `ragCitations`.
- [x] 6.2 Group citations by candidate/program metadata when available and provide a general evidence section for unscoped citations.
- [x] 6.3 Add compact source indicators or expandable evidence details for chat messages when evidence metadata exists.
- [x] 6.4 Keep reports and chat messages clean when no RAG evidence is returned.
- [x] 6.5 Add component-level tests or static checks for citation rendering and no-citation fallback.

## 7. Documentation and Acceptance

- [x] 7.1 Update README RAG description to distinguish manual general knowledge and database recommendation evidence.
- [x] 7.2 Update `docs/RAG-Qdrant-运维说明.md` with database generation, database reindexing, metadata search-test, and trace verification steps.
- [x] 7.3 Update AI recommendation documentation to explain where RAG is used in chat/report explanation rather than ranking.
- [x] 7.4 Add an acceptance checklist for MySQL export -> generated docs -> Qdrant -> metadata retrieval -> rerank -> AI/report citations -> admin trace.
- [x] 7.5 Document known limitations such as sparse data, fallback behavior, and generated Markdown commit policy.

## 8. Verification

- [x] 8.1 Run focused `ruoyi-postgrad` tests for RAG DTOs, metadata filters, evidence service, report citations, and traces.
- [x] 8.2 Run admin frontend build or available checks for `ruoyi-ui`.
- [x] 8.3 Run user frontend build or available checks for `user-ui`.
- [ ] 8.4 Manually verify admin RAG page can generate documents, reindex database RAG, run metadata search-test, and show traces.
- [ ] 8.5 Manually verify user AI recommendation report displays citations after RAG evidence is available.
- [x] 8.6 Validate the OpenSpec change before implementation is considered complete.

## 9. Guided Admin Database RAG Operation

- [x] 9.1 Add a generation-only warning state after `generateDatabaseDocuments` succeeds, explaining that Qdrant has not been updated yet.
- [x] 9.2 Add a combined `生成并索引数据库 RAG` action or equivalent guided flow that runs document generation and database reindexing in sequence.
- [x] 9.3 Ensure combined flow preserves the document generation summary if indexing fails.
- [x] 9.4 Refresh status, traces, and database operation result after indexing completes.
- [x] 9.5 Add frontend tests or static checks for generation-only warning, combined action success, and combined action failure copy.

## 10. Grounded RAG Evaluation

- [x] 10.1 Replace placeholder evaluation cases with known current records such as `schoolId=56`, `programId=2854`, `year=2025`, and expected sections.
- [x] 10.2 Add evaluation case metadata fields for expected school id, program id, year, expected section, and expected source-data availability.
- [x] 10.3 Update evaluation pass logic so a case only passes when selected evidence matches expected scope and expected section.
- [x] 10.4 Add failure reasons for `NO_RETRIEVAL`, `WRONG_SCOPE`, `MISSING_SECTION`, and `SOURCE_DATA_MISSING`.
- [x] 10.5 Record a `rag-evaluate` trace containing total cases, passed cases, failed cases, and representative failure reasons.
- [x] 10.6 Add backend tests for grounded score-line, admission-plan, admission-result, wrong-scope, and source-data-missing evaluation outcomes.

## 11. Trace and Acceptance Hardening

- [x] 11.1 Extend search-test traces to include metadata filters, embedding latency, retrieval latency, rerank latency, selected chunks, and empty-result diagnostics.
- [x] 11.2 Ensure the admin trace UI labels `rag-evaluate` and shows evaluation summary fields without raw debug payload noise.
- [ ] 11.3 Run interface acceptance: database generate -> database index -> search-test -> user chat evidence -> report citations -> trace inspection.
- [ ] 11.4 Confirm the known user prompt for `杭州电子科技大学-电子信息-网安方向` produces `getProgramEvidence` tool usage without hallucinated tool errors after backend restart.
- [x] 11.5 Update `docs/RAG-全链路验收清单.md` with the combined operation, grounded evaluation threshold, and exact acceptance queries.
