#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/activate-backend-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

app_dir="$fixture/app"
release_root="$app_dir/releases"
incoming_root="$app_dir/incoming"
log_dir="$fixture/logs"
diagnostic_root="$fixture/diagnostics"
env_file="$fixture/app.env"
fake_bin="$fixture/bin"
state_dir="$fixture/state"
provenance_verifier="$fixture/verify-release"
mkdir -p "$release_root" "$incoming_root" "$log_dir" "$diagnostic_root" "$fake_bin" "$state_dir"
printf 'test=true\n' > "$env_file"
chmod 0600 "$env_file"
touch "$log_dir/whiteboard-active.log" "$log_dir/whiteboard-error.log"
printf 'active\n' > "$state_dir/service"

cat > "$provenance_verifier" <<'EOF'
#!/usr/bin/env bash
if [ -f "$STATE_DIR/fail_provenance" ]; then exit 1; fi
exit 0
EOF
chmod +x "$provenance_verifier"

cat > "$fake_bin/sudo" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$SUDO_LOG"
if [ "${1:-}" = install ]; then
  shifted=()
  shift
  for argument in "$@"; do
    if [ "$argument" = -d ]; then
      last="${!#}"
      mkdir -p "$last"
      exit 0
    fi
  done
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -o|-g) shift 2 ;;
      *) shifted+=("$1"); shift ;;
    esac
  done
  exec install "${shifted[@]}"
fi
if [ "${1:-}" = chown ] && [ "${2:-}" = root:root ]; then exit 0; fi
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
    if [ -f "$STATE_DIR/arm_fail_cleanup_listing" ]; then touch "$STATE_DIR/fail_cleanup_listing"; fi
    ;;
  restart)
    if [ -f "$STATE_DIR/fail_restart" ]; then exit 1; fi
    printf 'active\n' > "$STATE_DIR/service"
    if [ -f "$STATE_DIR/arm_wrong_rollback_sha" ]; then touch "$STATE_DIR/wrong_rollback_sha"; fi
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
if [[ " $* " == *" /info "* ]] || [[ "${!#}" == */info ]]; then
  if grep -q '^old$' "$APP_DIR/app.jar"; then
    if [ -f "$STATE_DIR/wrong_rollback_sha" ]; then
      printf '{"build":{"commit":"%s"}}\n' "$NEW_SHA"
    else
      printf '{"build":{"commit":"%s"}}\n' "$OLD_SHA"
    fi
  else
    printf '{"build":{"commit":"%s"}}\n' "$NEW_SHA"
  fi
fi
exit 0
EOF
cat > "$fake_bin/mv" <<'EOF'
#!/usr/bin/env bash
last="${!#}"
if [ -f "$STATE_DIR/fail_activate_move" ] && [ "$last" = "$APP_DIR/app.jar" ]; then exit 1; fi
exec /usr/bin/mv "$@"
EOF
cat > "$fake_bin/rm" <<'EOF'
#!/usr/bin/env bash
for argument in "$@"; do
  if [ -f "$STATE_DIR/fail_cleanup" ] && [ "$argument" = "$RELEASE_ROOT/cleanup-victim" ]; then exit 1; fi
done
exec /usr/bin/rm "$@"
EOF
cat > "$fake_bin/find" <<'EOF'
#!/usr/bin/env bash
if [ -f "$STATE_DIR/fail_cleanup_listing" ] && [ "${1:-}" = "$RELEASE_ROOT" ]; then exit 1; fi
exec /usr/bin/find "$@"
EOF
chmod +x "$fake_bin"/*

invoke_activation() {
  local old_sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  local new_sha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  APP_DIR="$app_dir" \
  INCOMING_ROOT="$incoming_root" \
  RELEASE_ROOT="$release_root" \
  LOG_DIR="$log_dir" \
  DIAGNOSTIC_ROOT="$diagnostic_root" \
  ENV_FILE="$env_file" \
  ENV_FILE_OWNER="$(stat -c %U:%G "$env_file")" \
  ENV_FILE_MODE="$(stat -c %a "$env_file")" \
  STATE_DIR="$state_dir" \
  SUDO_LOG="$state_dir/sudo.log" \
  OLD_SHA="$old_sha" \
  NEW_SHA="$new_sha" \
  PATH="$fake_bin:$PATH" \
  PROVENANCE_VERIFIER="$provenance_verifier" \
  HEALTH_ATTEMPTS=2 \
  HEALTH_DELAY_SECONDS=0 \
  bash "$script" "$1" "${2:-}"
}

run_activation() {
  (
    cd "$1"
    sha256sum app.jar > SHA256SUMS
  )
  invoke_activation "$1"
}

printf 'old\n' > "$app_dir/app.jar"
success_release="$incoming_root/success"
mkdir -p "$success_release"
printf 'new\n' > "$success_release/app.jar"
success_output="$(run_activation "$success_release")"
grep -Fqx 'ACTIVATED_SHA=success' <<< "$success_output"
grep -qx new "$app_dir/app.jar"

printf 'old\n' > "$app_dir/app.jar"
provenance_failure_release="$incoming_root/provenance-failure"
mkdir -p "$provenance_failure_release"
printf 'new\n' > "$provenance_failure_release/app.jar"
touch "$state_dir/fail_provenance"
if run_activation "$provenance_failure_release"; then
  echo "Expected provenance verification failure" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"
grep -qx active "$state_dir/service"
rm "$state_dir/fail_provenance"

checksum_failure_release="$incoming_root/checksum-failure"
mkdir -p "$checksum_failure_release"
printf 'new\n' > "$checksum_failure_release/app.jar"
printf '%064d  app.jar\n' 0 > "$checksum_failure_release/SHA256SUMS"
if invoke_activation "$checksum_failure_release"; then
  echo "Expected checksum verification failure" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"

printf 'old\n' > "$app_dir/app.jar"
stop_failure_release="$incoming_root/stop-failure"
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
start_failure_release="$incoming_root/start-failure"
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
failed_release="$incoming_root/failed-health"
mkdir -p "$failed_release"
printf 'new\n' > "$failed_release/app.jar"
touch "$state_dir/fail_new_health"
printf 'CANARY-SECRET-MUST-STAY-ON-HOST\n' > "$log_dir/whiteboard-error.log"
set +e
failed_output="$(run_activation "$failed_release" 2>&1)"
failed_status=$?
set -e
if [ "$failed_status" -eq 0 ]; then
  echo "Expected failed health check" >&2
  exit 1
fi
if grep -Fq 'CANARY-SECRET-MUST-STAY-ON-HOST' <<< "$failed_output"; then
  echo "Application log content escaped into activation output" >&2
  exit 1
fi
grep -Fq 'Backend activation diagnostic stored locally:' <<< "$failed_output"
mapfile -t diagnostic_files < <(find "$diagnostic_root" -maxdepth 1 -type f -name 'backend-failed-health-*.log')
test "${#diagnostic_files[@]}" -eq 1
case "$(uname -s)" in
  MINGW*|MSYS*) ;;
  *) test "$(stat -c %a "${diagnostic_files[0]}")" = 600 ;;
esac
grep -Fq 'CANARY-SECRET-MUST-STAY-ON-HOST' "${diagnostic_files[0]}"
grep -Fq "install -d -o root -g root -m 0700 $diagnostic_root" "$state_dir/sudo.log"
grep -Fq "tee ${diagnostic_files[0]}" "$state_dir/sudo.log"
grep -Fq "chown root:root ${diagnostic_files[0]}" "$state_dir/sudo.log"
grep -qx old "$app_dir/app.jar"
rm "$state_dir/fail_new_health"

for index in $(seq 1 25); do
  printf 'old diagnostic %s\n' "$index" > "$diagnostic_root/backend-old-$index.log"
done

printf 'old\n' > "$app_dir/app.jar"
rollback_failure_release="$incoming_root/rollback-failure"
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
test "$(find "$diagnostic_root" -maxdepth 1 -type f -name 'backend-*.log' | wc -l)" -le 20
while IFS= read -r diagnostic_file; do
  test "$(stat -c %s "$diagnostic_file")" -le 1048576
done < <(find "$diagnostic_root" -maxdepth 1 -type f -name 'backend-*.log')

printf 'old\n' > "$app_dir/app.jar"
wrong_sha_release="$incoming_root/wrong-rollback-sha"
mkdir -p "$wrong_sha_release"
printf 'new\n' > "$wrong_sha_release/app.jar"
touch "$state_dir/fail_new_health" "$state_dir/arm_wrong_rollback_sha"
set +e
run_activation "$wrong_sha_release"
status=$?
set -e
if [ "$status" -ne 2 ]; then
  echo "Expected a healthy rollback with the wrong SHA to fail, got $status" >&2
  exit 1
fi
rm "$state_dir/fail_new_health" "$state_dir/arm_wrong_rollback_sha" "$state_dir/wrong_rollback_sha"

for index in 1 2 3 4 5 6; do
  old_release="$release_root/old-$index"
  mkdir -p "$old_release"
  touch -d "2026-07-$((10 + index))" "$old_release"
done
retention_release="$incoming_root/current"
mkdir -p "$retention_release"
printf 'new\n' > "$retention_release/app.jar"
run_activation "$retention_release"
release_count="$(find "$release_root" -mindepth 1 -maxdepth 1 -type d | wc -l)"
test "$release_count" -eq 5
test -d "$release_root/current"

mkdir -p "$release_root/cleanup-victim"
touch -d '2020-01-01' "$release_root/cleanup-victim"
touch "$state_dir/fail_cleanup"
cleanup_debt_release="$incoming_root/cleanup-debt"
mkdir -p "$cleanup_debt_release"
printf 'new\n' > "$cleanup_debt_release/app.jar"
cleanup_output="$(run_activation "$cleanup_debt_release" 2>&1)"
grep -Fqx 'ACTIVATED_SHA=cleanup-debt' <<< "$cleanup_output"
grep -Fq 'CLEANUP_DEBT=backend_release_retention' <<< "$cleanup_output"
test -d "$release_root/cleanup-victim"
rm "$state_dir/fail_cleanup"

touch "$state_dir/arm_fail_cleanup_listing"
listing_debt_release="$incoming_root/listing-debt"
mkdir -p "$listing_debt_release"
printf 'new\n' > "$listing_debt_release/app.jar"
listing_output="$(run_activation "$listing_debt_release" 2>&1)"
grep -Fqx 'ACTIVATED_SHA=listing-debt' <<< "$listing_output"
grep -Fq 'CLEANUP_DEBT=backend_release_retention' <<< "$listing_output"
rm "$state_dir/arm_fail_cleanup_listing" "$state_dir/fail_cleanup_listing"

printf 'old\n' > "$app_dir/app.jar"
move_failure_release="$incoming_root/move-failure"
mkdir -p "$move_failure_release"
printf 'new\n' > "$move_failure_release/app.jar"
touch "$state_dir/fail_activate_move"
if run_activation "$move_failure_release"; then
  echo "Expected activation move failure" >&2
  exit 1
fi
grep -qx old "$app_dir/app.jar"
grep -qx active "$state_dir/service"
rm "$state_dir/fail_activate_move"

outside_release="$fixture/outside"
mkdir -p "$outside_release"
printf 'new\n' > "$outside_release/app.jar"
if run_activation "$outside_release"; then
  echo "Expected outside release rejection" >&2
  exit 1
fi
test -d "$outside_release"

echo "Backend activation fixtures passed"
