## ADDED Requirements

### Requirement: Administrator can generate database RAG documents
The system SHALL provide an authenticated administrator workflow for generating database-backed RAG documents from structured MySQL data.

#### Scenario: Generate documents with year range
- **WHEN** an administrator submits a start year, end year, and limit for database RAG generation
- **THEN** the backend generates `program_history`, `program_year`, and `data_quality` documents under the configured generated document directory

#### Scenario: Generation returns a summary
- **WHEN** database RAG document generation completes
- **THEN** the response includes generated document count, output directory, and a bounded list of written files or examples

#### Scenario: Generation fails
- **WHEN** database RAG document generation fails
- **THEN** the UI displays a clear failure message and keeps the previous RAG status visible

### Requirement: Administrator can index database RAG knowledge
The system SHALL provide an authenticated administrator workflow for indexing database-generated semantic chunks into Qdrant.

#### Scenario: Reindex database knowledge
- **WHEN** an administrator starts database RAG reindexing with year range and limit parameters
- **THEN** the backend indexes generated semantic chunks with business metadata into the configured Qdrant collection

#### Scenario: Reindex status is updated
- **WHEN** database RAG reindexing completes
- **THEN** RAG status reflects document count, chunk count, last indexed time, and last run mode for database knowledge

### Requirement: Admin UI distinguishes manual and database RAG flows
The RAG management page SHALL distinguish manual general knowledge operations from database recommendation evidence operations.

#### Scenario: Database controls are visible
- **WHEN** an administrator opens the RAG management page
- **THEN** the page shows database document generation and database reindex controls with year range and limit inputs

#### Scenario: Manual flow remains accessible
- **WHEN** the manual `rag-documents/` flow is still supported
- **THEN** the page labels it separately from database-generated recommendation evidence

### Requirement: Admin UI guides the database RAG operation to an indexed state
The RAG management page SHALL make it clear that generated Markdown documents are not searchable until database RAG indexing completes.

#### Scenario: Documents generated but not indexed
- **WHEN** an administrator successfully generates database RAG documents
- **THEN** the page shows the generated document count and output directory
- **AND** the page explains that Qdrant has not been updated by generation alone
- **AND** the page offers the next action to index database RAG evidence

#### Scenario: Combined database RAG operation completes
- **WHEN** an administrator runs a combined generate-and-index database RAG operation
- **THEN** the operation generates documents using the requested start year, end year, and limit
- **AND** the operation indexes the generated database RAG chunks into Qdrant
- **AND** the page refreshes status and trace data after indexing completes

#### Scenario: Combined database RAG operation partially fails
- **WHEN** document generation succeeds but indexing fails
- **THEN** the page keeps the generated document summary visible
- **AND** the page displays the indexing failure as the current action failure
- **AND** the status does not imply the Qdrant index contains the newly generated documents

### Requirement: RAG status describes the active knowledge source
The system SHALL expose enough status metadata for operators to understand what kind of knowledge was last indexed.

#### Scenario: Database knowledge was last indexed
- **WHEN** the last successful indexing operation used database-generated knowledge
- **THEN** the status identifies database mode and avoids describing the count as only `rag-documents/` files

#### Scenario: No database knowledge is indexed
- **WHEN** the database RAG index has not been generated or indexed
- **THEN** the admin UI provides an actionable empty state explaining the next generation and indexing steps
