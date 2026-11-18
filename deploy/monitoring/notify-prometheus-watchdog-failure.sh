#!/usr/bin/env bash
set -euo pipefail

# A stable, non-sensitive alert identity allows Alertmanager to deduplicate
# retries from the watchdog timer. Never include journal output or config data.
curl_command="${CURL_COMMAND:-curl}"
payload='[{"labels":{"alertname":"NoviIsPrometheusWatchdogFailure","instance":"localhost","severity":"critical","source":"systemd-watchdog"},"annotations":{"summary":"Independent Prometheus watchdog failed","description":"Inspect the watchdog unit locally; Prometheus may be down, stale, or running an unapproved configuration."}}]'
if ! printf '%s' "$payload" | "$curl_command" --fail --silent --max-time 10 \
  --connect-timeout 3 --output /dev/null --request POST \
  --header 'Content-Type: application/json' --data-binary @- \
  http://127.0.0.1:9093/api/v2/alerts 2>/dev/null; then
  echo 'Prometheus watchdog notification submission failed' >&2
  exit 1
fi
echo 'Prometheus watchdog notification submitted to local Alertmanager'
