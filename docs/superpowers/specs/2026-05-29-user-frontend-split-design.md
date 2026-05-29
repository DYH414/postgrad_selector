# User-Facing Frontend Split Design

**Date:** 2026-05-29
**Status:** Approved

## Goal

Split the current single RuoYi frontend (`ruoyi-ui/`) into two independent projects:
- **Project A (Admin):** Keep `ruoyi-ui/` — admin panel on port 8081, used by the administrator only
- **Project B (User):** New `user-ui/` — student-facing app on port 8080, with its own simple login/registration

## Tech Stack (Project B)

| Layer | Choice | Reason |
|---|---|---|
| Framework | Vue 3 (Composition API) | Modern Vue, better TS support, independent of RuoYi |
| UI Library | Element Plus | Smooth migration path from Element UI |
| Build Tool | Vite | Standard for Vue 3 projects |
| State Management | Pinia | Vue 3 official recommendation |
| HTTP | axios (custom instance) | Uses App-Token, not RuoYi's Admin-Token |

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

## Auth Flow

1. Login/Register: POST `/app/auth/login` or `/app/auth/register` with phone/email + password
2. Backend returns `token` + `userId`, stored as `App-Token` in localStorage
3. Axios request interceptor injects `Authorization: Bearer <token>` on every request
4. Response interceptor: 401 → clear token → redirect to `/login`
5. Route guard: redirect to `/login` if no token (except `/login`, `/register`)
6. On app load: `GET /app/auth/me` fetches user info + profile into Pinia store

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

## Backend — No Changes

The backend already exposes clean user-facing APIs under `/app/*`. Two independent auth layers operate on these endpoints:

| Layer | Mechanism | What it does |
|---|---|---|
| `@Anonymous` | Controller annotation | Bypasses RuoYi admin permission checks (Spring Security `@PreAuthorize`) — necessary because app users don't have admin roles |
| `App-Token` | `AppAuthenticationFilter` + JWT | Validates the student's own login session. The filter runs only for `/app/*` paths, extracts `Authorization: Bearer <token>`, and looks up `app_login_tokens:<uuid>` from Redis |

`@Anonymous` does NOT mean "no auth required." It means "skip the admin permission system." Every `/app/*` endpoint that needs a logged-in user (e.g., `/app/profile`, `/app/favorite`, `/app/recommendation`, `/app/ai-history`) must still verify the App-Token. The filter sets `SecurityContext`, and controller methods check for a valid `AppLoginUser` — without it they return an error or empty result.

Unauthenticated (no App-Token required): `/app/auth/login`, `/app/auth/register`
Authenticated (App-Token required): `/app/auth/me`, `/app/profile`, `/app/favorite/*`, `/app/recommendation/*`, `/app/ai-*`, `/app/programs/*`

Existing controllers:
- `AppAuthController` — register, login, logout, me
- `AppProfileController` — get/update profile
- `AppRecommendationController` — recommendations
- `AppFavoriteController` — favorites CRUD
- `AppProgramController` — program listings
- `AppAiRecommendationController` — AI recommendation endpoints

No backend changes needed.

## Build & Deployment

- Dev server: port 8082, proxy `/dev-api` → `http://localhost:8080`
- Production: deploy to port 80/443 (yourdomain.com)
- Production build: `npm run build` → `dist/`

## Layout

Mobile-first single-column layout (top nav + content area), responsive via Element Plus.

## What Gets Removed from ruoyi-ui

After the split, the following should be cleaned from `ruoyi-ui/`:
- Router: all `/app/*` routes from `constantRoutes`
- Views: `views/postgrad/app/` directory (all 9+ Vue files)
- API: `api/postgrad/appAuth.js`, `api/postgrad/appFavorites.js`, `api/postgrad/appProfile.js`, `api/postgrad/appPrograms.js`, `api/postgrad/appRecommendation.js`, `api/postgrad/ai.js`
- Utils: `utils/appAuth.js`, `utils/appRequest.js`
- Store: `store/modules/appUser.js`
- Permission guard: `handleAppRoute()` function and `/app/` path checks
