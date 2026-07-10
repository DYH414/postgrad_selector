## Context

The admin system now contains postgrad-specific data operations, AI observability, and RAG observability, but the browser review exposed several productization gaps:

- `/index` still renders the default RuoYi welcome page after login.
- The AI observability page has a button that navigates to `/postgrad/rag`, which becomes a 404. The working menu path is `/postgrad/ops/rag`.
- The admin "用户端" menu opens `/app/recommend`, but the admin shell renders an empty page there. The real user-facing experience lives in `user-ui`.
- AI/RAG observability pages work on wide desktop, but medium-width views such as the in-app browser can collapse KPI cards into a long one-column page.
- Empty observability states show zeros or sparse placeholders, but do not guide an operator to generate a first AI/RAG event.

This change is primarily frontend and documentation alignment. It should not alter AI ranking logic, RAG retrieval behavior, or Qdrant indexing semantics.

## Goals / Non-Goals

**Goals:**
- Make the first logged-in admin screen useful for postgrad operations.
- Make admin navigation paths consistent with the real menu paths.
- Make AI/RAG observability pages reachable from each other without 404s.
- Replace the blank admin "用户端" route with a clear and useful experience.
- Improve medium-width layout behavior and empty-state guidance.
- Add smoke checks that catch 404 and blank-page regressions.

**Non-Goals:**
- Rebuild the visual design system or migrate away from Element UI.
- Change the RAG search-test API, trace recorder model, or AI observability metrics aggregation.
- Merge `user-ui` into `ruoyi-ui`.
- Implement long-term RAG trace persistence or production APM integration.

## Decisions

### Decision 1: Treat `postgrad/ops/*` as the production admin observability route family

The current menu data exposes:

- `/postgrad/ops/rag`
- `/postgrad/ops/ai-observe`

The implementation should align internal buttons and documentation to those routes instead of inventing parallel `/postgrad/rag` or `/postgrad/observe` paths.

Alternative considered: add alias routes for `/postgrad/rag` and `/postgrad/observe`. Aliases can be useful for backward compatibility, but relying on aliases would hide the mismatch between menu seed data, breadcrumbs, and docs. The primary fix should align the source paths; aliases can be added only as a convenience if the router pattern supports them cleanly.

### Decision 2: Replace the default RuoYi index with a postgrad operations dashboard

The default RuoYi index page is appropriate for a scaffold, not a production project. The admin landing page should become a concise postgrad dashboard with shortcuts and health cards:

- School data workspace
- Data health / review backlog
- RAG health
- AI observability
- User client entry

Alternative considered: redirect `/index` to `/postgrad/workspace`. This is simpler and acceptable as a first implementation, but a small dashboard gives better first-run orientation and can expose AI/RAG health without forcing navigation.

### Decision 3: Make the admin user-client entry explicit

Because `user-ui` is a separate app, `ruoyi-ui` should not pretend `/app/recommend` is a native admin page unless an actual wrapper exists. The admin route should either:

- open a configured user-ui URL in a new tab; or
- render a bridge page explaining the user client and offering buttons to open the recommendation flow.

Use the bridge page if deployment URL configuration is not yet centralized. It avoids a blank page and makes local development clear.

### Decision 4: Use progressive responsive grids for observability KPIs

The current wide desktop layout is acceptable, but medium-width views should not collapse directly to one column. Use breakpoint steps:

- wide desktop: 6 columns
- medium desktop / in-app browser: 3 or 2 columns
- mobile: 1 column

The same principle applies to RAG KPI cards and the AI observe cards.

### Decision 5: Empty states should describe the next action

For production observability, "0" is not enough. Empty states should explain whether the system has no data yet, lacks permissions, or failed to load. Examples:

- "暂无 AI 调用，先在用户端生成一次 AI 推荐或发起对话。"
- "暂无 RAG Trace，先执行一次检索测试或 AI 对话。"
- "RAG 已索引，但尚无检索记录。"

## Risks / Trade-offs

- [Risk] Changing `/index` may surprise users accustomed to the RuoYi intro page. → Mitigation: keep framework/admin links available through system menus, but make the product dashboard the default.
- [Risk] User-client URLs differ between local development and deployment. → Mitigation: use a bridge page first, and read configured URL only if a reliable configuration source exists.
- [Risk] Route fixes can diverge from database menu seed data. → Mitigation: update code, SQL seed, and docs together; add smoke tests for the real visible menu paths.
- [Risk] Responsive CSS changes can affect existing wide desktop layout. → Mitigation: verify at wide desktop and medium viewport sizes.
