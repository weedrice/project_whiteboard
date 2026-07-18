#!/usr/bin/env bash
set -euo pipefail

prometheus_url="${PROMETHEUS_URL:-http://127.0.0.1:9090}"
expected_checksum_file="${PROMETHEUS_EXPECTED_CONFIG_CHECKSUM_FILE:-/etc/prometheus/active-config.sha256}"
maximum_rule_age_seconds="${PROMETHEUS_MAX_RULE_EVALUATION_AGE_SECONDS:-180}"
systemctl_command="${SYSTEMCTL_COMMAND:-systemctl}"
curl_command="${CURL_COMMAND:-curl}"

fail() {
  echo "Prometheus independent health check failed: $*" >&2
  exit 1
}

[[ "$maximum_rule_age_seconds" =~ ^[1-9][0-9]*$ ]] || fail "invalid maximum rule evaluation age"
[ -f "$expected_checksum_file" ] || fail "expected active config checksum is missing"
expected_checksum="$(tr -d '[:space:]' < "$expected_checksum_file")"
[[ "$expected_checksum" =~ ^[0-9a-f]{64}$ ]] || fail "expected active config checksum is invalid"

"$systemctl_command" is-active --quiet prometheus || fail "prometheus.service is not active"
"$curl_command" --fail --silent --show-error --max-time 5 "$prometheus_url/-/ready" >/dev/null \
  || fail "readiness endpoint failed"

status_config="$("$curl_command" --fail --silent --show-error --max-time 5 "$prometheus_url/api/v1/status/config")" \
  || fail "active config endpoint failed"
active_checksum="$(jq -jer 'if .status == "success" and (.data.yaml | type == "string") then .data.yaml else error("missing active config") end' <<< "$status_config" \
  | sha256sum | awk '{print $1}')" || fail "active config response is invalid"
[ "$active_checksum" = "$expected_checksum" ] || fail "active config checksum differs from the approved value"

rules="$("$curl_command" --fail --silent --show-error --max-time 5 "$prometheus_url/api/v1/rules")" \
  || fail "rule status endpoint failed"
last_evaluation="$(jq -er '
  if .status != "success" then error("rule API status is not success")
  elif (.data.groups | type) != "array" or (.data.groups | length) == 0 then error("no evaluated rule groups")
  elif any(.data.groups[]; (.lastEvaluation | type) != "string" or .lastEvaluation == "") then error("invalid group evaluation timestamp")
  else [.data.groups[].lastEvaluation] | min
  end
' <<< "$rules")" \
  || fail "no evaluated rule groups were reported"
last_evaluation_epoch="$(date -u -d "$last_evaluation" +%s 2>/dev/null)" \
  || fail "last rule evaluation timestamp is invalid"
now_epoch="$(date -u +%s)"
age_seconds=$((now_epoch - last_evaluation_epoch))
[ "$age_seconds" -ge 0 ] || fail "last rule evaluation is in the future"
[ "$age_seconds" -le "$maximum_rule_age_seconds" ] || fail "last rule evaluation is stale"

echo "Prometheus independent health check passed"
