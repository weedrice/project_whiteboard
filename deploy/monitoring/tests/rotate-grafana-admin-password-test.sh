#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Grafana password rotation fixture must run as root" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/monitoring/rotate-grafana-admin-password.sh"
grafana_override="$project_root/deploy/monitoring/systemd/grafana-server.service.d/override.conf"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

grep -Fq 'test ! -L /etc/noviis/monitoring.env' "$grafana_override"

cat > "$fixture/curl" <<'EOF'
#!/usr/bin/env bash
test "$#" -eq 2
test "$1" = --config
config="$2"
test "$(stat -c %a "$config")" = 600
payload="$(sed -n 's/^data-binary = "@\(.*\)"$/\1/p' "$config")"
test "$(stat -c %a "$payload")" = 600
case "$config" in
  *rollback*)
    grep -Fq 'new-password-that-is-long' "$config"
    grep -Fq 'current-password-that-is-long' "$payload"
    if [ -f "$FAIL_ROLLBACK_MARKER" ]; then exit 23; fi
    ;;
  *)
    grep -Fq 'current-password-that-is-long' "$config"
    grep -Fq 'new-password-that-is-long' "$payload"
    if [ -f "$FAIL_MARKER" ]; then exit 22; fi
    ;;
esac
EOF
chmod +x "$fixture/curl"

cat > "$fixture/mv" <<'EOF'
#!/usr/bin/env bash
if [ -f "$FAIL_MOVE_MARKER" ]; then exit 1; fi
exec /usr/bin/mv "$@"
EOF
chmod +x "$fixture/mv"

env_file="$fixture/monitoring.env"
write_old_env() {
  printf 'GF_SECURITY_ADMIN_PASSWORD=current-password-that-is-long\nGF_USERS_ALLOW_SIGN_UP=false\n' > "$env_file"
  chown root:root "$env_file"
  chmod 0600 "$env_file"
}

run_rotation() {
  printf '%s\n%s\n%s\n' \
    'current-password-that-is-long' \
    'new-password-that-is-long' \
    'new-password-that-is-long' | \
    CURL_BIN="$fixture/curl" \
    MV_BIN="$fixture/mv" \
    GRAFANA_ENV_FILE="$env_file" \
    FAIL_MARKER="$fixture/fail" \
    FAIL_ROLLBACK_MARKER="$fixture/fail-rollback" \
    FAIL_MOVE_MARKER="$fixture/fail-move" \
    bash "$script"
}

write_old_env
run_rotation
grep -Fqx 'GF_SECURITY_ADMIN_PASSWORD="new-password-that-is-long"' "$env_file"
grep -Fqx 'GF_USERS_ALLOW_SIGN_UP=false' "$env_file"
test "$(stat -c %U:%G "$env_file")" = root:root
test "$(stat -c %a "$env_file")" = 600

write_old_env
touch "$fixture/fail"
if run_rotation; then
  echo "Expected Grafana API failure" >&2
  exit 1
fi
grep -Fqx 'GF_SECURITY_ADMIN_PASSWORD=current-password-that-is-long' "$env_file"
rm "$fixture/fail"

if printf '%s\n%s\n%s\n' old short short | \
  CURL_BIN="$fixture/curl" GRAFANA_ENV_FILE="$env_file" bash "$script"; then
  echo "Expected short Grafana password rejection" >&2
  exit 1
fi

write_old_env
if printf '%s\n%s\n%s\n' \
  'different-current-password-that-is-long' \
  'new-password-that-is-long' \
  'new-password-that-is-long' | \
  CURL_BIN="$fixture/curl" GRAFANA_ENV_FILE="$env_file" bash "$script"; then
  echo "Expected a password that differs from the environment source of truth to fail" >&2
  exit 1
fi
grep -Fqx 'GF_SECURITY_ADMIN_PASSWORD=current-password-that-is-long' "$env_file"

write_old_env
touch "$fixture/fail-move"
if run_rotation; then
  echo "Expected environment replacement failure" >&2
  exit 1
fi
grep -Fqx 'GF_SECURITY_ADMIN_PASSWORD=current-password-that-is-long' "$env_file"
test -z "$(find "$fixture" -maxdepth 1 -name 'monitoring.env.rotation-recovery.*' -print -quit)"

write_old_env
touch "$fixture/fail-rollback"
set +e
run_rotation
status=$?
set -e
test "$status" -eq 2
recovery_file="$(find "$fixture" -maxdepth 1 -name 'monitoring.env.rotation-recovery.*' -print -quit)"
test -n "$recovery_file"
test "$(stat -c %U:%G "$recovery_file")" = root:root
test "$(stat -c %a "$recovery_file")" = 600
grep -Fqx 'GF_SECURITY_ADMIN_PASSWORD="new-password-that-is-long"' "$recovery_file"
test -z "$(find "$fixture" -maxdepth 1 -name 'monitoring.env.next.*' -print -quit)"
rm "$fixture/fail-move" "$fixture/fail-rollback" "$recovery_file"

write_old_env
mv "$env_file" "$fixture/monitoring-real.env"
ln -s "$fixture/monitoring-real.env" "$env_file"
if run_rotation; then
  echo "Expected a symlink monitoring environment file to be rejected" >&2
  exit 1
fi

echo "Grafana password rotation fixtures passed"
