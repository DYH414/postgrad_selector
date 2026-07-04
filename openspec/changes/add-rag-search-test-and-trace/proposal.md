## Why

The current RAG implementation can confirm whether Qdrant indexing succeeded, but it cannot explain what happened for a specific user query. This makes it hard to prove that RAG is actually improving an AI Chat answer, debug bad retrieval, or demonstrate production-level observability.

Popular RAG products and frameworks converge on the same operational pattern: Dify exposes retrieval settings and retrieval testing, LangSmith/LlamaIndex emphasize RAG tracing and evaluation, and Qdrant documents hybrid/multi-stage retrieval patterns. The project should first add a narrow observability layer that makes the existing Qdrant + rerank pipeline visible before expanding retrieval algorithms.

## What Changes

- Add a RAG search test capability for administrators to submit a query and inspect retrieved chunks.
- Show retrieval metadata such as source name, document type, chunk index, content preview, and scores where available.
- Add request-level RAG trace recording for AI Chat, including query, retrieved count, top sources, latency breakdown, selected chunks, and errors.
- Surface recent RAG traces and search-test results in the management UI.
- Extend RAG status with operational configuration details such as collection name, backend, embedding model, rerank model, chunk size, overlap, max results, and rerank top-N.
- Keep this change scoped to observability and management; it does not replace the recommendation pipeline, add PDF parsing, add hybrid search, or change model selection.

## Capabilities

### New Capabilities

- `rag-search-testing`: Allows administrators to test RAG retrieval for a query and inspect what would be used as context.
- `rag-trace-observability`: Records and exposes recent request-level RAG traces for AI Chat.

### Modified Capabilities

- None. There are no existing OpenSpec capabilities in `openspec/specs/` yet.

## Impact

- Backend RAG service layer: search-test orchestration around the existing `ContentRetriever`, Qdrant embedding store, and reranker.
- Backend observability layer: in-memory or lightweight recent trace recorder for RAG retrieval events.
- Admin APIs under `/postgrad/rag`: new search-test endpoint and trace listing endpoint.
- Admin UI `ruoyi-ui/src/views/postgrad/rag/index.vue`: search-test panel, recent trace panel, and richer status fields.
- Permission model: reuse `postgrad:rag:view` for read-only test/trace viewing; keep `postgrad:rag:reindex` for destructive indexing actions.
- Tests: service-level tests for no-key, no-index, Qdrant unavailable, empty retrieval, rerank failure, and successful trace recording.
