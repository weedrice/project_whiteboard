#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
validator="$project_root/deploy/monitoring/verify-resource-limit-evidence.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/deploy/systemd" "$fixture/deploy/monitoring/systemd/prometheus.service.d" \
  "$fixture/deploy/monitoring/systemd/grafana-server.service.d"
cp "$project_root/deploy/systemd/app.service" "$fixture/deploy/systemd/app.service"
cp "$project_root/deploy/monitoring/systemd/prometheus.service.d/override.conf" \
  "$fixture/deploy/monitoring/systemd/prometheus.service.d/override.conf"
cp "$project_root/deploy/monitoring/systemd/grafana-server.service.d/override.conf" \
  "$fixture/deploy/monitoring/systemd/grafana-server.service.d/override.conf"

RESOURCE_LIMIT_PROJECT_ROOT="$fixture" bash "$validator" >/dev/null
printf '\n  MemoryHigh=80\n  MemoryMax=100\n' >> "$fixture/deploy/systemd/app.service"
if RESOURCE_LIMIT_PROJECT_ROOT="$fixture" bash "$validator"; then
  echo "Expected an application resource limit without evidence to fail" >&2
  exit 1
fi
cat > "$fixture/deploy/monitoring/resource-limit-evidence.env" <<'EOF'
MEASUREMENT_TICKET=OPS-1234
REPRESENTATIVE_DAYS=7
STAGING_PRESSURE_VERIFIED=true
ROLLBACK_VERIFIED=true
APP_MEMORY_P99_BYTES=70
APP_MEMORY_PEAK_BYTES=90
APP_MEMORY_MAX_BYTES=100
EOF
RESOURCE_LIMIT_PROJECT_ROOT="$fixture" bash "$validator" >/dev/null
sed -i 's/APP_MEMORY_MAX_BYTES=100/APP_MEMORY_MAX_BYTES=99/' "$fixture/deploy/monitoring/resource-limit-evidence.env"
if RESOURCE_LIMIT_PROJECT_ROOT="$fixture" bash "$validator"; then
  echo "Expected evidence that differs from the configured application limit to fail" >&2
  exit 1
fi

echo "Resource-limit evidence fixtures passed"
