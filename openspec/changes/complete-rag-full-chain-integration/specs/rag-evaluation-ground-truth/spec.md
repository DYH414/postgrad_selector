## ADDED Requirements

### Requirement: RAG evaluation uses grounded cases
The RAG evaluation workflow SHALL use concrete school, program, year, and expected-section metadata instead of placeholder natural-language prompts.

#### Scenario: Evaluate known program score-line retrieval
- **WHEN** the default RAG evaluation suite runs
- **THEN** at least one case queries a known school/program/year score-line fact
- **AND** the case includes expected metadata such as `schoolId`, `programId`, `year`, and expected section `admission_score`
- **AND** the case passes only when selected evidence matches the expected program and section

#### Scenario: Evaluate known program admission-plan retrieval
- **WHEN** the default RAG evaluation suite runs
- **THEN** at least one case queries a known school/program/year admission-plan fact
- **AND** the case includes expected metadata such as `schoolId`, `programId`, `year`, and expected section `admission_plan`
- **AND** the case passes only when selected evidence matches the expected program and section

#### Scenario: Evaluate known program admission-result retrieval
- **WHEN** the default RAG evaluation suite runs
- **THEN** at least one case queries a known school/program/year admission-result fact
- **AND** the case includes expected metadata such as `schoolId`, `programId`, `year`, and expected section `admission_result`
- **AND** the case records a missing-source-data outcome separately from a retrieval failure when the source database lacks that section

### Requirement: RAG evaluation reports actionable failure reasons
The RAG evaluation response SHALL identify why each failed case failed.

#### Scenario: No chunks are retrieved
- **WHEN** a grounded evaluation case retrieves zero chunks
- **THEN** the failure reason identifies `NO_RETRIEVAL`

#### Scenario: Wrong program is selected
- **WHEN** retrieved or selected evidence belongs to a different school or program than the expected metadata
- **THEN** the failure reason identifies `WRONG_SCOPE`

#### Scenario: Expected section is missing
- **WHEN** evidence is retrieved for the correct program but none of the selected chunks contains the expected section
- **THEN** the failure reason identifies `MISSING_SECTION`

#### Scenario: Source data is missing
- **WHEN** the configured case points to a known program but the source database has no data for the expected section
- **THEN** the failure reason identifies `SOURCE_DATA_MISSING`
- **AND** the case does not get counted as proof that vector retrieval is broken

### Requirement: RAG evaluation is visible in observability
The RAG evaluation workflow SHALL produce operator-visible trace records.

#### Scenario: Evaluation run completes
- **WHEN** an administrator runs the RAG evaluation endpoint
- **THEN** the system records an operation trace with key `rag-evaluate`
- **AND** the trace includes total case count, passed count, failed count, and representative failure summaries
