# Deployment AGENTS.md

## Scope And Sources

This directory owns Nginx, host services and permissions, release activation, provenance, freshness, and monitoring. For CI or release-pipeline changes, follow [.github/AGENTS.md](../.github/AGENTS.md), which owns candidate/release separation, immutable artifact identity, deployment ordering, and concurrency.

| Deployment model | Implementation and operating guide |
| --- | --- |
| Current production | Inline activation in [backend](../.github/workflows/deploy-backend.yml) and [frontend](../.github/workflows/deploy-frontend.yml) workflows; [workflow guide](../.github/workflows/README.md) |
| Separately tested hardened profile | `scripts/activate-*-release.sh`, `systemd/`, and `sudoers/`; [hardening guide](../docs/ops/application-service-hardening.md) |

- Current workflows do not invoke the hardened activators. Passing CI does not prove that profile is installed or active.
- Adoption requires coordinated workflows, host provisioning, rollout, fixtures, rollback, and documentation. If a task does not select a model, preserve current workflow behavior and report differences.
- For monitoring behavior, follow [monitoring/AGENTS.md](monitoring/AGENTS.md); for instrumentation, also follow the backend instructions.

## Execution Boundary

- Editing and local validation do not authorize deployment or host mutation. Use fixtures, temporary directories, containers, or disposable local services.
- Do not run `sudo`, `systemctl`, `install`, production SSH/SCP, password rotation, deployment, rollback, production cleanup, or commands targeting `/etc`, `/opt`, `/var`, or a live host without the user's explicit request.
- Never print, copy, synthesize, or commit deployment credentials, SSH keys, Grafana passwords, environment/recovery-file contents, or unredacted diagnostics.

## Host And Activation Contracts

- Preserve SHA-256 verification, provenance, freshness, independent readback, and fail-closed behavior across the runner/host boundary.
- General backend failures may restore the verified previous JAR. For contract releases, failure before attempting the new start may still restore it; after the start attempt, do not automatically restore a potentially schema-incompatible JAR.
- Inline activation preserves bounded staging, verified JAR/directory replacement, and cleanup of run-specific temporary paths.
- Hardened activation preserves the shared activation lock, root-owned active state, generation high-water marks, target-bound rollback authorization, and contract recovery state.
- Keep [release-freshness-paths.txt](release-freshness-paths.txt) aligned with files consumed by current artifacts and inline workflows. Hardened-only changes require ops validation but do not stale inline releases until adoption.
- Keep the hardened backend on its dedicated non-login account with the least-privilege sudoers matrix. Preserve root ownership and restrictive modes for each model's environment, state, authorization, recovery, and diagnostic files.
- Do not broaden systemd writable paths, capabilities, address families, or sandbox exceptions without reviewed operational evidence and a rollback plan.
- Keep management endpoints, Prometheus, node exporter, and Grafana loopback-only; do not expose them or `/actuator` through Nginx, firewalls, or public security groups.
- Nginx changes preserve forwarded-header clearing, security headers, upload limits, SSE/MCP buffering, and release-endpoint caching.
- Preserve LF line endings for executable shell scripts.

## Verification And Documentation

Commands below run from the repository root. These files target Linux: use WSL, compatible Git Bash, or CI containers for Linux checks; do not invent PowerShell equivalents.

| Changed area | Narrow verification |
| --- | --- |
| Shell script | `bash -n <changed-script.sh>`, `shellcheck <changed-script.sh>`, then its matching Bash fixture |
| Hardened service profile | `python deploy/systemd/verify-hardening.py` with Python 3, plus affected systemd/sudoers fixtures |
| Nginx | `bash deploy/scripts/test-nginx-config.sh` with Docker and Bash |
| Workflows/release contracts | Validation required by [.github/AGENTS.md](../.github/AGENTS.md) |
| Monitoring | Validation required by [monitoring/AGENTS.md](monitoring/AGENTS.md) |

The `ops-config-test` job in [ci.yml](../.github/workflows/ci.yml) owns the complete validation list. Report the fixture group actually run. Update the relevant `docs/ops/` guide and workflow README when prerequisites, permissions, ordering, rollback, CI validation, monitoring semantics, secret contracts, or operator actions change.
