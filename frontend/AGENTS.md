# Frontend AGENTS.md

## Module Overview

This directory contains the NoviIs frontend built with Vue 3, TypeScript, Vite, Vue Router, Pinia, and TanStack Vue Query. From an AI agent perspective, this module owns:

- Public community UI
- Auth UI including OAuth callback handling
- User settings, notifications, messages, and profile pages
- Admin pages
- Client-side API integration, token refresh behavior, routing, i18n, and SEO build scripts

This is not a static frontend. It is tightly coupled to backend response envelopes, auth flows, and cache invalidation behavior.

## Directory Guide

```text
frontend/
|-- src/
|   |-- api/          Axios clients and request/response handling
|   |-- components/   Reusable UI components
|   |-- composables/  Vue Query and Composition API logic
|   |-- locales/      Translation resources
|   |-- router/       Route definitions and route guards
|   |-- stores/       Pinia stores
|   |-- test/         Shared test setup
|   |-- types/        Shared TypeScript types
|   |-- utils/        Constants, env helpers, logger, sanitizers, storage, date helpers
|   `-- views/        Route-level pages
|-- public/           Static assets
|-- scripts/          SEO and prerender scripts
|-- vite.config.ts    Vite config and dev proxy
|-- vitest.config.ts  Test and coverage config
`-- package.json      npm scripts and dependencies
```

### Where AI agents usually work

- `src/api`: endpoint definitions, token refresh handling, request/response typing
- `src/composables`: query and mutation orchestration
- `src/views`: route-level feature work
- `src/components`: reusable UI work
- `src/router`: route metadata, auth guards, navigation rules
- `src/stores`: auth, theme, toast, and other shared state
- `src/locales`: user-facing text
- `src/types`: backend contract alignment

## Local Setup

### Prerequisites

- Node.js `^20.19.0` or `>=22.12.0`
- npm

### Run

From `frontend/`:

```bash
npm install
npm run dev
```

Default local server:

```text
http://localhost:5173
```

### Environment variables

Use untracked Vite env files such as:

- `frontend/.env.local`
- `frontend/.env.development.local`

Client-exposed variables currently referenced in the app:

- `VITE_API_BASE_URL`
- `VITE_API_URL`
- `VITE_INQUIRY_BOARD_URL`
- `VITE_COMMIT_HASH`

Important behavior:

- `vite.config.ts` proxies `/api` and `/oauth2` to `VITE_API_BASE_URL`
- `API.BASE_URL` defaults to `import.meta.env.VITE_API_URL || '/api/v1'`
- Do not hardcode environment-specific URLs in components

## Frontend Conventions

### Component and page rules

- Use Vue 3 Composition API with `<script setup lang="ts">`
- Put route pages in `views` and reusable UI in `components`
- Reuse existing base components under `components/common/ui` and widgets under `components/common/widgets`
- Keep route-level orchestration in views and extract reusable logic into composables when it crosses pages

### Data fetching and mutations

- Server state belongs in Vue Query composables
- Shared app state belongs in Pinia stores
- Do not create ad hoc `fetch` or isolated Axios clients in feature code; use the shared API layer in `src/api`
- Preserve existing cache invalidation behavior after mutations
- Query errors and mutation errors are globally surfaced through the configured `QueryClient`; do not duplicate the same toast behavior everywhere

### API contract rules

- Backend responses commonly arrive as `ApiResponse`, so frontend code usually reads `response.data.data`
- Keep `src/types` aligned with backend DTOs
- If an endpoint shape changes, update `src/api`, `src/types`, relevant composables, and UI consumers together

### Routing and auth rules

- Route meta fields such as `requiresAuth`, `guestOnly`, `roles`, and `layout` are meaningful
- Do not add protected pages without updating route meta intentionally
- Auth and token refresh behavior is centralized in `src/api/index.ts` and `src/stores/auth.ts`
- Avoid duplicating token handling in components

### UI, i18n, and accessibility

- Add user-facing text through locale files when the surrounding feature already uses i18n
- Prefer consistent toast, modal, and form handling through existing common components and stores
- Keep dark-mode compatibility and responsive behavior intact when editing shared components

## Testing

Run from `frontend/`:

```bash
npm run lint
npm run type-check
npm run test:run
npm run coverage
```

Useful targeted commands:

```bash
npm run test:run -- PostWrite.spec.ts
npm run test:run -- useUser.spec.ts
npm run test:run -- authApi.spec.ts
```

Test notes:

- Tests use Vitest with `jsdom` and `@vue/test-utils`
- Shared test setup lives in `src/test/setup.ts`
- Coverage output is written to `coverage/`
- For component, API, and composable changes, add or update tests close to the affected module in `__tests__`
- When backend API behavior, DTO fields, validation, or error responses change with frontend impact, run `npm run type-check` and the narrowest related Vitest target before broader checks
- If a frontend change is made only to mirror backend normalization, add or update a component/composable test that proves the UI state follows the server rule

## Commit Guidance

Use the repository-wide commit style:

```text
Type: short summary
```

Examples for this module:

- `Feat: add agent management section to my page`
- `Fix: refresh board post cache after edit`
- `Refactor: centralize auth error normalization`

## Security Notes

- Never store new secrets in client code or Vite env files that will be committed
- Only `VITE_*` variables are exposed to the browser; do not place sensitive secrets there
- Access and refresh token handling is already centralized through storage and the shared API layer
- Do not invent new token storage locations or duplicate refresh logic in feature code
- Be careful when changing OAuth callback paths, auth redirects, or API base URLs because they must stay aligned with backend and Nginx behavior

## Common AI Agent Mistakes In This Module

### 1. Bypassing the shared API client

If you skip `src/api/index.ts`, you will likely break auth refresh handling, error normalization, or global toasts.

### 2. Forgetting Vue Query cache invalidation

Mutations often require invalidating `post`, `posts`, `board`, `user`, or notification-related queries. Missing this causes stale UI.

### 3. Mixing server state and UI state

Do not move backend data into Pinia without a strong reason. Prefer Vue Query for network-backed state.

### 4. Hardcoding strings instead of using i18n

Many pages already use translated strings. Follow the existing pattern instead of adding scattered literals.

### 5. Breaking route protection unintentionally

Changes to `router/index.ts` can affect auth-only pages, guest-only pages, admin pages, and OAuth callback flows.

### 6. Forgetting backend envelope assumptions

This frontend expects the backend `ApiResponse` wrapper and frequently reads nested `data.data`. If backend integration changes, update types and consumers carefully.

### 7. Ignoring shared base components

Before creating a new input, button, modal, table, or spinner, check whether a common component already exists.

### 8. Storing sensitive data in browser-visible places

Do not expose secrets through `VITE_*`, component constants, test fixtures, or debug logging.
