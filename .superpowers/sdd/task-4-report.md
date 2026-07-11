# Task 4: RagCorpusAssembler — Unified Corpus Assembly

## Files Created

### Production Code
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/build/AssembledCorpus.java` — record holding `List<TextSegment> chunks`, `int generalDocumentCount`, `int databaseDocumentCount`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/build/RagCorpusAssembler.java` — Spring `@Component` that assembles a unified corpus from:
  - **General documents**: loaded from classpath `rag-documents/`, split via `DocumentSplitters.recursive(500, 50)`, metadata with `source_type=general` (no school/program fields)
  - **Database evidence**: via `RagKnowledgeExportService` (histories, year facts, data quality) -> `StructuredRagChunkBuilder` -> `TextSegment` with `source_type=database` (includes school/program fields)

### Test
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/build/RagCorpusAssemblerTest.java` — 5 TDD test cases from the plan's T2 section

## Test Results

All 5 tests pass:

| Test | Description | Status |
|------|-------------|--------|
| T2.1 | General docs assembled (sourceType=general, contentHash non-null) | PASS |
| T2.2 | Database docs assembled (schoolId/programId/year non-null) | PASS |
| T2.3 | General + database merged, sourceId unique across all chunks | PASS |
| T2.4 | Empty corpus doesn't throw | PASS |
| T2.5 | External Markdown written to `${RAG_DATA_DIR}/generated/` | PASS |

## Implementation Details

- **Metadata schema**: Unified `source_type`, `source_id`, `source_name`, `doc_type`, `section`, `chunk_index`, `content`, `content_hash`, `document_version`, `embedding_version`, `indexed_at` for all chunks
- **Database chunks additionally**: `school_id`, `school_name`, `program_id`, `program_name`, `year` (and all other metadata from `StructuredRagChunkBuilder`)
- **General chunks omit**: school/program/year fields entirely (not set to empty strings)
- **source_id generation**: General `general:fileName:index`, Database `chunk.getChunkId()` (e.g., `program_year:1:101:2025:admission_score:hash`)
- **External Markdown**: Written to `${ragDataDir}/generated/` per chunk, using chunkId as filename
- **Validation**: `Set<String>` check for source_id uniqueness, throws `IllegalStateException` on duplicates
- **Limit**: Database queries use 1000 as default limit
