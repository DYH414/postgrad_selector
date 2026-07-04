## 1. Backend DTOs and Trace Model

- [x] 1.1 Add request/response DTOs for RAG search testing, including query, retrieval parameters, latency, error summary, and chunk result fields.
- [x] 1.2 Add RAG trace domain objects for recent traces, selected chunk summaries, top sources, stage timings, outcome, and failure stage.
- [x] 1.3 Add unit tests for DTO defaults and safe handling of missing metadata fields.

## 2. Search-Test Service

- [x] 2.1 Add a RAG search-test service that validates non-empty queries before calling embedding or retrieval components.
- [x] 2.2 Reuse the existing embedding model and Qdrant embedding store to retrieve candidate chunks for a query.
- [x] 2.3 Reuse the existing SiliconFlow reranker where available and return vector order if rerank fails.
- [x] 2.4 Map `TextSegment` metadata into source name, source id, document type, chunk index, chunk title, content hash, and content preview fields.
- [x] 2.5 Record best-effort rank and score fields without inventing unavailable vector scores.

## 3. RAG Trace Recorder

- [x] 3.1 Add a bounded in-memory RAG trace recorder with reverse chronological listing.
- [x] 3.2 Record successful search-test traces with query, selected chunks, top sources, and latency summary.
- [x] 3.3 Record failed search-test traces with failure stage and error summary.
- [x] 3.4 Integrate AI Chat RAG trace recording at the controllable boundary in `AiChatServiceImpl` or a RAG wrapper service.
- [x] 3.5 Ensure trace storage avoids unbounded growth and does not persist sensitive query data beyond process memory.

## 4. Admin APIs and Permissions

- [x] 4.1 Add `POST /postgrad/rag/search-test` protected by `postgrad:rag:view`.
- [x] 4.2 Add `GET /postgrad/rag/traces` protected by `postgrad:rag:view` with bounded limit handling.
- [x] 4.3 Extend RAG status response or add companion fields for backend, collection name, embedding model, rerank model, chunk size, chunk overlap, max results, and rerank top-N.
- [x] 4.4 Preserve existing `POST /postgrad/rag/reindex` behavior and `postgrad:rag:reindex` permission boundary.

## 5. Management UI

- [x] 5.1 Update `ruoyi-ui` RAG API client with search-test and trace listing functions.
- [x] 5.2 Add a search-test panel to the RAG management page with query input, loading state, validation feedback, and result list.
- [x] 5.3 Display source, rank, score, selected status, document type, chunk index, and content preview for each search-test result.
- [x] 5.4 Add a recent RAG trace panel with query, outcome, selected sources, retrieved count, selected count, latency, and error summary.
- [x] 5.5 Correct existing RAG page wording from "内存向量库" to Qdrant/vector store wording.
- [x] 5.6 Hide or disable the reindex button for users lacking `postgrad:rag:reindex` if permission helpers are available.

## 6. Verification

- [x] 6.1 Add backend unit tests for blank query, no embedding model, Qdrant unavailable, empty retrieval, rerank failure, and successful search-test mapping.
- [x] 6.2 Add controller tests for permission-protected search-test and trace endpoints.
- [x] 6.3 Run targeted Maven tests for RAG and AI observe modules.
- [x] 6.4 Run `mvn -pl ruoyi-admin -am -DskipTests compile`.
- [x] 6.5 Run `npm run build:prod` in `ruoyi-ui`.

## 7. Documentation and Demo Notes

- [x] 7.1 Update RAG operations documentation with search-test usage and trace interpretation.
- [x] 7.2 Add a short demo script: query input, expected source document, trace row, and how to explain RAG evidence in an interview.
- [x] 7.3 Document known limitations: no hybrid search yet, nullable vector score, in-memory trace retention, and no long-term evaluation set in this change.
