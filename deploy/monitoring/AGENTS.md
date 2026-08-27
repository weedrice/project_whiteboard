# Monitoring AGENTS.md

## Scope And Source Of Truth

This directory owns the local Prometheus, Grafana, exporter, watchdog, alert, dashboard, metric-manifest, resource-evidence, and administrator-password rotation contracts.

- Read `README.md` before changing monitoring behavior. It documents current installation, metric semantics, alert delivery limits, enable gates, operator actions, and recovery expectations.
- Editing tracked files and running fixtures do not authorize installing packages, rotating a password, reloading a service, opening a port, or changing a live host.
- Keep Prometheus, Grafana, node exporter, and backend management endpoints loopback-only. Do not expose them through Nginx, firewall, or public security-group changes.

## Coordinated Contracts

- Keep Prometheus rules, rule fixtures, Grafana panels, required metric manifests, README contract blocks, and backend instrumentation synchronized.
- Update `scheduled-jobs.txt`, scheduler freshness rules, README cadence documentation, and `verify-scheduled-jobs.py` when adding, renaming, or rescheduling a non-Agent `@Scheduled` method.
- Update `required-backend-metrics.txt`, matching `absent()` checks, dashboard panels, README generated block, and `verify-required-backend-metrics.py` together when required metrics change.
- Treat `tool-versions.env` as the pinned monitoring tool contract. Change version, architecture, checksum, CI download, host rollout, and rollback guidance together.
- Preserve the distinction between rule evaluation and external delivery: no Alertmanager receiver is currently configured, so a firing rule alone does not prove Slack or email delivery.
- Do not add guessed CPU or memory ceilings. A tracked limit requires the representative evidence and staging validation defined in `README.md` and must pass the resource-evidence fixture.
- Never place Grafana passwords, recovery-file contents, raw host telemetry, or unredacted diagnostics in the repository, command arguments, logs, fixtures, or chat.

## Validation

From the repository root on Windows, run the applicable Python verifiers:

```powershell
python deploy/monitoring/verify-grafana-dashboard.py
python deploy/monitoring/verify-scheduled-jobs.py
python deploy/monitoring/verify-required-backend-metrics.py
```

Run changed shell-script fixtures through Bash, Git Bash, WSL, or the same Linux container used by CI:

```bash
bash deploy/monitoring/tests/<matching-test>.sh
```

Prometheus rule and configuration changes also require the pinned `promtool` checks from `ops-config-test` in `.github/workflows/ci.yml`. Do not substitute YAML parsing for PromQL evaluation.
