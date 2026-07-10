## MODIFIED Requirements

### Requirement: Administrator can test RAG retrieval for a query
The system SHALL provide an authenticated administrator endpoint that accepts a text query and optional metadata filters, then returns the chunks retrieved by the current RAG pipeline without invoking the chat model.

#### Scenario: Query returns retrieved chunks
- **WHEN** an administrator submits a non-empty query to the RAG search-test endpoint
- **THEN** the system returns the query, retrieval parameters, applied filters, total retrieved count, and an ordered list of retrieved chunks

#### Scenario: Query uses metadata filters
- **WHEN** an administrator submits a query with school, program, year, doc type, or section filters
- **THEN** the system restricts retrieval to chunks whose metadata matches the supplied filters

#### Scenario: Query is blank
- **WHEN** an administrator submits a blank or whitespace-only query
- **THEN** the system rejects the request with a validation error and does not call the embedding model or Qdrant

### Requirement: Search-test results expose evidence metadata
Each returned chunk SHALL include enough metadata for an operator to understand why the chunk may be useful as RAG context.

#### Scenario: Chunk metadata is available
- **WHEN** a retrieved chunk has metadata from indexing
- **THEN** the result includes source name, source id, document type, school, college, program, year, section, chunk index, chunk title, content hash, citation url, and content preview

#### Scenario: Metadata is missing
- **WHEN** a retrieved chunk lacks one or more metadata fields
- **THEN** the result still includes the chunk text preview and marks missing metadata fields as empty or unknown
