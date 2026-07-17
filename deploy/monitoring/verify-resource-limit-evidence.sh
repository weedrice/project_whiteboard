#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
evidence="$root/resource-limit-evidence.env"
limit_files=(
  "$root/systemd/prometheus.service.d/override.conf"
  "$root/systemd/grafana-server.service.d/override.conf"
)

if ! grep -Eq '^(MemoryHigh|MemoryMax|CPUQuota)=' "${limit_files[@]}"; then
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
for service in PROMETHEUS GRAFANA; do
  p99_name="${service}_MEMORY_P99_BYTES"
  peak_name="${service}_MEMORY_PEAK_BYTES"
  max_name="${service}_MEMORY_MAX_BYTES"
  p99="${!p99_name:-}"
  peak="${!peak_name:-}"
  maximum="${!max_name:-}"
  [[ "$p99" =~ ^[1-9][0-9]*$ && "$peak" =~ ^[1-9][0-9]*$ && "$maximum" =~ ^[1-9][0-9]*$ ]]
  [ "$maximum" -gt "$peak" ] && [ "$peak" -ge "$p99" ]
done

echo "Monitoring resource-limit evidence verified"
