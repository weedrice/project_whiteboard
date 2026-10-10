# Frontend AGENTS.md

## Scope

This module owns the Vue 3, TypeScript, Vite, Pinia, Vue Router, and TanStack Vue Query application: public pages, admin UI, authentication, editors, notifications, and PWA behavior. Root repository rules remain in force.

- Before editing `src/features/`, read [src/features/AGENTS.md](src/features/AGENTS.md).
- Before editing `scripts/`, read [scripts/AGENTS.md](scripts/AGENTS.md).
- Apply the contracts below across features, views, components, API modules, stores, utilities, and router guards; directory placement does not exempt a caller.

## Sources Of Truth

| Concern | Read before changing it |
| --- | --- |
| Setup, directory map, environment, browser-test variants | [README.md](README.md); [package.json](package.json) owns supported Node versions and commands |
| API client and response handling | [src/api/index.ts](src/api/index.ts), [src/api/response.ts](src/api/response.ts), backend controllers/DTOs |
| Authentication, async actions, private caches | [Authentication session lifecycle](../docs/design-notes/auth-session-lifecycle.md) and its implementation references |
| Generated API types | [docs/api/AGENTS.md](../docs/api/AGENTS.md); do not hand-edit generated types |
| Production assets, SEO, PWA | [scripts/AGENTS.md](scripts/AGENTS.md) and its release references; [static-cache guide](../docs/ops/frontend-static-cache.md) |

## Invariants

### Ownership And API Contracts

- Use Vue Composition API with `<script setup lang="ts">`; put route pages in `src/views` and reusable UI in `src/components`.
- Keep domain query keys, cache invalidation, forms, page resources, and mutation orchestration in `src/features/{domain}`; keep genuinely shared helpers in `src/composables` or `src/utils`.
- Import domain logic from its feature directly. Do not add compatibility re-export shims under `src/composables` or undertake unrelated mass migrations.
- Server state belongs in Vue Query; shared client state belongs in Pinia. Use typed modules through the shared `src/api` client, not ad hoc fetches or isolated feature clients.
- Reuse `src/api/response.ts` or shared query helpers for `ApiResponse` and page envelopes. Keep `src/types`, API methods, feature consumers, and backend DTOs aligned.
- Preserve cache invalidation and error ownership. QueryClient/API handlers surface errors globally unless a flow explicitly owns them; avoid duplicate toasts.

### Authentication And Async Work

- Token storage and refresh belong to `src/stores/auth.ts`, `src/api/apiInterceptors.ts`, `src/api/apiRefreshRetry.ts`, and `src/api/authRefreshCoordinator.ts`.
- Use `useAuthScopedMutation` for authenticated Vue Query writes. For manual multi-step actions, reuse `createAuthSessionScope` / `runAuthScopedAction` or preserve an existing equivalent scope; capture before confirmation, queuing, or any await.
- Use `useAuthScopedOperation` when a manual action also owns local pending state; reset local copies with `useAuthSessionReset`. Keep target IDs, form revisions, and domain cleanup decisions in the feature.
- Forward the initiating `expectedSession` and cancellation signal through every affected API method. Retries must preserve that original identity; request-time token capture alone cannot protect a paused action.
- A wrapper cannot guard side effects after an await inside caller code. Recheck with the supplied assertion or synchronous `runIfCurrent` before subsequent writes, cache updates, navigation, or UI changes.
- Same-session access-token rotation is allowed. Logout, account switch, and a new login invalidate the prior action; `sessionGeneration` and user identity both matter. Login, logout, bootstrap, and deliberate old-session cleanup retain their dedicated lifecycle handling.
- Treat data as session-scoped whenever its response or access depends on the viewer, even on a public route. Use `AUTH_SCOPED_QUERY_META` with shared API query helpers, or explicit session keys with raw queries and prefetches.
- Keep normal queries, router prefetches, optimistic updates, mutation invalidation, and SSE invalidation on the same scoped key contract. Consume query cancellation signals.
- Use the shared `authScopedQuery` option factories for raw or imperative authenticated queries, and forward `requestConfig` from the query context through the API facade. Preserve the initiating identity during retries.
- Query removal alone does not clear local copies. Reset sensitive forms, previews, selections, and pending confirmation intent on the relevant session boundary; keep route/selection revisions when they protect a different race.
- Preserve valid same-session mutation completion after component unmount when the shared mutation wrapper owns it. Dispose operation listeners on settlement and keep resource cleanup separate from stale UI callbacks.
- Preserve route metadata such as `requiresAuth`, `guestOnly`, `roles`, and `layout`; protected routes must retain deliberate authorization guards.

### UI And Security

- Reuse base UI, toast, modal, and form components. Keep responsive behavior, dark mode, and keyboard accessibility intact.
- Put changed user-visible copy in the existing locale system, unless the user explicitly requests a one-off local-only label.
- Prefer `BaseButton`. Custom buttons and action links need a pointer cursor while enabled, visible hover, active/pressed, and focus-visible states. Colored buttons lower brightness on hover; disabled controls use `cursor: not-allowed` with no hover/active motion.
- Never place secrets in client code or browser-exposed `VITE_*` values. Use untracked local Vite env files and do not hardcode environment-specific URLs in components.
- Access tokens remain memory-only through `src/utils/authTokenStorage.ts`; legacy localStorage token keys are removed. Refresh tokens remain backend-issued HttpOnly cookies sent with credentials. Do not introduce another token store.

## Impact Review

Before editing a shared contract, locate its consumers and verification targets:

- API shape, validation, or normalization: backend controller/DTO, typed API module, generated contract, feature and UI consumers.
- Authentication or cache identity: action entry, each await/request, refresh/replay, router prefetch, cache writers, SSE, local form state, and cleanup.
- OAuth callback paths, auth redirects, API base URLs, or cookie behavior: frontend router/client, backend security configuration, and Nginx/deployment configuration.
- Build, PWA, SEO, or script invocation: package scripts, workflows, fixtures, deployment verification, and their maintained documentation.

## Verification

Follow repository verification rules. Commands below run from `frontend/` in Windows PowerShell unless noted.

| Change | Verification |
| --- | --- |
| Component, feature, API, or composable behavior | Nearby Vitest regression (`npm.cmd run test:run -- <test-file>`), `npm.cmd run type-check`, and `npm.cmd run lint:ci`; test meaningful behavior |
| Session, async mutation, or cache boundary | Relevant real Vue Query/Axios tests for account change, delayed responses, ordinary refresh, and cleanup; isolated mocks alone do not prove scheduling or transport behavior |
| Backend API or normalization mirrored by UI | Affected frontend tests and type check; `npm.cmd run api:check` for contract changes; include a UI/composable regression proving server normalization is reflected |
| Build or production asset behavior | Relevant script fixtures, `npm.cmd run build`, and [script verification](scripts/AGENTS.md#validation) |
| Browser interaction, navigation, or integration risk | Relevant Playwright suite from README; choose the suite whose prerequisites are available |

- Put behavior regressions near the changed module in `__tests__`; shared setup is `src/test/setup.ts`.
- Vitest uses jsdom and Vue Test Utils; Playwright suites live in `e2e/` and are excluded from Vitest. `test:e2e` builds before the default browser suite; variant commands are documented in README.
- Run `npm.cmd run coverage` only when requested or justified by risk; output is generated under `coverage/`.
- `api:check` compares generated types with `docs/api/openapi-frontend.json`; use `api:generate` only after an intentional snapshot update following the API instructions.
- `npm.cmd run build` runs Vite and `pwa:verify`; it does not run type checking or tests.

For changes that must appear in the local Docker runtime, rebuild from the repository root:

```powershell
docker compose build frontend
```

The Compose image is `noviis-frontend:local`, built from `frontend/Dockerfile`; its image build already runs the production frontend build. A Vite-only development session or standalone build check does not require an image refresh unless requested.
