# Monitoring AGENTS.md

## Scope And Sources

This directory owns Prometheus, Grafana, exporters, watchdogs, alerts, dashboards, metric manifests, resource evidence, and administrator-password rotation. Read [README.md](README.md) for installation, metric semantics, enable gates, operator actions, and recovery. The parent deployment instructions govern live-host authorization and loopback-only exposure.

## Coordinated Contracts

- Keep rules/fixtures, panels, required metric manifests, README contract blocks, and backend instrumentation synchronized; follow backend instructions for instrumentation changes.
- Adding, renaming, or rescheduling a non-Agent `@Scheduled` method requires coordinated [scheduled-jobs.txt](scheduled-jobs.txt), freshness rules, README cadence, and [verifier](verify-scheduled-jobs.py) updates.
- Required metric changes coordinate [required-backend-metrics.txt](required-backend-metrics.txt), matching `absent()` checks, panels, README generated block, and [verifier](verify-required-backend-metrics.py).
- [tool-versions.env](tool-versions.env) owns pinned monitoring versions. Change version, architecture, checksum, CI download, rollout, and rollback guidance together.
- A firing rule proves evaluation only: no Alertmanager receiver is configured, so it does not establish Slack/email delivery.
- CPU/memory ceilings require representative evidence and staging validation from README plus the resource-evidence fixture; never guess limits.
- Keep Grafana passwords, recovery contents, raw telemetry, and unredacted diagnostics out of the repository, arguments, logs, fixtures, and chat.

## Verification

From the repository root on Windows with Python 3, run applicable checks:

```powershell
python deploy/monitoring/verify-grafana-dashboard.py
python deploy/monitoring/verify-scheduled-jobs.py
python deploy/monitoring/verify-required-backend-metrics.py
```

Run matching fixtures under `deploy/monitoring/tests/` through Bash, compatible Git Bash, WSL, or CI containers. Prometheus rule/configuration changes also require pinned `promtool` checks from `ops-config-test` in [ci.yml](../../.github/workflows/ci.yml); YAML parsing does not evaluate PromQL.
