# Documentation AGENTS.md

## Scope

This directory contains design decisions, operational runbooks, API snapshots, QA checklists, audits, backlogs, contracts, and PostgreSQL reference SQL. Documentation should describe verified repository behavior and clearly distinguish current state from proposals or follow-up work.

## Organization And Indexing

- Put design rationale and planned contracts in `design-notes/`.
- Put production procedures, prerequisites, rollback, and recovery guidance in `ops/`.
- Put repeatable manual QA procedures in `qa/`.
- Treat `sql/` as reviewed reference or operator SQL, not Flyway history.
- Put generated API contract snapshots in `api/` and machine-readable shared contracts in `contracts/`.
- Update [README.md](README.md) when adding, removing, renaming, or materially reclassifying a discoverable document. Follow its surrounding language and style, using relative repository links.

## Evidence And Source Of Truth

- Treat implemented code, controller mappings, tests, Flyway history, and runtime configuration as primary evidence. Do not let a report or design note override contradictory implementation silently.
- Label statements as applied, partially applied, proposed, deprecated, or follow-up design when status could be ambiguous.
- Include concrete affected paths and verification evidence, but do not paste secrets, credentials, raw production telemetry, or unredacted diagnostics.
- When a dated audit or design note becomes historical, preserve the original record and add a status update or a newer document instead of rewriting history without explanation.
- Operational commands must state prerequisites, target environment, safety checks, expected result, and rollback or recovery when failure can change external state.

## API Contract Chain

Backend controllers/DTOs produce the OpenAPI snapshot, which produces frontend types. Before changing this chain or files under `api/`, follow [api/AGENTS.md](api/AGENTS.md) for generation, ordinary verification, and coordinated specification updates. Do not hand-edit generated outputs to bypass a mismatch.

## Database And Operations Documentation

- Treat [Flyway migrations](../backend/src/main/resources/db/migration/AGENTS.md) as schema history and [DATABASE.md](../backend/DATABASE.md) as its maintained summary.
- Do not copy a file from `docs/sql/` into Flyway or execute it against a database without reviewing current schema, idempotency, locking, target, backup, and rollback implications and obtaining the required authorization.
- Follow the [migration policy](ops/database-migration-policy.md) for expand/backfill/contract decisions and the [recovery guide](ops/postgres-backup-restore.md) for recovery claims.
- Update operational documents when permissions, host paths, service units, monitoring thresholds, release ordering, rollback, or operator actions change.

## Verification

Follow the root documentation checks. Also confirm environment variable names, versions, ports, and endpoint shapes against implementation; link to canonical policies instead of copying their full contents.
