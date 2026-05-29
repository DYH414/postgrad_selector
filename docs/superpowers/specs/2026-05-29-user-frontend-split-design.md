# User-Facing Frontend Split Design

**Date:** 2026-05-29
**Status:** Approved

## Goal

Split the current single RuoYi frontend (`ruoyi-ui/`) into two independent projects:
- **Project A (Admin):** Keep `ruoyi-ui/` — admin panel on port 8081, used by the administrator only
- **Project B (User):** New `user-ui/` — student-facing app on port 8082, with its own simple login/registration

## Tech Stack (Project B)

| Layer | Choice | Reason |
|---|---|---|
| Framework | Vue 3 (Composition API) | Modern Vue, better TS support, independent of RuoYi |
| UI Library | Element Plus | Smooth migration path from Element UI |
| Build Tool | Vite | Standard for Vue 3 projects |
| State Management | Pinia | Vue 3 official recommendation |
| HTTP | axios (custom instance) | Uses App-Token, not RuoYi's Admin-Token |

## Port Allocation

| Service | Port |
|---|---|
| Spring Boot backend | 8080 |
| Admin `ruoyi-ui` dev server | 8081 |
| User `user-ui` dev server | 8082 |

## Project Structure

```
user-ui/
├── index.html
├── vite.config.js
├── package.json
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/index.js       # Flat routes, no dynamic routing
│   ├── stores/                # Pinia: user, profile
│   ├── api/
│   │   ├── request.js         # axios instance with App-Token interceptor
│   │   ├── auth.js
│   │   ├── profile.js
│   │   ├── recommendation.js
│   │   └── ai.js
│   ├── views/
│   │   ├── Login.vue
│   │   ├── Register.vue
│   │   ├── Home.vue
│   │   ├── Profile.vue         # Student profile form
│   │   ├── Recommend.vue       # Smart school recommendations
│   │   ├── AiReport.vue        # AI recommendation report
│   │   ├── AiHistory.vue       # AI recommendation history
│   │   ├── Results.vue         # Recommendation results
│   │   ├── Favorites.vue       # Saved schools
│   │   └── History.vue         # Recommendation history
│   └── components/             # Shared UI components
```

## Token Convention

- **localStorage key:** `App-Token`
- **Request header:** `Authorization: Bearer <token>`

Backend `AppAuthenticationFilter` extracts the token from the `Authorization` header (stripping `Bearer ` prefix) and looks it up from Redis key `app_login_tokens:<uuid>`.

## Auth Flow

1. Login: POST `/app/auth/login` with `{ account, password }` → backend returns `{ token, userId }`
2. Register: POST `/app/auth/register` with `{ phone?, email?, password }`
3. On success: set `localStorage.setItem('App-Token', token)`
4. Axios request interceptor: if token exists, set header `Authorization: Bearer <token>`
5. Axios response interceptor must handle **both** unauthorized scenarios:
   - HTTP 401 status code
   - HTTP 200 with `body.code === 401` (RuoYi convention: `{ code: 401, msg: "登录状态已过期" }`)
   - In either case: clear token from localStorage, redirect to `/login`
6. On app init (or route enter): call `GET /app/auth/me` to restore user info + profile into Pinia store
7. Logout: POST `/app/auth/logout`, then clear localStorage and Pinia state

## Backend API Surface

Two independent auth layers operate on `/app/*` endpoints:

| Layer | Mechanism | What it does |
|---|---|---|
| `@Anonymous` | Controller annotation | Bypasses RuoYi admin permission checks (`@PreAuthorize`) — necessary because app users don't have admin roles |
| `App-Token` | `AppAuthenticationFilter` + JWT | Validates the student's own login session. The filter runs only for `/app/*` paths, extracts `Authorization: Bearer <token>`, and looks up `app_login_tokens:<uuid>` from Redis |

`@Anonymous` does NOT mean "no auth required." It means "skip the admin permission system." The filter sets `SecurityContext`; controller methods check for a valid `AppLoginUser` — without it they return an error.

**PUBLIC endpoints** (no App-Token required):
- `POST /app/auth/login`
- `POST /app/auth/register`

**AUTHENTICATED endpoints** (App-Token required):
- `GET /app/auth/me`
- `POST /app/auth/logout`
- `GET/PUT /app/profile`
- `/app/recommendation/*`
- `/app/favorite/*`
- `/app/programs/*`
- `/app/ai/*`

Existing controllers — no backend changes needed:
- `AppAuthController` — register, login, logout, me
- `AppProfileController` — get/update profile
- `AppRecommendationController` — recommendations
- `AppFavoriteController` — favorites CRUD
- `AppProgramController` — program listings
- `AppAiRecommendationController` — AI recommendation endpoints

## Routing

Flat route table — no dynamic routes, no backend-driven menu generation:

```
PUBLIC (no App-Token required):
/login         → Login.vue
/register      → Register.vue

AUTHENTICATED (App-Token required):
/               → Home.vue
/profile        → Profile.vue
/recommend      → Recommend.vue
/ai-report/:id  → AiReport.vue
/ai-history     → AiHistory.vue
/results        → Results.vue
/favorites      → Favorites.vue
/history        → History.vue
/history/:id    → HistoryDetail.vue
```

Single route guard: public routes pass through; all others redirect to `/login?redirect=...` when no App-Token is present.

## Vite Config

```js
// vite.config.js
server: {
  port: 8082,
  proxy: {
    '/dev-api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/dev-api/, '')
    }
  }
}
```

Production build: `npm run build` → `dist/`.

## Layout

Mobile-first single-column layout (top nav + content area), responsive via Element Plus.

## Implementation Order

**Phase 0 — Backend Contract Check:** Verify every `/app/*` endpoint works correctly with `Authorization: Bearer <App-Token>` before writing any frontend code.

Check each endpoint using curl or a comparable tool:

```
# 1. Register a test user
curl -X POST http://localhost:8080/app/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000001","password":"test123456"}'

# 2. Login and capture the token
curl -X POST http://localhost:8080/app/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"13800000001","password":"test123456"}'
# → extract TOKEN from response

# 3. Verify /me with token
curl http://localhost:8080/app/auth/me \
  -H "Authorization: Bearer $TOKEN"

# 4. Verify /profile
curl http://localhost:8080/app/profile \
  -H "Authorization: Bearer $TOKEN"

# 5. Verify favorites
curl http://localhost:8080/app/favorite/list \
  -H "Authorization: Bearer $TOKEN"

# 6. Verify programs
curl http://localhost:8080/app/programs/list \
  -H "Authorization: Bearer $TOKEN"

# 7. Verify recommendation (if endpoint requires optional params, just test auth)
curl http://localhost:8080/app/recommendation/options \
  -H "Authorization: Bearer $TOKEN"

# 8. Verify /me WITHOUT token → should return error (not 200 with data)
curl http://localhost:8080/app/auth/me

# 9. Logout
curl -X POST http://localhost:8080/app/auth/logout \
  -H "Authorization: Bearer $TOKEN"

# 10. Verify token is dead after logout
curl http://localhost:8080/app/auth/me \
  -H "Authorization: Bearer $TOKEN"
# → should fail
```

Confirmations from this phase:
- `AppAuthenticationFilter` correctly extracts the token from the header
- authenticated endpoints reject requests with missing/invalid tokens
- logout invalidates the token in Redis
- response format is consistent (JSON, predictable field names)

If any endpoint doesn't behave as expected, fix it before moving on. This phase should take half a day at most.

**Phase 1 — Scaffold:** Create `user-ui/` project with Vite + Vue 3 + Element Plus + Pinia + axios. Get `npm run dev` working on port 8082 with proxy to backend.

**Phase 2 — Auth:** Login, Register, `/app/auth/me` restore, logout, route guard, token expiry handling.

**Phase 3 — Core pages:** Profile, Recommend, Results, Favorites, History — migrated from existing `ruoyi-ui/src/views/postgrad/app/*.vue`.

**Phase 4 — AI pages:** AiReport, AiHistory.

**Phase 5 — Cleanup ruoyi-ui:** Only after user-ui is fully functional and verified:
- Router: all `/app/*` routes from `constantRoutes`
- Views: `views/postgrad/app/` directory
- API: `api/postgrad/appAuth.js`, `appFavorites.js`, `appProfile.js`, `appPrograms.js`, `appRecommendation.js`, `ai.js`
- Utils: `utils/appAuth.js`, `utils/appRequest.js`
- Store: `store/modules/appUser.js`
- Permission guard: `handleAppRoute()` function and `/app/` path checks

Do NOT delete ruoyi-ui app code before user-ui is verified. Keep the fallback.

## Acceptance Checklist

1. **Phase 0:** All 10 contract-check steps pass — endpoints correctly accept/reject App-Token
2. `user-ui` can start independently with `npm install && npm run dev`
3. Login writes `App-Token` to localStorage
4. All authenticated requests carry `Authorization: Bearer <token>` header
5. Accessing `/profile` without token redirects to `/login`
6. Expired token (HTTP 401 or code=401) clears localStorage and redirects to `/login`
7. `GET /app/auth/me` restores user info and profile on app init
8. `ruoyi-ui` admin panel is unaffected
9. `user-ui` (8082) and `ruoyi-ui` (8081) can run simultaneously
10. After cleanup, `ruoyi-ui` admin functions still work correctly
