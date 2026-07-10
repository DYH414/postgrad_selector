## ADDED Requirements

### Requirement: Search-test supports metadata filters
The administrator RAG search-test workflow SHALL allow metadata-filtered retrieval for database-generated RAG chunks.

#### Scenario: Search with program filter
- **WHEN** an administrator submits a query with `programId`
- **THEN** the backend restricts retrieval to chunks whose metadata matches the requested program id

#### Scenario: Search with document type and section filters
- **WHEN** an administrator submits `docType` or `section` filters
- **THEN** the backend applies those filters and the UI displays the active filter values with the results

#### Scenario: Search with no filters
- **WHEN** an administrator submits only a text query
- **THEN** the search-test endpoint preserves the existing unfiltered retrieval behavior

### Requirement: Search-test UI exposes database RAG metadata
The RAG management page SHALL expose database RAG metadata in search-test results.

#### Scenario: Database chunk is returned
- **WHEN** a retrieved chunk contains database RAG metadata
- **THEN** the result table displays program id, school id, year, document type, section, source document, vector rank, rerank rank, and selected status where available

#### Scenario: Metadata is missing
- **WHEN** a retrieved chunk lacks database RAG metadata
- **THEN** the result table still renders the chunk and marks missing fields as empty or unknown
