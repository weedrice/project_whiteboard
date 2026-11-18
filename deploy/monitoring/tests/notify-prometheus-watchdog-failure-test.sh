#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
notifier="$project_root/deploy/monitoring/notify-prometheus-watchdog-failure.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
cat > "$fixture/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == *'--max-time 10'* ]]
[[ "$*" == *'--connect-timeout 3'* ]]
[[ "$*" == *'--data-binary @-'* ]]
[[ "$*" == *'--output /dev/null'* ]]
[[ "${*: -1}" == 'http://127.0.0.1:9093/api/v2/alerts' ]]
payload="$(cat)"
[[ "$payload" == *'"alertname":"NoviIsPrometheusWatchdogFailure"'* ]]
[[ "$payload" == *'"instance":"localhost"'* ]]
if [ "${FAIL_CURL:-false}" = true ]; then
  echo 'raw-response-must-not-leak' >&2
  exit 22
fi
EOF
chmod +x "$fixture/curl"
CURL_COMMAND="$fixture/curl" bash "$notifier" >"$fixture/success" 2>&1
grep -q 'submitted to local Alertmanager' "$fixture/success"
if FAIL_CURL=true CURL_COMMAND="$fixture/curl" bash "$notifier" >"$fixture/failure" 2>&1; then
  echo 'A rejected submission must fail' >&2; exit 1
fi
grep -q 'notification submission failed' "$fixture/failure"
if grep -q 'raw-response-must-not-leak' "$fixture/failure"; then
  echo 'Raw receiver errors must not be logged' >&2; exit 1
fi
grep -q '^OnFailure=noviis-prometheus-watchdog-notify.service$' <(tr -d '\r' < "$project_root/deploy/monitoring/systemd/noviis-prometheus-watchdog.service")
if grep -q '^OnFailure=' "$project_root/deploy/monitoring/systemd/noviis-prometheus-watchdog-notify.service"; then
  echo 'Notification failures must not recurse' >&2; exit 1
fi
echo 'Prometheus watchdog notification fixtures passed'
