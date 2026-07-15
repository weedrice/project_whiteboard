# AGENTS.md

## Project Overview

NoviIs (`noviis.kr`) is a production-adjacent community platform. For agent work in this repository, prioritize small, local backend changes, preserve existing behavior, and avoid speculative refactors.

This repository is a monorepo, but most report-driven maintenance work is expected to touch:

- `backend/`: Spring Boot 3.x API server
- `docs/`: reports, audits, and project documentation
- `logs/`: runtime logs; do not commit generated files
- `uploads/`: local upload storage; do not commit generated files

## Backend Work Areas

- `backend/src/main/java/com/weedrice/whiteboard/domain/*`: business domains such as `auth`, `board`, `post`, `comment`, `notification`, `user`, and `agent`
- `backend/src/main/java/com/weedrice/whiteboard/global/*`: shared config, security, validation, exceptions, logging, and response wrappers
- `backend/src/main/resources/`: Spring profile and logging configuration
- `backend/src/test/java/`: JUnit and Spring tests

Important notes:

- The backend package root is `com.weedrice.whiteboard`.
- Keep new code in the closest existing domain package.
- Preserve API URLs, response envelopes, and DTO shapes unless the task explicitly requires coordinated API changes.
- Do not add libraries, framework changes, or DB migrations without explicit approval.

## Backend Environment

Prerequisites:

- Java 21
- PostgreSQL for local backend development

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

Prefer shell environment variables, IDE run configuration variables, or deployment-level secret injection over tracked YAML changes.

## Dirty Worktree And Secrets

- Treat pre-existing dirty and untracked files as user-owned.
- Stage and commit only files required for the current task.
- Never commit `.env`, `.env.*`, `*.local`, generated reports, logs, uploads, build artifacts, editor directories, or local config containing suspected secrets.
- Do not commit `application-*.yml` unless the user explicitly asks for a safe config change.
- If a tracked config or env file appears to contain secrets, report only the file path and secret type. Do not quote values.
- Never hardcode new secrets, tokens, API keys, passwords, JWT secrets, OAuth secrets, or AWS credentials.

## Backend Conventions

- Keep controllers thin and delegate business logic to services.
- Keep services transactional. The codebase commonly uses `@Transactional(readOnly = true)` at class level and overrides mutating methods with `@Transactional`.
- Reuse the existing exception pattern with `BusinessException` and `ErrorCode`.
- Preserve existing wrappers such as `ApiResponse` and `PageResponse`.
- Use DTOs for request and response payloads. Do not expose JPA entities directly from controllers.
- Prefer constructor injection via Lombok `@RequiredArgsConstructor`.
- Match existing Lombok usage such as `@Getter`, `@Builder`, and protected no-args constructors.
- Be careful with JPA fetch behavior. Reuse existing repository, Querydsl, `@EntityGraph`, and pagination patterns to avoid N+1 regressions.
- Keep security-sensitive logic in existing security/config layers rather than duplicating auth checks inside controllers.
- For refactors that move responsibility to a new service or module, remove private dead code and unused constants from the old location in the same focused change.

## Backend Testing

Run from `backend/`:

```bash
./gradlew test
./gradlew jacocoTestReport
```

Useful targeted command:

```bash
./gradlew test --tests "*PostServiceTest" --rerun-tasks
```

Backend test notes:

- Tests use JUnit 5, Mockito, Spring Boot Test, Spring Security Test, and H2 in PostgreSQL mode.
- `test` fails the Gradle task when tests fail; still confirm the actual test count, failure count, error count, and skipped count.
- Always check the actual test count, failure count, error count, and skipped count.
- For targeted verification after code changes, use `--rerun-tasks` so Gradle does not report stale `UP-TO-DATE` results as a real check.
- Some test XML can be hard to parse because display names may contain broken encoding. If XML parsing fails, read the Gradle console summary or extract only `<testsuite ... tests/failures/errors/skipped>` attributes.

PowerShell helper for aggregate test XML results:

```powershell
$totalTests=0; $totalFailures=0; $totalErrors=0; $totalSkipped=0; $failed=@()
Get-ChildItem -Path build/test-results/test -Filter TEST-*.xml | ForEach-Object {
  $line = Select-String -Path $_.FullName -Pattern '<testsuite ' -List | Select-Object -ExpandProperty Line
  if ($line -match 'name="([^"]+)".*tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"') {
    $name=$matches[1]; $tests=[int]$matches[2]; $skipped=[int]$matches[3]; $failures=[int]$matches[4]; $errors=[int]$matches[5]
    $totalTests += $tests; $totalFailures += $failures; $totalErrors += $errors; $totalSkipped += $skipped
    if ($failures -gt 0 -or $errors -gt 0) { $failed += [pscustomobject]@{Name=$name; Tests=$tests; Failures=$failures; Errors=$errors; Skipped=$skipped; File=$_.Name} }
  }
}
[pscustomobject]@{Tests=$totalTests; Failures=$totalFailures; Errors=$totalErrors; Skipped=$totalSkipped}
$failed | Format-Table -AutoSize
```

## Report Implementation Workflow

Before editing:

- Read the target report and classify every item as: apply now, already applied, or needs follow-up design.
- Inspect `git status --short` and treat pre-existing dirty files as user-owned.
- Identify suspected secrets in tracked config files without quoting their values.

Implementation flow:

- Work in small, focused steps.
- Before broad, security-sensitive, API-contract, frontend-visible, or cross-module changes, inspect likely impact and use a pre-change review agent when requested.
- Edit only files needed for the current step.
- Run the narrowest relevant targeted tests first.
- Use post-change review for regression risk, exception flow, API/frontend impact, lint/style, or conventions when relevant.
- Address review findings, rerun affected tests, then stage only files for that step and commit with a focused Korean message when commits are requested.
- Close each sub-agent as soon as the step is finished.

Final verification and report:

- Run the broadest practical verification after all steps, including full backend tests when requested.
- Summarize created commits, applied items, already-applied items, remaining follow-up items, tests run and results, whether any full-suite failures are related, and dirty/security risks.

## Commit Message Rules

Follow the repository's existing prefix style:

```text
Type: short summary
```

Common prefixes:

- `Feat:`
- `Fix:`
- `Refactor:`
- `Docs:`
- `Test:`
- `Chore:`

Keep commits focused. For this repository, write commit summaries in Korean when the user requests Korean commit messages.

## Common Cautions

- Do not break the shared API envelope.
- If a backend change affects URL, response shape, validation, or error behavior, check for frontend impact before finalizing.
- Auth changes require extra care around JWT, OAuth2, email verification, password reset, token expiry, redirect URLs, and filter/config consistency.
- H2 PostgreSQL-mode tests do not prove every PostgreSQL-specific behavior. Be cautious with native SQL, indexing assumptions, and dialect-specific queries.
- Do not add dependencies without approval.
- Do not solve local setup by adding credentials to tracked config, docs, or examples.
- For anything beyond a trivial text change, run the narrowest relevant tests first, then broader checks when requested.
