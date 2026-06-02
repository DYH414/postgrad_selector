# AI Agent Recommendation Design

## Purpose

The current AI recommendation flow is too dependent on a narrow candidate pool and score-distance rules. The next version should let AI make more flexible school-selection decisions while keeping the product's truthfulness boundary: local database data is the primary recommendation source, and internet search is only used to verify or supplement evidence.

The target experience is a hybrid mode:

- The user can click one button to get an initial AI recommendation report.
- The user can continue chatting with AI to narrow, expand, or change the recommendation direction.
- AI can call tools to query the local database and, when needed, verify official information online.
- Every recommendation must explain its basis and disclose missing or unverified data.

## Product Principles

1. Do not fake missing data. If the platform has no reliable admission data, the school can enter a pending-verification pool but must not be presented as a confirmed main recommendation.
2. Do not make the AI depend on a tiny static pool. The initial pool should be broad enough for meaningful exploration.
3. Do not present a pseudo-precise "match percentage" as if it were a scientific probability.
4. Keep AI flexible, but force every final recommendation to be traceable to local database records, tool results, or clearly labeled verification evidence.
5. Internet search is evidence verification, not the main recommendation engine.

## High-Level Architecture

The AI recommendation system becomes a controlled agent with three layers:

1. Local database layer
   - Owns the primary school/program/admission data.
   - Builds and expands candidate pools.
   - Provides structured query tools for the AI.

2. AI decision layer
   - Reads the user profile and candidate-pool summaries.
   - Calls tools to inspect, filter, compare, and expand candidates.
   - Produces an initial report and follow-up chat answers.

3. Verification layer
   - Uses internet search only to verify official or near-official evidence.
   - Returns source summaries and verification status.
   - Does not directly add schools to the main recommendation list unless local data and product rules allow it.

## User Flow

1. User opens the standalone AI recommendation page.
2. Frontend reads and displays the recommendation basis:
   - estimated score
   - target regions
   - undergraduate tier
   - cross-major status
   - undergraduate major
   - degree/study-mode preferences
3. User clicks quick recommendation.
4. Backend builds a broad candidate pool from the local database.
5. AI receives a compact user-profile summary, candidate-pool statistics, representative candidates, and available tool instructions.
6. AI calls local database tools as needed, then generates an initial report.
7. User can continue chatting. AI can call tools to search, compare, expand, or verify.
8. Report and chat responses display both recommendation judgement and evidence.

## Candidate Pool Strategy

The current "up to 50 candidates" approach should be replaced by a two-level pool.

### Working Candidate Pool

The working pool should contain about 300 to 500 programs. It is not the final recommendation list. It is the AI's search space for initial report generation and follow-up chat.

The 300-500 range is an initial engineering constraint, not a fixed product truth. It balances:

- AI context-window pressure: the model should receive pool summaries and representative rows, not hundreds of full records.
- Response latency: the initial report should remain usable in an interactive page.
- Recommendation breadth: the pool must be much wider than the current 50-row summary so AI can explore safer, steadier, and stretch choices.
- Redis/report storage size: the pool and tool traces should remain cheap enough to cache per conversation/report.

The exact limit should be configurable and tuned with measured prompt size, report latency, and recommendation quality. A reasonable first implementation is `initialPoolLimit = 300` and `maxExpandedPoolLimit = 500`.

Hard filters:

- school and program are active
- program is within the supported exam/professional scope
- study mode matches user preference unless the user accepts broader options
- there is at least basic score evidence, such as score line or admitted-score statistics

Soft ranking signals:

- target region match
- degree type match
- professional direction match
- data completeness A/B
- has admitted average score
- has admitted low/high score
- has plan count or admitted count
- data year recency

Score range should be broader than the current tight proximity model. A practical starting point:

- include candidates whose admitted average is at most about 40 points above the user's estimated score
- include a reasonable number of safer candidates below the user's score
- keep score-line-only candidates in a "data insufficient / pending verification" bucket instead of main recommendation

### Report Candidate Set

The AI should narrow the working pool into a report candidate set before final output:

- 3 to 8 backup candidates per tier
- 1 to 3 final displayed schools per tier
- each final school must include evidence and risk notes

When multiple directions from the same school appear in the report or frontend cards, the primary direction must be deterministic. Sort directions by:

1. judgement closest to `steady`, using priority `steady`, `steady_reach`, `safe`, `small_reach`, `high_risk_reach`, `data_insufficient_pending`
2. higher data completeness, using priority A, B, C
3. smaller absolute admitted-average gap, if available
4. newer data year
5. stable text tie-breaker: college name plus program name

The first item after this sort is the primary direction shown on the school card.

## AI Tools

The AI should not receive every full row in the prompt. It should receive summaries and call tools for details.

### searchPrograms(filters)

Searches either the current candidate pool or the full local database.

Supported filters should include:

- province/city
- school tier
- 985/211/double-first-class flags
- program code or direction
- degree type
- study mode
- average-score range
- score-line range
- data completeness
- minimum plan count
- source/verification state

### getProgramDetail(programId)

Returns one program's structured detail:

- school and college
- program name/code/direction
- exam subjects
- score line
- admitted low/average/high
- plan count
- admitted count
- retest count
- data year
- data completeness
- source URL and source owner
- known warnings

### comparePrograms(programIds)

Returns multiple programs in a consistent comparison structure, suitable for AI explanations and frontend comparison cards.

### expandCandidatePool(filters)

Expands the working candidate pool when the user asks for a new direction, such as:

- "add Zhejiang"
- "only 211"
- "look at safer schools"
- "include professional master's programs"
- "show schools with larger enrollment"

The result should say how many candidates were added and what constraints were used.

Deduplication happens in the tool layer before the expanded pool is saved. The primary dedupe key is `programId`. If a row has no local `programId`, use the fallback key `(schoolName, collegeName, programCode, programName, dataYear)`. The tool response should include:

- `addedCount`
- `duplicateCount`
- `totalPoolCount`
- `appliedFilters`

AI should never be responsible for deduping candidate rows in prompt text.

### verifyOfficialInfo(input)

Uses internet search only for verification or evidence supplementation.

Inputs:

- programId, if the program exists locally
- or schoolName plus programName, if the user asks about a school not already in the local pool

Outputs:

- verification status: official, third_party, local_data_only, verification_failed, or pending
- verification provider, if internet verification was attempted
- source title
- source URL
- short evidence summary
- fields verified, such as score line, plan count, admissions list, or announcement date

Rules:

- Official school or graduate-school pages are preferred.
- Education authority or graduate-admission platforms can be near-official.
- Third-party sources can be shown as clues but not as confirmed evidence.
- A school found online but missing from local data must be labeled pending verification.
- Limit internet verification calls to a configurable quota. Initial defaults: at most 5 calls per report generation and at most 3 calls per chat turn.
- If the quota is exhausted, do not call the provider again; mark unresolved items as `local_data_only` when local data is sufficient or `pending` when local evidence is insufficient.

Provider decision:

- Phase 1 should reserve `verificationProvider` in the report schema even if no internet provider is enabled.
- Phase 1 must choose the provider interface shape before implementation finishes.
- Phase 2 can then plug in a built-in search API, custom crawler, or manually configured provider without changing the report schema.

## Report Output

The report should avoid a single "matchScore" percentage. Use a judgement label plus evidence.

`judgement` and `verificationStatus` are separate fields:

- `judgement` describes AI's admission-risk judgement.
- `verificationStatus` describes evidence/source verification.

Judgement is a backend-controlled enum. AI output must be parsed and mapped to one legal value. If mapping fails, default to `data_insufficient_pending`.

Allowed judgement values:

- `safe`: 保底
- `steady`: 稳妥
- `steady_reach`: 稳妥偏冲
- `small_reach`: 小冲
- `high_risk_reach`: 高风险冲刺
- `data_insufficient_pending`: 数据不足待核验

Allowed verification statuses:

- `official`: verified against an official school, graduate-school, or equivalent authority source
- `third_party`: only third-party evidence is available
- `local_data_only`: local database data is available but no internet verification was attempted
- `verification_failed`: verification was attempted but failed
- `pending`: data or source is not sufficient for a confirmed main recommendation

Example school item:

```json
{
  "level": "steady",
  "label": "稳妥档",
  "judgement": "steady_reach",
  "judgementLabel": "稳妥偏冲",
  "schoolName": "Example University",
  "programName": "Computer Technology",
  "evidence": [
    "录取均分比你的预估分高 3 分",
    "目标地区匹配",
    "近年招生人数 42",
    "数据完整度 B"
  ],
  "risks": [
    "缺少官方核验",
    "复试比例未确认"
  ],
  "verificationStatus": "local_data_only",
  "verificationProvider": null,
  "recommendedAction": "作为稳妥备选继续核验官网招生计划"
}
```

The frontend can still show a visual indicator, but it should be based on judgement and evidence rather than a fake precision percentage.

`recommendedAction` should be generated by backend templates from `judgement` plus `verificationStatus`, not free-form AI text. AI may suggest rationale in `evidence` and `risks`, but the final action wording should be normalized for UX consistency.

Initial template examples:

- `safe + official`: "可作为保底备选，建议复查当年招生计划后加入最终名单"
- `steady + local_data_only`: "可作为稳妥候选，建议优先核验官网招生计划"
- `steady_reach + local_data_only`: "可作为稳妥偏冲候选，建议核验近年复试与录取波动"
- `small_reach + official`: "可作为小冲目标，建议同时准备更稳妥备选"
- `high_risk_reach + any`: "风险较高，仅建议在用户明确愿意冲刺时保留"
- `data_insufficient_pending + any`: "数据不足，先放入待核验池，不作为主推荐"

### Tool-Call Guardrails

The prompt should tell AI to call tools before making concrete claims, but the runtime should also validate the report.

Required guardrails:

- If a final school item includes specific statistics, the tool trace must contain `getProgramDetail(programId)` or an equivalent detail lookup for that program.
- If a final school item is outside the initial working pool, the trace must contain `expandCandidatePool` or `searchPrograms` evidence.
- If AI references official verification, the trace must contain `verifyOfficialInfo`.
- If required traces are missing, mark the affected item as `tool_trace_incomplete` and apply the Phase 1 retry policy.

Tool-call budget:

- Set a per-report tool budget before generation. Initial defaults: at most 20 total tool calls, at most 12 `getProgramDetail` calls, at most 3 `expandCandidatePool` calls, and at most 5 `verifyOfficialInfo` calls.
- Set a per-report tool-result budget. Initial default: no more than about 12,000 tokens of tool results should be injected into one report-generation context.
- If the budget is reached, stop additional exploration, keep already collected evidence, and mark report metadata as `explorationLimited = true`.
- When exploration is limited, AI should avoid making broad claims about uninspected schools and should explain that the report is based on the inspected subset.

This prevents prompt-only compliance from becoming the only safety mechanism.

## Data Traceability

Every final recommended school should be traceable to:

- local programId
- candidate-pool ID or search result ID
- tool calls used by AI
- source URL, if available
- verification status
- verification provider, if any
- missing fields
- tool trace completeness status
- exploration-limited status, if tool-call or token budgets were reached

This trace can be stored in the report JSON for later review and debugging.

## Cache And Invalidation

The working candidate pool and tool traces should be cached, but stale recommendations must be visible and controlled.

Redis keys:

- `ai:agent:pool:{conversationId}` or `ai:agent:pool:{reportId}` for the working pool
- `ai:agent:trace:{conversationId}` or `ai:agent:trace:{reportId}` for tool-call traces
- `ai:agent:profile:{conversationId}` or report snapshot for profile fields used to build the pool

TTL:

- conversation working pools: 30 minutes sliding TTL, matching the current chat-session style
- report-generation pools: 1 hour fixed TTL while the report is pending
- completed report JSON: 7 days or the existing report retention policy

Invalidation triggers:

- user estimated score changes
- target regions change
- exam/professional scope changes
- degree type or study-mode preference changes
- user explicitly clicks "重新推荐"

When profile fields change, the frontend should start a new recommendation instead of silently reusing the old pool. Historical reports can still be opened, but they should show the profile snapshot used at generation time.

Redis fallback:

- If Redis is unavailable before report generation, build the working pool synchronously and store the compact report snapshot in the database.
- If Redis expires during pending generation, return an explicit `pool_expired` error and ask the user to regenerate.
- If Redis expires after report completion, use stored report JSON and do not regenerate silently.

## Error Handling

If AI service is unavailable:

- keep the existing JSON fallback behavior
- show that AI generation failed
- optionally return database-only candidates grouped by score gap

If internet verification fails:

- keep the recommendation if local data is sufficient
- mark verificationStatus as verification_failed if verification was attempted
- keep verificationStatus as local_data_only if no internet verification was attempted
- do not block the whole report

If the candidate pool is too small:

- widen non-essential filters
- explain which filters were relaxed
- avoid pretending the pool is complete

If the user asks for schools outside the local pool:

- search local database first
- then use verification search if enabled
- mark externally discovered schools as pending verification until local data is added or confirmed

If a generated report fails tool-trace validation:

- apply the Phase 1 retry policy, which must be locked before implementation starts
- default policy proposal: retry once with a stricter prompt that names the missing tool calls
- if retry is skipped or still fails, keep only validated items
- mark removed or incomplete items as `tool_trace_incomplete` in debug metadata, not as confirmed recommendations

The retry execution mode is not decided in this spec. It may be synchronous or queued, but Phase 1 must choose one before implementation begins.

## Frontend Changes

The standalone AI page should keep the current quick recommendation and AI chat entry points, but update the explanation:

- "AI uses your profile plus a broad local candidate pool."
- "Internet search is used only for verification."
- "Recommendations include evidence and missing-data warnings."

The report page should replace match percentage with:

- judgement label
- evidence list
- risk list
- data completeness
- verification status
- source links
- exploration-limited or tool-trace-incomplete notices when applicable

Historical report compatibility:

- If a report has the new `judgement` field, render the new evidence/risk layout.
- If a report only has old `matchScore` data, keep a compact legacy card that shows the old match percentage and a banner: "这是旧版 AI 报告，推荐依据字段不完整。"
- Do not automatically regenerate old reports when the user opens history.
- Provide a "重新生成新版报告" action when the current profile is available.

If report validation removed candidates because of `tool_trace_incomplete`, the frontend should show a report-level notice:

"有 N 所学校因数据核验不完整被移出推荐，可重新生成或查看调试详情。"

If `explorationLimited = true`, show:

"本次 AI 数据探索已达到工具调用上限，报告基于已核验的候选生成。"

## Backend Changes

Required backend changes:

- Extend candidate-pool service from 50-candidate summary to 300-500 working pool.
- Add or reshape tools for search, detail, compare, expand, and verification.
- Store working candidate pools and tool traces in Redis/report JSON.
- Update AI prompts so the model calls tools before making concrete recommendations.
- Change report schema from matchScore-centered output to judgement/evidence/risk-centered output.
- Add report validation that checks judgement enum mapping, verification status mapping, and required tool traces.
- Add deterministic same-school direction sorting before frontend rendering or document that the frontend applies the same comparator.
- Add tool-level candidate deduplication for expanded pools.
- Add configurable tool-call, verification-call, and tool-result token budgets.
- Keep local database as the source of truth for main recommendations.

## Testing

Service-level tests:

- candidate pool respects hard filters
- candidate pool expands to the target size when enough data exists
- score-line-only records are not promoted to confirmed main recommendations
- expandCandidatePool adds only eligible records
- verifyOfficialInfo status mapping handles official, third-party, and not-found cases
- expandCandidatePool dedupes by `programId`, with fallback key behavior for rows without local IDs
- tool-call budgets stop additional exploration and set `explorationLimited`
- verification call quota downgrades unresolved items without blocking the report

Prompt/tool tests:

- AI cannot recommend a school outside local data without pending-verification labeling
- AI calls getProgramDetail before discussing specific school statistics
- AI uses expandCandidatePool when user asks for a new region or school tier
- AI does not expose internal program IDs in user-facing text
- report validation rejects or marks items with missing required tool traces
- AI judgement strings map to one of the legal backend enum values
- recommendedAction uses backend templates rather than free-form AI wording

Frontend tests:

- report renders judgement/evidence/risk fields
- missing verification status is visible
- source link is shown when available
- old matchScore-only report data renders the legacy card and old-report banner
- "重新生成新版报告" is available for legacy reports when profile data exists
- report-level notice appears when candidates are removed for `tool_trace_incomplete`
- report-level notice appears when `explorationLimited` is true
- same-school cards choose the deterministic primary direction

## Rollout Plan

Phase 1: Local-database agent

- Build broad candidate pool.
- Add database tools.
- Update AI prompt and report schema.
- Add `judgement`, `verificationStatus`, and `verificationProvider` fields.
- Add enum mapping and tool-trace validation.
- Add deterministic same-school direction sorting and backend action templates.
- Add candidate-pool dedupe and tool-call budget controls.
- Replace matchScore UI with judgement/evidence/risk display.
- Lock the verification provider interface before Phase 1 finishes.
- Lock retry execution mode before Phase 1 implementation begins.

Phase 2: Verification tool

- Add internet verification tool behind a feature flag.
- Store verification status and source evidence.
- Display verification status in reports.
- Plug a concrete provider into the Phase 1 `verificationProvider` interface without changing report schema.

Phase 3: Product refinement

- Tune candidate-pool breadth.
- Add tool-call trace review for debugging poor recommendations.
- Add admin visibility into pending-verification schools.

## Open Decisions

The main product direction is fixed:

- hybrid mode
- local database as primary recommendation source
- internet search only for verification
- 300-500 broad working candidate pool
- AI judgement with evidence instead of pseudo-precise match percentage

Open implementation choices:

- exact configurable default for `initialPoolLimit` and `maxExpandedPoolLimit`
- concrete internet verification provider for Phase 2
- whether report validation retries are synchronous or queued; this must be decided before Phase 1 implementation begins

These choices must not change the report schema fields defined above.
