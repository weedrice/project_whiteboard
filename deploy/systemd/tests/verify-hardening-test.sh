#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
validator="$project_root/deploy/systemd/verify-hardening.py"
if python3 --version >/dev/null 2>&1; then python_bin=python3; else python_bin=python; fi
"$python_bin" "$validator" --root "$project_root" >/dev/null

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
while IFS= read -r relative; do
  mkdir -p "$fixture/$(dirname "$relative")"
  cp "$project_root/$relative" "$fixture/$relative"
done <<'EOF'
deploy/systemd/hardening-contract.json
deploy/systemd/app.service
deploy/monitoring/systemd/prometheus.service.d/override.conf
deploy/monitoring/systemd/grafana-server.service.d/override.conf
deploy/monitoring/systemd/prometheus-node-exporter.service.d/override.conf
deploy/monitoring/systemd/noviis-prometheus-watchdog.service
EOF

sed -i 's/^ProtectSystem=strict$/ProtectSystem=false/' "$fixture/deploy/systemd/app.service"
if "$python_bin" "$validator" --root "$fixture"; then
  echo "Expected a weakened ProtectSystem directive to fail" >&2
  exit 1
fi
cp "$project_root/deploy/systemd/app.service" "$fixture/deploy/systemd/app.service"
sed -i '/^\[Install\]$/i ReadWritePaths=/etc' "$fixture/deploy/systemd/app.service"
if "$python_bin" "$validator" --root "$fixture"; then
  echo "Expected an expanded application write path to fail" >&2
  exit 1
fi

echo "systemd hardening negative fixtures passed"
