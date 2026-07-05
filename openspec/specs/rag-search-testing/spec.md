# rag-search-testing Specification

## Purpose
TBD - created by archiving change add-rag-search-test-and-trace. Update Purpose after archive.
## Requirements
### Requirement: Administrator can test RAG retrieval for a query
The system SHALL provide an authenticated administrator endpoint that accepts a text query and returns the chunks retrieved by the current RAG pipeline without invoking the chat model.

#### Scenario: Query returns retrieved chunks
- **WHEN** an administrator submits a non-empty query to the RAG search-test endpoint
- **THEN** the system returns the query, retrieval parameters, total retrieved count, and an ordered list of retrieved chunks

#### Scenario: Query is blank
- **WHEN** an administrator submits a blank or whitespace-only query
- **THEN** the system rejects the request with a validation error and does not call the embedding model or Qdrant

### Requirement: Search-test results expose evidence metadata
Each returned chunk SHALL include enough metadata for an operator to understand why the chunk may be useful as RAG context.

#### Scenario: Chunk metadata is available
- **WHEN** a retrieved chunk has metadata from indexing
- **THEN** the result includes source name, source id, document type, chunk index, chunk title, content hash, and content preview

#### Scenario: Metadata is missing
- **WHEN** a retrieved chunk lacks one or more metadata fields
- **THEN** the result still includes the chunk text preview and marks missing metadata fields as empty or unknown

### Requirement: Search-test results expose retrieval and rerank information
The system SHALL expose retrieval ordering and rerank ordering so administrators can compare vector recall and final context selection.

#### Scenario: Rerank succeeds
- **WHEN** vector retrieval and rerank both complete
- **THEN** the result includes vector rank, rerank rank, rerank score where available, and whether the chunk would be selected for prompt context

#### Scenario: Rerank fails
- **WHEN** vector retrieval succeeds but rerank fails
- **THEN** the result returns vector retrieval order, marks rerank as failed, records the error summary, and does not fail the whole search-test response

### Requirement: RAG management UI provides a search-test panel
The RAG management page SHALL allow administrators to run a query and inspect returned chunks in the browser.

#### Scenario: Administrator runs a search test
- **WHEN** an administrator enters a query and submits the search-test form
- **THEN** the page displays retrieved chunks with source, rank, score, selected status, and content preview

#### Scenario: Search test fails
- **WHEN** the backend search-test endpoint returns an error
- **THEN** the page displays a clear error message without clearing the current RAG status cards

