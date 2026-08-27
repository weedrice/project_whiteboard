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
|   |-- assets/       CSS and static frontend assets
|   |-- components/   Reusable UI components
|   |-- composables/  Shared cross-feature and low-level composables
|   |-- extensions/   TipTap and editor extensions
|   |-- features/     Domain feature logic, feature-local composables, and query helpers
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

### Nested instructions

- Before editing under `src/features/`, read and follow `src/features/AGENTS.md`.
- Before editing under `scripts/`, read and follow `scripts/AGENTS.md`.

## Local Setup

### Prerequisites

- Node.js `>=24.11.0 <25`
- npm

### Run

From `frontend/`:

```bash
npm ci
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
- `VITE_WEB_VITALS_ENDPOINT`
- `VITE_ANALYZE`

Important behavior:

- `vite.config.ts` proxies `/api` and `/oauth2` to `VITE_API_BASE_URL`
- `API.BASE_URL` defaults to `import.meta.env.VITE_API_URL || '/api/v1'`
- `VITE_WEB_VITALS_ENDPOINT` selects the production Web Vitals delivery endpoint
- `VITE_ANALYZE=true` enables the production bundle analysis report
- Do not hardcode environment-specific URLs in components

## Frontend Conventions

### Component and page rules

- Use Vue 3 Composition API with `<script setup lang="ts">`
- Put route pages in `views` and reusable UI in `components`
- Reuse existing base components under `components/common/ui` and widgets under `components/common/widgets`
- Keep route-level orchestration in views and extract domain-specific logic into `src/features/{domain}` when it belongs to a feature slice
- Use `src/composables` for shared cross-feature helpers and low-level reusable behavior

### Data fetching and mutations

- Server state belongs in Vue Query composables, usually under the closest `src/features/{domain}` package for domain-specific flows
- Shared app state belongs in Pinia stores
- Do not create ad hoc `fetch` or isolated Axios clients in feature code; use the shared API layer in `src/api`
- Preserve existing cache invalidation behavior after mutations
- Query errors and mutation errors are globally surfaced through the configured `QueryClient`; do not duplicate the same toast behavior everywhere

### Feature boundary rules

- Keep feature-specific query keys, cache invalidation helpers, form state, page resources, and mutation orchestration in `src/features/{domain}`.
- Import domain logic directly from `src/features/...`; do not recreate compatibility re-export shims under `src/composables`.

### API contract rules

- Backend responses commonly arrive as `ApiResponse`, so frontend code usually reads `response.data.data`
- Keep `src/types` aligned with backend DTOs
- If an endpoint shape changes, update `src/api`, `src/types`, relevant feature composables, and UI consumers together

### Routing and auth rules

- Route meta fields such as `requiresAuth`, `guestOnly`, `roles`, and `layout` are meaningful
- Do not add protected pages without updating route meta intentionally
- Auth and token refresh behavior is centralized in `src/api/index.ts` and `src/stores/auth.ts`
- Avoid duplicating token handling in components

### UI, i18n, and accessibility

- Add user-facing text through locale files when the surrounding feature already uses i18n
- When a request changes visible copy, update the relevant locale value instead of hardcoding display text in components, unless the user explicitly asks for a one-off/local-only label.
- Prefer consistent toast, modal, and form handling through existing common components and stores
- Keep dark-mode compatibility and responsive behavior intact when editing shared components
- Keep button interactions consistent across the module. Prefer `BaseButton` for actions; when a custom `<button>` or link styled as a button is necessary, include a pointer cursor for enabled states, a visible hover state matching nearby buttons such as management buttons, an active/pressed state, and a focus-visible state. For colored buttons, hover should lower brightness rather than increase it. Disabled buttons must keep `cursor: not-allowed` and avoid hover/active motion.

## Testing

Run from `frontend/`:

```bash
npm run lint
npm run type-check
npm run test:run
```

Run coverage only when the task calls for coverage data or the risk level justifies it:

```bash
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
- For component, API, feature, and composable changes, add or update tests close to the affected module in `__tests__`
- When backend API behavior, DTO fields, validation, or error responses change with frontend impact, run `npm run type-check` and the narrowest related Vitest target before broader checks
- If a frontend change is made only to mirror backend normalization, add or update a component/composable test that proves the UI state follows the server rule

## Build And Docker Image

For frontend changes that should be reflected in the local Docker runtime, rebuild the frontend Docker image after verification.

For a standalone production build check, run from `frontend/`:

```bash
npm run build
```

For a refreshed local Docker image, run from the repository root:

```bash
docker compose build frontend
```

Notes:

- The compose service builds `noviis-frontend:local` from `frontend/Dockerfile`.
- The Docker build runs `npm run build` inside the image build stage, so `docker compose build frontend` is enough when the user specifically asks to refresh the frontend image.
- If only running local Vite dev server work, Docker rebuild is not required unless the user asks for the frontend image to be refreshed.

## Security Notes

- Never store new secrets in client code or Vite env files that will be committed
- Only `VITE_*` variables are exposed to the browser; do not place sensitive secrets there
- Access and refresh token handling is already centralized through storage and the shared API layer
- Do not invent new token storage locations or duplicate refresh logic in feature code
- Be careful when changing OAuth callback paths, auth redirects, or API base URLs because they must stay aligned with backend and Nginx behavior
