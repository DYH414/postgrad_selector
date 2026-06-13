# Final Report Web Redesign Design

## Context

The AI recommendation workflow already generates a final report through `/ai-report-v2/:id`.
The current page is functionally correct but visually reads like a basic detail/list page:

- It renders a title, generated time, summary, recommendation basis, and tier candidate list.
- It reuses `DraftCandidateCard`, which is designed for the workbench draft flow rather than a final read-only report.
- The page does not feel like a formal deliverable that a student or parent could read, screenshot, or share.

This design upgrades only the **web report page**. It does not implement PDF export, Excel export, sharing, or backend changes.

## Goal

Turn the final AI school-selection report into a professional, read-only web report that feels like a formal consulting deliverable while preserving the existing report API contract.

## Non-Goals

- Do not implement PDF download.
- Do not implement Excel export.
- Do not implement report sharing.
- Do not change backend report generation.
- Do not change the AI workbench draft-generation flow.
- Do not reuse workbench-only actions such as remove candidate, replace candidate, or ask AI inside the final report.

## Users And Jobs

Primary user:

- A postgraduate exam applicant reviewing the final school-selection recommendation.

Secondary user:

- A parent, advisor, or reviewer who may read the report from a screenshot or shared screen.

Core jobs:

- Understand the final conclusion quickly.
- See the candidate profile basis used for recommendation.
- Review why each school belongs in reach, steady, or safe tier.
- Understand risks, tradeoffs, and recommended action for each school.
- Trust that the report is a final snapshot, not an editable draft list.

## Visual Direction

Use the confirmed **formal web report** direction:

- Keep the existing user-side `AppHeader` for product consistency.
- Use a cool light gray-blue page background.
- Center a white report document surface with restrained shadow and document-like spacing.
- Use deep navy text, `#1769f6` blue accents, emerald success accents, and amber warning accents.
- Use `Noto Sans SC` / existing app font stack.
- Prefer 8px radius, thin borders, and dense but readable layout.
- Avoid mascots, decorative blobs, marketing hero layouts, and playful report visuals.

The page should feel like an official consulting report embedded in the web app, not a dashboard card stack.

## Information Architecture

### Page Shell

The route remains `/ai-report-v2/:id`.

The page shell should contain:

- Existing `AppHeader`.
- A report background area.
- A centered report document.
- A simple top action row with `返回工作台`.

No fake export actions should appear in this phase.

### Report Cover

The top of the report document should show:

- Title: `AI 择校推荐报告`
- Report ID when available.
- Generated time when available.
- Status label, for example `已生成`.
- A concise identity line such as `基于当前画像与候选池生成`.

The cover should make the page immediately feel like a final report.

### Executive Summary

The first content section should show:

- `report.summary`
- Total selected school count.
- Reach, steady, and safe counts.
- Candidate insufficiency notices when any tier is insufficient.

If `summary` is missing, show a fallback such as `报告已生成，请查看下方分档推荐与风险说明。`

### Recommendation Basis

Show all available `profileBasis` fields in a structured matrix:

- estimatedScore: `预计分数`
- targetRegions: `目标地区`
- undergradTier: `本科层次`
- isCrossMajor: `跨考情况`
- riskPreference: `风险偏好`
- schoolTierPreference: `院校层次偏好`
- regionStrategy: `地区策略`
- candidateScope: `候选范围`

Missing values should render as `未填写` or be omitted consistently. The page must not crash when `profileBasis` is null.

### Tier Sections

Render tiers as report chapters:

- `01 冲刺档`
- `02 稳妥档`
- `03 保底档`

Each chapter should include:

- Tier label and candidate count.
- A short strategy note.
- Target count and actual count when available.
- Insufficient notice when `tier.insufficient` is true.
- Read-only candidate cards.

The section should read like a report chapter, not a workbench list.

### Report Candidate Card

Final report candidates need their own card instead of `DraftCandidateCard`.

The card should display:

- School name.
- College name and program name.
- Program code when available.
- School tier and city/province tags.
- Core fact metrics:
  - Average admitted score.
  - Score gap.
  - Plan count or unified exam quota.
  - Data year.
  - Data completeness.
- AI opinion:
  - reason
  - pros
  - risks
  - cons
  - tradeoffs
  - recommendedAction
- Adjustment information:
  - adjusted
  - adjustReason
  - finalJudgement
- Source or data notice when available.

The card must be read-only. It must not emit draft actions or show workbench controls.

### Data Notice

At the end of the report, show a compact data notice:

- This report is generated from the current profile and candidate pool snapshot.
- Recommendation depends on data completeness and available school/program facts.
- If some tier is insufficient, explain that the report preserves this uncertainty instead of pretending the candidate set is complete.

## Component Design

Keep `ReportView.vue` as the route-level container.

Create report-specific components under:

`user-ui/src/views/ai-recommend-v2/components/report/`

### ReportView.vue

Responsibilities:

- Read `reportId` from route params.
- Call `getReport(reportId)`.
- Track loading, error, and empty states.
- Render `ReportDocument` when report data is available.

It should not contain detailed report layout or candidate card markup.

### ReportDocument.vue

Props:

- `report: Object`

Responsibilities:

- Compose the full report document.
- Pass data to cover, summary, profile basis, tier sections, and data notice.
- Compute safe tier arrays and counts.

### ReportCover.vue

Props:

- `reportId: String | Number`
- `createdAt: String`
- `status: String`
- `summary: String`

Responsibilities:

- Render title, metadata, and final-report identity.

### ReportExecutiveSummary.vue

Props:

- `summary: String`
- `tiers: Array`

Responsibilities:

- Render the summary paragraph.
- Compute and show reach, steady, safe, and total counts.
- Surface insufficiency notices.

### ReportProfileBasis.vue

Props:

- `profileBasis: Object`

Responsibilities:

- Render the recommendation basis matrix.
- Apply field labels and fallback display values.

### ReportTierSection.vue

Props:

- `tier: Object`
- `index: Number`

Responsibilities:

- Render tier chapter heading.
- Render strategy note, target/actual counts, and insufficient notice.
- Render `ReportCandidateCard` for each candidate.

### ReportCandidateCard.vue

Props:

- `candidate: Object`
- `tierLevel: String`
- `tierLabel: String`
- `index: Number`

Responsibilities:

- Render one read-only report candidate.
- Safely read `candidate.fact` and `candidate.opinion`.
- Display facts, opinion, risks, and action guidance.

### ReportDataNotice.vue

Props:

- `tiers: Array`
- `profileBasis: Object`

Responsibilities:

- Render final data-scope and uncertainty notes.

## Data Contract

The design uses the current `ReportVO` shape:

```js
{
  reportId,
  summary,
  tiers,
  profileBasis,
  status,
  note,
  createdAt
}
```

Each tier uses:

```js
{
  level,
  label,
  targetCount,
  candidates,
  insufficient,
  insufficientReason
}
```

Each candidate uses:

```js
{
  fact,
  opinion,
  finalJudgement,
  adjusted,
  adjustReason,
  status,
  tags
}
```

All nested fields must be null-safe.

## Empty And Error States

Loading:

- Show a report-shaped skeleton or simple centered loading state.

Error:

- Show `报告加载失败，请返回工作台后重试。`
- Provide a `返回工作台` action.

Empty report:

- Show `报告暂不可用`.
- Do not render empty broken sections.

Missing candidate facts:

- Render card title as `未知院校`.
- Hide unavailable metrics or display `-`.

Missing opinion:

- Show `暂无 AI 观点说明`.

## Responsive Behavior

Desktop:

- Report document width should feel formal and readable, roughly `960px-1100px`.
- Keep generous page margins.

Tablet:

- Reduce side margin.
- Keep report sections single-column where needed.

Mobile:

- Use single-column layout.
- Allow school and program names to wrap.
- Metrics become two-column or one-column.
- No horizontal scrolling.

## Testing Strategy

Add or update lightweight layout checks:

- Route still points to `ReportView.vue`.
- `ReportView.vue` imports and renders `ReportDocument`.
- Report-specific components exist under `components/report`.
- Final report does not import `DraftCandidateCard`.
- Final report code does not include workbench action labels such as `问 AI` or `移出草稿`.
- Candidate card has null-safe access patterns.

Run:

```bash
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.layout.test.mjs
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.streaming.test.mjs
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui'; npm run build"
```

## Acceptance Criteria

- The final report page looks like a formal web report, not a draft list.
- The page is read-only.
- No fake PDF, Excel, or share actions are shown.
- `DraftCandidateCard` is no longer used by the final report page.
- Report sections are visually distinct: cover, summary, basis, tier chapters, data notice.
- Missing report fields do not crash the page.
- Browser at 100% zoom shows clean layout without obvious clipping or overlap.
- Existing AI workbench draft and chat flow remains unchanged.
- Build passes.

## Open Decisions Resolved

- Scope: web report visual upgrade only.
- Export buttons: excluded for this phase.
- Candidate card reuse: do not reuse `DraftCandidateCard`; create report-only read component.
- Backend changes: excluded.
