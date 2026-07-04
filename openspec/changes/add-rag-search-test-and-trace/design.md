## Context

The project currently has a working RAG indexing and AI Chat integration:

- `RagDocumentService` loads `classpath:rag-documents/`, chunks Markdown documents, embeds them, and writes `TextSegment` payloads into Qdrant.
- `RagConfig` wires `EmbeddingStoreContentRetriever`, `SiliconFlowReranker`, `ReRankingContentAggregator`, and `RetrievalAugmentor`.
- `AiChatServiceImpl` passes the optional `RetrievalAugmentor` into LangChain4j `AiServices`.
- `RagAdminController` exposes status and reindex endpoints.
- `AiObserveServiceImpl` exposes model-level Micrometer metrics and a RAG status snapshot.

Current observability answers "is RAG indexed?" but not "what did this query retrieve?" or "which chunks entered the answer context?" Production RAG examples from Dify, LangSmith/LlamaIndex, and Qdrant point toward retrieval testing, traceability, and stage-aware evaluation as the next useful layer.

## Goals / Non-Goals

**Goals:**

- Let administrators run a query against the current RAG retrieval pipeline without invoking the LLM.
- Show the retrieved and selected chunks with source metadata and available scores.
- Record recent RAG traces for AI Chat so operators can inspect query, selected sources, timings, and errors.
- Keep trace storage bounded and simple for the first iteration.
- Make the management UI useful for demos, debugging, and interview evidence.

**Non-Goals:**

- Do not implement hybrid sparse+dense search in this change.
- Do not add PDF/DOCX parsing or document upload.
- Do not redesign the AI recommendation pipeline.
- Do not persist long-term RAG traces to MySQL in the first iteration.
- Do not replace LangChain4j retrieval internals wholesale unless necessary for observability.
- Do not add user-facing citations to AI Chat responses in this change; this change prepares the data needed for citations.

## Decisions

### Decision 1: Add an explicit RAG search-test service instead of relying only on `RetrievalAugmentor`

Use a new service-level abstraction, tentatively `RagSearchTestService`, to run a query and return an inspectable result. This keeps the management endpoint independent from AI Chat and avoids triggering LLM generation.

The service should reuse existing components where possible:

```text
query
  -> EmbeddingModel
  -> EmbeddingStore / Qdrant
  -> optional SiliconFlowReranker
  -> result DTO for UI
```

Alternative considered: call `RetrievalAugmentor` directly and inspect augmented content. This is simpler conceptually but less useful operationally because it hides intermediate retrieval/rerank ordering and makes it harder to expose raw metadata.

### Decision 2: Treat scores as best-effort fields

LangChain4j abstractions may not expose every score needed for a perfect vector-score/rerank-score comparison. The DTO should support nullable score fields:

```text
vectorScore: nullable
rerankScore: nullable
vectorRank: required when retrieved
rerankRank: nullable
selectedForPrompt: boolean
```

This avoids inventing scores while still making rank/order visible. If a lower-level Qdrant query path is later added, vector scores can be populated without changing the UI contract.

### Decision 3: Record recent RAG traces in memory first

Add a bounded recorder similar in spirit to `AiInvocationSnapshotRecorder`, but storing a ring buffer of recent RAG traces rather than only the latest snapshot.

Initial shape:

```text
RagTrace
├─ traceId
├─ operationKey
├─ userId
├─ query
├─ outcome
├─ failureStage
├─ errorSummary
├─ retrievedCount
├─ selectedCount
├─ topSources
├─ embeddingLatencyMs?
├─ retrievalLatencyMs?
├─ rerankLatencyMs?
├─ totalLatencyMs
└─ selectedChunks[]
```

Alternative considered: persist traces to MySQL immediately. That is more durable but adds schema design, cleanup policy, PII retention decisions, and migration risk. In-memory traces are enough for the first observability step.

### Decision 4: Integrate tracing at the boundary we control

The application fully controls `AiChatServiceImpl` and RAG service beans. It does not fully control LangChain4j internals. The first iteration should trace what can be measured accurately:

- AI Chat query entering RAG
- explicit search-test retrieval
- selected chunk summaries
- total retrieval path latency
- rerank success/failure when calling the reranker explicitly

If framework-level hooks are unavailable for internal `RetrievalAugmentor` calls, the trace should mark unavailable stages honestly instead of guessing.

### Decision 5: Keep permissions read-oriented

Use `postgrad:rag:view` for search-test and trace listing because they are read-only diagnostic actions. Keep `postgrad:rag:reindex` only for reindexing. The UI should hide destructive actions from users without reindex permission.

### Decision 6: Update UI wording from "memory vector store" to Qdrant

The current RAG page still says "内存向量库" in multiple places, but the active backend is Qdrant. The observability change should correct the wording to avoid misleading operators.

## Candidate API Shape

```text
POST /postgrad/rag/search-test
permission: postgrad:rag:view
body:
{
  "query": "调剂是什么意思",
  "maxResults": 5,
  "minScore": 0.6,
  "rerankTopN": 3
}

response:
{
  "query": "...",
  "retrievalMode": "dense-vector-rerank",
  "retrievedCount": 5,
  "selectedCount": 3,
  "rerankEnabled": true,
  "rerankFailed": false,
  "errorSummary": null,
  "latency": {
    "embeddingMs": 120,
    "retrievalMs": 30,
    "rerankMs": 210,
    "totalMs": 370
  },
  "chunks": [
    {
      "vectorRank": 1,
      "rerankRank": 2,
      "vectorScore": null,
      "rerankScore": 0.82,
      "selectedForPrompt": true,
      "sourceName": "04-调剂政策.md",
      "sourceId": "04-调剂政策.md",
      "docType": "adjustment_policy",
      "chunkIndex": "0",
      "chunkTitle": "04-调剂政策.md",
      "contentHash": "...",
      "contentPreview": "..."
    }
  ]
}
```

```text
GET /postgrad/rag/traces?limit=20
permission: postgrad:rag:view

response:
[
  {
    "traceId": "...",
    "operationKey": "ai-chat",
    "query": "...",
    "outcome": "SUCCESS",
    "retrievedCount": 5,
    "selectedCount": 3,
    "topSources": ["04-调剂政策.md", "03-国家线与复试.md"],
    "totalLatencyMs": 370,
    "createdAt": "..."
  }
]
```

## UI Sketch

```text
RAG 观测台
┌─────────────────────────────────────────────────────────────┐
│ 健康状态: Qdrant reachable | indexed | docs | chunks | model │
└─────────────────────────────────────────────────────────────┘

┌───────────────────────────────┐  ┌──────────────────────────┐
│ 检索测试                       │  │ 最近 RAG Trace           │
│ query: [调剂是什么意思____]    │  │ ai-chat SUCCESS 370ms    │
│ [测试召回]                     │  │ top: 04-调剂政策.md      │
│                               │  │ ai-chat ERROR rerank     │
│ 1. 04-调剂政策.md              │  │ ...                      │
│    vectorRank=1 rerankRank=2   │  └──────────────────────────┘
│    selected=true score=0.82    │
│    preview...                  │
└───────────────────────────────┘
```

## Risks / Trade-offs

- [Risk] LangChain4j may not expose vector similarity scores through the current retriever abstraction. → Mitigation: expose rank and nullable scores first; add direct Qdrant search only if needed.
- [Risk] Rerank failure currently throws and may be handled by framework internals. → Mitigation: search-test should catch rerank errors and return vector order; trace should mark `failureStage=rerank`.
- [Risk] Search-test may call paid embedding/rerank APIs frequently. → Mitigation: restrict to admin permission, optionally apply small request limits later, and show latency/cost hints.
- [Risk] Query text may contain user-sensitive information. → Mitigation: keep trace storage in memory, bound retention, and avoid long-term persistence in the first iteration.
- [Risk] UI could imply RAG guarantees answer correctness. → Mitigation: label results as retrieval evidence, not final truth; avoid claiming correctness without evaluation.

## Migration Plan

1. Add DTOs and services behind existing RAG module boundaries.
2. Add read-only admin endpoints under `/postgrad/rag`.
3. Extend the management UI without changing existing routes.
4. Add tests for success and failure modes.
5. Deploy as additive functionality; rollback is removing the new endpoints/UI while leaving existing RAG indexing untouched.

## Open Questions

- Should trace storage remain purely in memory, or should the later production version persist sampled traces to MySQL?
- Do we want user-facing citations in the next change after trace data exists?
- Should search-test use the same exact `RetrievalAugmentor` path as AI Chat, or a more transparent manual path that exposes intermediate details?
- Is it acceptable for search-test to consume SiliconFlow embedding/rerank quota during demos, or should it support a mock/sample mode?
