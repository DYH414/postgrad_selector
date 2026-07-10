## Why

The current RAG index is built mainly from small hand-written Markdown files and fixed-size text splitting, so it cannot reliably answer school/program/year-specific questions or explain recommendation evidence. The project already has structured admissions data in MySQL; converting that data into structured RAG chunks gives the product an enterprise-style retrieval pipeline without adding an oversized knowledge platform.

## What Changes

- Add a database-backed RAG knowledge export that converts school, college, program, admissions score, admissions plan, admissions result, source, and quality rows into structured knowledge chunks.
- Add structured chunking by entity and section instead of only fixed-size character splitting.
- Attach business metadata to each generated chunk, including school, college, program, year, section, source, data quality, and citation fields.
- Extend RAG search testing so administrators can pass metadata filters and inspect matched metadata in returned chunks.
- Add a lightweight RAG evaluation set and evaluation runner for common recommendation/retrieval questions.
- Add citation/evidence objects that recommendation reports can use to explain which RAG chunks support a conclusion.

## Capabilities

### New Capabilities
- `database-rag-knowledge-pipeline`: Covers database-derived RAG document generation, structured chunk construction, chunk metadata, evaluation sets, and recommendation citation evidence.

### Modified Capabilities
- `rag-search-testing`: Search-test requests and responses will support metadata filters and expose additional business metadata.
- `rag-trace-observability`: RAG traces will be able to record filter usage and selected citation evidence for retrieval/recommendation flows.

## Impact

- Affected backend code: `ruoyi-postgrad` RAG services, DTOs, mappers, controller endpoints, tests, and optional generated resource files.
- Affected data sources: existing MySQL tables for schools, colleges, programs, admissions facts, data sources, and data quality.
- Affected vector store: Qdrant payload metadata and deterministic chunk ids.
- No new external service dependency is required; existing embedding, rerank, and Qdrant integrations remain in use.
