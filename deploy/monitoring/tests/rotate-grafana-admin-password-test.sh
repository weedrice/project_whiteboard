#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Grafana password rotation fixture must run as root" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/monitoring/rotate-grafana-admin-password.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

cat > "$fixture/curl" <<'EOF'
#!/usr/bin/env bash
test "$#" -eq 2
test "$1" = --config
config="$2"
test "$(stat -c %a "$config")" = 600
grep -Fq 'current-password-that-is-long' "$config"
payload="$(sed -n 's/^data-binary = "@\(.*\)"$/\1/p' "$config")"
test "$(stat -c %a "$payload")" = 600
grep -Fq 'new-password-that-is-long' "$payload"
if [ -f "$FAIL_MARKER" ]; then exit 22; fi
EOF
chmod +x "$fixture/curl"

printf '%s\n%s\n%s\n' \
  'current-password-that-is-long' \
  'new-password-that-is-long' \
  'new-password-that-is-long' | \
  CURL_BIN="$fixture/curl" FAIL_MARKER="$fixture/fail" bash "$script"

touch "$fixture/fail"
if printf '%s\n%s\n%s\n' \
  'current-password-that-is-long' \
  'new-password-that-is-long' \
  'new-password-that-is-long' | \
  CURL_BIN="$fixture/curl" FAIL_MARKER="$fixture/fail" bash "$script"; then
  echo "Expected Grafana API failure" >&2
  exit 1
fi

if printf '%s\n%s\n%s\n' old short short | CURL_BIN="$fixture/curl" FAIL_MARKER="$fixture/unused" bash "$script"; then
  echo "Expected short Grafana password rejection" >&2
  exit 1
fi

echo "Grafana password rotation fixtures passed"
