# Unified RAG Runtime Design

**Date:** 2026-07-11

## Goal

Replace the remaining legacy RAG runtime with one versioned, alias-backed retrieval pipeline. Every RAG consumer - administration search testing, user chat, evidence tools, generated reports, and AI observation - must read the same active collection and the same persisted index state.

The migration must remove all production code paths that can clear a live collection or rebuild a fixed collection in place.

## Current Problem

The new index lifecycle builds a versioned Qdrant collection and atomically switches the `postgrad_rag_active` alias. However, the legacy `RagDocumentService` and `QdrantCollectionService` remain in the application:

- The legacy service still exposes destructive `reindex` and `reindexDatabaseKnowledge` methods.
- The AI observation page reads the legacy in-memory `RagStatus`, so it reports an empty index after a successful new build.
- The administration search endpoint and scoped evidence tool use `RagSearchTestService`, while general chat uses a separate LangChain4j `RetrievalAugmentor` implementation. Both currently use the alias, but they do not share metadata resolution, retrieval policy, or trace semantics.

## Chosen Architecture

### 1. Index Lifecycle Is the Only Writer

`RagIndexBuildService` remains the sole component allowed to write vectors. It assembles the corpus, writes a versioned candidate collection, validates it, and switches the `postgrad_rag_active` alias only after success.

`RagDocumentService` and `QdrantCollectionService` are removed. No Spring bean in production may expose `clearCollection`, `removeAll`, or a fixed-collection reindex operation.

```text
MySQL + general Markdown
          |
          v
 RagCorpusAssembler
          |
          v
 RagIndexBuildService --> postgrad_rag_<version>
                                  |
                                  v
                   postgrad_rag_active alias
```

### 2. One Retrieval Contract

Introduce `RagRetrievalService` as the sole service for embedding, metadata enrichment, metadata filtering, vector recall, reranking, result selection, and retrieval latency collection.

It accepts a `RagSearchTestRequest`-compatible request and returns the current `RagSearchTestResponse` DTO. The DTO remains stable during this migration so that the management API and frontend do not need a wire-format break.

The existing `RagSearchTestService` is replaced or renamed as part of the migration; it must not retain an independent implementation.

Consumers:

| Consumer | Integration |
|---|---|
| Admin RAG console | Calls `RagRetrievalService` directly. |
| `RagEvidenceService` | Calls it with program and school filters, then records citations and scoped traces. |
| Report generation | Continues to use `RagEvidenceService`; MySQL fact citation remains an explicit no-evidence fallback. |
| User general chat | Uses a `ContentRetriever` adapter backed by `RagRetrievalService`, rather than direct `EmbeddingStoreContentRetriever`. |
| Agent evidence tool | Uses `RagEvidenceService`; it is the precise, program-scoped entry point over the same retrieval service. |

### 3. User Chat Routing

General chat receives unscoped evidence through the unified `ContentRetriever` adapter. Questions about recommendation reasons, risks, scores, enrolment, admission results, and trends continue to expose `getProgramEvidence`; that tool applies the program-scoped filter and produces citations.

The route resolver must not select a legacy path. For scoped questions it may suppress broad context, but it must preserve the evidence tool. All retrieval outcomes are recorded under the same trace model with route values `general` or `scoped-evidence`.

### 4. One Status Source

`RagIndexStatusService` and the `rag_index_run` table are the source of truth for index status. The RAG console and AI observation console both read this source. The current active collection is resolved through the Qdrant alias, while the latest run is reported separately so a failed new build cannot erase the last known good index state.

### 5. Compatibility and Failure Rules

- Existing administration routes remain `/postgrad/rag/status`, `/postgrad/rag/index-runs/*`, and `/postgrad/rag/search-test`.
- Existing AI observation response shape remains unchanged; only the provider of its `rag` section changes.
- If no active alias exists, retrieval returns a structured no-index error; it does not initialize or clear Qdrant.
- Rerank failures retain vector ordering and record the fallback outcome.
- A failure during a candidate build never mutates the active alias.

## File-Level Migration

- Delete `RagDocumentService` and its legacy tests.
- Delete `QdrantCollectionService` and its legacy tests.
- Remove their bean wiring from `RagConfig` and all constructor dependencies.
- Rename or replace `RagSearchTestService` with `RagRetrievalService` and update its callers.
- Add a `ContentRetriever` adapter that converts unified selected chunks to LangChain4j `Content` objects.
- Update `AiObserveServiceImpl` to use `RagIndexStatusService`.
- Update dependency tests, controller tests, and the existing frontend contract test where API behavior changes.

## Acceptance Criteria

1. No production source contains `clearCollection`, `removeAll`, `reindexDatabaseKnowledge`, or a `RagDocumentService` dependency.
2. A successful index run leaves the active alias unchanged until candidate validation succeeds, then points it to the new collection.
3. Admin search test, `getProgramEvidence`, report citations, and generic chat retrieval use the same retrieval service.
4. AI observation reports the same indexed/document/chunk status as `/postgrad/rag/status`.
5. Scoped evidence uses `program_id` metadata filtering; generic chat does not bypass the unified vector/rerank implementation.
6. Rerank failure returns vector-ranked results rather than failing the user request.
7. Focused backend tests, full backend package build, frontend contract test, and production frontend build pass.

## Non-Goals

- Changing the current index versioning and alias-switch design.
- Changing vector model, rerank model, document schema, or Qdrant payload schema.
- Replacing the existing database fact fallback for report citations.
- Adding a new external evaluation platform in this migration.
