# Documentation AGENTS.md

## Scope

This directory contains design decisions, operational runbooks, API snapshots, QA checklists, audits, backlogs, contracts, and PostgreSQL reference SQL. Documentation should describe verified repository behavior and clearly distinguish current state from proposals or follow-up work.

## Organization And Indexing

- Put design rationale and planned contracts in `design-notes/`.
- Put production procedures, prerequisites, rollback, and recovery guidance in `ops/`.
- Put repeatable manual QA procedures in `qa/`.
- Treat `sql/` as reviewed reference or operator SQL, not Flyway history.
- Put generated API contract snapshots in `api/` and machine-readable shared contracts in `contracts/`.
- Update `docs/README.md` when adding, removing, renaming, or materially reclassifying a document that should be discoverable.
- Follow the surrounding document's language and style. Use UTF-8 and relative repository links where practical.

## Evidence And Source Of Truth

- Treat implemented code, controller mappings, tests, Flyway history, and runtime configuration as primary evidence. Do not let a report or design note override contradictory implementation silently.
- Label statements as applied, partially applied, proposed, deprecated, or follow-up design when status could be ambiguous.
- Include concrete affected paths and verification evidence, but do not paste secrets, credentials, raw production telemetry, or unredacted diagnostics.
- When a dated audit or design note becomes historical, preserve the original record and add a status update or a newer document instead of rewriting history without explanation.
- Operational commands must state prerequisites, target environment, safety checks, expected result, and rollback or recovery when failure can change external state.

## API Contract Chain

The contract chain is:

```text
backend controllers and DTOs
  -> docs/api/openapi-frontend.json
  -> frontend/src/types/generated/api.ts
```

- Do not hand-edit `docs/api/openapi-frontend.json` or the generated TypeScript file to bypass a mismatch.
- Regenerate the OpenAPI snapshot from `OpenApiSpecSnapshotTest`, review its diff, then regenerate frontend types.
- Keep `backend/API명세서.md`, `backend/기능명세서.md`, `docs/ops/api-contract-revision.txt`, frontend types, and affected consumers synchronized when the requested change alters their documented contract.
- Before editing under `docs/api/`, read and follow `docs/api/AGENTS.md` for the generation and verification procedure.

## Database And Operations Documentation

- Treat `backend/src/main/resources/db/migration/` as schema history and `backend/DATABASE.md` as its maintained summary.
- Do not copy a file from `docs/sql/` into Flyway or execute it against a database without reviewing current schema, idempotency, locking, target, backup, and rollback implications and obtaining the required authorization.
- Follow `docs/ops/database-migration-policy.md` for expand/backfill/contract decisions and `docs/ops/postgres-backup-restore.md` for recovery claims.
- Update operational documents when permissions, host paths, service units, monitoring thresholds, release ordering, rollback, or operator actions change.

## Review Checklist

- Confirm paths, commands, environment variable names, versions, ports, and endpoint shapes against the repository.
- Check that links and referenced files exist.
- Distinguish automated verification from manual checks and from checks that were not run.
- Avoid duplicating long canonical policies; link to the source and summarize only the rules needed in the local context.
