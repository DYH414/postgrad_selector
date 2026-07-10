## Why

Browser-based review of the logged-in admin system found several production-readiness gaps: the default landing page still shows the RuoYi framework welcome page, AI observability links to a 404 RAG route, the admin "user client" menu opens a blank page, and operational pages rely on inconsistent route assumptions. These are not algorithm problems, but they make the product feel unfinished and can block demos, handoff, and day-to-day operations.

## What Changes

- Replace or redirect the post-login admin landing experience so users see a postgrad-oriented dashboard instead of the default RuoYi intro page.
- Fix admin route consistency for AI/RAG observability links and documentation-visible paths.
- Make the admin "用户端" entry useful by opening a real user-facing experience or a clear bridge page instead of an empty route.
- Improve medium-width responsive behavior and empty states for AI/RAG observability pages so browser side panels, in-app browser, and narrower desktop windows remain usable.
- Add lightweight smoke coverage for the key admin UX paths that currently regress silently into 404 or blank screens.

## Capabilities

### New Capabilities
- `admin-production-ux`: Covers production-grade admin navigation, landing page, user-client entry, responsive observability layouts, and operator-friendly empty states.

### Modified Capabilities
- `rag-search-testing`: Ensure the administrator-facing RAG management page is reachable from the actual admin menu path and from AI observability links.
- `rag-trace-observability`: Ensure recent RAG trace observation remains reachable from the production admin route and has useful empty-state behavior.

## Impact

- Frontend routes and menu-driven navigation in `ruoyi-ui`, especially `src/views/index.vue`, `src/views/postgrad/observe/index.vue`, `src/views/postgrad/rag/index.vue`, and any admin user-client bridge route.
- SQL/menu seed assumptions in `sql/postgrad_menu_simplify.sql` if paths or menu labels need alignment.
- Project docs that mention admin page paths, including README and RAG/Qdrant operations docs.
- Test coverage for key browser paths: post-login landing, AI observability to RAG observability, RAG observability route, and user-client entry.
