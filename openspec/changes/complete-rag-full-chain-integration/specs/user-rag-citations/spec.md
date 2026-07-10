## ADDED Requirements

### Requirement: User report displays RAG citations
The user-facing AI report SHALL display RAG citations returned by the backend in a concise evidence section.

#### Scenario: Report has citations
- **WHEN** a user opens an AI report with `ragCitations`
- **THEN** the report displays citation source name, document type, semantic section, year when available, and content preview

#### Scenario: Report has no citations
- **WHEN** a user opens an AI report without `ragCitations`
- **THEN** the report remains usable and does not show an empty broken citation section

### Requirement: Citations are grouped by recommendation context
The user-facing report SHALL group citations so users can understand which recommendation or report section they support.

#### Scenario: Candidate-level citations exist
- **WHEN** citations include program or candidate metadata
- **THEN** the UI groups or associates citations with the relevant candidate or recommendation section

#### Scenario: General citations exist
- **WHEN** citations are general methodology or data-quality evidence
- **THEN** the UI displays them in a general evidence section rather than attaching them to the wrong candidate

### Requirement: Chat can surface evidence availability
The user-facing AI chat SHALL be able to show when an answer used RAG evidence without exposing raw retrieval internals.

#### Scenario: Chat answer has evidence metadata
- **WHEN** a chat response includes evidence metadata
- **THEN** the UI can display a compact source indicator or expandable source list

#### Scenario: Chat answer has no evidence metadata
- **WHEN** a chat response does not include evidence metadata
- **THEN** the UI renders the answer normally without implying that RAG evidence was used
