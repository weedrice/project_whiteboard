# Application service hardening

The production Spring Boot process must not run as the interactive SSH deployment account. Provision a dedicated non-login account before installing `deploy/systemd/app.service`:

```bash
sudo addgroup --system noviis-app
sudo adduser --system --ingroup noviis-app --no-create-home --shell /usr/sbin/nologin noviis-app
sudo adduser --disabled-password --gecos '' noviis-deploy
sudo install -d -o noviis-app -g noviis-app -m 0750 /opt/app/logs /opt/app/uploads
sudo install -d -o root -g root -m 0755 /opt/app/backend /opt/app/backend/releases /var/www/releases/frontend
sudo install -d -o root -g root -m 0700 /var/lib/noviis/deployment-diagnostics
sudo install -d -o noviis-deploy -g noviis-deploy -m 0750 /opt/app/backend/incoming /var/www/incoming /var/www/incoming/frontend
sudo install -o root -g root -m 0755 deploy/scripts/activate-backend-release.sh /usr/local/sbin/activate-noviis-backend
sudo install -o root -g root -m 0755 deploy/scripts/verify-active-backend-release.sh /usr/local/sbin/verify-noviis-backend
sudo install -o root -g root -m 0755 deploy/scripts/verify-backend-start-state.sh /usr/local/sbin/verify-noviis-backend-start-state
sudo install -o root -g root -m 0755 deploy/scripts/activate-frontend-release.sh /usr/local/sbin/activate-noviis-frontend
sudo install -o root -g root -m 0755 deploy/scripts/verify-active-frontend-release.sh /usr/local/sbin/verify-noviis-frontend
sudo install -o root -g root -m 0755 deploy/scripts/record-cleanup-debt.sh /usr/local/sbin/record-noviis-cleanup-debt
sudo install -o root -g root -m 0755 deploy/scripts/verify-release-provenance.sh /usr/local/sbin/verify-noviis-release
sudo install -o root -g root -m 0644 deploy/systemd/app.service /etc/systemd/system/app.service
sudo install -o root -g root -m 0440 deploy/sudoers/noviis-deploy /etc/sudoers.d/noviis-deploy
sudo visudo -cf /etc/sudoers.d/noviis-deploy
sudo touch /etc/noviis/app.env
sudo chown root:root /etc/noviis/app.env
sudo chmod 0600 /etc/noviis/app.env
sudo gh attestation trusted-root | sudo tee /etc/noviis/github-attestation-trusted-root.jsonl >/dev/null
sudo chown root:root /etc/noviis/github-attestation-trusted-root.jsonl
sudo chmod 0644 /etc/noviis/github-attestation-trusted-root.jsonl
```

The first deployment after installing the active-state verifier must run while the existing backend management health and build-info endpoints are readable. Activation records `/opt/app/backend/app.jar.active.state` as root-owned mode `0600` metadata containing commit, payload and envelope digests, run identity, API contract revision, and `pending|stable` phase. A pending state carries a short-lived nonce lease and is accepted only while the activation process still owns the global deployment lock; an orphaned pending JAR therefore cannot be restarted after a crash. The stability soak also requires the service PID, restart count, and monotonic start timestamp to remain unchanged.

The backend and frontend each retain a separate root-owned mode `0600` generation high-water file under `/var/lib/noviis/deployment-state/`. Rollback changes the active release but never lowers this high-water mark. A non-newer generation requires a one-use root-owned mode `0600` break-glass file with exactly these fields: `repository`, `target_commit`, `target_run_id`, `target_run_number`, `target_run_attempt`, `release_envelope_sha256`, `authorization_id`, `issued_at`, `expires_at`, and `reason`. The authorization must bind the exact verified artifact, be valid for no more than one hour, and is consumed before switching the release. A successful frontend activation additionally creates a root-owned, target-bound automatic rollback lease valid for at most 30 minutes. It can be consumed only while that exact release and run identity remain active; after expiry, or for any historical release, the same break-glass authorization is mandatory. Never grant the deployment account permission to create or replace authorization, lease, or high-water files.

If a signed contract release fails after the new backend start is attempted, the activator leaves the service stopped and writes `/var/lib/noviis/deployment-state/backend-contract-recovery.state` as root-owned mode `0600`. The orphaned pending state blocks systemd restart and the recovery state blocks another activation until an operator verifies the database schema and either restores the bound snapshot or selects a contract-compatible artifact. Never remove this state merely to retry the previous JAR.

Populate `/etc/noviis/app.env` through the host secret-management procedure. Keep it a regular, non-symlink file owned by `root:root` with mode `0600`. The unit pins `SPRING_PROFILES_ACTIVE=prod`, and both the unit and activation script fail closed on an invalid environment file.

The workflow connects only as `noviis-deploy`. Uploads land below the deploy-owned `incoming` directories. Each root-owned activation entrypoint first copies regular files into a new root-owned staging directory, then invokes the root-owned provenance verifier before extracting, promoting, or changing service state. The verifier accepts only the release-type allowlist, verifies the attested `RELEASE_ENVELOPE` against the root-owned trusted roots, repository, signer workflow, `main` ref, and expected commit SHA, and then recomputes every payload, metadata, SBOM, and checksum digest recorded by that envelope. The deploy account therefore cannot authorize a release by replacing its own checksum, metadata, or SBOM.

Backend failures after service state has started changing write detailed unit, journal, and application-log diagnostics below `/var/lib/noviis/deployment-diagnostics`; preflight failures do not create a bundle. Keep that directory root-owned mode `0700`; each bundle is mode `0600`. The activator limits each file to 1 MiB and retains at most 20 files for 14 days by default. The GitHub Actions stream receives only the bundle filename and failure phase, never the bundle contents. Review and remove bundles on the host according to the incident retention policy; do not upload an unredacted bundle to an issue or CI artifact.

Before switching a backend JAR, the activator records the previous JAR digest and the exact commit returned by the local management endpoint. A rollback succeeds only when the saved JAR still matches that digest and the restarted service reports the recorded commit. Frontend activation records the previous release target and commit in same-filesystem atomically replaced state files; rollback must restore that target and make both internal and public release endpoints return the recorded commit.

The passwordless sudo surface permits only the fixed backend/frontend incoming and rollback path forms. A frontend rollback path alone is not authority: the activator also requires the current web-root and stable active state to identify that release, then consumes its short-lived automatic lease or a root break-glass authorization. The workflow compares the installed verifier, activation script, and systemd unit hashes with the expected commit and fails closed on drift. Reinstall reviewed entrypoints as root whenever those tracked files change. Refresh the trusted-root file only through a reviewed host maintenance step and validate a known release afterward. The `noviis-app` runtime account has no sudo access and cannot write either incoming or active release roots.

Both activation entrypoints acquire the same non-blocking `/run/lock/noviis-deploy.lock` before reading or changing release state. A concurrent workflow, direct sudo invocation, backend activation, frontend activation, or rollback exits with status 75 without changing the active JAR or symlink. Do not configure separate lock paths on the production host; the override exists only for isolated fixtures.

The workflow invokes the cleanup helper in `reconcile incoming_release` mode after each removal attempt. The root-owned helper derives debt from the current filesystem instead of trusting a caller-provided set/clear flag, exports the orphan count and oldest age, and returns status 2 while orphaned incoming releases remain. Treat that status as cleanup debt after activation, not as evidence that the already verified release should be rolled back. The deployment account must not receive sudo permission for direct `set` or `clear` operations on incoming-release debt.

After installing the unit, verify it before enabling:

```bash
sudo systemd-analyze verify /etc/systemd/system/app.service
sudo systemd-analyze security app.service
sudo systemctl daemon-reload
sudo systemctl enable --now app.service
curl -fsS http://127.0.0.1:8081/actuator/health
```

The sandbox permits writes only to `/opt/app/logs` and `/opt/app/uploads`. Any new local persistent path must be reviewed and added explicitly instead of weakening `ProtectSystem`.

## Resource and restart policy gate

The unit limits crash loops to five starts per five minutes and explicitly uses `OOMPolicy=stop`; `Restart=on-failure` then handles a failed main process through the same bounded restart path. Do not add `MemoryHigh` or `MemoryMax` from workstation estimates. Before enabling a memory limit, collect at least seven representative production days including peak traffic, scheduled maintenance, and one normal deployment. Record `systemctl show app.service -p MemoryCurrent -p MemoryPeak`, JVM heap/non-heap metrics, direct-buffer usage, and host `MemAvailable` in the operations ticket.

Choose `MemoryHigh` above the measured p99 total process usage and `MemoryMax` above the measured peak with explicit native-memory and deployment headroom. Validate the proposed values in a temporary drop-in, force a heap-pressure test in staging, confirm the management health endpoint remains responsive, and record the rollback command. A missing measurement record blocks the limit change; `verify-resource-limit-evidence.sh` checks `app.service` as well as the Prometheus and Grafana drop-ins and requires configured byte values to match the reviewed evidence. The tracked unit intentionally contains no guessed memory cap.

After any restart-policy or resource change, run `systemd-analyze verify`, inspect `systemd-analyze security app.service`, and confirm that repeated startup failures reach `start-limit-hit` without affecting the active release rollback procedure.
