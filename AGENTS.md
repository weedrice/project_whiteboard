# AGENTS.md

## Repository Overview

NoviIs (`noviis.kr`) is a community platform maintained as a monorepo. Preserve existing behavior, prefer small and focused changes, and avoid speculative refactors.

Main areas:

- `frontend/`: Vue 3, TypeScript, Vite, Pinia, Vue Router, and TanStack Vue Query
- `backend/`: Spring Boot, Java, Gradle, JPA, Flyway, and PostgreSQL; use module build files for exact runtime and dependency versions
- `docs/`: architecture, operations, reports, audits, and project documentation
- `deploy/`: deployment and monitoring configuration
- `logs/`: generated runtime logs; do not commit
- `uploads/`: generated local upload storage; do not commit

## Instruction Scope

- Before editing, read the instructions for each affected module: [frontend](frontend/AGENTS.md), [backend](backend/AGENTS.md), [deployment](deploy/AGENTS.md), [documentation](docs/AGENTS.md), or [GitHub automation](.github/AGENTS.md).
- Follow only the matching entries in each module's reading map, including domain tests and callers outside the source subtree. Do not assume every nested file was loaded or recursively read every linked reference; consult implementation references when relevant to the change.
- All applicable instructions remain in force. The closest file controls conflicting local details; repository-wide safety rules still apply. Cross-module changes follow each affected module and verify their shared contract.
- For production deployment behavior, read both deployment and GitHub automation instructions, even if only one directory changes.
- Keep each durable rule in its owning instruction file and reference it from callers. Setup belongs in README files; shared lifecycle explanations belong in design notes. Add a scoped `AGENTS.md` only for distinct, durable rules, preserving existing safety requirements.

## Platform And Encoding

- The primary working environment is Windows with PowerShell.
- Read and write text files as UTF-8. Do not introduce legacy Windows encodings.
- Preserve existing line-ending style unless normalization is an explicit part of the task.
- Prefer Windows command examples first. When documenting cross-platform commands, label the Windows and macOS/Linux variants clearly.

## Working Principles

- Inspect `git status --short` before editing and treat pre-existing dirty or untracked files as user-owned.
- Edit only files needed for the current task and preserve unrelated changes.
- Preserve API URLs, response envelopes, DTO shapes, and user-visible behavior unless the task requires a coordinated change. Check frontend consumers for backend contract changes, and backend contracts for frontend API or integration changes.
- Add a dependency only when it is necessary for the requested change; keep its scope minimal and report the reason and verification. Framework baseline changes and new database migrations require explicit user approval.
- Do not stage or commit unless the user asks. When requested, stage only task-related files and keep commits focused.

## Agent Collaboration

- Use subagents for independent implementation areas or a separate review perspective when this improves speed or confidence.
- Assign explicit file ownership before parallel edits. Shared helpers, generated files, and shared build output must have one coordinating owner; avoid overlapping edits or concurrent builds that overwrite the same results.
- Give reviewers read-only scope unless a specific fix is delegated. The primary agent integrates findings, checks cross-module behavior, and owns the final verification report.
- Release completed subagents using the available lifecycle tools before moving to the next phase. If delegation is unavailable, continue the work locally without weakening verification.

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
- Use fresh targeted execution when caching could report stale results. Inspect test counts, failures, errors, and skips; a successful build banner alone is insufficient.
- For cross-module API changes, verify both backend behavior and the affected frontend types or consumers.
- For instruction/documentation-only changes, check source accuracy, links, referenced commands, UTF-8, line endings, and whitespace. Application tests are unnecessary unless the edit changes executable behavior or uncovers a concern requiring them.
- Report only checks actually run and distinguish automated tests, manual review, and unverified behavior.

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
