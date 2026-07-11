## ADDED Requirements

### Requirement: Streaming chat holds a user lock for the complete run
The system SHALL hold a per-user chat lock from accepting a message until the associated streaming model run completes, fails, or cannot be started.

#### Scenario: Concurrent message is rejected while a stream is active
- **WHEN** a user submits a second chat message before the first stream reaches a terminal outcome
- **THEN** the system SHALL reject the second message without persisting it or invoking a model

#### Scenario: Lock is released after successful stream completion
- **WHEN** a streaming chat run completes successfully
- **THEN** the system SHALL release only the lock owned by that run and allow a subsequent user message

#### Scenario: Lock is released after stream failure
- **WHEN** a streaming chat run fails or cannot be started
- **THEN** the system SHALL release only the lock owned by that run and allow a subsequent user message

### Requirement: Lock ownership is protected from stale releases
The system SHALL associate each chat lock with an opaque owner value and SHALL delete the lock only when the stored owner value matches the completing run.

#### Scenario: A stale run attempts to release a replaced lock
- **WHEN** a prior run's lock has expired and a later run has acquired a replacement lock
- **THEN** the prior run SHALL NOT delete the later run's lock
