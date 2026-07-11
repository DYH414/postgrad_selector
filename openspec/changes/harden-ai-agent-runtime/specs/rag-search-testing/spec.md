## ADDED Requirements

### Requirement: Fact-oriented chat uses scoped evidence retrieval
The system SHALL route candidate, school, program, admission-score, enrollment-plan, admission-result, and recommendation-risk explanations through Metadata-filtered evidence retrieval.

#### Scenario: Chat explains a candidate in the current draft
- **WHEN** a user asks why a draft candidate is recommended or what its risk is
- **THEN** the system SHALL retrieve evidence scoped to the candidate program and available school, year, document type, or section metadata

#### Scenario: Scoped retrieval has no strict evidence
- **WHEN** strict program evidence retrieval returns no chunks and a documented broad fallback is applicable
- **THEN** the system SHALL apply only the configured broad fallback, record that fallback, and preserve the candidate scope

### Requirement: Generic retrieval does not contaminate fact-oriented answers
The system SHALL NOT inject unfiltered general RAG context into a chat run classified as a fact-oriented candidate or program explanation.

#### Scenario: Fact-oriented chat has both policy and program-like terms
- **WHEN** a user message is classified as fact-oriented
- **THEN** the system SHALL use scoped evidence as the factual context and SHALL NOT add unfiltered automatic retrieval chunks
