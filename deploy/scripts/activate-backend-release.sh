#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
INCOMING_ROOT="${INCOMING_ROOT:-$APP_DIR/incoming}"
RELEASE_ROOT="${RELEASE_ROOT:-$APP_DIR/releases}"
SERVICE_NAME="${SERVICE_NAME:-app}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-30}"
HEALTH_DELAY_SECONDS="${HEALTH_DELAY_SECONDS:-2}"
LOG_DIR="${LOG_DIR:-/opt/app/logs}"
DIAGNOSTIC_ROOT="${DIAGNOSTIC_ROOT:-/var/lib/noviis/deployment-diagnostics}"
DIAGNOSTIC_MAX_FILES="${DIAGNOSTIC_MAX_FILES:-20}"
DIAGNOSTIC_MAX_FILE_BYTES="${DIAGNOSTIC_MAX_FILE_BYTES:-1048576}"
DIAGNOSTIC_RETENTION_DAYS="${DIAGNOSTIC_RETENTION_DAYS:-14}"
ENV_FILE="${ENV_FILE:-/etc/noviis/app.env}"
ENV_FILE_OWNER="${ENV_FILE_OWNER:-root:root}"
ENV_FILE_MODE="${ENV_FILE_MODE:-600}"
PROVENANCE_VERIFIER="${PROVENANCE_VERIFIER:-/usr/local/sbin/verify-noviis-release}"
DEPLOY_LOCK_FILE="${DEPLOY_LOCK_FILE:-/run/lock/noviis-deploy.lock}"
SOURCE_DIR="${1:?incoming release directory is required}"
EXPECTED_COMMIT="${2:-${EXPECTED_COMMIT:-}}"

command -v flock >/dev/null 2>&1 || { echo "flock is required for activation locking" >&2; exit 69; }
exec 9>"$DEPLOY_LOCK_FILE"
if ! flock -n 9; then
  echo "Another NoviIs activation is already running" >&2
  exit 75
fi

activated=false
service_stopped=false
rollback_available=false
rollback_in_progress=false
completed=false
activation_verified=false
activated_sha=""
staging_dir=""
release_real=""
failure_phase="preflight"
previous_commit=""
previous_digest=""
ROLLBACK_STATE_FILE="$APP_DIR/app.jar.rollback.state"

prune_diagnostics() {
  local listing entry path path_real keep_count=0
  local diagnostic_root_real
  diagnostic_root_real="$(realpath "$DIAGNOSTIC_ROOT")" || return 0
  sudo find "$diagnostic_root_real" -mindepth 1 -maxdepth 1 -type f -name 'backend-*.log' \
    -mtime "+$DIAGNOSTIC_RETENTION_DAYS" -delete 2>/dev/null || true
  listing="$(mktemp)" || return 0
  sudo find "$diagnostic_root_real" -mindepth 1 -maxdepth 1 -type f -name 'backend-*.log' \
    -printf '%T@ %p\0' 2>/dev/null | sort -z -nr > "$listing" || true
  while IFS= read -r -d '' entry; do
    path="${entry#* }"
    path_real="$(realpath "$path")" || continue
    case "$path_real/" in "$diagnostic_root_real"/*/) ;; *) continue ;; esac
    keep_count=$((keep_count + 1))
    if [ "$keep_count" -gt "$DIAGNOSTIC_MAX_FILES" ]; then sudo rm -f -- "$path_real" || true; fi
  done < "$listing"
  rm -f -- "$listing"
}

diagnose() {
  local diagnostic_file
  local diagnostic_name
  local diagnostic_trimmed
  diagnostic_name="backend-${release_id:-unknown}-$(date -u +%Y%m%dT%H%M%SZ)-$$.log"
  if ! sudo install -d -o root -g root -m 0700 "$DIAGNOSTIC_ROOT" 2>/dev/null; then
    echo "Backend activation diagnostic directory could not be secured (phase=$failure_phase)" >&2
    return 0
  fi
  diagnostic_file="$DIAGNOSTIC_ROOT/$diagnostic_name"
  if ! {
    printf 'timestamp=%s\nrelease_id=%s\nphase=%s\n' \
      "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${release_id:-unknown}" "$failure_phase"
    sudo systemctl show "$SERVICE_NAME" \
      --property=ActiveState,SubState,Result,ExecMainCode,ExecMainStatus,NRestarts || true
    sudo journalctl -u "$SERVICE_NAME" -n 80 --no-pager || true
    sudo tail -n 120 "$LOG_DIR/whiteboard-active.log" || true
    sudo tail -n 120 "$LOG_DIR/whiteboard-error.log" || true
  } 2>&1 | sudo tee "$diagnostic_file" >/dev/null; then
    sudo rm -f -- "$diagnostic_file" 2>/dev/null || true
    echo "Backend activation diagnostic could not be stored (phase=$failure_phase)" >&2
    return 0
  fi
  if ! sudo chown root:root "$diagnostic_file" || ! sudo chmod 0600 "$diagnostic_file"; then
    sudo rm -f -- "$diagnostic_file" 2>/dev/null || true
    echo "Backend activation diagnostic could not be secured (phase=$failure_phase)" >&2
    return 0
  fi
  if [ "$(sudo stat -c %s "$diagnostic_file")" -gt "$DIAGNOSTIC_MAX_FILE_BYTES" ]; then
    diagnostic_trimmed="$DIAGNOSTIC_ROOT/.backend-diagnostic-trimmed.$$"
    if sudo tail -c "$DIAGNOSTIC_MAX_FILE_BYTES" "$diagnostic_file" | sudo tee "$diagnostic_trimmed" >/dev/null; then
      sudo chown root:root "$diagnostic_trimmed" && sudo chmod 0600 "$diagnostic_trimmed" \
        && sudo mv -Tf "$diagnostic_trimmed" "$diagnostic_file" || true
    fi
    sudo rm -f -- "$diagnostic_trimmed" 2>/dev/null || true
  fi
  prune_diagnostics
  echo "Backend activation diagnostic stored locally: $diagnostic_name (phase=$failure_phase)" >&2
}

wait_for_health() {
  local expected_commit="${1:-}"
  local attempt
  for attempt in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null; then
      if [ -z "$expected_commit" ] || curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" | grep -Fq -- "\"commit\":\"$expected_commit\""; then
        return 0
      fi
    fi
    sleep "$HEALTH_DELAY_SECONDS"
  done
  return 1
}

rollback() {
  local original_status="$1"
  local actual_digest=""
  local state_commit=""
  local state_digest=""
  if [ "$completed" = true ] || [ "$rollback_in_progress" = true ]; then
    return "$original_status"
  fi

  rollback_in_progress=true
  set +e
  echo "Backend activation failed; starting rollback" >&2
  if [ "$service_stopped" = true ] || [ "$activated" = true ]; then diagnose; fi

  if [ "$activation_verified" = true ]; then
    echo "Activation was already verified; leaving the healthy release active after a maintenance failure" >&2
  elif [ "$service_stopped" = true ]; then
    local restore_status=0
    if [ "$activated" = true ]; then
      if [ "$rollback_available" = true ]; then
        if ! sudo test -f "$ROLLBACK_STATE_FILE" || sudo test -L "$ROLLBACK_STATE_FILE"; then
          echo "Rollback state is missing or unsafe" >&2
          restore_status=1
        else
          state_commit="$(sudo sed -n 's/^commit_sha=//p' "$ROLLBACK_STATE_FILE")"
          state_digest="$(sudo sed -n 's/^jar_sha256=//p' "$ROLLBACK_STATE_FILE")"
          actual_digest="$(sudo sha256sum "$APP_DIR/app.jar.rollback" | awk '{print $1}')"
          if [[ ! "$state_commit" =~ ^[0-9a-f]{40}$ ]] || [[ ! "$state_digest" =~ ^[0-9a-f]{64}$ ]] \
            || [ "$actual_digest" != "$state_digest" ]; then
            echo "Rollback JAR does not match the recorded commit and digest" >&2
            restore_status=1
          else
            previous_commit="$state_commit"
            sudo install -m 0644 "$APP_DIR/app.jar.rollback" "$APP_DIR/app.jar"
            restore_status=$?
          fi
        fi
      else
        echo "No previous JAR is available for rollback" >&2
        sudo systemctl stop "$SERVICE_NAME" || true
        return "$original_status"
      fi
    fi
    if [ "$restore_status" -eq 0 ]; then
      sudo systemctl daemon-reload
      sudo systemctl restart "$SERVICE_NAME"
      restore_status=$?
    fi
    if [ "$restore_status" -eq 0 ]; then
      wait_for_health "$previous_commit"
      restore_status=$?
    fi
    if [ "$restore_status" -ne 0 ]; then
      echo "Backend rollback failed" >&2
      diagnose
      exit 2
    fi
    echo "Backend rollback completed" >&2
  fi

  return "$original_status"
}

on_exit() {
  local status="$?"
  trap - EXIT INT TERM HUP
  if [ -n "$staging_dir" ] && [ -d "$staging_dir" ]; then
    rm -rf -- "$staging_dir"
  fi
  if [ "$status" -ne 0 ]; then
    rollback "$status" || status=$?
  fi
  exit "$status"
}

trap on_exit EXIT
trap 'exit 130' INT TERM HUP

incoming_root_real="$(realpath "$INCOMING_ROOT")"
source_real="$(realpath "$SOURCE_DIR")"
case "$source_real/" in
  "$incoming_root_real"/*/) ;;
  *) echo "Incoming release directory is outside incoming root: $source_real" >&2; exit 1 ;;
esac

release_id="$(basename "$source_real")"
if [[ ! "$release_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]]; then
  echo "Invalid release identifier: $release_id" >&2
  exit 1
fi

release_root_real="$(realpath "$RELEASE_ROOT")"
release_real="$release_root_real/$release_id"
test ! -e "$release_real"
staging_dir="$(mktemp -d "$release_root_real/.backend-release.XXXXXX")"

file_count=0
while IFS= read -r -d '' source_file; do
  test ! -L "$source_file"
  install -m 0644 "$source_file" "$staging_dir/$(basename "$source_file")"
  file_count=$((file_count + 1))
done < <(find "$source_real" -mindepth 1 -maxdepth 1 -type f -print0)
test "$file_count" -gt 0

"$PROVENANCE_VERIFIER" "$staging_dir" "$EXPECTED_COMMIT" backend

mapfile -d '' jars < <(find "$staging_dir" -maxdepth 1 -type f -name '*.jar' -print0)
if [ "${#jars[@]}" -ne 1 ]; then
  echo "Expected exactly one release JAR, found ${#jars[@]}" >&2
  exit 1
fi
test -f "$staging_dir/SHA256SUMS"
(cd "$staging_dir" && sha256sum --strict --check SHA256SUMS)
if [ -n "$EXPECTED_COMMIT" ]; then
  test -f "$staging_dir/RELEASE_METADATA"
  grep -Fqx -- "commit_sha=$EXPECTED_COMMIT" "$staging_dir/RELEASE_METADATA"
fi
mv -T "$staging_dir" "$release_real"
staging_dir=""
jars=("$release_real/$(basename "${jars[0]}")")

sudo test -d "$LOG_DIR"
sudo test -f "$ENV_FILE"
sudo test -s "$ENV_FILE"
sudo test ! -L "$ENV_FILE"
sudo test "$(sudo stat -c %U:%G "$ENV_FILE")" = "$ENV_FILE_OWNER"
sudo test "$(sudo stat -c %a "$ENV_FILE")" = "$ENV_FILE_MODE"
sudo test -w "$APP_DIR"

if [ -f "$APP_DIR/app.jar" ]; then
  previous_commit="$(curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" \
    | sed -n 's/.*"commit":"\([0-9a-f]\{40\}\)".*/\1/p')"
  if [[ ! "$previous_commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Current backend commit cannot be proven before activation" >&2
    exit 1
  fi
  previous_digest="$(sudo sha256sum "$APP_DIR/app.jar" | awk '{print $1}')"
  sudo install -m 0644 "$APP_DIR/app.jar" "$APP_DIR/app.jar.rollback"
  rollback_state_tmp="$(mktemp "$release_root_real/.backend-rollback-state.XXXXXX")"
  printf 'commit_sha=%s\njar_sha256=%s\n' "$previous_commit" "$previous_digest" > "$rollback_state_tmp"
  chmod 0600 "$rollback_state_tmp"
  sudo install -m 0600 "$rollback_state_tmp" "$ROLLBACK_STATE_FILE"
  rm -f -- "$rollback_state_tmp"
  rollback_available=true
fi

sudo install -m 0644 "${jars[0]}" "$APP_DIR/app.jar.next"
failure_phase="stop-service"
sudo systemctl stop "$SERVICE_NAME"
service_stopped=true
if sudo systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "Service remained active after stop" >&2
  exit 1
fi
sudo mv "$APP_DIR/app.jar.next" "$APP_DIR/app.jar"
activated=true
failure_phase="start-service"
sudo systemctl daemon-reload
sudo systemctl start "$SERVICE_NAME"
sudo systemctl is-active --quiet "$SERVICE_NAME"
failure_phase="verify-health"
wait_for_health "$EXPECTED_COMMIT"
activation_verified=true
activated_sha="${EXPECTED_COMMIT:-$release_id}"
completed=true
echo "ACTIVATED_SHA=$activated_sha"

cleanup_releases() {
  declare -A keep=()
  keep["$release_real"]=1
  local keep_count=1 entry path path_real
  local failed=false
  local listing
  local -a releases=()
  if ! listing="$(mktemp "$release_root_real/.backend-cleanup-list.XXXXXX")"; then
    return 1
  fi
  if ! chmod 0600 "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! find "$release_root_real" -mindepth 1 -maxdepth 1 -type d ! -name '.backend-release.*' -printf '%T@ %p\0' | sort -z -nr > "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! mapfile -d '' releases < "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! rm -f -- "$listing"; then
    return 1
  fi
  for entry in "${releases[@]}"; do
    path="${entry#* }"
    if ! path_real="$(realpath "$path")"; then failed=true; continue; fi
    [ "$path_real" = "$release_real" ] && continue
    if [ "$keep_count" -lt 5 ]; then
      keep["$path_real"]=1
      keep_count=$((keep_count + 1))
    fi
  done
  for entry in "${releases[@]}"; do
    path="${entry#* }"
    if ! path_real="$(realpath "$path")"; then failed=true; continue; fi
    case "$path_real/" in "$release_root_real"/*/) ;; *) echo "Refusing to remove path outside release root: $path_real" >&2; return 1 ;; esac
    if [ -z "${keep[$path_real]+x}" ] && ! rm -rf -- "$path_real"; then failed=true; fi
  done
  [ "$failed" = false ]
}

if ! cleanup_releases; then
  echo "CLEANUP_DEBT=backend_release_retention" >&2
fi
if ! sudo systemctl show "$SERVICE_NAME" \
  --property=ActiveState,SubState,Result,ExecMainCode,ExecMainStatus,NRestarts; then
  echo "CLEANUP_DEBT=backend_status_diagnostic" >&2
fi
if ! rm -rf -- "$source_real"; then
  echo "CLEANUP_DEBT=backend_incoming_release" >&2
fi
echo "Backend release activated: $release_real"
