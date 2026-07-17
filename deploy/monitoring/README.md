# NoviIs local monitoring

Prometheus and Grafana run on the application EC2 instance and listen on loopback only. Do not add their ports or `/actuator` to the EC2 security group or Nginx.

## Preflight

Before enabling either service, verify `MemAvailable` is at least 700 MiB and `/` has at least 3 GiB free. If either check fails, stop and resize the instance or reduce the existing workload.

```bash
awk '/MemAvailable/ { print int($2 / 1024) " MiB" }' /proc/meminfo
df -BM --output=avail / | tail -1
```

Install Prometheus and Grafana from their supported Ubuntu package repositories as native systemd services. Copy the files as follows, preserving root ownership for `/etc` and `grafana:grafana` ownership for the dashboard directory.

```text
prometheus/prometheus.yml                         -> /etc/prometheus/prometheus.yml
prometheus/noviis-alerts.yml                      -> /etc/prometheus/noviis-alerts.yml
grafana/provisioning/datasources/noviis.yml       -> /etc/grafana/provisioning/datasources/noviis.yml
grafana/provisioning/dashboards/noviis.yml        -> /etc/grafana/provisioning/dashboards/noviis.yml
grafana/dashboards/noviis-overview.json           -> /var/lib/grafana/dashboards/noviis-overview.json
systemd/prometheus.service.d/override.conf         -> /etc/systemd/system/prometheus.service.d/override.conf
systemd/grafana-server.service.d/override.conf     -> /etc/systemd/system/grafana-server.service.d/override.conf
```

Store the Grafana administrator password only on the server in `/etc/noviis/monitoring.env`:

```text
GF_SECURITY_ADMIN_PASSWORD=<strong unique password>
```

Create the file before Grafana's first start, keep it owned by `root:root` with mode `0600`, and use a password of at least 16 characters. The systemd unit fails closed when the file, ownership, permissions, or password length is invalid. Never print the value in workflow output or copy it into the repository.

Changing this environment variable does not rotate the administrator password in an already initialized Grafana database. Rotate an existing password explicitly on the host with Grafana CLI, then restart the service:

```bash
read -rsp 'New Grafana administrator password: ' GRAFANA_NEW_PASSWORD && echo
sudo grafana cli admin reset-admin-password "$GRAFANA_NEW_PASSWORD"
unset GRAFANA_NEW_PASSWORD
sudo systemctl restart grafana-server
```

Validate and start:

```bash
promtool check config /etc/prometheus/prometheus.yml
promtool check rules /etc/prometheus/noviis-alerts.yml
sudo systemctl daemon-reload
sudo systemctl enable --now prometheus grafana-server
curl -fsS http://127.0.0.1:8081/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:9090/-/ready
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

`noviis_scheduler_last_success_timestamp_seconds` advances only after a scheduled method completes successfully. A failed run records the existing error timer but does not overwrite its previous success timestamp. The stale rule also treats a missing timestamp as stale after the backend process has been up longer than that job's threshold, avoiding false alarms during normal startup.

| Scheduled cadence | Included schedules | Maximum age / startup grace |
| --- | --- | --- |
| Frequent | 25-second, 30-second, and 1-minute jobs | 10 minutes |
| Hourly | hourly cleanup and aggregation jobs | 2 hours 15 minutes |
| Daily | daily cleanup jobs | 30 hours |

Keep `noviis_scheduler_expected_max_age_seconds` entries synchronized when adding, renaming, or rescheduling an `@Scheduled` method. The dashboard compares each last-success age with this configured limit.

HTTP latency histograms are enabled in the production profile for `http.server.requests`; without that setting the p95 query has no bucket series. HTTP alerts require at least 20 requests in five minutes, then require either a 5xx ratio above 5 percent or p95 latency above 1 second for 10 minutes. HikariCP saturation fires after active connections exceed 85 percent of the configured maximum for 10 minutes. A change in `process_start_time_seconds` produces an informational restart alert over a 15-minute window; expected deployments should therefore be correlated with deployment history.

Host disk and filesystem time-series metrics are not collected by this stack because node_exporter is not installed. Continue the preflight disk check above and add an exporter plus explicit capacity rules before relying on Prometheus for disk exhaustion detection.

Alert rules are evaluated by Prometheus, but no Alertmanager route or external notification receiver is installed. A rule entering the firing state therefore does not send Slack or email notifications.

Before running more than one backend JVM, replace the local Caffeine rate-limit store with a shared implementation and define cross-node invalidation for the `GlobalConfig` cache. Local buckets do not coordinate across instances and process-local configuration caches may diverge. Multi-instance rollout is blocked until both mechanisms have tests and rollback procedures.
