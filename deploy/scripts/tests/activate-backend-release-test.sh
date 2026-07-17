#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/activate-backend-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

app_dir="$fixture/app"
release_root="$app_dir/releases"
log_dir="$fixture/logs"
env_file="$fixture/app.env"
fake_bin="$fixture/bin"
state_dir="$fixture/state"
mkdir -p "$release_root" "$log_dir" "$fake_bin" "$state_dir"
printf 'test=true\n' > "$env_file"
chmod 0600 "$env_file"
touch "$log_dir/whiteboard-active.log" "$log_dir/whiteboard-error.log"
printf 'active\n' > "$state_dir/service"

cat > "$fake_bin/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF

cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
command_name="${1:-}"
case "$command_name" in
  stop)
    if [ -f "$STATE_DIR/fail_stop" ]; then exit 1; fi
    printf 'inactive\n' > "$STATE_DIR/service"
    ;;
  start)
    if [ -f "$STATE_DIR/fail_start" ]; then exit 1; fi
    printf 'active\n' > "$STATE_DIR/service"
    ;;
  restart)
    if [ -f "$STATE_DIR/fail_restart" ]; then exit 1; fi
    printf 'active\n' > "$STATE_DIR/service"
    ;;
  is-active|status)
    grep -qx active "$STATE_DIR/service"
    ;;
esac
EOF

cat > "$fake_bin/journalctl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
if [ -f "$STATE_DIR/fail_new_health" ] && grep -q '^new$' "$APP_DIR/app.jar"; then
  exit 1
fi
exit 0
EOF
chmod +x "$fake_bin"/*

invoke_activation() {
  APP_DIR="$app_dir" \
  RELEASE_ROOT="$release_root" \
  LOG_DIR="$log_dir" \
  ENV_FILE="$env_file" \
  ENV_FILE_OWNER="$(stat -c %U:%G "$env_file")" \
  ENV_FILE_MODE="$(stat -c %a "$env_file")" \
  STATE_DIR="$state_dir" \
  PATH="$fake_bin:$PATH" \
  HEALTH_ATTEMPTS=2 \
  HEALTH_DELAY_SECONDS=0 \
  bash "$script" "$1"
}

run_activation() {
  (
    cd "$1"
    sha256sum app.jar > SHA256SUMS
  )
  invoke_activation "$1"
}

printf 'old\n' > "$app_dir/app.jar"
success_release="$release_root/success"
mkdir -p "$success_release"
printf 'new\n' > "$success_release/app.jar"
run_activation "$success_release"
grep -qx new "$app_dir/app.jar"

checksum_failure_release="$release_root/checksum-failure"
mkdir -p "$checksum_failure_release"
printf 'new\n' > "$checksum_failure_release/app.jar"
printf '%064d  app.jar\n' 0 > "$checksum_failure_release/SHA256SUMS"
if invoke_activation "$checksum_failure_release"; then
  echo "Expected checksum verification failure" >&2
  exit 1
fi
grep -qx new "$app_dir/app.jar"

printf 'old\n' > "$app_dir/app.jar"
stop_failure_release="$release_root/stop-failure"
mkdir -p "$stop_failure_release"
printf 'new\n' > "$stop_failure_release/app.jar"
touch "$state_dir/fail_stop"
if run_activation "$stop_failure_release"; then
  echo "Expected stop failure" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"
rm "$state_dir/fail_stop"

printf 'old\n' > "$app_dir/app.jar"
start_failure_release="$release_root/start-failure"
mkdir -p "$start_failure_release"
printf 'new\n' > "$start_failure_release/app.jar"
touch "$state_dir/fail_start"
if run_activation "$start_failure_release"; then
  echo "Expected start failure" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"
rm "$state_dir/fail_start"

printf 'old\n' > "$app_dir/app.jar"
failed_release="$release_root/failed-health"
mkdir -p "$failed_release"
printf 'new\n' > "$failed_release/app.jar"
touch "$state_dir/fail_new_health"
if run_activation "$failed_release"; then
  echo "Expected failed health check" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"
rm "$state_dir/fail_new_health"

printf 'old\n' > "$app_dir/app.jar"
rollback_failure_release="$release_root/rollback-failure"
mkdir -p "$rollback_failure_release"
printf 'new\n' > "$rollback_failure_release/app.jar"
touch "$state_dir/fail_new_health" "$state_dir/fail_restart"
set +e
run_activation "$rollback_failure_release"
status=$?
set -e
if [ "$status" -ne 2 ]; then
  echo "Expected rollback failure status 2, got $status" >&2
  exit 1
fi
rm "$state_dir/fail_new_health" "$state_dir/fail_restart"

for index in 1 2 3 4 5 6; do
  old_release="$release_root/old-$index"
  mkdir -p "$old_release"
  touch -d "2026-07-$((10 + index))" "$old_release"
done
retention_release="$release_root/current"
mkdir -p "$retention_release"
printf 'new\n' > "$retention_release/app.jar"
run_activation "$retention_release"
release_count="$(find "$release_root" -mindepth 1 -maxdepth 1 -type d | wc -l)"
test "$release_count" -eq 5
test -d "$retention_release"

outside_release="$fixture/outside"
mkdir -p "$outside_release"
printf 'new\n' > "$outside_release/app.jar"
if run_activation "$outside_release"; then
  echo "Expected outside release rejection" >&2
  exit 1
fi
test -d "$outside_release"

echo "Backend activation fixtures passed"
