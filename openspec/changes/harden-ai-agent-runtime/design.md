## Context

AI chat currently uses LangChain4j streaming, Redis-backed draft and conversation state, Tool Calling, a generic `RetrievalAugmentor`, and an explicit `getProgramEvidence` tool. The current chat lock is released immediately after `TokenStream.start()`, while completion callbacks continue asynchronously. Generic automatic retrieval has no candidate metadata scope, whereas explicit evidence retrieval does. Draft write tools mutate state in the same model turn and current observability cannot correlate a user message to its tools and evidence.

The first hardening stage must preserve the existing recommendation rule boundary: MySQL facts and deterministic services remain authoritative; the model may request explanation data and propose user-facing actions but must not independently commit sensitive draft changes.

## Goals / Non-Goals

**Goals:**

- Serialize a user's active streaming chat through terminal completion or error.
- Make fact-oriented chat evidence program-scoped and traceable.
- Require an authenticated, single-use confirmation before Agent-initiated draft writes.
- Propagate a run id through chat, model, tool, RAG and SSE terminal events.
- Retain existing direct REST draft mutation endpoints unless separately migrated.

**Non-Goals:**

- Replacing the global thread pool, implementing a durable event stream, or adding cancellation propagation.
- Introducing a vector-based long-term conversation memory.
- Persisting all traces to an external observability backend.
- Re-ranking candidate recommendation order with RAG.

## Decisions

### Chat lock is owned by the asynchronous run

Generate a random owner token when `AiChatServiceImpl.chat` accepts a message. Release with Redis compare-and-delete in exactly one terminal helper called by stream completion, stream error, and startup failure. The outer method must not delete the lock after `start()`.

This is preferred to a Java synchronized lock because Redis retains cross-process behavior. A lock value is required because TTL expiration can otherwise let a stale callback remove a later run's lock. Lock TTL will initially exceed the configured model timeout; lease renewal can be added later if the provider SLA requires longer runs.

### RAG is selected by intent and scope, not injected universally

Classify a chat request before assistant construction using deterministic context: explicit draft program references, current draft entities, and known fact/risk/trend intent. Fact-oriented requests construct an assistant without the generic `RetrievalAugmentor`; the prompt/tool contract requires `getProgramEvidence`. General policy/exam questions retain the generic augmentor.

This is preferred to trying to post-filter generic retrieval because context may already have reached the model. The initial classifier is intentionally conservative: uncertainty falls back to general retrieval and never invents a program id.

### Writes become proposals with confirmation tokens

Replace direct mutation behavior of bound write tools with a proposal service. The proposal contains user id, run id, action type, validated parameters, base draft version/hash, human-readable diff, expiry, and a random confirmation token stored in Redis. The tool returns only proposal data. A new authenticated confirmation endpoint consumes the token atomically, rechecks ownership and draft version, executes the existing mutation service, and records the outcome.

This is preferred to relying on prompt wording because prompt injection and ambiguous language are not enforceable security boundaries. Direct user-facing REST draft operations remain unchanged in this stage because they are explicit UI commands, not autonomous Agent actions.

### Run id is an application-level correlation key

Create a UUID at chat request ingress and pass it explicitly through callback context, tool context, RAG evidence request/trace, draft action proposal/audit, and SSE events. It is not derived from user id or conversation id, avoiding accidental exposure of stable identifiers.

Explicit propagation is preferred to ThreadLocal-only propagation because model streaming and tool callbacks may execute on framework-managed threads.

## Risks / Trade-offs

- [Intent classifier routes a fact question as general] -> Preserve the existing explicit tool rules and log the route/run id; tune classification from real traces before broadening automation.
- [Draft changes between proposal and confirmation] -> Compare base draft version/hash at confirmation and require the user to request a new proposal after a conflict.
- [Redis outage] -> Fail closed for chat locking and confirmation; do not execute Agent writes without durable confirmation state.
- [Lock TTL expires during a slow provider response] -> Set TTL above model timeout and use owner-safe release; add lease renewal if production measurements show expiry.
- [Additional confirmation step adds friction] -> Return a concise diff and one-click confirm UX; retain direct UI controls for explicit manual edits.

## Migration Plan

1. Add tests that reproduce current early lock release, generic-RAG contamination, and direct write behavior.
2. Ship backend run context and lock fix behind no behavioral UI change.
3. Ship RAG route selection and verify traces on staging with representative policy and candidate questions.
4. Ship proposal/confirm APIs and frontend confirmation UI together; disable direct Agent write execution only after the UI can confirm proposals.
5. Monitor lock rejection rate, confirmation failure rate, scoped-RAG fallback rate, tool error rate, and run-id trace completeness.
6. Roll back the confirmation UI/API feature flag if needed while retaining the lock and run-id fixes; do not roll back to unsafe direct Agent writes without an explicit risk decision.

## Open Questions

- What maximum chat stream duration should the lock lease cover under the selected model provider's observed P99?
- Should batch replacement require one confirmation for the full diff or one confirmation per candidate?
- Which user-facing event should render the run id for support without exposing it unnecessarily?
