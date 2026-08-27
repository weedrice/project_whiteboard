# Deployment AGENTS.md

## Module Overview

This directory contains production-sensitive Nginx, systemd, sudoers, release activation, provenance, freshness, and monitoring configuration. A small edit can affect availability, rollback, host permissions, or secret exposure.

## Deployment Sources Of Truth

- The current production deployment path is `.github/workflows/deploy-backend.yml` and `.github/workflows/deploy-frontend.yml`, documented by `.github/workflows/README.md`. These workflows perform inline single-host activation and do not invoke the tracked `activate-*-release.sh` helpers.
- `deploy/scripts/activate-*-release.sh`, `deploy/systemd/`, and `deploy/sudoers/` define a separately tested hardened host profile documented by `docs/ops/application-service-hardening.md`. Do not claim that profile is installed or active merely because its files pass CI.
- When changing current production behavior, treat the reusable workflows and their README as primary evidence. When changing the hardened profile, treat the scripts, systemd/sudoers fixtures, and hardening document as primary evidence.
- Adopting the hardened profile for production is a coordinated deployment migration. It must update workflows, host provisioning and rollout steps, verification fixtures, rollback procedures, and documentation together; do not switch models as a local refactor.
- If the two models disagree and the task does not explicitly choose one, preserve the current workflow behavior and report the difference.

## Execution Boundary

- Editing and running local validation do not authorize a real deployment or host mutation.
- Do not run `sudo`, `systemctl`, `install`, production SSH/SCP, password rotation, deployment, rollback, production cleanup, or commands targeting `/etc`, `/opt`, `/var`, or a live host unless the user explicitly requests that operation.
- Use fixtures, temporary directories, containers, or disposable local services for verification.
- Never print, copy, synthesize, or commit deployment credentials, SSH keys, Grafana passwords, environment-file contents, recovery files, or unredacted diagnostics.

## Release And Rollback Contracts

- Preserve artifact identity, commit and run metadata, SHA-256 verification, attestations, provenance, freshness checks, and fail-closed behavior.
- Keep candidate creation separate from the privileged release job. Consumers must use the immutable artifact identity produced by the creating job.
- Preserve backend-first activation when backend and frontend are deployed together.
- Preserve deployment locking and concurrency behavior; do not cancel an active production deployment to start a newer one.
- General backend failures may restore the verified previous JAR. Contract-migration startup failures must not automatically roll back to a schema-incompatible JAR.
- For the current workflow model, preserve bounded staging paths, verified JAR/directory replacement, independent readback, and cleanup of run-specific temporary paths.
- For the hardened profile, preserve the shared activation lock, root-owned active state, generation high-water marks, target-bound rollback authorization, and contract recovery state.
- Keep `deploy/release-freshness-paths.txt` synchronized with files actually consumed by the current production artifact and inline deployment workflows. Keep hardened-profile activation, provenance, sudoers, and systemd changes under ops validation, but do not make them stale an inline release until production adopts that profile.

## Host Security

- For the hardened profile, keep the backend process on the dedicated non-login account and preserve the least-privilege sudoers command matrix.
- Preserve root ownership and restrictive modes wherever the selected deployment model uses environment, state, authorization, recovery, and diagnostic files.
- Do not broaden systemd writable paths, capabilities, address families, or sandbox exceptions without reviewed operational evidence and a rollback plan.
- Do not expose the backend management port, Prometheus, node exporter, Grafana, or `/actuator` through Nginx, the host firewall, or a public security group.
- Preserve forwarded-header clearing, security headers, upload limits, SSE/MCP buffering rules, and release endpoint cache behavior when editing Nginx.
- Keep shell scripts and service/configuration files UTF-8. Preserve LF line endings for executable shell scripts.

## Monitoring Contracts

- Before editing under `monitoring/`, read and follow `monitoring/AGENTS.md` and its linked operational README.
- Keep application instrumentation changes coordinated with the backend module instructions and focused backend tests.

## Verification

These files target Linux. On Windows, run Bash, ShellCheck, systemd, sudoers, and Nginx checks through WSL, Git Bash where compatible, or the same containers used by CI. Do not rewrite Linux commands as unverified PowerShell equivalents.

For a changed shell script, run the narrowest syntax, ShellCheck, and matching fixture first:

```bash
bash -n <changed-script.sh>
shellcheck <changed-script.sh>
bash <matching-test-script.sh>
```

With an installed Python 3 interpreter, useful focused checks include:

```powershell
python deploy/systemd/verify-hardening.py
```

When Docker and Bash are available, validate Nginx with:

```bash
bash deploy/scripts/test-nginx-config.sh
```

Use the `ops-config-test` job in `.github/workflows/ci.yml` as the authoritative complete validation list. Run the affected fixture group rather than claiming the full ops suite from a partial check.

## Documentation

Update the relevant `docs/ops/` guide and `.github/workflows/README.md` whenever a change alters host prerequisites, permissions, deployment ordering, rollback behavior, CI validation, monitoring semantics, secrets, or operator actions.
