# PostgreSQL Backup And Restore

## Policy

- Recovery point objective: 24 hours. Recovery time objective: 2 hours.
- Create one custom-format dump (`pg_dump -Fc`) daily.
- Keep seven daily dumps and four weekly dumps. Store an encrypted copy outside the application server.
- Generate and retain a SHA-256 checksum beside every dump.
- Perform an isolated restore rehearsal every quarter; an untested dump is not considered a backup.

Credentials must come from a permission-restricted `.pgpass` file or the process environment. Never put them in this repository or command history.

## Backup

```bash
umask 077
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
pg_dump --format=custom --no-owner --no-acl --file="noviis-${timestamp}.dump" "$DATABASE_URL"
sha256sum "noviis-${timestamp}.dump" > "noviis-${timestamp}.dump.sha256"
sha256sum --check "noviis-${timestamp}.dump.sha256"
pg_restore --list "noviis-${timestamp}.dump" > /dev/null
```

Encrypt the dump before copying it off-host. Confirm the remote object size and checksum before applying retention deletion.

## Isolated restore rehearsal

1. Create an empty PostgreSQL 16 database that is not reachable by production traffic.
2. Verify the checksum and inspect the archive with `pg_restore --list`.
3. Restore with `pg_restore --clean --if-exists --no-owner --no-acl --dbname "$RESTORE_DATABASE_URL" dump-file`.
4. Start the application against the restored database with Flyway validation enabled.
5. Verify `/actuator/health` and representative board, post, authentication and file metadata reads.
6. Record dump timestamp, restore duration, Flyway result and smoke-test result; then destroy the rehearsal database.

Never run the restore command against a production URL without a separately reviewed incident procedure.
