## MODIFIED Requirements

### Requirement: AI Chat records request-level RAG trace
The system SHALL record a RAG trace whenever AI Chat attempts to use RAG retrieval for a user message, including applied metadata filters and selected citation evidence when available.

#### Scenario: RAG retrieval succeeds
- **WHEN** AI Chat processes a user message and RAG retrieval completes
- **THEN** the trace records trace id, operation key, user id, query, applied filters, retrieved count, selected count, top sources, citation evidence, latency values, and completion outcome

#### Scenario: RAG retrieval fails
- **WHEN** AI Chat processes a user message and RAG retrieval fails before context is produced
- **THEN** the trace records trace id, operation key, user id, query, applied filters, failure stage, error summary, and completion outcome
