## 1. Specification and Planning

- [x] 1.1 Validate OpenSpec change artifacts.

## 2. Structured Database RAG Chunks

- [x] 2.1 Add failing tests for program-year database facts producing semantic chunks with stable metadata.
- [x] 2.2 Implement database RAG row DTOs and structured chunk DTOs.
- [x] 2.3 Implement chunk builder for basic info, exam subjects, score, plan, result, quality, and source sections.
- [x] 2.4 Add mapper query for first-wave database export from existing school/college/program/admissions/source/quality tables.

## 3. Generated Indexing

- [x] 3.1 Add failing tests for converting structured chunks into LangChain4j `TextSegment` payloads and deterministic ids.
- [x] 3.2 Extend RAG indexing to index generated database chunks into Qdrant with business metadata.
- [x] 3.3 Add an administrator endpoint for database export/reindex.

## 4. Metadata Filtered Search

- [x] 4.1 Add failing tests for search-test metadata filter request handling.
- [x] 4.2 Extend search-test DTOs and service to apply metadata filters.
- [x] 4.3 Return business metadata in chunk results.

## 5. Evaluation Set

- [x] 5.1 Add failing tests for evaluation case matching by expected metadata.
- [x] 5.2 Add a seed evaluation set for common school/program/year questions.
- [x] 5.3 Add an evaluation service and administrator endpoint.

## 6. Recommendation Citation Evidence

- [x] 6.1 Add failing tests for citation DTOs built from selected chunks.
- [x] 6.2 Implement citation mapping for selected RAG chunks.
- [x] 6.3 Expose citation evidence in a form recommendation report generation can consume.

## 7. Verification

- [x] 7.1 Run focused `ruoyi-postgrad` tests for RAG additions.
- [x] 7.2 Run OpenSpec validation for `add-database-rag-knowledge-pipeline`.
