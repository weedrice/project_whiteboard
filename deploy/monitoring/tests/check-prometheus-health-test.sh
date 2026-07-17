#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
watchdog="$project_root/deploy/monitoring/check-prometheus-health.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
fake_bin="$fixture/bin"
mkdir -p "$fake_bin"

cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
[ "${SYSTEMD_ACTIVE:-true}" = true ]
EOF
cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
url="${*: -1}"
case "$url" in
  */-/ready) printf 'Prometheus is Ready.\n' ;;
  */api/v1/status/config) printf '{"status":"success","data":{"yaml":"global:\\n  scrape_interval: 30s\\n"}}\n' ;;
  */api/v1/rules) printf '{"status":"success","data":{"groups":[{"lastEvaluation":"%s"}]}}\n' "${RULE_EVALUATION:-2026-07-18T00:00:00Z}" ;;
  *) exit 1 ;;
esac
EOF
cat > "$fake_bin/date" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -u ] && [ "${2:-}" = -d ]; then
  [ "${INVALID_RULE_TIME:-false}" != true ] || exit 1
  printf '%s\n' "${RULE_EPOCH:-1000}"
else
  printf '%s\n' "${NOW_EPOCH:-1100}"
fi
EOF
cat > "$fake_bin/jq" <<'EOF'
#!/usr/bin/env bash
input="$(cat)"
[ -n "$input" ] || exit 1
case "$*" in
  *lastEvaluation*) printf '%s\n' "${RULE_EVALUATION:-2026-07-18T00:00:00Z}" ;;
  *) printf 'global:\n  scrape_interval: 30s\n' ;;
esac
EOF
chmod +x "$fake_bin"/*
printf 'global:\n  scrape_interval: 30s\n' | tr -d '\n' >/dev/null
printf '%s\n' "$(printf 'global:\n  scrape_interval: 30s\n' | sha256sum | awk '{print $1}')" > "$fixture/active-config.sha256"

run_watchdog() {
  PATH="$fake_bin:$PATH" \
    SYSTEMCTL_COMMAND="$fake_bin/systemctl" \
    CURL_COMMAND="$fake_bin/curl" \
    PROMETHEUS_EXPECTED_CONFIG_CHECKSUM_FILE="$fixture/active-config.sha256" \
    bash "$watchdog"
}

run_watchdog >/dev/null
if SYSTEMD_ACTIVE=false run_watchdog; then echo "Inactive Prometheus must fail" >&2; exit 1; fi
if RULE_EPOCH=1 NOW_EPOCH=1000 run_watchdog; then echo "Stale rule evaluation must fail" >&2; exit 1; fi
printf '%064d\n' 0 > "$fixture/active-config.sha256"
if run_watchdog; then echo "An unexpected active config checksum must fail" >&2; exit 1; fi

echo "Prometheus independent watchdog fixtures passed"
