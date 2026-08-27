# Flyway Migration AGENTS.md

## Scope And Authority

This directory is the production schema history for NoviIs. Changes are deployment-sensitive and remain subject to the repository rule requiring explicit user approval for new migrations.

- Treat `docs/ops/database-migration-policy.md` as the canonical migration policy.
- Use versioned SQL only. Repeatable (`R__`), undo (`U__`), and Java migrations are prohibited.
- Treat every migration already merged to the base revision as immutable. Fix it with a new version; do not edit, delete, rename, or extend a historical exception.
- Do not execute migrations against a shared, staging, or production database unless the user explicitly authorizes that exact target.

## File Contract

- Name new files `V<positive integer>__<description>.sql` using a unique version greater than the base revision's highest version.
- Include exactly one phase marker: `-- noviis:migration-phase expand`, `backfill`, or `contract`.
- Keep automatic releases compatible with both the new JAR and the previous JAR throughout the rollback window.
- Separate expand, restartable backfill, application switch, and destructive contract work. Do not combine a destructive schema change with the application switch.
- Contract migrations require a reviewed `-- noviis:design-doc docs/design-notes/...` reference and the operational checks defined by the canonical policy.
- Bounded deletes, redundant-index removal, procedural SQL, privilege changes, and other risky statements must use the exact markers and classification rules from the canonical policy.

## Online Indexes

For an index on an existing table:

- Use `CREATE INDEX CONCURRENTLY` with a `lock_timeout` from 1 to 10 seconds.
- Add `-- noviis:online-index <index_name>` and do not add `IF NOT EXISTS` to the concurrent index statement.
- Add an adjacent `<migration>.sql.conf` containing only `executeInTransaction=false`.
- Do not create a sidecar for an ordinary index on a table created in the same migration.
- Treat the sidecar as immutable with its migration. Do not use a `.sql.conf` filename as the documented migration range endpoint.

## Required Paired Changes

- Update `backend/DATABASE.md` in the same focused change. Review its date, highest migration, table count and list, affected columns, constraints, indexes, migration history, and operational cautions.
- Add or update focused migration contract tests when SQL semantics can be verified without PostgreSQL.
- Review affected entities, repositories, native queries, indexes, and previous-JAR compatibility.
- Record a contract migration in `docs/ops/applied-contract-migrations.txt` only after production succeeds, in a separate change that introduces no migration.

## Verification

Run from the repository root on Windows with an installed Python 3 interpreter (`python`, or `py -3` when that launcher is configured):

```powershell
python backend/scripts/verify-database-doc.py
.\backend\gradlew.bat test --tests "*Migration*Test" --rerun-tasks
```

The document verifier checks migration range, table count, and table inventory only. Manually compare columns, constraints, index purpose, and operational impact.

Run the compatibility checker through Git Bash or WSL with a real comparison commit:

```bash
bash backend/scripts/check-migration-compatibility.sh <base-ref> HEAD
```

When a disposable PostgreSQL instance with the required extensions is configured, also run:

```powershell
.\backend\gradlew.bat postgresSmokeTest --rerun-tasks
```

Never substitute H2 success for PostgreSQL verification when the change depends on PostgreSQL SQL, locking, indexing, extensions, or dialect behavior.
