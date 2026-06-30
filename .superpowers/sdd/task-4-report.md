# Task 4 Report: Add Stable Chunk Payload And Qdrant Reindex Flow

## Changes Made

### 1. `RagDocumentService.java`
- Added Qdrant bootstrap at the start of `reindex()` after the document-empty check:
  - calls `qdrantCollectionService.ensureCollection()`
  - calls `qdrantCollectionService.clearCollection()`
  - sets status `backend=qdrant`, `collectionName`, and `reachable`
- Added a guarded Qdrant failure path:
  - sets `backend=qdrant`
  - sets `collectionName`
  - sets `reachable=false`
  - sets `indexed=false`
  - sets `lastError` to `Qdrant unavailable: <message>`
  - returns `new RagIndexResult(0, 0)` without attempting ingestion
- Replaced the old document ingestor path with explicit segment generation and upload:
  - splits documents with `DocumentSplitters.recursive(chunkSize, chunkOverlap)`
  - enriches each `TextSegment` with stable metadata payload fields
  - embeds with `embeddingModel.embedAll(segments)`
  - stores with `embeddingStore.addAll(ids, embeddings, segments)`
- Added metadata/payload fields on every chunk:
  - `school_id`
  - `school_name`
  - `major_id`
  - `major_name`
  - `doc_type`
  - `year`
  - `source_id`
  - `source_name`
  - `source_url`
  - `chunk_index`
  - `chunk_title`
  - `content`
  - `content_hash`
  - `embedding_version`
  - `updated_at`
  - `tags`
- Added helper methods:
  - `splitWithMetadata(...)`
  - `sourceName(...)`
  - `inferDocType(...)`
  - `sha256(...)`
- Removed the old `countSegments(...)` helper.

### 2. `RagDocumentServiceTest.java`
- Added `shouldEnsureAndClearQdrantBeforeIndexing()`
- Added `shouldAttachStableMetadataToSplitSegments()`
- Added `shouldExposeUsefulStatusWhenQdrantBootstrapFails()`

## Local API Verification

- Verified locally with `javap` that in this workspace's LangChain4j version:
  - `Metadata` uses typed accessors such as `getString(...)` rather than a generic `get(...)`
  - `EmbeddingStoreIngestor` does **not** provide `ingest(List<TextSegment>)`
- Adapted implementation accordingly:
  - used `document.metadata().getString(...)`
  - used `embeddingModel.embedAll(segments).content()` plus `embeddingStore.addAll(...)`

## Verification

- Red phase:
  - `mvn -pl ruoyi-postgrad "-Dtest=RagDocumentServiceTest#shouldEnsureAndClearQdrantBeforeIndexing" test`
  - Result: **FAIL** as expected before implementation (`ensureCollection()` not invoked)
- Acceptance suite:
  - `mvn -pl ruoyi-postgrad "-Dtest=RagDocumentServiceTest,QdrantCollectionServiceTest,RagInitializerTest" test`
  - Result: **PASS** (15 tests, 0 failures, 0 errors)

## Review Fix: Stable Chunk IDs

- Updated `RagDocumentService.reindex()` to call `embeddingStore.addAll(ids, embeddings, segments)` instead of the two-argument overload.
- Added `segmentIds(...)` to derive deterministic point IDs from existing stable metadata using:
  - `sha256(source_id + ":" + chunk_index + ":" + content_hash)`
- Kept the chunk payload metadata unchanged; the ID is derived from metadata already attached during `splitWithMetadata(...)`.
- Added `shouldWriteDeterministicChunkIdsToEmbeddingStore()` in `RagDocumentServiceTest` to capture IDs passed to the store and assert:
  - the ID count matches the segment count
  - each ID matches the deterministic SHA-256 formula above

## Review Fix Verification

- Red phase:
  - `mvn -pl ruoyi-postgrad "-Dtest=RagDocumentServiceTest#shouldWriteDeterministicChunkIdsToEmbeddingStore" test`
  - Result: **FAIL** before the fix because `RagDocumentService` invoked `embeddingStore.addAll(embeddings, segments)` and never supplied IDs.
- Green phase:
  - `mvn -pl ruoyi-postgrad "-Dtest=RagDocumentServiceTest#shouldWriteDeterministicChunkIdsToEmbeddingStore" test`
  - Result: **PASS** after switching to `embeddingStore.addAll(ids, embeddings, segments)`.
- Coverage suite:
  - `mvn -pl ruoyi-postgrad "-Dtest=RagDocumentServiceTest,QdrantCollectionServiceTest,RagInitializerTest" test`
  - Result: **PASS** (16 tests, 0 failures, 0 errors).
