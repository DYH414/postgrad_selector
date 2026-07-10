# rag-trace-observability Specification

## Purpose
Define request-level RAG trace observability for AI Chat and search-test flows so administrators can inspect recent retrieval outcomes, selected sources, stage timing, and failure summaries from the RAG management page.

## Requirements
### Requirement: AI Chat records request-level RAG trace
The system SHALL record a RAG trace whenever AI Chat attempts to use RAG retrieval for a user message.

#### Scenario: RAG retrieval succeeds
- **WHEN** AI Chat processes a user message and RAG retrieval completes
- **THEN** the trace records trace id, operation key, user id, query, retrieved count, selected count, top sources, latency values, and completion outcome

#### Scenario: RAG retrieval fails
- **WHEN** AI Chat processes a user message and RAG retrieval fails before context is produced
- **THEN** the trace records trace id, operation key, user id, query, failure stage, error summary, and completion outcome

### Requirement: RAG trace separates pipeline stages
The trace SHALL distinguish embedding, vector retrieval, rerank, context selection, and prompt injection stages where the application can observe them.

#### Scenario: Stage timings are available
- **WHEN** the system can measure a stage duration
- **THEN** the trace includes the duration in milliseconds for that stage

#### Scenario: Stage timing is unavailable
- **WHEN** a stage is handled inside a framework component without timing hooks
- **THEN** the trace records the stage as unavailable rather than inventing a duration

### Requirement: Recent RAG traces are available to administrators
The system SHALL expose recent RAG traces through an authenticated administrator endpoint.

#### Scenario: Administrator requests recent traces
- **WHEN** an administrator requests recent RAG traces
- **THEN** the system returns the most recent traces in reverse chronological order with bounded result count

#### Scenario: No traces exist
- **WHEN** no RAG traces have been recorded since application startup
- **THEN** the system returns an empty list and the UI displays an empty state

### Requirement: RAG trace UI summarizes context evidence
The RAG management page SHALL summarize recent RAG traces and show which sources were used for AI Chat context.

#### Scenario: Trace has selected chunks
- **WHEN** a trace contains selected chunks
- **THEN** the page displays query, outcome, selected sources, retrieved count, selected count, and latency summary

#### Scenario: Trace has an error
- **WHEN** a trace contains an error summary
- **THEN** the page displays the failing stage and error summary in the trace row
