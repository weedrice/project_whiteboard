# Database migration policy

NoviIs deploys a new JAR after Spring Boot/Flyway has applied pending migrations. A release must leave the database usable by both the new application and the previous JAR throughout the rollback window.

## Required sequence

1. **Expand**: add nullable columns, additive tables, indexes, and defaults compatible with the previous application.
2. **Backfill**: migrate data in restartable batches without holding a table-wide transaction for the entire data set.
3. **Application switch**: deploy code that reads the expanded shape and stops depending on the old shape.
4. **Contract**: after the rollback window closes, remove or rename old schema in a separately reviewed release.

Do not drop or rename an in-use table or column, narrow a type, add `SET NOT NULL` without a completed backfill, or remove a default/check/enum value still required by the previous application in the same release as the application switch.

Versioned `V*.sql` files are immutable after merge. Fix an applied migration with a new version instead of editing or deleting the original file.

## CI contract marker

Every new migration must contain exactly one phase marker: `-- noviis:migration-phase expand`, `backfill`, or `contract`. `backend/scripts/check-migration-compatibility.sh` rejects modified or deleted versioned migrations and detects common destructive statements in new migrations. A deliberately scheduled contract migration must include both lines and point to a reviewed, tracked document under `docs/design-notes/`:

```sql
-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/example-contract.md
```

`DROP INDEX` is permitted because it does not change the schema consumed by an application binary. It still requires query-plan and PostgreSQL smoke verification. Before applying any contract migration, take and verify a backup according to `docs/ops/postgres-backup-restore.md`.

Contract migrations are never eligible for an automatic main deployment. Run the integrated CI manually from `main`, explicitly approve the contract phase and confirm the pre-change snapshot, then verify the production deployment. Only after that deployment succeeds may its filename and deployment run URL be appended to `docs/ops/applied-contract-migrations.txt`. The compatibility check rejects an allowlist entry introduced in the same change as its migration, so an un-applied contract remains a deployment blocker across later commits.

## Multi-instance scale gate

Before a second backend JVM is deployed, replace process-local rate-limit buckets with a shared store and define cross-node invalidation for `GlobalConfig` caches. A multi-instance release must not pass operational review until both consistency mechanisms have tests and rollback procedures.
