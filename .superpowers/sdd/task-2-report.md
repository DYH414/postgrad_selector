# Task 2 Report: Qdrant Collection Bootstrap Adapter

## Status
DONE

## Scope Delivered
- Created `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/qdrant/QdrantCollectionService.java`
- Created `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/qdrant/QdrantCollectionServiceTest.java`
- Updated `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagStatus.java`
- Updated `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagDocumentService.java` to copy the new status fields

## TDD Record
1. Added `QdrantCollectionServiceTest` first.
2. Ran `mvn -pl ruoyi-postgrad -Dtest=QdrantCollectionServiceTest test`.
3. Observed red due to missing `QdrantCollectionService` type referenced by `RagDocumentService`.
4. Implemented the minimal adapter and status field propagation.
5. Ran `mvn -pl ruoyi-postgrad "-Dtest=QdrantCollectionServiceTest,RagDocumentServiceTest" test`.
6. Observed green: 7 tests passed.

## Implementation Notes
- `QdrantCollectionService` consumes `RestTemplate`, Qdrant URL, API key, collection name, and embedding dimension.
- `ensureCollection()` checks collection reachability and issues a `PUT` with cosine vector settings when missing.
- `clearCollection()` issues `POST /collections/{name}/points/delete?wait=true` with the filter body from the brief.
- `isReachable()` returns `false` on `RestClientException` and logs at debug level.
- `RagStatus` now carries `backend`, `collectionName`, and `reachable`.
- `RagDocumentService.getStatus()` now copies those new fields into the returned snapshot.

## Verification
- Command: `mvn -pl ruoyi-postgrad "-Dtest=QdrantCollectionServiceTest,RagDocumentServiceTest" test`
- Result: PASS (`QdrantCollectionServiceTest` 4/4, `RagDocumentServiceTest` 3/3)

## Concerns
- None.

## Commit
- `feat(rag): add qdrant collection bootstrap`

## Review Fixes
- Updated `QdrantCollectionService.ensureCollection()` to create the collection only when `GET /collections/{name}` returns 404/not found. Other `RestClientException` failures now surface to callers so `RagDocumentService` can treat Qdrant as unavailable instead of silently recreating.
- Updated `QdrantCollectionService.clearCollection()` to delete all points in the configured collection via `POST /collections/{name}/points/delete?wait=true` with body `{"filter":{}}`, rather than filtering on a single `embedding_version`.
- Expanded `QdrantCollectionServiceTest` to distinguish 404 from 500 and to assert the delete request body matches the whole-collection selector.

## Review Fix Verification
- Red: `mvn -pl ruoyi-postgrad -Dtest=QdrantCollectionServiceTest test` failed before the service change because `ensureCollection()` attempted `PUT` after a 500 response.
- Green: `mvn -pl ruoyi-postgrad -Dtest=QdrantCollectionServiceTest test` passed after the service change.
- Coverage run: `mvn -pl ruoyi-postgrad "-Dtest=QdrantCollectionServiceTest,RagDocumentServiceTest" test`
- Result: PASS (`QdrantCollectionServiceTest` 5/5, `RagDocumentServiceTest` 3/3)
