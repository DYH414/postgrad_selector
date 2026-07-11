## MODIFIED Requirements

### Requirement: AI Chat records request-level RAG trace
The system SHALL record a RAG trace whenever AI Chat attempts to use RAG retrieval for a user message. Each trace SHALL include the originating run id and retrieval route in addition to observable retrieval outcome fields.

#### Scenario: RAG retrieval succeeds
- **WHEN** AI Chat processes a user message and RAG retrieval completes
- **THEN** the trace records trace id, run id, retrieval route, operation key, user id, query, retrieved count, selected count, top sources, latency values, and completion outcome

#### Scenario: RAG retrieval fails
- **WHEN** AI Chat processes a user message and RAG retrieval fails before context is produced
- **THEN** the trace records trace id, run id, retrieval route, operation key, user id, query, failure stage, error summary, and completion outcome
