## ADDED Requirements

### Requirement: Database facts can be converted into RAG knowledge chunks
The system SHALL convert existing postgraduate selection database facts into structured RAG chunks grouped by school, college, program, year, and semantic section.

#### Scenario: Program-year facts produce section chunks
- **WHEN** a program-year row contains school, college, program, year, score, plan, result, source, and quality facts
- **THEN** the system emits separate chunks for semantic sections such as basic info, exam subjects, admission score, admission plan, admission result, data quality, and source evidence

#### Scenario: Missing optional facts are represented explicitly
- **WHEN** a program-year row is missing score, plan, result, or official source facts
- **THEN** the generated chunk text and metadata identify the missing facts instead of silently omitting the section

### Requirement: Generated chunks include business metadata
Each generated RAG chunk SHALL include metadata that can be used for filtering, ranking, tracing, and citation.

#### Scenario: Chunk metadata is generated
- **WHEN** a database row is converted into a chunk
- **THEN** the chunk metadata includes doc type, school id, school name, college id, college name, program id, program code, program name, year, section, source owner, source url, quality level, chunk id, chunk title, content hash, and generated timestamp

#### Scenario: Stable chunk identity is generated
- **WHEN** the same database facts are converted repeatedly
- **THEN** the chunk id remains stable for the same entity, year, section, and content hash

### Requirement: Generated knowledge can be indexed into Qdrant
The system SHALL index generated database RAG chunks into the existing Qdrant-backed embedding store alongside or instead of manual RAG documents.

#### Scenario: Administrator rebuilds generated knowledge index
- **WHEN** an administrator triggers database RAG export and reindex
- **THEN** the system reads database facts, builds structured chunks, embeds them, writes them to Qdrant, and returns document and chunk counts

#### Scenario: No database facts exist
- **WHEN** database export finds no eligible facts
- **THEN** the system returns a clear zero-count result and does not mark indexing as successful for database knowledge

### Requirement: RAG evaluation set can verify retrieval quality
The system SHALL provide a lightweight evaluation set for representative RAG questions and compute retrieval quality against expected metadata.

#### Scenario: Evaluation case has expected metadata
- **WHEN** an evaluation case defines a query and expected school, program, year, doc type, or section metadata
- **THEN** the evaluator checks whether retrieved chunks contain at least one expected metadata match in the selected top-k results

#### Scenario: Evaluation run completes
- **WHEN** an administrator runs the evaluation set
- **THEN** the system returns total cases, passed cases, failed cases, per-case retrieved evidence, and an aggregate pass rate

### Requirement: Recommendation reports can attach RAG citations
The system SHALL provide citation objects that recommendation report generation can use to explain factual claims with retrieved RAG evidence.

#### Scenario: Citation evidence is built from selected chunks
- **WHEN** retrieved chunks are selected as evidence for a recommendation claim
- **THEN** the system creates citations containing title, source name, source url, school, program, year, section, content hash, and content preview

#### Scenario: Evidence is unavailable
- **WHEN** no selected chunk supports a recommendation claim
- **THEN** the system records an empty citation list rather than fabricating a source
