## ADDED Requirements

### Requirement: Documentation describes the database RAG lifecycle
Project documentation SHALL describe the current database RAG lifecycle from MySQL export through user-visible evidence.

#### Scenario: Operator reads RAG docs
- **WHEN** an operator reads the RAG operations documentation
- **THEN** the documentation explains database document generation, Qdrant indexing, metadata-filtered search testing, AI usage, and user citation display

#### Scenario: Old manual RAG docs remain
- **WHEN** documentation mentions `rag-documents/`
- **THEN** it clearly distinguishes that manual general knowledge path from `rag-generated/` database recommendation evidence

### Requirement: Acceptance checks cover the full RAG chain
The project SHALL define acceptance checks for the full database RAG chain.

#### Scenario: Full chain is accepted
- **WHEN** the acceptance checklist is executed
- **THEN** it verifies document generation, database reindexing, metadata-filtered retrieval, recommendation evidence retrieval, report citation rendering, and trace visibility
- **AND** it verifies that generation-only state is not treated as indexed state
- **AND** it verifies that the default grounded evaluation suite passes its configured threshold

#### Scenario: Partial chain fails
- **WHEN** one stage of the full chain is unavailable
- **THEN** the checklist identifies the failed stage and the expected diagnostic page, endpoint, or log to inspect

#### Scenario: User-facing RAG answer is accepted
- **WHEN** a user asks why a recommended program was selected and what risks it has
- **THEN** the AI chat uses the draft context and program evidence retrieval tool without hallucinated tool errors
- **AND** the admin trace list contains an `ai-chat` record and a `recommendation-evidence` record for the interaction

#### Scenario: Report citations are accepted
- **WHEN** a user generates a recommendation report after database RAG evidence is indexed
- **THEN** the report response includes `ragCitations`
- **AND** the user report page renders evidence titles, source previews, document type, section, year, and source URL when available
