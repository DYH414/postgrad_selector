## ADDED Requirements

### Requirement: Backend retrieves program-scoped RAG evidence
The backend SHALL provide a recommendation evidence retrieval service that retrieves Qdrant chunks using program-aware metadata filters before vector retrieval and rerank.

#### Scenario: Retrieve evidence for a program
- **WHEN** the recommendation flow requests evidence for a known `programId` and question
- **THEN** the service searches with `program_id` metadata filters and returns selected chunks with citation metadata

#### Scenario: Program filter has no results
- **WHEN** a program-scoped retrieval returns no chunks
- **THEN** the service records the empty result and may apply a controlled fallback without inventing evidence

### Requirement: AI chat can use recommendation evidence
AI chat SHALL be able to retrieve program-specific RAG evidence when answering recommendation explanation questions.

#### Scenario: User asks why a school is recommended
- **WHEN** a user asks AI chat why a specific recommended program is suitable or risky
- **THEN** the assistant can use a backend evidence retrieval tool and ground the answer in retrieved RAG chunks plus structured draft data

#### Scenario: Evidence retrieval fails
- **WHEN** the evidence retrieval tool fails during chat
- **THEN** the assistant response does not fabricate sources and the backend records a RAG trace failure

### Requirement: Recommendation reports include RAG evidence citations
Final recommendation reports SHALL include RAG citations derived from selected evidence chunks where available.

#### Scenario: Report evidence is available
- **WHEN** report generation can retrieve selected chunks for report candidates
- **THEN** the report response includes `ragCitations` derived from chunks with source, document type, section, preview, and optional URL metadata

#### Scenario: Report evidence is unavailable
- **WHEN** RAG evidence cannot be retrieved for a report candidate
- **THEN** report generation completes using MySQL facts and includes an evidence-unavailable warning rather than fabricating citations

### Requirement: MySQL facts remain authoritative for structured fields
The system SHALL keep MySQL-derived candidate facts authoritative for scores, years, plans, source URLs, and identity fields.

#### Scenario: RAG chunk conflicts with structured fact
- **WHEN** a retrieved RAG chunk appears inconsistent with the candidate's structured MySQL snapshot
- **THEN** the report uses the structured fact for numeric and identity fields and treats the chunk as supporting context only
