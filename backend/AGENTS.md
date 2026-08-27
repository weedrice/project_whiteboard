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
|-- DATABASE_INIT.sql    Legacy seed source; Flyway migrations are the runtime source of truth
`-- README.md            Backend overview
```

## Local Setup

### Prerequisites

- Java 25
- PostgreSQL with the `pg_trgm` and `vector` extensions

Flyway creates both extensions during migration. The migration user therefore needs permission to create them; when that permission is unavailable, have a database administrator install both extensions before starting the backend.

On Windows, if multiple JDKs are installed, set Java 25 explicitly before running Gradle. Replace the placeholder with the installed JDK 25 directory:

```powershell
$env:JAVA_HOME='<absolute path to JDK 25>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
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

### Backend environment variables

- Treat `ENVIRONMENT_VARIABLES.md` as the maintained production variable checklist; do not duplicate that inventory here.
- `EnvironmentValidator` checks required production variables used by `application-prod.yml`. Update the validator, its tests, runtime configuration, and `ENVIRONMENT_VARIABLES.md` together when that contract changes.

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
- When extracting a read or command service, remove private helpers and constants that become dead code so the old and new implementations cannot drift.
- Raise `BusinessException` with `ErrorCode` instead of ad hoc runtime exceptions for expected business failures

### Persistence rules

- Keep new domain code inside the closest existing domain package
- Reuse repository and Querydsl patterns already in the codebase
- Be careful with pagination, sorting, and entity loading to avoid N+1 regressions
- When changing database behavior, review `DATABASE.md`, related repositories, and entity indexes
- Before creating or editing Flyway files, read and follow `src/main/resources/db/migration/AGENTS.md` and `docs/ops/database-migration-policy.md`.

### Security rules

- Check `SecurityConfig`, filters, and method security before changing endpoint access
- For authenticated user APIs, prefer `@CurrentUserId Long userId`
- For public APIs with optional user context, use `@CurrentUserId(required = false) Long userId`
- For agent APIs, understand `AgentAuthenticationFilter` before changing behavior
- Do not bypass token, role, or ownership checks in controllers

### Agent API rules

Agent endpoints under `/api/v1/agents/**` are not standard user APIs.

- `POST /api/v1/agents/register` bypasses `AgentAuthenticationFilter` and does not require Agent, internal-secret, or bearer headers; the public registration rate limit still applies
- Every other Agent request must send `X-NoviIs-Agent: true`
- Every other Agent request must send `X-NoviIs-Internal-Secret` with the configured secret; loopback addresses are not exempt
- Every other Agent request requires `Authorization: Bearer <agent token>`
- For controller methods that only need the agent identifier, prefer `@CurrentAgentId Long agentId`
- Agent ownership and authorization logic should stay consistent with the service that owns the flow and the shared `AgentOwnershipService`; lifecycle operations belong to `AgentLifecycleService`, reads to `AgentQueryService`, and writes to `AgentCommandService`

### Validation, i18n, and logging

- Prefer validation annotations on DTOs over manual controller validation
- Reuse message keys from `messages.properties` and `messages_en.properties`
- Keep error handling compatible with `GlobalExceptionHandler`
- Preserve error logging and masking behavior in `global/log`

## Testing

Run from `backend/` on Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat jacocoTestReport
```

On macOS or Linux, use `./gradlew` instead of `.\gradlew.bat`.

Useful targeted commands on Windows:

```powershell
.\gradlew.bat test --tests "*PostServiceTest"
.\gradlew.bat test --tests "*AgentServiceTest"
.\gradlew.bat test --tests "*SecurityConfigAuthorizationTest"
```

Test notes:

- Tests use JUnit 5, Mockito, Spring Boot Test, Spring Security Test, and H2
- H2 runs in PostgreSQL mode, which helps but does not fully replace PostgreSQL behavior
- `test` fails the Gradle task when tests fail; still inspect the actual test summary and XML counts
- Use `--rerun-tasks` for targeted verification after edits when you need proof that tests actually executed instead of being reported as `UP-TO-DATE`
- Do not rely on `BUILD SUCCESSFUL`; confirm the Gradle summary and/or test result attributes for `failures` and `errors`
- Some generated test XML can fail strict XML parsing when display names are garbled. If that happens, extract only the first `<testsuite ...>` line with text/regex and read `tests`, `failures`, `errors`, and `skipped`
- Coverage output is written to `build/reports/jacoco/html`

## Security Notes

- Never add new secrets to `application-dev.yml`, `application-prod.yml`, or test fixtures
- Never log raw JWTs, OAuth secrets, agent tokens, passwords, or AWS credentials
- If a tracked config diff appears to contain real OAuth, mail, JWT, AWS, database, or agent secrets, do not quote the values in chat, commits, docs, tests, or comments. Report only the file path and the suspected secret categories.
- If you touch auth, OAuth, JWT, refresh tokens, or password reset flows, review both security behavior and frontend compatibility
- If you touch file uploads or S3 behavior, review both metadata persistence and storage-side effects
- If you change CORS, callback URLs, or hostnames, treat that as deployment-sensitive work
