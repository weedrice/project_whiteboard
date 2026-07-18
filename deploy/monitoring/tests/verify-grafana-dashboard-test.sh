#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
validator="$project_root/deploy/monitoring/verify-grafana-dashboard.py"
if python3 --version >/dev/null 2>&1; then python_bin=python3; else python_bin=python; fi
"$python_bin" "$validator" --root "$project_root" >/dev/null

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/deploy/monitoring/grafana/dashboards"
dashboard="$fixture/deploy/monitoring/grafana/dashboards/noviis-overview.json"
cp "$project_root/deploy/monitoring/grafana/dashboards/noviis-overview.json" "$dashboard"

"$python_bin" - "$dashboard" <<'PY'
import json, sys
path = sys.argv[1]
dashboard = json.load(open(path, encoding="utf-8"))
dashboard["panels"][1]["id"] = dashboard["panels"][0]["id"]
open(path, "w", encoding="utf-8").write(json.dumps(dashboard))
PY
if "$python_bin" "$validator" --root "$fixture"; then
  echo "Expected a duplicate panel id to fail" >&2
  exit 1
fi

cp "$project_root/deploy/monitoring/grafana/dashboards/noviis-overview.json" "$dashboard"
"$python_bin" - "$dashboard" <<'PY'
import json, sys
path = sys.argv[1]
dashboard = json.load(open(path, encoding="utf-8"))
panel = next(panel for panel in dashboard["panels"] if panel["title"] == "Backend process restarts")
panel["targets"][0]["expr"] = "changes(process_start_time_seconds[15m])"
open(path, "w", encoding="utf-8").write(json.dumps(dashboard))
PY
if "$python_bin" "$validator" --root "$fixture"; then
  echo "Expected an unscoped backend process query to fail" >&2
  exit 1
fi

echo "Grafana dashboard negative fixtures passed"
