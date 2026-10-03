# Backend AGENTS.md

## Scope And Sources

This module owns the NoviIs API, persistence, authentication, background jobs, and file storage integration. Repository-wide instructions remain in force for production code, tests, scripts, and configuration.

- Use [build.gradle](build.gradle) and the [Gradle wrapper](gradle/wrapper/gradle-wrapper.properties) for Java, framework, dependency, and build versions.
- Use [README.md](README.md) for setup and execution, [ENVIRONMENT_VARIABLES.md](ENVIRONMENT_VARIABLES.md) for configuration, and [DATABASE.md](DATABASE.md) for the maintained database summary. Flyway history is the schema source of truth; `DATABASE_INIT.sql` is legacy seed material.
- Controller mappings and DTOs define the HTTP contract. Keep [API명세서.md](API명세서.md) and [기능명세서.md](기능명세서.md) aligned with implemented behavior.
- `src/main/java/com/weedrice/whiteboard/domain/` owns domain behavior; `global/` owns shared security, configuration, errors, and logging. Corresponding tests live under `src/test/java/com/weedrice/whiteboard/`.

## Required Reading By Change

Read only the matching guidance, including its conditional sources, for production code, tests, and callers that alter the contract. Tests outside `src/main/` and integration callers do not inherit domain instructions automatically.

| Change area | Read and follow |
| --- | --- |
| Posts, drafts, scheduled publication, or their callers/tests | [post/AGENTS.md](src/main/java/com/weedrice/whiteboard/domain/post/AGENTS.md) |
| File upload, association, reservations, storage, cleanup, or their callers/tests | [file/AGENTS.md](src/main/java/com/weedrice/whiteboard/domain/file/AGENTS.md) |
| Authentication, OAuth, token/session lifecycle, user sessions, or security that establishes user identity | [auth/AGENTS.md](src/main/java/com/weedrice/whiteboard/domain/auth/AGENTS.md) |
| Agent endpoints, Agent identity/ownership, or `AgentAuthenticationFilter` | [agent/AGENTS.md](src/main/java/com/weedrice/whiteboard/domain/agent/AGENTS.md); also auth guidance if shared user authentication changes |
| Draft/file/scheduled-post lifecycle across domains | Both post and file instructions |
| Schema changes, Flyway, or migration-related SQL/scripts | [migration/AGENTS.md](src/main/resources/db/migration/AGENTS.md) |
| HTTP DTO, validation, error, authorization, or documented behavior | Affected domain guidance; [frontend/AGENTS.md](../frontend/AGENTS.md) when editing consumers; [API contract guidance](../docs/api/AGENTS.md) when the generated contract changes |
| Scheduler cadence, required metrics, or production host behavior | [monitoring/AGENTS.md](../deploy/monitoring/AGENTS.md) or [deploy/AGENTS.md](../deploy/AGENTS.md), as applicable |

Other domains inherit this file. Add a domain instruction only for durable domain-specific rules; do not create copies of these common conventions.

## Shared Invariants

- Keep controllers thin and business rules in services. Reuse existing service flows, DTOs, and response mappers. Return `ApiResponse<T>` and `PageResponse<T>` where appropriate; never expose JPA entities directly.
- Raise `BusinessException` with `ErrorCode` for expected failures. Keep validation annotations, `messages.properties`, `messages_en.properties`, and `GlobalExceptionHandler` consistent with the existing response contract.
- Preserve error masking in `global/log`; never log raw credentials, tokens, or sensitive configuration, including in tests and diagnostics.
- Prefer `@CurrentUserId Long userId` for authenticated user APIs and `@CurrentUserId(required = false)` for public APIs with optional user context. Agent APIs use their own identity contract. Check [SecurityConfig](src/main/java/com/weedrice/whiteboard/global/config/SecurityConfig.java), filters, and method security before changing access; do not bypass role or ownership validation in controllers.
- Keep new code in the closest domain package. Reuse repositories and Querydsl patterns. Preserve pagination, stable ordering, loading behavior, and entity indexes; check for N+1 queries when changing reads.
- Respect explicit boundary contracts and integration adapters. For content-core dependencies, use [ContentBoundaryArchitectureTest](src/test/java/com/weedrice/whiteboard/ContentBoundaryArchitectureTest.java) as executable evidence rather than importing another domain's internal entity, repository, or service.
- When extracting a service, remove helpers and constants that become dead code so the old and new implementations cannot diverge.

## Transactions And Persistence

- Preserve transaction boundaries; the usual service pattern is class-level `@Transactional(readOnly = true)` with transactional command methods. Check whether calls actually cross a Spring proxy before relying on propagation or interception.
- For concurrent commands, review the existing lock/version policy, lock ordering, conditional updates, rollback, and repeat execution together. Do not add locks to every read or assume a Java precheck makes a later update atomic.
- A previously loaded JPA entity may remain stale after a locking query. Where correctness depends on the latest state, use the established refresh/revalidation flow and database conditions; review flush timing before refreshing managed entities.
- Review `DATABASE.md`, affected repositories, native queries, and indexes for database behavior changes. Follow file guidance for metadata/storage effects and the repository approval rule for new migrations or framework baseline changes.

## Coordinated Changes

- Check frontend consumers when URLs, payloads, validation, authorization, errors, ordering, or public behavior change, even if the endpoint path stays the same.
- A user-visible backend feature or endpoint change must update `기능명세서.md` and `API명세서.md` in the same focused change unless the user explicitly excludes documentation. Mark implementation notes as applied, partially applied, or follow-up design. Regenerate snapshots only when their represented contract changes.
- Keep [EnvironmentValidator](src/main/java/com/weedrice/whiteboard/global/config/EnvironmentValidator.java), its tests, runtime configuration, and `ENVIRONMENT_VARIABLES.md` synchronized when production configuration requirements change.
- Treat CORS, OAuth callbacks, hostnames, and API origins as deployment-sensitive; check frontend and Nginx contracts. Changing files does not authorize deployment or shared-database operations.

## Verification

Run commands from `backend/` in PowerShell with the JDK required by `build.gradle`; use `.\gradlew.bat`, or `./gradlew` on macOS/Linux. Setup and coverage instructions are in `README.md`.

| Change | Relevant verification |
| --- | --- |
| Service or controller behavior | Fresh affected domain tests, including authorization/error paths and the relevant callers |
| Repository, file state, or transaction behavior | Repository/service integration tests plus affected lifecycle tests; use disposable PostgreSQL for PostgreSQL-specific SQL, locks, indexes, extensions, or dialect claims |
| Cross-domain dependency changes | The matching architecture tests and integration callers |
| API contract changes | Snapshot verification/generation as required by `docs/api/AGENTS.md`, then frontend API/type checks and affected consumer tests |
| Migration or operational changes | The checks specified by the linked migration, deployment, or monitoring instructions |

Choose classes from the affected domain's Verification section. Repeat `--tests` for related classes; for example:

```powershell
.\gradlew.bat test --tests "*PostServiceTest" --rerun-tasks
```

- Add regression coverage for meaningful behavior, not tests that only mirror implementation details.
- Use runner summaries or XML for the repository-required test counts. If display-name encoding prevents XML parsing, inspect the `<testsuite ...>` header as text.
- State whether evidence came from mocks, H2, or PostgreSQL. H2 in PostgreSQL mode does not prove PostgreSQL concurrency or dialect behavior; report required verification that the environment prevented.
