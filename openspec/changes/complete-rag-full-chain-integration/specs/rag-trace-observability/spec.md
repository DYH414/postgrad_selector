## ADDED Requirements

### Requirement: Database RAG operations are observable
The RAG observability surface SHALL show recent database RAG generation and reindexing outcomes.

#### Scenario: Database reindex succeeds
- **WHEN** database RAG reindexing completes successfully
- **THEN** the admin page can show last run mode, indexed document count, chunk count, and last indexed time

#### Scenario: Database reindex fails
- **WHEN** database RAG reindexing fails
- **THEN** the admin page shows the last error and does not imply the index is healthy

### Requirement: Recommendation evidence retrieval records traces
The system SHALL record traces for recommendation evidence retrieval.

#### Scenario: Evidence retrieval succeeds
- **WHEN** the recommendation report or chat retrieves RAG evidence
- **THEN** a trace records operation key, query or evidence intent, filters, retrieved count, selected count, top sources, and outcome

#### Scenario: Evidence retrieval falls back
- **WHEN** evidence retrieval uses a fallback because a strict metadata filter returned no chunks
- **THEN** the trace records the fallback mode and final selected count

### Requirement: Search-test records detailed retrieval traces
The system SHALL record detailed traces for administrator search-test runs.

#### Scenario: Search-test succeeds
- **WHEN** an administrator runs a metadata-filtered search-test
- **THEN** the trace records query, metadata filters, retrieved count, selected count, top sources, total latency, embedding latency, retrieval latency, and rerank latency when available

#### Scenario: Search-test rerank fails but retrieval succeeds
- **WHEN** vector retrieval succeeds but rerank fails during search-test
- **THEN** the trace records failure stage `rerank`
- **AND** the trace keeps retrieved count and selected fallback chunks visible

#### Scenario: Search-test returns no chunks
- **WHEN** search-test returns zero chunks
- **THEN** the trace records the filters that produced the empty result
- **AND** the admin UI can distinguish an empty retrieval from an endpoint failure

### Requirement: Evaluation runs are observable
The system SHALL record traces for RAG evaluation runs.

#### Scenario: Evaluation run has failures
- **WHEN** the RAG evaluation endpoint completes with failed cases
- **THEN** the trace records operation key `rag-evaluate`
- **AND** the trace records total cases, failed cases, and representative failure reasons

#### Scenario: Evaluation run passes
- **WHEN** all grounded RAG evaluation cases pass
- **THEN** the trace records operation key `rag-evaluate`
- **AND** the trace outcome is success

### Requirement: Admin UI distinguishes trace operation types
The RAG management page SHALL distinguish search-test, AI chat, recommendation evidence, and database operation traces.

#### Scenario: Multiple trace types exist
- **WHEN** recent traces include different operation keys
- **THEN** the UI shows operation labels that let administrators identify which product flow produced each trace
