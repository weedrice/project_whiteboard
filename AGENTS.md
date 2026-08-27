# AGENTS.md

## Repository Overview

NoviIs (`noviis.kr`) is a community platform maintained as a monorepo. Preserve existing behavior, prefer small and focused changes, and avoid speculative refactors.

Main areas:

- `frontend/`: Vue 3, TypeScript, Vite, Pinia, Vue Router, and TanStack Vue Query
- `backend/`: Spring Boot 4.1, Java 25, Gradle 9.6.1, JPA, Flyway, and PostgreSQL
- `docs/`: architecture, operations, reports, audits, and project documentation
- `deploy/`: deployment and monitoring configuration
- `logs/`: generated runtime logs; do not commit
- `uploads/`: generated local upload storage; do not commit

## Instruction Scope

- This file contains repository-wide rules.
- Before editing under `frontend/`, read and follow `frontend/AGENTS.md`.
- Before editing under `backend/`, read and follow `backend/AGENTS.md`.
- Before editing under `deploy/`, `docs/`, or `.github/`, read and follow the `AGENTS.md` in that directory.
- All applicable `AGENTS.md` files remain in force. When directory-specific details conflict, the closest file controls only those local details while repository-wide safety rules still apply.
- Keep module-specific commands and conventions in the closest module `AGENTS.md` instead of duplicating them here.
- When a change crosses modules, follow both module files and verify the shared API contract.

## Platform And Encoding

- The primary working environment is Windows with PowerShell.
- Read and write text files as UTF-8. Do not introduce legacy Windows encodings.
- Preserve existing line-ending style unless normalization is an explicit part of the task.
- Prefer Windows command examples first. When documenting cross-platform commands, label the Windows and macOS/Linux variants clearly.

## Working Principles

- Inspect `git status --short` before editing and treat pre-existing dirty or untracked files as user-owned.
- Edit only files needed for the current task and preserve unrelated changes.
- Preserve API URLs, response envelopes, DTO shapes, and user-visible behavior unless the task requires a coordinated change.
- Check frontend consumers when backend URLs, payloads, validation, authentication, or error behavior change.
- Check backend contracts when frontend API types or integration assumptions change.
- Add a dependency only when it is necessary for the requested change; keep its scope minimal and report the reason and verification. Framework baseline changes and new database migrations require explicit user approval.
- Do not stage or commit unless the user asks. When requested, stage only task-related files and keep commits focused.

## Local Containers And Persistent State

- Treat `docker-compose.yml` and `docker-compose.local-db.yml` as the shared local runtime contract for backend, frontend, Redis, and PostgreSQL.
- After changing a Compose file, run `docker compose config` for the default file or `docker compose -f docker-compose.local-db.yml config` for the local database file, then review resolved ports, mounts, environment, health checks, and dependencies.
- Do not run `docker compose down -v`, delete named volumes, reset the local database, or remove bind-mounted data unless the user explicitly requests that destructive operation.
- Keep service names, ports, health checks, Dockerfiles, and module documentation synchronized when their contract changes.

## Secrets, Configuration, And Generated Files

- Never hardcode or commit secrets, tokens, API keys, passwords, JWT secrets, OAuth secrets, database credentials, or cloud credentials.
- Prefer environment variables, IDE run configuration variables, or deployment-level secret injection.
- Do not edit tracked `application-*.yml` merely to make local setup work. Non-secret configuration changes required by the requested feature are allowed, but must remain safe for tracked configuration.
- Never commit `.env`, `.env.*`, `*.local`, generated tool reports, coverage output, logs, uploads, build artifacts, editor directories, or local configuration containing suspected secrets.
- If a tracked file appears to contain a secret, report only the file path and suspected secret type; never quote the value.

## Verification

- Run the narrowest relevant tests or checks first, then broader verification in proportion to risk.
- Use fresh targeted test execution when build caching could otherwise report stale results.
- Do not treat a successful build banner alone as proof: inspect test counts and failures where the test runner provides them.
- For cross-module API changes, verify both backend behavior and the affected frontend types or consumers.
- Do not claim checks were run when they were not.

## Report-Driven Work

When implementing a report or audit:

- Classify each item as `apply now`, `already applied`, or `needs follow-up design` before editing.
- Identify affected modules, contract impact, security risk, and suspected secrets without exposing values.
- Apply changes in focused steps and run the narrowest relevant verification after each step.
- In the final summary, distinguish applied, already-applied, and remaining items and list the checks actually run.

## Commit Messages

Follow the existing repository style:

```text
Type: short summary
```

Common prefixes are `Feat:`, `Fix:`, `Refactor:`, `Docs:`, `Test:`, and `Chore:`. Use Korean summaries when the user requests Korean commit messages.

## Final Handoff

Summarize changed files, important behavior or contract decisions, verification results, and any remaining risks. Mention relevant pre-existing dirty files when they could affect review, but do not include unrelated file contents.
