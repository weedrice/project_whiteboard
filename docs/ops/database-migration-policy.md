# Database migration policy

NoviIs deploys a new JAR after Spring Boot/Flyway has applied pending migrations. A release must leave the database usable by both the new application and the previous JAR throughout the rollback window.

Repeatable (`R__`), undo (`U__`), and Java Flyway migrations are prohibited. Versioned SQL is the only supported format. `DO`, `CALL`, function/procedure definitions, `EXECUTE`, and dollar-quoted SQL are classified as contract-phase by default because static analysis cannot prove their effects. CI applies the new schema and runs the previous backend revision with Flyway disabled; a failure means the change is not expand-compatible.

## Required sequence

1. **Expand**: add nullable columns, additive tables, indexes, and defaults compatible with the previous application.
2. **Backfill**: migrate data in restartable batches without holding a table-wide transaction for the entire data set.
3. **Application switch**: deploy code that reads the expanded shape and stops depending on the old shape.
4. **Contract**: after the rollback window closes, remove or rename old schema in a separately reviewed release.

Do not drop or rename an in-use table or column, narrow a type, add `SET NOT NULL` without a completed backfill, or remove a default/check/enum value still required by the previous application in the same release as the application switch.

Versioned `V*.sql` files are immutable after merge. Fix an applied migration with a new version instead of editing or deleting the original file. The only repository exception is the checksum-pinned V88 correction recorded in `check-migration-compatibility.sh`: its first main-branch CI run failed before every deployment job was skipped, so the checker accepts exactly that predeployment bad-to-good transition and still runs the corrected file through all new-migration validations. No other checksum or migration is covered by this exception.

New migrations use `V<positive integer>__<description>.sql`. Versions must be unique and strictly greater than the highest version in the base revision. CI migrates a PostgreSQL database with the base revision and upgrades it with HEAD, then verifies that every tracked version appears exactly once as successful in `flyway_schema_history`. Do not rely on Flyway out-of-order execution.

An index on an existing table must be built online. Add `-- noviis:online-index <index_name>`, set `lock_timeout` to 1–10 seconds, use `CREATE INDEX CONCURRENTLY`, and add an adjacent `<migration>.sql.conf` containing only `executeInTransaction=false`. CI applies that migration without `--single-transaction`. A normal `CREATE INDEX` remains allowed when the indexed table is created in the same migration. Sidecars are immutable with their SQL migration, may only be introduced beside that new migration, and are rejected when they are not required by an online index.

Do not add `IF NOT EXISTS` to the concurrent index statement. PostgreSQL can leave a same-named `INVALID` index after a failed concurrent build, and `IF NOT EXISTS` would silently accept it on retry. Before repairing and rerunning a failed online-index migration, inspect `pg_index.indisvalid`. If the named index is invalid, remove only that index with `DROP INDEX CONCURRENTLY`, confirm no valid index was removed, run Flyway repair, and rerun the migration. A valid same-named index or an unverified catalog state blocks automated retry and requires operator review.

## CI contract marker

Every new migration must contain exactly one phase marker: `-- noviis:migration-phase expand`, `backfill`, or `contract`. `backend/scripts/check-migration-compatibility.sh` rejects modified or deleted versioned migrations and detects common destructive statements in new migrations. A deliberately scheduled contract migration must include both lines and point to a reviewed, tracked document under `docs/design-notes/`:

```sql
-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/example-contract.md
```

`SET NOT NULL` is accepted outside a contract migration only when an earlier statement in the same migration updates the same table and column and limits the backfill to rows where that column is null. An unrelated `UPDATE` is not evidence of a completed backfill.
Complex dollar-quoted procedural SQL is not accepted as automated evidence for either backfill or bounded deletion; classify that migration as contract and provide a design note.

Non-contract `DELETE` statements require an exact table marker and a bounded ID subquery with an explicit positive `LIMIT`:

```sql
-- noviis:migration-phase backfill
-- noviis:bounded-delete stale_rows
DELETE FROM stale_rows
WHERE row_id IN (SELECT row_id FROM stale_rows WHERE expires_at < now() LIMIT 500);
```

Unqualified deletes and tautologies such as `WHERE TRUE` or `WHERE 1 = 1` are always treated as contract operations.

`DROP INDEX` is not blanket-approved. A non-contract removal must drop exactly one statically named index per statement, identify a distinct replacement, and point to a tracked design note:

```sql
-- noviis:migration-phase expand
-- noviis:design-doc docs/design-notes/example-redundant-index.md
-- noviis:redundant-index idx_old_lookup replacement=uq_new_lookup
DROP INDEX IF EXISTS idx_old_lookup;
```

The design note must record the PostgreSQL catalog evidence that the replacement unique index has the same key columns or a compatible leading prefix, plus before/after query plans and smoke results. Dynamic or multi-index drops are contract operations. Before applying any contract migration, confirm that automated backups and the latest restorable time satisfy the recovery policy in `docs/ops/postgres-backup-restore.md`.

Contract migrations are never eligible for an automatic main deployment. Run the integrated CI manually from `main` and explicitly set `deploy_backend=true` and `allow_contract_migration=true`. Manual DB snapshots, snapshot identifiers, and AWS snapshot evidence are not deployment requirements.

Only after production succeeds may the migration filename be appended to `docs/ops/applied-contract-migrations.txt`. The compatibility check keeps unrecorded contract migrations behind the manual approval gate and rejects an applied record introduced in the same change as any new migration.

The risk classifier removes SQL comments and string literals before matching, so examples and stored text cannot authorize or accidentally classify a change. Privilege grants/revocations, default privilege or owner changes, `REASSIGN/DROP OWNED`, trigger or row-level-security policy changes, `SET UNLOGGED`, and partition attachment/detachment are contract operations.

Extension, role, user, database, tablespace, foreign server/data wrapper/user mapping changes, `ALTER SYSTEM`, `COPY ... PROGRAM`, session authorization changes, and security labels are also contract operations. The production migration principal must deny cluster-wide administration even when a contract marker is present.

Applied migration records are immutable. An allowlist record must reference an existing contract-phase migration, and no applied record may be added in a change that introduces any migration. This separation prevents an unapplied contract acknowledgement from being bundled with unrelated schema work.

## Multi-instance scale gate

Before a second backend JVM is deployed, replace process-local rate-limit buckets with a shared store and define cross-node invalidation for `GlobalConfig` caches. A multi-instance release must not pass operational review until both consistency mechanisms have tests and rollback procedures.
