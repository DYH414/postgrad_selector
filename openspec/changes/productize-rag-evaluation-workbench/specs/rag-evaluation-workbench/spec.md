## ADDED Requirements

### Requirement: Administrators can manage versioned RAG evaluation datasets
The system SHALL allow authorized administrators to create and inspect `DEV`, `FROZEN`, and `SAFETY` RAG evaluation datasets with a stable version identifier and lifecycle state.

#### Scenario: Create an editable development dataset
- **WHEN** an authorized administrator creates a dataset with type `DEV`
- **THEN** the system persists an editable dataset with a unique identifier, name, version, description, and audit metadata

#### Scenario: Frozen dataset is immutable
- **WHEN** an administrator attempts to add, edit, or delete a case in a `FROZEN` dataset
- **THEN** the system rejects the mutation and instructs the administrator to copy it into a new development version

#### Scenario: Safety cases remain separate
- **WHEN** a case is intended to verify no-answer or false-positive behavior
- **THEN** the system stores and evaluates it in a `SAFETY` dataset rather than mixing it into positive ranking denominators

### Requirement: Administrators can curate evaluation cases with human ground truth
The system SHALL support human-authored ground truth for queries, expected metadata filters, relevant evidence conditions, no-answer expectations, category, difficulty, tags, and reviewer notes.

#### Scenario: Save a positive retrieval case
- **WHEN** an administrator supplies a query and at least one relevant evidence condition
- **THEN** the system validates and persists the case in an editable development dataset

#### Scenario: Save a no-answer case
- **WHEN** an administrator marks a case as no-answer
- **THEN** the system allows no relevant evidence condition and records the abstention expectation explicitly

#### Scenario: Invalid annotation is rejected
- **WHEN** a positive case has neither a relevant condition nor a relevant source reference
- **THEN** the system rejects the case with a field-level validation error

### Requirement: Development datasets can be frozen as auditable snapshots
The system SHALL publish an immutable dataset version from an editable development dataset and SHALL produce a deterministic JSONL representation and SHA-256 manifest.

#### Scenario: Freeze a valid development dataset
- **WHEN** an administrator freezes a non-empty valid development dataset
- **THEN** the system creates a new immutable version, records its case count and SHA-256, and exports matching JSONL and manifest content

#### Scenario: Frozen checksum is reproducible
- **WHEN** the same frozen version is exported more than once
- **THEN** canonical case ordering and serialization produce the same SHA-256 checksum

### Requirement: Administrators can run persistent retrieval experiments
The system SHALL execute an evaluation dataset using the existing RAG retrieval and metric services and SHALL persist the run status, dataset version, index identity, model identity, retrieval parameters, aggregate metrics, and per-case outcomes.

#### Scenario: Run vector and rerank ablation
- **WHEN** an administrator starts the standard ablation for a frozen ranking dataset
- **THEN** the system evaluates `VECTOR_ONLY` and `VECTOR_RERANK` against the same cases and immutable parameter snapshot

#### Scenario: Run safety evaluation
- **WHEN** an administrator starts a safety dataset run
- **THEN** the system calculates no-answer accuracy and false-positive rate separately from positive ranking metrics

#### Scenario: One case fails technically
- **WHEN** a case encounters an embedding, Qdrant, rerank, or persistence error
- **THEN** the system records the case error and continues the remaining cases unless the run is cancelled

#### Scenario: Duplicate run submission
- **WHEN** an equivalent dataset and parameter snapshot is already running
- **THEN** the system rejects the duplicate submission or returns the existing active run identifier

### Requirement: Evaluation execution is isolated from online recommendation traffic
The system SHALL use a bounded evaluation executor and SHALL not mutate online RAG configuration while an experiment is running.

#### Scenario: Evaluation capacity is exhausted
- **WHEN** the bounded executor cannot accept another run
- **THEN** the system returns a stable capacity error without consuming online recommendation executor capacity

#### Scenario: Service restarts during a run
- **WHEN** the application restarts while a run is `RUNNING`
- **THEN** startup recovery marks the run `INTERRUPTED` and preserves completed case results for diagnosis

### Requirement: Administrators can inspect and annotate case-level failures
The workbench SHALL expose expected evidence, effective metadata, vector candidates, rerank ordering, selected evidence, latency, and failure classification for each evaluated case.

#### Scenario: Inspect a ranking regression
- **WHEN** an administrator opens a regressed case result
- **THEN** the page displays expected evidence beside vector rank, vector score, rerank rank, rerank score, and final selection

#### Scenario: Add human diagnosis
- **WHEN** an administrator assigns a supported failure category and review note
- **THEN** the diagnosis is persisted with reviewer and timestamp without changing the immutable run output

### Requirement: Administrators can compare compatible evaluation runs
The system SHALL compare two completed runs only when they use the same frozen dataset checksum and SHALL report aggregate and per-case changes.

#### Scenario: Compare compatible runs
- **WHEN** two completed runs share the same dataset checksum
- **THEN** the workbench displays metric deltas, latency deltas, and improved, unchanged, and regressed case counts

#### Scenario: Compare incompatible runs
- **WHEN** two runs use different dataset checksums
- **THEN** the system permits side-by-side inspection but does not present a direct improvement percentage

### Requirement: Production traces can enter the evaluation feedback loop
The system SHALL allow an authorized administrator to copy an eligible persistent RAG trace into an editable development dataset after reviewing and completing its ground truth.

#### Scenario: Copy trace into development dataset
- **WHEN** an administrator selects a trace and a target development dataset
- **THEN** the system pre-fills the query and observed metadata but requires explicit ground-truth review before saving the case

#### Scenario: Copy trace into frozen dataset
- **WHEN** an administrator selects a frozen target dataset
- **THEN** the system rejects the operation because frozen datasets are immutable

### Requirement: Evaluation reports are reproducible and exportable
The system SHALL export completed evaluation runs as JSON and Markdown reports containing metrics, configuration, dataset checksum, index version, model versions, Git commit where available, and failure summaries.

#### Scenario: Export completed run
- **WHEN** an administrator exports a completed run
- **THEN** the generated report is derived from persisted run and case results and does not rerun retrieval

#### Scenario: Export unfinished run
- **WHEN** an administrator requests a final report for a pending or running evaluation
- **THEN** the system rejects the request and identifies the current run state

