## 1. Route Alignment and Navigation Fixes

- [ ] 1.1 Identify the canonical admin routes from menu seed data and dynamic router output for RAG observability, AI observability, workspace, and user-client entry.
- [ ] 1.2 Fix the AI observability "查看 RAG 观测台" button to navigate to the working RAG observability route.
- [ ] 1.3 Update any hard-coded frontend links that still point to `/postgrad/rag` or `/postgrad/observe`.
- [ ] 1.4 Align README and operations docs with the production admin routes.

## 2. Product-Oriented Admin Landing

- [ ] 2.1 Replace the default RuoYi landing content in `ruoyi-ui/src/views/index.vue` with a postgrad operations dashboard or redirect strategy.
- [ ] 2.2 Include quick actions for school data workspace, data health, RAG observability, AI observability, and user-client entry.
- [ ] 2.3 Display useful high-level status cards without requiring the user to open nested menus first.
- [ ] 2.4 Remove or de-emphasize framework marketing content from the first screen.

## 3. User-Client Entry

- [ ] 3.1 Determine whether the admin user-client menu should open a configured user-ui URL or render a bridge page.
- [ ] 3.2 Implement the selected user-client bridge/entry so `/app/recommend` no longer renders a blank admin page.
- [ ] 3.3 Provide clear local-development guidance when the user-ui target is not configured.
- [ ] 3.4 Keep the entry compatible with deployed environments where `user-ui` may be hosted separately.

## 4. Observability Page UX

- [ ] 4.1 Improve AI observability KPI responsive breakpoints so medium-width desktop views use 2-3 columns before collapsing to 1 column.
- [ ] 4.2 Improve RAG observability KPI/status responsive breakpoints so medium-width desktop views remain scannable.
- [ ] 4.3 Add actionable empty states for AI observability when no calls have been recorded.
- [ ] 4.4 Add actionable empty states for RAG traces when no search-test or AI-chat traces exist.
- [ ] 4.5 Verify wide desktop layout remains unchanged or improved after responsive CSS changes.

## 5. Verification

- [ ] 5.1 Add or update frontend smoke tests for `/index`, `/postgrad/ops/ai-observe`, `/postgrad/ops/rag`, and `/app/recommend`.
- [ ] 5.2 Add a regression check that clicking from AI observability to RAG observability does not land on `/404`.
- [ ] 5.3 Run `npm run build:prod` in `ruoyi-ui`.
- [ ] 5.4 Run relevant frontend tests or static checks available in `ruoyi-ui`.
- [ ] 5.5 Manually verify the four critical paths in browser: landing page, AI observability, RAG observability, and user-client entry.

## 6. Documentation and Handoff

- [ ] 6.1 Update README route references and feature descriptions for the production admin UX.
- [ ] 6.2 Update `docs/RAG-Qdrant-运维说明.md` with the real RAG observability route and how to generate traces.
- [ ] 6.3 Update `docs/项目技术文档.md` and AI recommendation documentation if route or dashboard behavior changes.
- [ ] 6.4 Document remaining known limitations, if any, such as separate `user-ui` deployment URL configuration.
