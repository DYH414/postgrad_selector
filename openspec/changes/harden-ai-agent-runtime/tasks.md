## 1. Baseline and Contracts

- [ ] 1.1 Add focused failing tests for active-stream chat locking, stale lock release, fact RAG routing, confirmation-token validation, and run-id propagation.
- [ ] 1.2 Define `AgentRunContext` and the run-id fields required by chat callbacks, SSE payloads, RAG evidence requests/traces, and draft-action audit records.
- [ ] 1.3 Define Redis key formats, TTLs, atomic consume behavior, and draft version/hash contract for action-confirmation proposals.

## 2. Streaming Chat Concurrency

- [x] 2.1 Change chat lock acquisition to use an opaque owner value and a timeout aligned with model execution limits.
- [x] 2.2 Move lock release into a single owner-safe terminal helper invoked on stream completion, stream error, and startup failure.
- [x] 2.3 Ensure terminal callback paths complete the SSE response and release the lock exactly once.
- [ ] 2.4 Verify a concurrent user message is rejected during token streaming and succeeds immediately after success or error completion.

## 3. RAG Routing and Evidence Scope

- [x] 3.1 Add deterministic chat request routing for general knowledge versus candidate/program fact explanation.
- [x] 3.2 Construct fact-oriented chat assistants without the generic unfiltered RetrievalAugmentor.
- [ ] 3.3 Require scoped `getProgramEvidence` usage for candidate recommendation, risk, score, plan, result, and trend explanations.
- [ ] 3.4 Preserve the existing strict-to-broad evidence fallback and expose route, filters, selected chunks, and fallback fields in traces.
- [ ] 3.5 Add regression tests proving fact-oriented requests cannot inject generic unfiltered RAG context.

## 4. Confirmed Agent Draft Actions

- [ ] 4.1 Implement an action-proposal service that validates requested write parameters and returns an immutable diff plus single-use confirmation token.
- [ ] 4.2 Change bound Agent write tools to create proposals instead of mutating drafts directly.
- [ ] 4.3 Add an authenticated confirmation endpoint that atomically consumes the token, validates owner and draft version, then delegates to existing mutation services.
- [ ] 4.4 Record proposal, confirmation, execution, conflict, expiry, and rejection audit summaries with run id.
- [ ] 4.5 Update user chat UI to render proposal diffs and submit explicit confirmation without exposing internal program identifiers.
- [ ] 4.6 Add unit and controller tests for success, expired token, replayed token, cross-user token, and stale draft version cases.

## 5. Agent Run Correlation and Verification

- [ ] 5.1 Create a run id at chat ingress and propagate it through model listeners, tool context, RAG evidence, draft action audit, and SSE terminal events.
- [ ] 5.2 Extend RAG and AI observability responses to expose run id and retrieval route without exposing raw private prompts or secrets.
- [ ] 5.3 Add end-to-end tests for one policy question and one program-risk question, asserting route, run-id continuity, tools, citations, and terminal SSE result.
- [ ] 5.4 Run focused backend and frontend test suites, then manually verify concurrent chat rejection, action confirmation, scoped evidence, and admin trace linkage.
