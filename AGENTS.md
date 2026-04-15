# AGENTS.md

## Project Overview

NoviIs (`noviis.kr`) is an open-source community platform inspired by Reddit and ArcaLive-style free boards. From an AI agent perspective, this repository is a monorepo for a production-facing web service that combines:

- A Spring Boot API for authentication, boards, posts, comments, notifications, moderation, file handling, and agent-facing endpoints
- A Vue 3 frontend for the public community UI and admin UI
- AWS-oriented deployment assumptions: single EC2 instance, RDS PostgreSQL, S3, and Nginx
- AI agent integration through backend `agent` APIs, with MCP server integration expected to exist as an external Python service using streamable HTTP transport

Treat this repository as production-adjacent. Minimize change scope, preserve existing behavior, and avoid speculative refactors.

## Repository Structure

```text
noviis/
|-- backend/        Spring Boot 3.x API server
|-- frontend/       Vue 3 + TypeScript client
|-- docs/           Notes, audits, and project documentation
|-- logs/           Runtime logs; do not commit generated files
|-- uploads/        Local upload storage for development; do not commit generated files
`-- AGENTS.md       This file
```

### Where AI agents usually work

- `backend/src/main/java/com/weedrice/whiteboard/domain/*`: business domains such as `auth`, `board`, `post`, `comment`, `notification`, `user`, and `agent`
- `backend/src/main/java/com/weedrice/whiteboard/global/*`: shared config, security, validation, exceptions, logging, and common response wrappers
- `backend/src/main/resources/`: Spring profiles and logging configuration
- `backend/src/test/java/`: JUnit and Spring tests
- `frontend/src/views/`: route-level pages
- `frontend/src/components/`: reusable UI components
- `frontend/src/composables/`: Vue Query and Composition API logic
- `frontend/src/api/`: HTTP API clients
- `frontend/src/types/`: shared TypeScript types
- `frontend/src/utils/`: constants, env helpers, formatting, sanitizing, and client-side utilities

### Important architecture notes

- The backend package root is `com.weedrice.whiteboard`, even though the product name is NoviIs.
- The backend follows a domain-oriented structure. Keep new code in the closest existing domain instead of creating broad utility dumping grounds.
- The frontend separates server state and UI state. Server data usually belongs in Vue Query composables, while local UI state belongs in component state or Pinia stores.
- There is no dedicated top-level `mcp-server/` directory in this repository at the moment. If MCP-related work is requested, first verify whether it belongs in backend agent APIs, external infrastructure, or a new module.

## Development Environment Setup

### Prerequisites

- Java 21
- Node.js `^20.19.0` or `>=22.12.0`
- npm
- PostgreSQL for local backend development

### Backend setup

Run from `backend/`:

```bash
# Windows
.\gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

Default local backend URL:

```text
http://localhost:8080
```

Backend profile behavior:

- `src/main/resources/application.yml` defaults `SPRING_PROFILES_ACTIVE` to `dev`
- `application-dev.yml` is used for local development
- `application-prod.yml` is used for production

Preferred backend environment variable setup:

- Use shell environment variables, IDE run configuration variables, or deployment-level secret injection
- Do not add new real secrets to tracked YAML files
- If local overrides are needed, prefer environment variables over creating new tracked profile files

Backend environment variables used in production:

- `SPRING_PROFILES_ACTIVE`
- `FRONTEND_URL`
- `DB_HOST`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `DB_MAX_POOL_SIZE`
- `DB_MIN_IDLE`
- `JWT_SECRET`
- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `DISCORD_CLIENT_ID`
- `DISCORD_CLIENT_SECRET`
- `MAIL_USERNAME`
- `MAIL_APP_PASSWORD`
- `AWS_ACCESS_KEY`
- `AWS_SECRET_KEY`
- `AWS_S3_REGION`
- `S3_BUCKET`
- `RATE_LIMIT_TRUST_PROXY_HEADERS`
- `RATE_LIMIT_BUCKET_CACHE_MAX_SIZE`
- `RATE_LIMIT_BUCKET_CACHE_TTL_MINUTES`

### Frontend setup

Run from `frontend/`:

```bash
npm install
npm run dev
```

Default local frontend URL:

```text
http://localhost:5173
```

Frontend environment variable setup:

- Vite is used, so local variables should go in untracked files such as `frontend/.env.local` or `frontend/.env.development.local`
- Only variables prefixed with `VITE_` are exposed to client code

Frontend variables currently referenced by the codebase:

- `VITE_API_BASE_URL`
- `VITE_API_URL`
- `VITE_INQUIRY_BOARD_URL`
- `VITE_COMMIT_HASH`

SEO and build scripts also read optional non-`VITE_` environment variables:

- `SITEMAP_SITE_URL`
- `SITEMAP_API_BASE_URL`
- `SITEMAP_PAGE_SIZE`
- `SITEMAP_MAX_PAGES_PER_BOARD`
- `SITEMAP_REQUEST_TIMEOUT_MS`
- `PRERENDER_SITE_URL`
- `PRERENDER_API_BASE_URL`
- `PRERENDER_MAX_URLS`
- `PRERENDER_REQUEST_TIMEOUT_MS`
- `SEO_SITE_URL`
- `SEO_SITEMAP_URL`
- `SEO_VERIFY_TIMEOUT_MS`
- `SEO_VERIFY_MAX_URLS`
- `SEO_GOOGLEBOT_UA`
- `SEO_SUBMIT_TIMEOUT_MS`
- `GOOGLE_SEARCH_CONSOLE_SITE_URL`
- `GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_ID`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET`
- `GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN`
- `CUSTOM_SITEMAP_SUBMIT_URL`
- `CUSTOM_SITEMAP_METHOD`

### Local full-stack workflow

1. Start PostgreSQL
2. Start backend on `:8080`
3. Start frontend on `:5173`
4. Let Vite proxy `/api` and `/oauth2` requests through `VITE_API_BASE_URL`

## Coding Conventions

### General rules

- Follow the existing code style in the file you are editing
- Keep changes small and local to the requested scope
- Do not introduce new libraries or frameworks without explicit approval
- Do not rename or move files unless the task requires it
- Preserve existing API contracts unless the task explicitly includes coordinated backend and frontend changes

### Backend conventions

- Keep controllers thin and delegate business logic to services
- Keep services transactional. The codebase commonly uses `@Transactional(readOnly = true)` at class level and overrides mutating methods with `@Transactional`
- Preserve the domain-oriented package layout under `domain/*` and shared concerns under `global/*`
- Use DTOs for request and response payloads. Do not expose JPA entities directly from controllers
- Preserve the existing response wrappers such as `ApiResponse` and `PageResponse`
- Reuse the existing exception pattern with `BusinessException` and `ErrorCode`
- Prefer constructor injection via Lombok `@RequiredArgsConstructor`
- Match existing Lombok usage for entities and DTOs such as `@Getter`, `@Builder`, and protected no-args constructors
- Be careful with JPA fetch behavior. Reuse existing repository, Querydsl, `@EntityGraph`, and pagination patterns to avoid N+1 regressions
- Keep security-sensitive logic in existing security/config layers rather than duplicating auth checks inside controllers

### Frontend conventions

- Use Vue 3 Composition API with `<script setup lang="ts">`
- Put route pages in `views`, reusable UI in `components`, HTTP calls in `api`, types in `types`, and data-fetching logic in `composables`
- Use Vue Query for server state and cache invalidation
- Use Pinia only for app-level state such as auth, toasts, and shared UI state
- Preserve the existing `@/` import alias
- Prefer explicit TypeScript types over `any`, even though the current ESLint config is permissive
- Reuse existing base UI components before creating new one-off controls
- When changing mutation flows, update related query invalidation so the UI stays consistent
- Match the surrounding file's indentation and formatting. Vue SFCs in this repository commonly use two-space indentation; backend Java uses four spaces

## Testing

### Backend tests

Run from `backend/`:

```bash
./gradlew test
./gradlew jacocoTestReport
```

Useful targeted command:

```bash
./gradlew test --tests "*PostServiceTest"
```

Backend test notes:

- Tests use JUnit 5, Mockito, Spring Boot Test, Spring Security Test, and H2 in PostgreSQL mode
- Coverage reports are generated under `backend/build/reports/jacoco/html`
- `test` is configured with `ignoreFailures = true`, so do not assume a green build purely because Gradle continues. Read the actual test summary and coverage output

### Frontend tests

Run from `frontend/`:

```bash
npm run lint
npm run type-check
npm run test:run
npm run coverage
```

Useful targeted command:

```bash
npm run test:run -- PostWrite.spec.ts
```

Frontend test notes:

- Tests use Vitest with `jsdom` and `@vue/test-utils`
- Coverage output is written under `frontend/coverage/`
- For API or composable changes, update or add tests near the affected module under `src/**/__tests__`

## Commit Message Rules

Follow the repository's existing prefix style:

```text
Type: short summary
```

Common prefixes seen in history:

- `Feat:`
- `Fix:`
- `Refactor:`
- `Docs:`
- `Test:`
- `Chore:`

Examples:

- `Feat: add board-level agent posting controls`
- `Fix: preserve notification settings during bulk update`
- `Refactor: simplify post query cache invalidation`

Keep commits focused. Do not combine unrelated backend, frontend, and infrastructure changes in one commit unless they are part of the same feature.

## Security Notes

### Secret handling

- Never hardcode new secrets, tokens, API keys, passwords, JWT secrets, OAuth secrets, or AWS credentials in source code
- Never copy credentials from existing files into new files, docs, tests, or comments
- If you discover real credentials in tracked files, treat them as sensitive, do not repeat them, and recommend migration to environment variables
- Prefer environment variables or deployment secret managers over tracked config

### Files that must never be committed

- `.env`
- `.env.*`
- `*.local`
- runtime logs and generated reports
- uploaded user files under `uploads/`
- build artifacts such as `backend/build/`, `backend/.gradle/`, `frontend/dist/`, `frontend/coverage/`
- local editor directories such as `.idea/` and `.vscode/`

### Operational caution

- This project assumes EC2, RDS PostgreSQL, S3, and Nginx. Treat changes to storage paths, proxy behavior, auth redirects, file upload logic, and host URLs as production-sensitive
- Do not change OAuth callback URLs, JWT behavior, S3 bucket semantics, or Nginx-facing paths without checking the full backend, frontend, and deployment impact

## Common AI Agent Mistakes In This Project

### 1. Breaking the shared API envelope

Backend responses are wrapped in `ApiResponse` and paginated responses use `PageResponse`. Do not return raw entities or ad hoc JSON shapes unless the whole stack is being updated together.

### 2. Forgetting coordinated backend and frontend changes

Many features cross both modules. If you rename a field, URL, enum, or validation rule on the backend, verify the matching frontend API client, types, composables, and views.

### 3. Missing Vue Query invalidation after mutations

The frontend relies heavily on cache invalidation. After create, update, delete, like, notification, or profile changes, verify that affected queries are refreshed.

### 4. Ignoring agent-specific rules

This repository has dedicated `domain/agent` behavior and agent ownership flows. Do not treat AI agent actions as normal user actions without checking authorization, board access, and ownership rules.

### 5. Bypassing domain rules around boards, secret content, and moderation

Posts, comments, notifications, reports, sanctions, and admin actions have business rules already encoded in services. Reuse existing service flows instead of reimplementing logic in controllers or UI code.

### 6. Introducing security regressions in auth work

Auth touches JWT, OAuth2 providers, email verification, password reset, and agent auth. Any auth change must be reviewed for redirect URLs, secret handling, token expiry, and filter/config consistency.

### 7. Treating H2 tests as perfect PostgreSQL proof

Backend tests run on H2 in PostgreSQL mode. That catches many issues, but not every PostgreSQL-specific behavior. Be cautious with native SQL, indexing assumptions, and dialect-specific queries.

### 8. Adding new dependencies without approval

This project explicitly prefers minimal change scope. Do not add npm or Gradle dependencies unless the user has approved them.

### 9. Editing tracked config files as a shortcut for local secrets

Do not solve local setup by putting credentials into tracked `application-*.yml`, docs, or frontend env examples. Use untracked local env files or shell variables instead.

### 10. Skipping tests on non-trivial changes

For anything beyond a trivial text change, run the narrowest relevant tests first, then the broader module-level checks if needed. If you cannot run tests, say so explicitly in your final report.
