# NoviIs local monitoring

This guide defines installation of Prometheus, Grafana, and Alertmanager on the application EC2 instance with loopback-only listeners. Tracked configuration and CI validation do not establish that the services are installed on a live host. Do not add their ports or `/actuator` to the EC2 security group or Nginx.

## Preflight

Before enabling either service, verify `MemAvailable` is at least 700 MiB and `/` has at least 3 GiB free. If either check fails, stop and resize the instance or reduce the existing workload.

```bash
awk '/MemAvailable/ { print int($2 / 1024) " MiB" }' /proc/meminfo
df -BM --output=avail / | tail -1
```

The tracked application unit and monitoring drop-ins intentionally set bounded restart and `OOMPolicy=stop` without guessed `MemoryMax`, `MemoryHigh`, or `CPUQuota` values. Before adding a ceiling, collect at least seven representative production days covering peak traffic, scheduled work, and a normal deployment. Record p99 and peak service memory, choose a maximum above the peak with native/runtime headroom, validate pressure and rollback in staging, then add a reviewed `resource-limit-evidence.env`. `verify-resource-limit-evidence.sh` checks `app.service`, Prometheus, and Grafana independently and blocks a tracked ceiling when matching evidence is absent, incomplete, or differs from the configured byte value. Do not commit raw host telemetry or credentials in the evidence file.

Install Prometheus and Grafana as native systemd services using either supported signed packages or official upstream archives whose architecture, version, and published SHA-256 have been verified before extraction or execution. The approved host versions are stored in `tool-versions.env`. A distribution package may lag the approved upstream release; in that case install the verified archive under a root-owned versioned directory and select its absolute binary path through a reviewed systemd drop-in. Verify the exact active binary version before copying configuration; a PATH lookup alone can still report the older packaged binary. CI sources the same manifest for Prometheus rule validation and downloads the pinned Grafana architecture, verifies the official SHA-256 before extraction or execution, then checks its reported version and dashboard JSON. Update the version, architecture, checksum, host rollout procedure, and rollback note in one reviewed change.

## Security update and recovery

The current approved versions are **Prometheus 3.16.0** and **Grafana OSS 13.1.10**. Prometheus 3.5 reached end of life on 2026-07-31; updating only to an older 3.5 security patch does not restore supported security maintenance. Keep the version and immutable validation-image digest in `tool-versions.env` together. Grafana remains on the existing 13.1 minor line, with the official Linux AMD64 archive SHA-256 pinned for CI verification. Prometheus 3.16.0 is the current regular release, not an LTS: it includes newer gRPC, compression and OpenTelemetry fixes absent from the current 3.13.5 LTS build. Regular minor releases generally receive fixes for a six-week cycle; review each new stable/security release and upgrade promptly with the same checks, instead of assuming the 3.13 LTS support end date applies. The official 3.16.0 image still embeds Go 1.27.3 (GO-2026-6859; fixed in 1.27.4) and BusyBox findings without a published scanner fix. Keep these open for a rebuilt upstream release; loopback binding and disabling optional receivers/tracing reduce exposure but do not remove vulnerable components. CI runs this image only in disposable validation containers; native host packages must be scanned separately.

Sources: [Prometheus release cycle](https://prometheus.io/docs/introduction/release-cycle/), [Prometheus 3.16.0 release](https://github.com/prometheus/prometheus/releases/tag/v3.16.0), [Grafana 13.1.10 release](https://github.com/grafana/grafana/releases/tag/v13.1.10), and [Grafana archive checksum](https://dl.grafana.com/oss/release/grafana-13.1.10.linux-amd64.tar.gz.sha256).

These repository changes prepare an upgrade; they do not install or restart services on a host. Perform the following only in an explicitly authorized host maintenance window:

1. Record the installed binary/package versions and service arguments, verify the resource preflight, and retain the current configuration, provisioning files, data directories and package/binary artifacts in restricted backups. Stop each service before taking its final consistent backup; for Grafana include its database (or a database-native consistent backup), plugins and configuration. Do not publish backup contents or credentials.
2. Rehearse the upgrade on an isolated copy of the data with the pinned binaries. Run `promtool check config`, `promtool check rules` and `promtool test rules` against the repository fixtures using Prometheus 3.16.0. Validate the Grafana archive using `verify-grafana-archive.sh` before extraction, and check `grafana --version`. Ensure the chosen package or checksum-verified archive actually contains the approved upstream version; a successful package-manager install alone is insufficient. For archive installations, confirm `systemctl show -p ExecStart` selects the reviewed versioned path after daemon reload.
3. Upgrade one service at a time while preserving its service account, paths, ownership, loopback listeners, textfile collector and systemd drop-ins. Keep Prometheus storage settings and feature flags unchanged during this security update. In the existing host layout, retain `/var/lib/prometheus/metrics2`, seven-day retention and the 1 GB retention limit; changing the executable path must not silently create a new empty TSDB. Do not expose the remote-read API or Grafana through the public reverse proxy.
4. Verify the binary versions, Prometheus `/-/ready`, Grafana `/api/health`, backend scrape targets, rule evaluations, provisioned dashboard and authenticated Prometheus data-source queries. If the parsed Prometheus active configuration changes, review the change and refresh the root-owned expected configuration digest used by the watchdog; never overwrite it automatically from an unexplained mismatch.
5. If validation fails, stop the affected service. Restore a matching pre-upgrade data/configuration backup with its matching binary/package; do not run an older Grafana binary against an already migrated database or assume Prometheus TSDB/WAL downgrade compatibility. Restore the consistent pre-upgrade Prometheus data backup to the same approved TSDB path together with its matching binary and configuration; changing only the executable while reusing a newer-written WAL is not a verified rollback. A rollback to the vulnerable 3.5.0/13.1.0 binaries is emergency recovery only: retain loopback access, restrict the SSH tunnel, record the risk and schedule the corrected upgrade. If a consistent, compatible restore cannot be verified, keep the affected monitoring service stopped rather than altering or deleting its data.

The archive deployment recorded on 2026-11-18 uses root-owned version directories. Prometheus selects its executable through the host-local `zz-noviis-version.conf` drop-in; node exporter uses `99-noviis-loopback.conf`. systemd orders drop-ins lexicographically: an existing `override.conf` sorts after `99-noviis-version.conf` and can replace its `ExecStart`. Check all effective drop-ins, the running process executable, and `/api/v1/status/buildinfo` after activation; the installed archive or `promtool` version alone does not verify the running Prometheus version. These are recorded deployed artifacts, not permission to skip the manifest/version review on a future upgrade:

| Component | Active archive executable | Official Linux AMD64 archive SHA-256 |
| --- | --- | --- |
| Prometheus 3.16.0 | `/opt/noviis-monitoring/prometheus-3.16.0/prometheus` | `68727009472a2c02e1ff7f062fd5b3e637285bffc8fc2d340c76fb449d6e0f34` |
| Node exporter 1.12.1 | `/opt/noviis-monitoring/node_exporter-1.12.1/node_exporter` | `b51d8a76aa2a9156a55d501aca6276fae09e262259a5e4e831d2c2222f084e63` |

Prometheus was validated against an isolated copy of the existing data and a consistent backup was retained before activation. Keep the matching original binary, configuration, storage flags and backup for recovery; do not delete or reinitialize the live TSDB during an upgrade or rollback. The exporter override must retain the textfile collector flags and loopback binding.

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

Deployment follow-up debt is exported through the node-exporter textfile collector as `noviis_deployment_cleanup_debt`. Install `record-cleanup-debt.sh` as `/usr/local/sbin/record-noviis-cleanup-debt` and use the checked-in exporter override. Under the separately provisioned hardened host profile, successful cleanup writes zero; failed retention, incoming-release cleanup, or status diagnostics write one and alert after 15 minutes. The current inline production workflows do not invoke this writer. The six `noviis_deployment_cleanup_expected` recording rules belong to `prometheus/optional/noviis-deployment-profile.yml`, not the baseline rules. The default configuration accepts `/etc/prometheus/optional/*.yml`, but that directory must contain no deployment-profile file for the inline model: otherwise missing unprovisioned writers produce false alerts. Install the optional file only after explicitly adopting the hardened deployment profile, provisioning every expected writer/label, and verifying their initial samples. Validate and reload Prometheus, refresh its approved watchdog configuration digest, and verify the active rule groups. When retiring that profile, remove its expectation file under the approved maintenance change before removing writers; do not disable unrelated host monitoring. Baseline inline deployments load 84 rules; opting into these six expectations brings the total to 90. CI validates both the inline no-writer case and the enabled-profile missing-writer alert. The rules retain the legacy `seo_submission` debt label, but current production workflows and the SEO monitor only verify public SEO endpoints and perform no search-engine submission. Do not treat that label as evidence of an active submission integration. Incoming release reconciliation also exports `noviis_deployment_incoming_orphans` and `noviis_deployment_incoming_orphan_oldest_age_seconds`; any orphan persisting for 15 minutes is a warning, and an orphan older than one hour is escalated to critical. Re-run the component-specific `reconcile incoming_release` command after removing only verified, unreferenced release directories.

`noviis_scheduler_last_success_timestamp_seconds` advances only after a scheduled method completes successfully. A failed run records the existing error timer but does not overwrite its previous success timestamp. The stale and heartbeat startup grace calculations use only `process_start_time_seconds{job="noviis-backend"}`; Prometheus uptime or restart cannot bypass backend startup grace or produce a backend restart event. A missing timestamp becomes stale only after the backend process has been up longer than that job's threshold.

Prometheus scrapes its own loopback metrics as the `prometheus` job. Critical rules cover self-scrape loss, rule evaluation failures, failed configuration reloads, and TSDB WAL, compaction, checkpoint, or truncation failures. The backend's heartbeat, semantic backlog, JVM heap, and Hikari capacity metrics are also required after a 10-minute process startup grace; disappearance is distinct from a healthy zero value and raises `NoviIsRequiredBackendMetricMissing`.

| Scheduled cadence | Included schedules | Maximum age / startup grace |
| --- | --- | --- |
| Frequent | 5-second, 10-second, 25-second, 30-second, and 1-minute jobs | 10 minutes |
| Hourly | hourly cleanup and aggregation jobs | 2 hours 15 minutes |
| Daily | daily cleanup jobs | 30 hours |

Keep `noviis_scheduler_expected_max_age_seconds` entries synchronized when adding, renaming, or rescheduling an `@Scheduled` method. The dashboard compares each last-success age with this configured limit.
The canonical non-Agent list is `scheduled-jobs.txt`; `verify-scheduled-jobs.py` fails CI when source annotations, that manifest, and freshness rules drift. `NoviIsSchedulerPoolBacklogGrowing` fires only when every worker is busy and the scheduled queue continues growing, because a steady queue can contain legitimate future executions.

HTTP latency histograms are enabled in the production profile for `http.server.requests`; without that setting the p95 query has no bucket series. HTTP alerts require at least 20 requests in five minutes, then require either a 5xx ratio above 5 percent or p95 latency above 1 second for 10 minutes. HikariCP saturation fires after active connections exceed 85 percent of the configured maximum for 10 minutes. A change in `process_start_time_seconds` produces an informational restart alert over a 15-minute window; expected deployments should therefore be correlated with deployment history.

Web Push delivery counters distinguish success, timeout, retryable failure, permanent failure, and expired subscriptions. The failure alert requires at least 20 attempts in 15 minutes and a combined `timeout`, `retryable_failure`, and `permanent_failure` ratio above 10 percent for 10 minutes. Expired-subscription cleanup failures alert independently. The dashboard displays both delivery and cleanup outcomes.

Durable notification delivery exposes pending and dead-letter gauges, oldest-due and oldest-lease ages, plus retry outcomes. A sustained pending backlog above 100 or oldest due age above five minutes is a warning; any dead-lettered notification or lease older than two minutes is critical because automatic recovery is no longer keeping pace. The dashboard keeps backlog state, ages, and 15-minute outcomes together for incident triage.

Feed generation exposes pending, processing, failed, and oldest-pending gauges plus a result-labelled processed counter. Pending work above 100 or an oldest pending job above five minutes is a warning. A persisted `FAILED` row or a new `dead_letter` result is critical; `redrive` is an operator action and is shown on the dashboard for correlation rather than alerted by itself. First inspect the failed row and its last error, correct the underlying cause, and then use the SUPER_ADMIN redrive endpoint once; repeated redrives without progress require incident escalation.

Semantic pending age is measured from the current request `queued_at`, retained across retries and reset on re-enqueue/redrive; legacy rows fall back to `updated_at`/`created_at`. Pending counts include jobs waiting for their persisted `next_attempt_at`, so provider cooldown can contribute to backlog age.

Individual semantic indexing jobs expose `noviis_semantic_jobs_failed`, including permanent provider/input failures and retry-exhausted `FAILED` rows that no longer contribute to the pending backlog. Any failed row persisting for two minutes raises the critical `NoviIsSemanticIndexingFailed` alert; a healthy zero gauge remains required even when semantic indexing is disabled. Inspect the failed job and its last error, correct the provider/configuration or content problem, then enqueue the affected content again through the supported backfill/reindex flow. Confirm the failed gauge clears and the embedding is refreshed; an empty pending queue alone does not prove indexing succeeded.

Semantic reindexing exposes failed jobs and result-labelled processed outcomes in addition to its active cursor gauges. A persisted failed job or a new dead-letter result is critical. Redrive outcomes remain visible on the dashboard so an operator can confirm that manual recovery is making progress.

Async executor metrics expose active workers, queue depth, remaining queue capacity, and rejection outcomes. Durable rejection is critical because durable work was not accepted. Notification rejection is a warning: durable notification jobs remain available to the scheduler, while best-effort push dispatch may be skipped without moving external I/O onto the caller thread. Observability drops are warning-level data loss. Sustained zero remaining capacity while work is active raises a separate saturation alert. `NoviIsAsyncExecutorGaugeNaN` separately detects any of the three capacity gauges returning NaN for two minutes, for all four executors including `stream`; series presence alone does not prove a usable gauge. It retains the executor and metric name so simultaneous failures cannot mask one another. A healthy zero does not fire this alert. `required-backend-metrics.txt` is the canonical startup metric contract and `verify-required-backend-metrics.py` prevents its `absent()` checks from drifting.

Image variant reconciliation is separate from stale-variant deletion: `noviis_file_variant_reconciliation_total{outcome="success|failure|deferred"}` counts generation outcomes, while `noviis_file_variant_reconciliation_backoff_files` reports files whose retry deadline is still in the future when the worker last refreshed the gauge. Due-but-not-yet-revisited files are excluded, so this is not the total unresolved file count. Any new failure in one hour raises `NoviIsFileVariantReconciliationFailure`; a nonzero retry gauge for five minutes raises `NoviIsFileVariantReconciliationBackoff`. The failure-window alert can remain firing for up to one hour after recovery. Retry state is bounded and in memory, resets on restart, and is not a durable database backlog or dead-letter count. Inspect the fixed error code in the reconciliation warning, repair the input/storage or validation cause, and confirm successful generation and clearing retry state; stale-cleanup zero gauges do not establish reconciliation success. Never remove user files or mark them reconciled merely to clear an alert.

Shop sale-status streaming keeps local SSE delivery available when the optional Redis relay is disabled or unavailable. Redis is therefore excluded from the aggregate backend health status. Relay publish or receive failures raise `NoviIsShopStreamRelayFailure`, while the Grafana SSE panel shows relay failures and per-client shop SSE delivery failures together. The frontend polling fallback remains the final convergence path during a relay incident.

Generic Micrometer series such as JVM, HTTP server, and HikariCP metrics are always scoped to `job="noviis-backend"` in alert rules. A separately scraped Java process must neither suppress a missing-backend-metric alert nor contribute traffic, heap, or pool utilization to a NoviIs backend alert. Prometheus rule fixtures include an unrelated Java target to preserve this isolation.

The documented startup metric contract is generated and reviewed as this exact set:

<!-- required-backend-metrics:start -->
```text
hikaricp_connections_max
jvm_memory_used_bytes
noviis_async_active
noviis_async_queue_depth
noviis_async_queue_remaining
noviis_async_rejected_total
noviis_feed_generation_jobs_failed
noviis_feed_generation_jobs_oldest_pending_age_seconds
noviis_feed_generation_jobs_pending
noviis_feed_generation_jobs_processed_total
noviis_feed_generation_jobs_processing
noviis_notification_delivery_jobs_dead_letter
noviis_notification_delivery_jobs_oldest_due_age_seconds
noviis_notification_delivery_jobs_oldest_lease_age_seconds
noviis_notification_delivery_jobs_pending
noviis_notification_keyword_fanout_jobs_dead_letter
noviis_notification_keyword_fanout_jobs_oldest_due_age_seconds
noviis_notification_keyword_fanout_jobs_pending
noviis_notification_keyword_fanout_jobs_total
noviis_file_variant_cleanup_dead_letter
noviis_file_variant_cleanup_oldest_due_age_seconds
noviis_file_variant_cleanup_total
noviis_file_variant_reconciliation_total
noviis_file_variant_reconciliation_backoff_files
noviis_semantic_reindex_jobs_oldest_active_age_seconds
noviis_semantic_reindex_jobs_failed
noviis_semantic_reindex_jobs_pending
noviis_semantic_reindex_jobs_processed_total
noviis_semantic_reindex_jobs_processing
noviis_semantic_jobs_failed
noviis_semantic_jobs_oldest_age_seconds
noviis_semantic_jobs_pending
noviis_sse_heartbeat_gap_seconds
noviis_webpush_job_backlog
noviis_webpush_job_oldest_age_seconds
noviis_webpush_job_outcome_total
```
<!-- required-backend-metrics:end -->

## Host filesystem exporter enable gate

Install either the distribution's signed `prometheus-node-exporter` package or a reviewed official Linux AMD64 archive verified against its published SHA-256, and confirm the active executable's upstream version is at least `NODE_EXPORTER_MIN_VERSION` in `tool-versions.env`. Distribution package revisions may differ; record the installed package or archive version and checksum in the host change ticket, and fail the rollout when the upstream version is below the manifest minimum. For an archive, preserve the existing service account and textfile collector settings, use its root-owned versioned absolute path in a host-local override, and bind only `127.0.0.1:9100`. The package-path example below applies only when the packaged binary meets that minimum; do not overwrite a validated archive override with this older package path. Install the reviewed override, but do not yet create the Prometheus target file:

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

The tracked Prometheus configuration routes alerts to loopback Alertmanager at `127.0.0.1:9093`. The secret-free email example must be rendered and installed locally as described below. A firing rule, checked-in template, or successful API submission proves neither SMTP delivery nor inbox receipt; verify actual notification results before claiming email is working.

Before enabling the watchdog, install its explicit host dependencies: Bash, `curl`, `jq`, GNU coreutils (`date`, `sha256sum`, `tr`) and `awk`, plus systemd. On Ubuntu, install the missing packages through the signed distribution repository (`sudo apt-get install jq curl coreutils gawk`), then verify `command -v bash curl jq date sha256sum tr awk systemctl` succeeds. A missing `jq` makes the health check fail even while Prometheus is healthy. Run `/usr/local/sbin/check-noviis-prometheus-health` successfully once with the approved active-config digest before enabling the timer; dependency installation does not bypass configuration validation.

Prometheus cannot reliably report its own total outage. Install `check-prometheus-health.sh` as `/usr/local/sbin/check-noviis-prometheus-health`, store the SHA-256 of the approved `/api/v1/status/config` YAML in `/etc/prometheus/active-config.sha256`, and enable `noviis-prometheus-watchdog.timer`. The root-owned oneshot checks the native service state, loopback readiness, active configuration checksum, and the oldest successful rule-group evaluation every minute; an API error status, an empty group list, or a group without `lastEvaluation` fails closed. A failed check is recorded by systemd/journal and triggers `noviis-prometheus-watchdog-notify.service`, which posts a fixed alert directly to local Alertmanager without relying on Prometheus. Install the notifier script and unit before enabling `OnFailure`. The notifier makes one bounded request, never includes raw logs or credentials, and does not recursively notify on its own failure. Repeated watchdog failures use stable labels for Alertmanager deduplication; the five-minute resolve timeout means a missing refresh is not proof of recovery. This path still depends on the same host, network, and Alertmanager: complete host/Alertmanager failure requires an independent host or cloud control-plane signal.

When multiple rule groups exist, watchdog health uses the oldest group evaluation timestamp and requires the rule API status to be `success`; a fresh group cannot mask a stalled group.

Generate the checksum only after a successful config reload, using `curl -fsS http://127.0.0.1:9090/api/v1/status/config | jq -jr '.data.yaml' | sha256sum`, then install only the 64-character digest as `root:root` mode `0644`. Install the watchdog script as `root:root` mode `0755`, copy the tracked service and timer units, run `systemd-analyze verify`, and enable the timer. Do not derive the expected digest from the tracked source file because Prometheus returns its parsed active configuration.

Cleanup reconciliation counts every immediate incoming entry, including partial files and broken symlinks, and writes `noviis_deployment_cleanup_writer_success_timestamp_seconds`. Missing writer series alert independently so deletion of a textfile cannot look healthy; an active debt whose writer timestamp is older than 30 minutes raises an additional stale alert. This freshness rule also covers the retained legacy `seo_submission` debt label.

When first enabling these rules, initialize every expected textfile explicitly: clear backend `release_retention` and `status_diagnostic`, clear frontend `release_retention` and the retained `seo_submission` label (no submission is performed), then run `reconcile incoming_release` for both components. A reconciliation exit status of 2 is an observed cleanup debt, not an initialization failure; inspect and remove only verified unreferenced entries before reconciling again.

Before running more than one backend JVM, replace the local Caffeine rate-limit store with a shared implementation and define cross-node invalidation for the `GlobalConfig` cache. Local buckets do not coordinate across instances and process-local configuration caches may diverge. Multi-instance rollout is blocked until both mechanisms have tests and rollback procedures.

Durable Web Push jobs expose pending and failed backlogs, oldest pending age, and retry/dead-letter outcomes. The `hidden_source` outcome records terminal skips when a queued keyword or mention source is no longer eligible at send time or legacy notification metadata can no longer be resolved; it does not count as a provider delivery failure. Existing outcome-grouped panels include it automatically. Pending work above 100 or older than five minutes warns after ten minutes; any failed row or new dead-letter outcome is critical. Delivery failure ratios use `timeout`, `retryable_failure`, and `permanent_failure`; `expired` is an expected terminal cleanup outcome rather than a delivery failure.


## Alertmanager email receiver

`tool-versions.env` pins the official Alertmanager 0.35.0 Linux AMD64 archive and SHA-256. Download `https://github.com/prometheus/alertmanager/releases/download/v${ALERTMANAGER_VERSION}/alertmanager-${ALERTMANAGER_VERSION}.${ALERTMANAGER_ARCH}.tar.gz`, verify the pinned digest before extraction, and install its `alertmanager` and `amtool` binaries as root-owned executables in `/usr/local/bin`. Keep the previous matching binary, restricted configuration, and consistent state backup for rollback. Verify both binary versions. The [official configuration reference](https://prometheus.io/docs/alerting/latest/configuration/) defines SMTP password-file and required TLS settings.

Create the system account/group `noviis-alertmanager` with no login shell. Install `systemd/noviis-alertmanager.service` in `/etc/systemd/system/`; it listens only on `127.0.0.1:9093`, disables cluster gossip, and writes only its dedicated `/var/lib/noviis-alertmanager` state directory. Never expose ports 9093/9094 publicly. Create `/etc/alertmanager` as `root:noviis-alertmanager` mode `0750`.

Render `alertmanager/alertmanager.yml.example` on the server. Replace both sender placeholders with the approved Gmail SMTP account and the recipient placeholder with the operator-approved email destination. Reuse the existing application's `MAIL_USERNAME` and `MAIL_APP_PASSWORD` only through a privileged server-local process; never print them, put the password in arguments/history, copy the app environment wholesale, or store the rendered files in Git. Save the password alone to `/etc/alertmanager/smtp-password`, and the rendered configuration to `/etc/alertmanager/alertmanager.yml`, both `root:noviis-alertmanager` mode `0640`. The application service account must not have access. Rotation requires updating this dedicated password file when the SMTP account's app password changes. SMTP uses `smtp.gmail.com:587`, required STARTTLS, certificate verification, and TLS 1.2 or newer; do not weaken TLS to work around delivery errors.

In an authorized Linux maintenance session, validate and start the provisioned receiver:

```bash
sudo -u noviis-alertmanager /usr/local/bin/amtool check-config /etc/alertmanager/alertmanager.yml
sudo systemd-analyze verify /etc/systemd/system/noviis-alertmanager.service
sudo systemctl daemon-reload
sudo systemctl enable --now noviis-alertmanager.service
curl -fsS http://127.0.0.1:9093/-/ready >/dev/null
promtool check config /etc/prometheus/prometheus.yml
sudo systemctl reload prometheus
```

After reload, verify `/api/v1/alertmanagers` reports the expected endpoint, the `alertmanager` scrape target is healthy, and refresh the watchdog's approved active-config checksum using the existing reviewed procedure. `NoviIsAlertmanagerDown` detects loss/deletion of this target and `NoviIsAlertmanagerNotificationFailure` detects email send failures. These rules are diagnostic signals, not an independent notification path if Alertmanager or SMTP itself is unavailable.

Send one clearly labelled test alert with a unique test label and a bounded `endsAt` through the local API during the authorized delivery test. Wait past `group_wait: 30s`, verify the matching notification attempt succeeded (compare `alertmanager_notifications_total{integration="email"}` with `alertmanager_notifications_failed_total{integration="email"}`), and confirm the intended recipient received it. An API 2xx only confirms acceptance. Resolve the synthetic alert and confirm it clears; `send_resolved` deliberately sends a recovery email. Avoid dumping `/api/v2/status`, rendered configuration, message contents, or credentials into reports. Record counts/status only. Grouping is by alert name/instance, initial grouping waits 30 seconds, changed groups wait five minutes, and continuing failures repeat every four hours.

Install `notify-prometheus-watchdog-failure.sh` as `/usr/local/sbin/notify-noviis-prometheus-watchdog-failure` with `root:root` mode `0755`, and the checked-in `noviis-prometheus-watchdog-notify.service` alongside the watchdog service/timer. Validate all three units before daemon reload. The notification unit runs as `noviis-alertmanager`, has a 15-second timeout and no restart or `OnFailure` chain; curl has a 3-second connect timeout and 10-second total timeout. Test submission failure locally with `bash deploy/monitoring/tests/notify-prometheus-watchdog-failure-test.sh`; this fixture never contacts an external receiver. Use the real one-shot only when an operational test email is authorized.
