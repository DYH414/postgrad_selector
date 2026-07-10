## Context

The project now has two RAG knowledge paths:

- Manual general knowledge under `rag-documents/`, originally used by the LangChain4j `RetrievalAugmentor`.
- Database-generated recommendation knowledge under `rag-generated/`, built from MySQL program, admission, source, and quality data and indexed into Qdrant as semantic chunks with metadata.

The second path is closer to the enterprise-shaped RAG flow the product needs, but it is not yet complete at the product boundary. The administrator page still calls the old reindex endpoint, search testing does not expose filter controls, user reports do not render RAG citations, and AI chat lacks a program-scoped evidence tool. MySQL remains the structured source of truth; Qdrant should serve as an evidence retrieval and explanation layer, not as a replacement for deterministic database queries.

Current frontend boundaries:

- `ruoyi-ui` is the administrator console.
- `user-ui` is the user-facing recommendation experience.
- `ruoyi-postgrad` owns backend RAG, recommendation, report, and AI chat services.

## Goals / Non-Goals

**Goals:**

- Let administrators generate and index database RAG documents from the browser.
- Let administrators complete the production RAG setup through either a clearly labeled two-step flow or a combined generate-and-index action.
- Let administrators verify retrieval with metadata filters and inspect returned chunk metadata.
- Let administrators run a meaningful RAG evaluation suite based on concrete school/program/year metadata instead of placeholder prompts.
- Add a backend evidence retrieval service that can answer "what RAG evidence supports this program-specific recommendation?".
- Use RAG evidence in AI chat and final reports while preserving MySQL-backed factual fields.
- Render citations and source snippets in the user report.
- Expand observability so operators can see whether RAG is using manual or database-generated knowledge and whether evidence retrieval is working.
- Update documentation and acceptance checks to match the current RAG architecture.

**Non-Goals:**

- Do not replace deterministic MySQL recommendation ranking with vector search.
- Do not migrate Qdrant, embedding models, or rerank providers.
- Do not merge `user-ui` into `ruoyi-ui`.
- Do not require every historical program row to have perfect data before the flow can work.
- Do not expose internal ids or raw debug payloads to end users unless needed for support.
- Do not treat Markdown document generation alone as proof that Qdrant has been updated.

## Decisions

### Decision 1: Treat database RAG as a separate operational mode

The admin page should distinguish:

- Manual/general knowledge reindexing from `rag-documents/`.
- Database recommendation evidence generation and reindexing from `rag-generated/`.

The existing `/postgrad/rag/reindex` endpoint remains useful, but the primary production recommendation evidence path should call `/postgrad/rag/generate-documents` and `/postgrad/rag/reindex-database`.

Alternative considered: repoint the old `reindex` button to the database endpoint. That would be fast, but it hides the fact that the two knowledge sources have different purposes and acceptance criteria.

### Decision 2: Evidence retrieval uses filters first, then vector search, then rerank

Recommendation evidence should not perform a broad global semantic search when the system already knows the target `programId`. The service should:

1. Build metadata filters from known context such as `program_id`, `school_id`, `doc_type`, `year`, and `section`.
2. Run vector retrieval within that filtered subset.
3. Rerank the recalled chunks.
4. Return selected chunks and citations.

This keeps RAG grounded in the selected candidate while still allowing semantic matching inside that candidate's evidence.

Alternative considered: rely only on the global LangChain4j `RetrievalAugmentor`. That is useful for open-ended general questions, but it is too loose for report-grade per-program explanation.

### Decision 3: MySQL remains the fact source, RAG supplies explanatory evidence

Draft cards and report facts should continue to use structured MySQL snapshots for scores, plans, years, school names, and program ids. RAG citations should support narrative explanations, risk summaries, data quality warnings, and source traceability.

This avoids a common RAG failure mode where vector snippets overwrite structured truth.

### Decision 4: User-facing citations are curated, not raw debug traces

The user report should show concise evidence blocks:

- source title or generated document name
- document type
- semantic section
- year when available
- short content preview
- optional source URL/owner when available

The UI should not show vector scores, Qdrant payload internals, or raw trace ids by default. Those remain administrator observability data.

### Decision 5: Observability links search-test, chat, and report evidence

RAG traces should include operation keys such as:

- `search-test`
- `ai-chat`
- `recommendation-evidence`
- `database-reindex`
- `database-generate`
- `rag-evaluate`

This makes the admin page useful during acceptance: generate docs, index docs, test retrieval, trigger user recommendation/chat/report, then confirm traces exist.

### Decision 6: Generation-only state is an intermediate state

`/postgrad/rag/generate-documents` creates Markdown evidence files, but it does not make those chunks available to retrieval until `/postgrad/rag/reindex-database` rebuilds the Qdrant collection. The admin UI should therefore treat successful document generation as an intermediate state and either:

1. show an explicit "documents generated, not yet indexed" warning, or
2. provide a combined guided operation that runs generation and indexing in sequence.

Alternative considered: make the generate button silently perform indexing too. That reduces clicks, but it hides a slow Qdrant rebuild behind a label that only mentions document generation. The preferred shape is an explicit combined button or a clear two-step prompt.

### Decision 7: Evaluation cases must be grounded in known records

The default evaluation suite should use concrete queries tied to known database records, such as `schoolId=56`, `programId=2854`, `year=2025`, and expected sections like `admission_score`, `admission_plan`, or `admission_result`. A generic prompt such as "某学校某专业某一年复试线是多少" is not a valid acceptance case because it cannot prove that metadata filters, vector retrieval, and rerank found the right program.

Evaluation results should distinguish:

- no chunks were retrieved
- chunks were retrieved for the wrong program or school
- expected semantic section was missing
- data is absent or sparse in the source database
- rerank selected the wrong evidence despite retrieval finding candidates

Alternative considered: pass evaluation when any chunk is returned. That would overstate quality and allow a broad vector match from the wrong program to pass.

## Risks / Trade-offs

- [Risk] Database-generated chunks can be sparse for programs with incomplete historical data. -> Mitigation: include `data_quality` chunks and display coverage warnings instead of pretending evidence is complete.
- [Risk] Metadata filters may be too strict and return no chunks. -> Mitigation: support controlled fallback from `program_id` to `school_id` or from narrow sections to broader `program_history`, while recording fallback in trace metadata.
- [Risk] User-facing citations could make the report noisy. -> Mitigation: group citations by candidate and show only selected top evidence with a compact expand/collapse interaction.
- [Risk] Admin reindexing can be slow with large limits. -> Mitigation: expose `startYear`, `endYear`, and `limit`, show running state, and document safe first-run values.
- [Risk] Operators may stop after document generation and assume Qdrant is updated. -> Mitigation: add generation-only warnings and a combined generate-and-index action.
- [Risk] Evaluation can fail because a record is missing from the current database rather than because retrieval is broken. -> Mitigation: seed default cases from known records and report missing-source-data failures separately.
- [Risk] Existing uncommitted RAG changes overlap with this spec. -> Mitigation: implement against current working tree carefully and avoid reverting unrelated local modifications.

## Migration Plan

1. Keep the old manual RAG endpoint and status behavior working.
2. Add database RAG controls and labels without removing existing manual reindexing.
3. Add a combined or guided database RAG operation after the two primitive endpoints are stable.
4. Add backend evidence retrieval as a separate service and integrate it into chat/report flows behind existing response DTOs.
5. Add user-facing citation rendering only when `ragCitations` exists; keep old reports readable.
6. Replace placeholder evaluation cases with grounded records from the current database.
7. Update docs and acceptance scripts after the new endpoints and UI are verified.

## Open Questions

- Should report generation block when RAG evidence retrieval fails, or should it complete with a "RAG evidence unavailable" warning? The initial recommendation is to complete with warning.
- Should database RAG generation write bulk Markdown files into source resources by default in development, or treat them as generated artifacts excluded from commits? The current plan keeps generated bulk output out of commits unless explicitly requested.
- Should AI chat use the program evidence tool automatically for every program-specific answer, or only when the prompt/tool planner detects recommendation explanation intent?
- Should the combined generate-and-index operation live as a backend endpoint or as frontend orchestration over the two existing endpoints? The initial recommendation is frontend orchestration unless backend-side transaction/status semantics become necessary.
