#!/usr/bin/env bash
set -euo pipefail

project_root="${RESOURCE_LIMIT_PROJECT_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
evidence="$project_root/deploy/monitoring/resource-limit-evidence.env"
declare -A limit_files=(
  [APP]="$project_root/deploy/systemd/app.service"
  [PROMETHEUS]="$project_root/deploy/monitoring/systemd/prometheus.service.d/override.conf"
  [GRAFANA]="$project_root/deploy/monitoring/systemd/grafana-server.service.d/override.conf"
)

if ! grep -Eq '^[[:space:]]*(MemoryHigh|MemoryMax|CPUQuota)=' "${limit_files[@]}"; then
  echo "Monitoring units contain no unproven resource ceilings"
  exit 0
fi

test -f "$evidence" || {
  echo "Monitoring resource limits require deploy/monitoring/resource-limit-evidence.env" >&2
  exit 1
}
# shellcheck disable=SC1090
source "$evidence"
[[ "${MEASUREMENT_TICKET:-}" =~ ^[A-Za-z0-9._/-]+$ ]]
[[ "${REPRESENTATIVE_DAYS:-}" =~ ^[0-9]+$ ]] && [ "$REPRESENTATIVE_DAYS" -ge 7 ]
[ "${STAGING_PRESSURE_VERIFIED:-false}" = true ]
[ "${ROLLBACK_VERIFIED:-false}" = true ]
for service in APP PROMETHEUS GRAFANA; do
  limit_file="${limit_files[$service]}"
  grep -Eq '^[[:space:]]*(MemoryHigh|MemoryMax|CPUQuota)=' "$limit_file" || continue
  p99_name="${service}_MEMORY_P99_BYTES"
  peak_name="${service}_MEMORY_PEAK_BYTES"
  max_name="${service}_MEMORY_MAX_BYTES"
  p99="${!p99_name:-}"
  peak="${!peak_name:-}"
  maximum="${!max_name:-}"
  [[ "$p99" =~ ^[1-9][0-9]*$ && "$peak" =~ ^[1-9][0-9]*$ && "$maximum" =~ ^[1-9][0-9]*$ ]]
  [ "$maximum" -gt "$peak" ] && [ "$peak" -ge "$p99" ]
  configured_max="$(sed -n 's/^[[:space:]]*MemoryMax=//p' "$limit_file")"
  if [ -n "$configured_max" ]; then
    [[ "$configured_max" =~ ^[1-9][0-9]*$ ]]
    [ "$configured_max" = "$maximum" ]
  fi
  configured_high="$(sed -n 's/^[[:space:]]*MemoryHigh=//p' "$limit_file")"
  if [ -n "$configured_high" ]; then
    [[ "$configured_high" =~ ^[1-9][0-9]*$ ]]
    [ "$configured_high" -gt "$p99" ] && [ "$configured_high" -le "$maximum" ]
  fi
done

echo "Monitoring resource-limit evidence verified"
