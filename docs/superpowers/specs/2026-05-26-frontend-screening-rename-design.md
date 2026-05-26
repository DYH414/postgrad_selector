# Frontend Screening Rename Design

## Goal

Change the user-facing product language in the postgraduate app from "推荐" to "筛选" for the current condition-based candidate finder.

This keeps the current feature positioned as a data filtering tool, leaving "AI 推荐" available for a later dedicated module.

## Scope

Rename the frontend product layer only:

- Top navigation labels.
- App route meta titles.
- Recommendation entry page headings, helper text, CTA, login prompt, and privacy text.
- Results page title, empty states, caution text, export filename, and action labels.
- History, detail, favorites, and profile entry copy.
- AI explanation block copy, renamed from "AI 推荐解读" to "AI 分析解读".
- Brand copy changed from "408 考研推荐平台" to "408 考研择校平台" where shown to users.

## Out Of Scope

Do not rename backend APIs, database tables, Java classes, internal request fields, or storage keys in this pass.

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
6. Empty, history, favorite, and export states all use "筛选" wording.

The intended mental model is:

- "筛选" means rule-based candidate narrowing by known fields and score data.
- "AI 推荐" will later mean personalized advice or ranking based on the screened candidate set.

## Text Mapping

- "快速推荐" -> "快速筛选"
- "开始推荐" -> "开始筛选"
- "推荐结果" -> "筛选结果"
- "推荐历史" -> "筛选记录"
- "推荐详情" -> "筛选详情"
- "还没有真实推荐结果" -> "还没有筛选结果"
- "生成推荐需要登录" -> "生成筛选结果需要登录"
- "AI 推荐解读" -> "AI 分析解读"
- "考研择校推荐结果" -> "考研择校筛选结果"
- "408 考研推荐平台" -> "408 考研择校平台"

## Implementation Notes

Prefer text-only frontend changes. Keep current route paths and internal variable names unchanged unless a visible label requires changing.

Use targeted searches for Chinese display text in:

- `ruoyi-ui/src/views/postgrad/app`
- `ruoyi-ui/src/router/index.js`

Avoid changing backend Java, mapper XML, or API names for this rename.

## Verification

After implementation:

- Run `npm run build:prod` in `ruoyi-ui`.
- Search the app frontend for user-facing stale phrases such as "开始推荐", "推荐结果", "推荐历史", and "AI 推荐解读".
- Verify `/app/recommend` in the browser shows "快速筛选" and "开始筛选".
- Verify `/app/results` user-facing title/empty state uses "筛选结果" where reachable.
