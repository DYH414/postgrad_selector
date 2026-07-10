## Why

The database RAG document pipeline exists, but the product does not yet expose or consume it end to end. Administrators still operate the older manual `rag-documents/` flow, user-facing reports do not render Qdrant evidence, and AI recommendation explanations cannot reliably retrieve program-scoped RAG chunks.

This change closes the production gap between "RAG can be indexed" and "RAG is observable, testable, and visible in recommendations."

## What Changes

- Add administrator-facing controls for database RAG document generation and database RAG reindexing, including year range and limit parameters.
- Add a guided administrator workflow that can complete document generation and Qdrant indexing as one acceptance-oriented operation, while clearly warning when only Markdown documents have been generated.
- Extend RAG search testing so operators can test metadata-filtered retrieval by school, program, year, document type, and semantic section.
- Replace placeholder RAG evaluation cases with grounded school/program/year cases that verify real metadata-filtered retrieval and expected evidence sections.
- Add a backend evidence retrieval layer for recommendation use cases, centered on program-scoped metadata filters before vector retrieval and rerank.
- Integrate retrieved RAG evidence into AI chat and final recommendation reports without replacing MySQL as the structured source of truth.
- Render RAG citations and evidence summaries in the user-facing AI report so users can see what knowledge supported the recommendation.
- Extend RAG/AI observability to distinguish manual knowledge, database-generated knowledge, search-test traces, AI-chat traces, and recommendation evidence retrieval.
- Update project documentation and acceptance guidance to describe the current `rag-generated/` database RAG flow instead of only the old `rag-documents/` flow.

## Capabilities

### New Capabilities

- `rag-database-ops`: Administrator workflows for generating database RAG documents, indexing database RAG chunks, and seeing the active RAG source mode.
- `rag-evaluation-ground-truth`: Grounded RAG evaluation cases that bind queries to known school/program/year metadata and expected evidence sections.
- `recommendation-rag-evidence`: Backend evidence retrieval for AI recommendation chat and report generation using Qdrant metadata filters, vector retrieval, rerank, and citation DTOs.
- `user-rag-citations`: User-facing report and chat surfaces that display RAG evidence, citations, and source metadata without exposing internal implementation noise.
- `rag-full-chain-acceptance`: Documentation and acceptance checks for the complete database RAG lifecycle from MySQL export to user-visible recommendation evidence.

### Modified Capabilities

- `rag-search-testing`: Add metadata filter controls and result metadata visibility for database RAG v2 chunks.
- `rag-trace-observability`: Expand trace visibility to cover database generation/reindexing, metadata-filtered search tests, evaluation runs, recommendation evidence retrieval, and AI chat retrieval.

## Impact

- Backend RAG services and controllers in `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/**`.
- AI recommendation services and tools in `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/recommend/**`.
- Prompt resources under `ruoyi-postgrad/src/main/resources/prompts/v2/`.
- Admin frontend APIs and pages in `ruoyi-ui/src/api/postgrad/rag.js`, `ruoyi-ui/src/views/postgrad/rag/index.vue`, and AI observability pages.
- User frontend report and chat components in `user-ui/src/views/ai-recommend-v2/**`.
- Existing OpenSpec specs for `rag-search-testing` and `rag-trace-observability`.
- README and docs that currently describe only `rag-documents/` or omit the database-generated RAG lifecycle.
