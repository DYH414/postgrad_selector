## ADDED Requirements

### Requirement: Agent runs have a correlation identifier
The system SHALL assign a unique run id to each AI chat request and SHALL propagate it to model invocation, tool execution, RAG evidence retrieval, draft-action audit, and terminal SSE event payloads.

#### Scenario: Chat run starts
- **WHEN** an authenticated user sends a chat message
- **THEN** the system SHALL create a run id before model invocation and include it in observable events for that message

#### Scenario: Tool and RAG work occurs during a run
- **WHEN** the model invokes a tool or retrieves RAG evidence
- **THEN** the resulting tool summary and RAG trace SHALL contain the originating run id

#### Scenario: Run terminates with an error
- **WHEN** the chat stream fails
- **THEN** the terminal error event and associated trace SHALL contain the same run id
