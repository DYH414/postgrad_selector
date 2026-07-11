## ADDED Requirements

### Requirement: Agent draft writes require explicit confirmation
The system SHALL require a user confirmation token before an Agent-initiated operation can add, remove, replace, refill, or batch modify draft candidates.

#### Scenario: Agent proposes a draft modification
- **WHEN** the model requests a draft write tool
- **THEN** the system SHALL return a non-mutating proposed action with a human-readable diff and a single-use confirmation token

#### Scenario: User confirms a proposed modification
- **WHEN** the authenticated owner submits a valid unexpired confirmation token
- **THEN** the system SHALL execute exactly the proposed operation once and return the resulting draft state

#### Scenario: Missing or invalid confirmation token
- **WHEN** a write confirmation is missing, expired, already consumed, or belongs to another user
- **THEN** the system SHALL reject the operation without changing the draft

### Requirement: Agent draft writes are auditable
The system SHALL record an audit summary for every proposed and executed Agent draft write.

#### Scenario: Proposed action is recorded
- **WHEN** a write tool creates a proposal
- **THEN** the audit summary SHALL include run id, user id, action type, parameter summary, draft version, and proposal outcome

#### Scenario: Confirmed action is recorded
- **WHEN** a proposed action is executed or rejected
- **THEN** the audit summary SHALL include run id, confirmation outcome, resulting draft version when executed, and error summary when rejected
