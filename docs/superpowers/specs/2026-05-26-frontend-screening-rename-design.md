# Frontend Screening Rename Design

## Goal

Change the most visible user-facing language in the current condition-based candidate finder from "推荐" to "筛选".

This keeps the current feature positioned as a data filtering tool, leaving "AI 推荐" available for a later dedicated module.

## Scope

Rename only the current screening flow's visible copy:

- Recommendation entry page heading, CTA, login prompt, and immediate helper text.
- Results page title, empty state, caution text, and export filename.
- Navigation label that points to the current result list.
- Small page metadata labels where they directly describe the current flow.

## Out Of Scope

Do not perform a broad product migration in this pass.

Keep these areas unchanged unless they are immediately confusing in the current flow:

- Brand name.
- History/detail/favorites/profile page concepts.
- Existing route paths.
- Backend APIs, database tables, Java classes, internal request fields, or storage keys.
- Future AI recommendation module design.

The following can remain internally named `recommendation` for compatibility:

- Backend endpoints and service names.
- `recommendationId`.
- Existing history records.
- Frontend API modules.
- Session storage keys.
- Route paths such as `/app/recommend` and `/app/results`.

## User Experience

The current flow should read as:

1. User opens the app home page.
2. User enters score, exam combo, region, degree type, and screening range.
3. User clicks "开始筛选".
4. If not logged in, the login prompt says screening requires login and will continue after login.
5. User lands on "筛选结果".
6. Empty and export states in the current result page use "筛选" wording.

The intended mental model is:

- "筛选" means rule-based candidate narrowing by known fields and score data.
- "AI 推荐" will later mean personalized advice or ranking based on the screened candidate set.

## Text Mapping

- "快速推荐" -> "快速筛选"
- "开始推荐" -> "开始筛选"
- "推荐结果" -> "筛选结果"
- "还没有真实推荐结果" -> "还没有筛选结果"
- "生成推荐需要登录" -> "生成筛选结果需要登录"
- "考研择校推荐结果" -> "考研择校筛选结果"

## Implementation Notes

Prefer text-only frontend changes. Keep current route paths and internal variable names unchanged unless a visible label requires changing.

Use targeted searches for Chinese display text in:

- `ruoyi-ui/src/views/postgrad/app`
- `ruoyi-ui/src/router/index.js`

Avoid changing backend Java, mapper XML, or API names for this rename.

## Verification

After implementation:

- Run `npm run build:prod` in `ruoyi-ui`.
- Search the current flow for stale phrases such as "快速推荐", "开始推荐", "生成推荐需要登录", and "还没有真实推荐结果".
- Verify `/app/recommend` in the browser shows "快速筛选" and "开始筛选".
- Verify `/app/results` user-facing title/empty state uses "筛选结果" where reachable.
