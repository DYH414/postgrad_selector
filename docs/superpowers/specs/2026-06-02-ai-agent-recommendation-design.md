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

### verifyOfficialInfo(input)

Uses internet search only for verification or evidence supplementation.

Inputs:

- programId, if the program exists locally
- or schoolName plus programName, if the user asks about a school not already in the local pool

Outputs:

- verification status: official_verified, near_official, third_party_only, not_found
- source title
- source URL
- short evidence summary
- fields verified, such as score line, plan count, admissions list, or announcement date

Rules:

- Official school or graduate-school pages are preferred.
- Education authority or graduate-admission platforms can be near-official.
- Third-party sources can be shown as clues but not as confirmed evidence.
- A school found online but missing from local data must be labeled pending verification.

## Report Output

The report should avoid a single "matchScore" percentage. Use a judgement label plus evidence.

Example school item:

```json
{
  "level": "steady",
  "label": "稳妥档",
  "aiJudgement": "稳妥偏冲",
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
  "recommendedAction": "作为稳妥备选继续核验官网招生计划"
}
```

Suggested judgement labels:

- 保底
- 稳妥
- 稳妥偏冲
- 小冲
- 高风险冲刺
- 数据不足待核验

The frontend can still show a visual indicator, but it should be based on judgement and evidence rather than a fake precision percentage.

## Data Traceability

Every final recommended school should be traceable to:

- local programId
- candidate-pool ID or search result ID
- tool calls used by AI
- source URL, if available
- verification status
- missing fields

This trace can be stored in the report JSON for later review and debugging.

## Error Handling

If AI service is unavailable:

- keep the existing JSON fallback behavior
- show that AI generation failed
- optionally return database-only candidates grouped by score gap

If internet verification fails:

- keep the recommendation if local data is sufficient
- mark verificationStatus as local_data_only or verification_failed
- do not block the whole report

If the candidate pool is too small:

- widen non-essential filters
- explain which filters were relaxed
- avoid pretending the pool is complete

If the user asks for schools outside the local pool:

- search local database first
- then use verification search if enabled
- mark externally discovered schools as pending verification until local data is added or confirmed

## Frontend Changes

The standalone AI page should keep the current quick recommendation and AI chat entry points, but update the explanation:

- "AI uses your profile plus a broad local candidate pool."
- "Internet search is used only for verification."
- "Recommendations include evidence and missing-data warnings."

The report page should replace match percentage with:

- AI judgement label
- evidence list
- risk list
- data completeness
- verification status
- source links

## Backend Changes

Required backend changes:

- Extend candidate-pool service from 50-candidate summary to 300-500 working pool.
- Add or reshape tools for search, detail, compare, expand, and verification.
- Store working candidate pools and tool traces in Redis/report JSON.
- Update AI prompts so the model calls tools before making concrete recommendations.
- Change report schema from matchScore-centered output to judgement/evidence/risk-centered output.
- Keep local database as the source of truth for main recommendations.

## Testing

Service-level tests:

- candidate pool respects hard filters
- candidate pool expands to the target size when enough data exists
- score-line-only records are not promoted to confirmed main recommendations
- expandCandidatePool adds only eligible records
- verifyOfficialInfo status mapping handles official, third-party, and not-found cases

Prompt/tool tests:

- AI cannot recommend a school outside local data without pending-verification labeling
- AI calls getProgramDetail before discussing specific school statistics
- AI uses expandCandidatePool when user asks for a new region or school tier
- AI does not expose internal program IDs in user-facing text

Frontend tests:

- report renders judgement/evidence/risk fields
- missing verification status is visible
- source link is shown when available
- old matchScore-only report data has a graceful fallback if historical reports are opened

## Rollout Plan

Phase 1: Local-database agent

- Build broad candidate pool.
- Add database tools.
- Update AI prompt and report schema.
- Replace matchScore UI with judgement/evidence/risk display.

Phase 2: Verification tool

- Add internet verification tool behind a feature flag.
- Store verification status and source evidence.
- Display verification status in reports.

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

Implementation still needs to choose exact field names for the new report JSON and whether internet verification is initially implemented with a built-in search API, a custom crawler, or a manually configured provider.
