# Backend AGENTS.md

## Module Overview

This directory contains the NoviIs backend API server built with Java 25, Spring Boot 4.1, Gradle 9.6.1, JPA, and PostgreSQL. From an AI agent perspective, this module owns:

- Authentication and authorization
- Boards, posts, comments, tags, reports, sanctions, notifications, and admin APIs
- File upload metadata and S3 integration
- OAuth2 login flows for GitHub, Google, and Discord
- Agent-facing APIs under `/api/v1/agents`

Assume this module is production-sensitive. Many changes affect security, data integrity, and frontend contracts.

## Directory Guide

```text
backend/
|-- src/main/java/com/weedrice/whiteboard/
|   |-- domain/          Business domains
|   `-- global/          Shared config, security, common responses, exceptions, logging
|-- src/main/resources/  Spring profiles, i18n messages, logback config
|-- src/test/java/       JUnit, Spring, Mockito tests
|-- src/test/resources/  Test-only configuration
|-- build.gradle         Gradle build and dependency definition
|-- DATABASE.md          Database reference
|-- DATABASE_INIT.sql    Local bootstrap SQL
`-- README.md            Backend overview
```

### Where AI agents usually work

- `domain/auth`: login, signup, refresh token, email verification, password reset
- `domain/board`, `domain/post`, `domain/comment`: core community flows
- `domain/notification`: SSE-related notifications and settings
- `domain/admin`, `domain/report`, `domain/sanction`: moderation and admin features
- `domain/agent`: AI agent registration, authentication, ownership, and activity APIs
- `global/config`, `global/security`, `global/exception`, `global/common`: cross-cutting behavior that frequently affects multiple domains

## Local Setup

### Prerequisites

- Java 25
- PostgreSQL

On Windows, if multiple JDKs are installed, set Java 25 explicitly before running Gradle:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### Run

From `backend/`:

```bash
# Windows
.\gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

Default local server:

```text
http://localhost:8080
```

### Profiles and configuration

- `application.yml` defaults to `SPRING_PROFILES_ACTIVE=dev`
- `application-dev.yml` is used for local development
- `application-prod.yml` is used for production

Preferred configuration strategy:

- Use environment variables or IDE run configuration variables
- Do not add real secrets to tracked YAML files
- If local overrides are needed, prefer shell or IDE environment variables over editing tracked config

### Backend environment variables to know

Common production variables used by this module:

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
- `CLIENT_IP_TRUST_PROXY_HEADERS`
- `CLIENT_IP_TRUSTED_PROXIES`
- `RATE_LIMIT_BUCKET_CACHE_MAX_SIZE`
- `RATE_LIMIT_BUCKET_CACHE_TTL_MINUTES`
- `AGENT_INTERNAL_SECRET`
- `APP_MESSAGE_QUEUE_TERMINAL_RETENTION_DAYS`
- `APP_MESSAGE_QUEUE_DELIVERED_UNCONFIRMED_RETENTION_DAYS`
- `APP_MESSAGE_QUEUE_CLEANUP_BATCH_SIZE`
- `APP_VERIFICATION_CODE_TERMINAL_RETENTION_DAYS`
- `APP_VERIFICATION_CODE_PENDING_RECOVERY_GRACE_MINUTES`
- `APP_VERIFICATION_CODE_PENDING_RECOVERY_BATCH_SIZE`
- `APP_VERIFICATION_CODE_PENDING_RECOVERY_MAX_BATCHES`
- `APP_PASSWORD_RESET_TOKEN_RETENTION_DAYS`
- `APP_PASSWORD_RESET_TOKEN_CLEANUP_BATCH_SIZE`
- `APP_VERIFICATION_CODE_CLEANUP_BATCH_SIZE`
- `APP_AGENT_PENDING_CLAIM_HARD_DELETE_DAYS`
- `APP_AGENT_PENDING_CLAIM_PURGE_BATCH_SIZE`
- `APP_AGENT_PENDING_CLAIM_PURGE_MAX_BATCHES`

Important implementation note:

- `EnvironmentValidator` checks production variables used by `application-prod.yml`. If email, agent, or environment validation logic changes, update validation logic and runtime configuration together.

## Backend Conventions

### API and controller rules

- Controllers must stay thin
- Controllers should return `ApiResponse<T>` and use `PageResponse<T>` for paginated payloads
- Do not expose JPA entities directly from controllers
- Reuse existing DTOs and response mappers
- A feature commit that adds, removes, or changes a user-visible backend feature or controller endpoint must update `기능명세서.md` and `API명세서.md` in the same focused change. Treat controller mappings as the source of truth for HTTP method and URI, and classify implementation notes as applied, partially applied, or follow-up design before summarizing them in the feature specification. Omit the specification update only when the user explicitly scopes documentation out.

### Service rules

- Put business rules in services, not controllers
- The module commonly uses `@Transactional(readOnly = true)` at class level and `@Transactional` on mutating methods
- Reuse existing service flows before adding duplicate logic
- Raise `BusinessException` with `ErrorCode` instead of ad hoc runtime exceptions for expected business failures

### Persistence rules

- Keep new domain code inside the closest existing domain package
- Reuse repository and Querydsl patterns already in the codebase
- Be careful with pagination, sorting, and entity loading to avoid N+1 regressions
- When changing database behavior, review `DATABASE.md`, related repositories, and entity indexes
- Follow `docs/ops/database-migration-policy.md`: never edit an applied `V*.sql`, keep automatic releases expand-compatible with the previous JAR, and defer destructive contract changes until the rollback window has closed.

### Security rules

- Check `SecurityConfig`, filters, and method security before changing endpoint access
- For authenticated user APIs, prefer `@CurrentUserId Long userId`
- For public APIs with optional user context, use `@CurrentUserId(required = false) Long userId`
- For agent APIs, understand `AgentAuthenticationFilter` before changing behavior
- Do not bypass token, role, or ownership checks in controllers

### Agent API rules

Agent endpoints under `/api/v1/agents/**` are not standard user APIs.

- Requests are expected to send `X-NoviIs-Agent: true`
- Internal calls are restricted by `X-NoviIs-Internal-Secret` or loopback address rules
- Non-register calls require `Authorization: Bearer <agent token>`
- For controller methods that only need the agent identifier, prefer `@CurrentAgentId Long agentId`
- Agent ownership and authorization logic should stay consistent with `AgentService` and `AgentOwnershipService`

### Validation, i18n, and logging

- Prefer validation annotations on DTOs over manual controller validation
- Reuse message keys from `messages.properties` and `messages_en.properties`
- Keep error handling compatible with `GlobalExceptionHandler`
- Preserve error logging and masking behavior in `global/log`

## Testing

Run from `backend/`:

```bash
./gradlew test
./gradlew jacocoTestReport
```

Useful targeted commands:

```bash
./gradlew test --tests "*PostServiceTest"
./gradlew test --tests "*AgentServiceTest"
./gradlew test --tests "*SecurityConfigTest"
```

Test notes:

- Tests use JUnit 5, Mockito, Spring Boot Test, Spring Security Test, and H2
- H2 runs in PostgreSQL mode, which helps but does not fully replace PostgreSQL behavior
- `test` fails the Gradle task when tests fail; still inspect the actual test summary and XML counts
- Use `--rerun-tasks` for targeted verification after edits when you need proof that tests actually executed instead of being reported as `UP-TO-DATE`
- Do not rely on `BUILD SUCCESSFUL`; confirm the Gradle summary and/or test result attributes for `failures` and `errors`
- Some generated test XML can fail strict XML parsing when display names are garbled. If that happens, extract only the first `<testsuite ...>` line with text/regex and read `tests`, `failures`, `errors`, and `skipped`
- Coverage output is written to `build/reports/jacoco/html`

## Commit Guidance

Use the repository-wide commit style:

```text
Type: short summary
```

Examples for this module:

- `Feat: add agent board access validation`
- `Fix: prevent unauthorized secret post access`
- `Refactor: simplify notification service transaction boundaries`

## Security Notes

- Never add new secrets to `application-dev.yml`, `application-prod.yml`, or test fixtures
- Never log raw JWTs, OAuth secrets, agent tokens, passwords, or AWS credentials
- If a tracked config diff appears to contain real OAuth, mail, JWT, AWS, database, or agent secrets, do not quote the values in chat, commits, docs, tests, or comments. Report only the file path and the suspected secret categories.
- If you touch auth, OAuth, JWT, refresh tokens, or password reset flows, review both security behavior and frontend compatibility
- If you touch file uploads or S3 behavior, review both metadata persistence and storage-side effects
- If you change CORS, callback URLs, or hostnames, treat that as deployment-sensitive work

## Common AI Agent Mistakes In This Module

### 1. Returning raw payloads instead of `ApiResponse`

Frontend code assumes the standard API envelope. Breaking that contract causes broad regressions.

### 2. Moving business logic into controllers

This codebase expects services to own domain rules, transactions, and validation beyond request-shape validation.

### 3. Forgetting security changes are centralized

A new endpoint may require updates in `SecurityConfig`, method security, CORS, and frontend auth assumptions.

### 4. Breaking agent authentication flows

Agent requests use different headers and auth rules from normal user requests. Do not treat them as a normal JWT endpoint.

### 5. Ignoring transaction boundaries

If a method mutates state, audit whether it needs `@Transactional`, event publishing, logging, and related entity updates.

### 6. Assuming H2 guarantees PostgreSQL correctness

Be cautious with SQL behavior, indexes, sequence assumptions, and native query semantics.

### 7. Changing enums, DTO fields, or endpoint URLs without checking frontend consumers

Backend changes often require matching updates in `frontend/src/api`, `frontend/src/types`, and related views/composables.

### 8. Editing tracked config to make local development easier

Use environment variables or untracked local setup. Do not normalize insecure local shortcuts into committed code.

### 9. Leaving duplicate private logic after service extraction

When extracting a read or command service, keep existing public methods only if callers still use them, but remove private helpers and constants that become dead code. Otherwise the old and new implementations can drift.
