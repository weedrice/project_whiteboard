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

Open Grafana only through an SSH tunnel: `ssh -L 3000:127.0.0.1:3000 ubuntu@<host>` and browse to `http://127.0.0.1:3000`.

Alert rules are evaluated by Prometheus, but no Alertmanager route or external notification receiver is installed. A rule entering the firing state therefore does not send Slack or email notifications.

Before running more than one backend JVM, replace the local Caffeine rate-limit store with a shared implementation and define cross-node invalidation for the `GlobalConfig` cache. Local buckets do not coordinate across instances and process-local configuration caches may diverge. Multi-instance rollout is blocked until both mechanisms have tests and rollback procedures.
