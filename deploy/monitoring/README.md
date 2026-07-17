# NoviIs local monitoring

Prometheus and Grafana run on the application EC2 instance and listen on loopback only. Do not add their ports or `/actuator` to the EC2 security group or Nginx.

## Preflight

Before enabling either service, verify `MemAvailable` is at least 700 MiB and `/` has at least 3 GiB free. If either check fails, stop and resize the instance or reduce the existing workload.

```bash
awk '/MemAvailable/ { print int($2 / 1024) " MiB" }' /proc/meminfo
df -BM --output=avail / | tail -1
```

The tracked drop-ins intentionally set bounded restart and `OOMPolicy=stop` without guessed `MemoryMax`, `MemoryHigh`, or `CPUQuota` values. Before adding a ceiling, collect at least seven representative production days covering peak traffic, scheduled work, and a normal deployment. Record p99 and peak service memory, choose a maximum above the peak with native/runtime headroom, validate pressure and rollback in staging, then add a reviewed `resource-limit-evidence.env`. `verify-resource-limit-evidence.sh` blocks a tracked ceiling when that evidence is absent or incomplete. Do not commit raw host telemetry or credentials in the evidence file.

Install Prometheus and Grafana from their supported Ubuntu package repositories as native systemd services. The approved host versions are stored in `tool-versions.env`. Install exactly those native package versions and verify `prometheus --version` and `grafana --version` before copying configuration. CI sources the same manifest for Prometheus rule validation and downloads the pinned Grafana architecture, verifies the official SHA-256 before extraction or execution, then checks its reported version and dashboard JSON. Update the version, architecture, checksum, host rollout procedure, and rollback note in one reviewed change.

```text
prometheus/prometheus.yml                         -> /etc/prometheus/prometheus.yml
prometheus/noviis-alerts.yml                      -> /etc/prometheus/noviis-alerts.yml
grafana/provisioning/datasources/noviis.yml       -> /etc/grafana/provisioning/datasources/noviis.yml
grafana/provisioning/dashboards/noviis.yml        -> /etc/grafana/provisioning/dashboards/noviis.yml
grafana/dashboards/noviis-overview.json           -> /var/lib/grafana/dashboards/noviis-overview.json
systemd/prometheus.service.d/override.conf         -> /etc/systemd/system/prometheus.service.d/override.conf
systemd/grafana-server.service.d/override.conf     -> /etc/systemd/system/grafana-server.service.d/override.conf
systemd/prometheus-node-exporter.service.d/override.conf -> /etc/systemd/system/prometheus-node-exporter.service.d/override.conf
rotate-grafana-admin-password.sh                   -> /usr/local/sbin/rotate-noviis-grafana-password
```

Store the Grafana administrator password only on the server in `/etc/noviis/monitoring.env`:

```text
GF_SECURITY_ADMIN_PASSWORD=<strong unique password>
```

Create the file before Grafana's first start, keep it owned by `root:root` with mode `0600`, and use a password of at least 16 characters. The systemd unit fails closed when the file, ownership, permissions, or password length is invalid. Never print the value in workflow output or copy it into the repository.

Changing this environment variable alone does not rotate the administrator password in an already initialized Grafana database. Install the reviewed helper as `root:root` mode `0755`, then invoke it interactively. It requires a regular, non-symlink, root-owned mode `0600` environment file whose current password matches the entered current password. It prepares a replacement in the same directory, calls the loopback User API with root-only `0700`/`0600` temporary files, then atomically updates the environment source of truth. Neither password is placed in shell history or a process argument. An API error leaves the file unchanged. If the atomic file replacement fails, the helper attempts to revert the API password; if that also fails it preserves the new state in a unique root-only `.rotation-recovery.*` file and exits with status 2 for immediate operator recovery.

```bash
sudo install -o root -g root -m 0755 deploy/monitoring/rotate-grafana-admin-password.sh /usr/local/sbin/rotate-noviis-grafana-password
sudo /usr/local/sbin/rotate-noviis-grafana-password
```

Validate and start:

```bash
promtool check config /etc/prometheus/prometheus.yml
promtool check rules /etc/prometheus/noviis-alerts.yml
sudo systemctl daemon-reload
sudo systemctl enable --now prometheus grafana-server
curl -fsS http://127.0.0.1:8081/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:9090/metrics >/dev/null
curl -fsS http://127.0.0.1:3000/api/health
```

Before copying rules to a host, run the repository rule fixtures as well:

```bash
cd deploy/monitoring/prometheus
promtool test rules noviis-alerts.test.yml
```

The fixture is CI-only and does not need to be installed under `/etc/prometheus`.

Open Grafana only through an SSH tunnel: `ssh -L 3000:127.0.0.1:3000 ubuntu@<host>` and browse to `http://127.0.0.1:3000`.

## Alert thresholds and metric semantics

`noviis_scheduler_last_success_timestamp_seconds` advances only after a scheduled method completes successfully. A failed run records the existing error timer but does not overwrite its previous success timestamp. The stale and heartbeat startup grace calculations use only `process_start_time_seconds{job="noviis-backend"}`; Prometheus uptime or restart cannot bypass backend startup grace or produce a backend restart event. A missing timestamp becomes stale only after the backend process has been up longer than that job's threshold.

Prometheus scrapes its own loopback metrics as the `prometheus` job. Critical rules cover self-scrape loss, rule evaluation failures, failed configuration reloads, and TSDB WAL, compaction, checkpoint, or truncation failures. The backend's heartbeat, semantic backlog, JVM heap, and Hikari capacity metrics are also required after a 10-minute process startup grace; disappearance is distinct from a healthy zero value and raises `NoviIsRequiredBackendMetricMissing`.

| Scheduled cadence | Included schedules | Maximum age / startup grace |
| --- | --- | --- |
| Frequent | 25-second, 30-second, and 1-minute jobs | 10 minutes |
| Hourly | hourly cleanup and aggregation jobs | 2 hours 15 minutes |
| Daily | daily cleanup jobs | 30 hours |

Keep `noviis_scheduler_expected_max_age_seconds` entries synchronized when adding, renaming, or rescheduling an `@Scheduled` method. The dashboard compares each last-success age with this configured limit.
The canonical non-Agent list is `scheduled-jobs.txt`; `verify-scheduled-jobs.py` fails CI when source annotations, that manifest, and freshness rules drift. `NoviIsSchedulerPoolBacklogGrowing` fires only when every worker is busy and the scheduled queue continues growing, because a steady queue can contain legitimate future executions.

HTTP latency histograms are enabled in the production profile for `http.server.requests`; without that setting the p95 query has no bucket series. HTTP alerts require at least 20 requests in five minutes, then require either a 5xx ratio above 5 percent or p95 latency above 1 second for 10 minutes. HikariCP saturation fires after active connections exceed 85 percent of the configured maximum for 10 minutes. A change in `process_start_time_seconds` produces an informational restart alert over a 15-minute window; expected deployments should therefore be correlated with deployment history.

Web Push delivery counters distinguish success, failure, timeout, and expired subscriptions. The failure alert requires at least 20 attempts in 15 minutes and a failure-plus-timeout ratio above 10 percent for 10 minutes. Expired-subscription cleanup failures alert independently. The dashboard displays both delivery and cleanup outcomes.

Durable notification delivery exposes pending and dead-letter gauges plus retry outcomes. A sustained pending backlog above 100 is a warning; any dead-lettered notification is critical because automatic retries are exhausted. The dashboard keeps backlog state and 15-minute outcomes together for incident triage.

Async executor metrics expose active workers, queue depth, remaining queue capacity, and rejection outcomes. Durable rejection is critical because durable work was not accepted. Notification caller-runs is a latency warning because the request or scheduler thread inherited the work. Observability drops are warning-level data loss. Sustained zero remaining capacity while work is active raises a separate saturation alert. `required-backend-metrics.txt` is the canonical startup metric contract and `verify-required-backend-metrics.py` prevents its `absent()` checks from drifting.

The documented startup metric contract is generated and reviewed as this exact set:

<!-- required-backend-metrics:start -->
```text
hikaricp_connections_max
jvm_memory_used_bytes
noviis_async_active
noviis_async_queue_depth
noviis_async_queue_remaining
noviis_async_rejected_total
noviis_notification_delivery_jobs_dead_letter
noviis_notification_delivery_jobs_pending
noviis_semantic_jobs_oldest_age_seconds
noviis_semantic_jobs_pending
noviis_sse_heartbeat_gap_seconds
```
<!-- required-backend-metrics:end -->

## Host filesystem exporter enable gate

Install the distribution's signed native `prometheus-node-exporter` package and confirm its reported upstream version is at least `NODE_EXPORTER_MIN_VERSION` in `tool-versions.env`. Distribution package revisions may differ; record the installed package version in the host change ticket and fail the rollout when the upstream version is below the manifest minimum. Install the reviewed systemd override, but do not yet create the Prometheus target file:

```bash
source tool-versions.env
sudo apt-get install prometheus-node-exporter
prometheus-node-exporter --version
test "$(printf '%s\n' "$NODE_EXPORTER_MIN_VERSION" "$(prometheus-node-exporter --version 2>&1 | sed -n 's/.*version \([^ ]*\).*/\1/p' | head -n1)" | sort -V | head -n1)" = "$NODE_EXPORTER_MIN_VERSION"
sudo install -d -o root -g root -m 0755 /etc/systemd/system/prometheus-node-exporter.service.d /etc/prometheus/targets
sudo install -o root -g root -m 0644 systemd/prometheus-node-exporter.service.d/override.conf /etc/systemd/system/prometheus-node-exporter.service.d/override.conf
sudo systemctl daemon-reload
sudo systemctl enable --now prometheus-node-exporter
curl -fsS http://127.0.0.1:9100/metrics >/dev/null
```

Only after the local metrics request succeeds, copy both `prometheus/noviis-host-target.json.example` and the independent `prometheus/noviis-host-contract.json.example` marker atomically, then reload Prometheus. The target carries `monitoring=enabled`; the separate contract self-scrape carries `monitoring=expected`. Before both files exist, host alerts do not fire during staged installation. Once enabled, deletion of the exporter target, exporter loss, missing root-filesystem metrics, free-byte and free-inode ratios, and 24-hour exhaustion prediction are monitored. Disabling host monitoring requires an approved maintenance record and removal of the contract marker before the target.

```bash
sudo install -o root -g root -m 0644 prometheus/noviis-host-target.json.example /etc/prometheus/targets/.noviis-host.json.new
sudo install -o root -g root -m 0644 prometheus/noviis-host-contract.json.example /etc/prometheus/targets/.noviis-host-contract.json.new
sudo mv -T /etc/prometheus/targets/.noviis-host.json.new /etc/prometheus/targets/noviis-host.json
sudo mv -T /etc/prometheus/targets/.noviis-host-contract.json.new /etc/prometheus/targets/noviis-host-contract.json
sudo systemctl reload prometheus
```

Alert rules are evaluated by Prometheus, but no Alertmanager route or external notification receiver is installed. A rule entering the firing state therefore does not send Slack or email notifications.

Before running more than one backend JVM, replace the local Caffeine rate-limit store with a shared implementation and define cross-node invalidation for the `GlobalConfig` cache. Local buckets do not coordinate across instances and process-local configuration caches may diverge. Multi-instance rollout is blocked until both mechanisms have tests and rollback procedures.
