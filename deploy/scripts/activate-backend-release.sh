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
EXPECTED_RUN_ID="${3:-${EXPECTED_RUN_ID:-}}"
EXPECTED_RUN_ATTEMPT="${4:-${EXPECTED_RUN_ATTEMPT:-}}"
EXPECTED_RUN_NUMBER="${5:-${EXPECTED_RUN_NUMBER:-}}"
STABILITY_SUCCESS_COUNT="${STABILITY_SUCCESS_COUNT:-3}"
STABILITY_DELAY_SECONDS="${STABILITY_DELAY_SECONDS:-5}"
PENDING_LEASE_SECONDS="${PENDING_LEASE_SECONDS:-600}"
ROLLBACK_AUTH_FILE="${ROLLBACK_AUTH_FILE:-/var/lib/noviis/deployment-state/backend-rollback.allow}"
GENERATION_HIGH_WATER_FILE="${GENERATION_HIGH_WATER_FILE:-/var/lib/noviis/deployment-state/backend-generation.state}"
GENERATION_STATE_OWNER="${GENERATION_STATE_OWNER:-root:root}"
BACKEND_ACTIVATION_LEASE_FILE="${BACKEND_ACTIVATION_LEASE_FILE:-/run/lock/noviis-backend-activation.lease}"
CLEANUP_DEBT_WRITER="${CLEANUP_DEBT_WRITER:-/usr/local/sbin/record-noviis-cleanup-debt}"
CONTRACT_RECOVERY_STATE_FILE="${CONTRACT_RECOVERY_STATE_FILE:-/var/lib/noviis/deployment-state/backend-contract-recovery.state}"

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
contract_migration=false
contract_start_attempted=false

generate_activation_nonce() {
  if [ -r /proc/sys/kernel/random/uuid ]; then
    tr -d '-' < /proc/sys/kernel/random/uuid | cut -c1-32
    return
  fi
  command -v openssl >/dev/null 2>&1 || {
    echo "A cryptographically secure nonce generator is required" >&2
    return 1
  }
  openssl rand -hex 16
}
activated_sha=""
staging_dir=""
release_real=""
failure_phase="preflight"
previous_commit=""
previous_digest=""
previous_run_id="0"
previous_run_attempt="0"
previous_run_number="0"
previous_envelope_digest=""
previous_api_contract_revision="legacy"
ROLLBACK_STATE_FILE="$APP_DIR/app.jar.rollback.state"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-$APP_DIR/app.jar.active.state}"
ACTIVE_STATE_FILE_OWNER="${ACTIVE_STATE_FILE_OWNER:-root:root}"
ACTIVE_STATE_FILE_MODE="${ACTIVE_STATE_FILE_MODE:-600}"

write_active_state() {
  local commit_sha="$1"
  local jar_sha256="$2"
  local run_id="${3:-0}"
  local run_attempt="${4:-0}"
  local run_number="${5:-0}"
  local envelope_sha256="${6:-$jar_sha256}"
  local api_contract_revision="${7:-legacy}"
  local phase="${8:-stable}"
  local activation_nonce="${9:-none}"
  local activation_issued_at="${10:-0}"
  local activation_expires_at="${11:-0}"
  local state_tmp
  [[ "$commit_sha" =~ ^[0-9a-f]{40}$ ]] || return 1
  [[ "$jar_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  [[ "$run_id" =~ ^[0-9]+$ ]] || return 1
  [[ "$run_attempt" =~ ^[1-9][0-9]*$|^0$ ]] || return 1
  [[ "$run_number" =~ ^[0-9]+$ ]] || return 1
  [[ "$envelope_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  [[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || return 1
  [[ "$phase" = pending || "$phase" = stable ]] || return 1
  if [ "$phase" = pending ]; then
    [[ "$activation_nonce" =~ ^[0-9a-f]{32}$ ]] || return 1
    [[ "$activation_issued_at" =~ ^[0-9]+$ && "$activation_expires_at" =~ ^[0-9]+$ ]] || return 1
    [ "$activation_expires_at" -gt "$activation_issued_at" ] || return 1
  else
    activation_nonce=none
    activation_issued_at=0
    activation_expires_at=0
  fi
  state_tmp="$(mktemp "$release_root_real/.backend-active-state.XXXXXX")" || return 1
  if ! printf 'commit_sha=%s\njar_sha256=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\napi_contract_revision=%s\nphase=%s\nactivation_nonce=%s\nactivation_issued_at=%s\nactivation_expires_at=%s\n' \
      "$commit_sha" "$jar_sha256" "$run_id" "$run_number" "$run_attempt" "$envelope_sha256" "$api_contract_revision" "$phase" \
      "$activation_nonce" "$activation_issued_at" "$activation_expires_at" > "$state_tmp" \
    || ! chmod 0600 "$state_tmp" \
    || ! sudo install -o root -g root -m 0600 "$state_tmp" "$ACTIVE_STATE_FILE"; then
    rm -f -- "$state_tmp"
    return 1
  fi
  rm -f -- "$state_tmp"
}

write_pending_active_state() {
  local now expires nonce lease_dir lease_tmp
  now="$(date +%s)"
  expires=$((now + PENDING_LEASE_SECONDS))
  nonce="$(generate_activation_nonce)" || return 1
  { exec 8>&-; } 2>/dev/null || true
  lease_dir="$(dirname "$BACKEND_ACTIVATION_LEASE_FILE")"
  sudo install -d -o root -g root -m 0755 "$lease_dir"
  lease_tmp="$(mktemp "$release_root_real/.backend-lease.XXXXXX")"
  printf '%s\n' "$nonce" > "$lease_tmp"
  chmod 0600 "$lease_tmp"
  sudo install -o root -g root -m 0600 "$lease_tmp" "$BACKEND_ACTIVATION_LEASE_FILE"
  rm -f -- "$lease_tmp"
  exec 8<>"$BACKEND_ACTIVATION_LEASE_FILE"
  flock -n 8 || return 1
  write_active_state "$1" "$2" "$3" "$4" "$5" "$6" "$7" pending "$nonce" "$now" "$expires"
}

write_contract_recovery_state() {
  local directory temporary
  directory="$(dirname "$CONTRACT_RECOVERY_STATE_FILE")"
  sudo install -d -o root -g root -m 0700 "$directory"
  temporary="$(mktemp "$release_root_real/.backend-contract-recovery.XXXXXX")"
  printf 'commit_sha=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\nfailure_phase=%s\nrecovery=database-contract-review-required\n' \
    "$EXPECTED_COMMIT" "$release_run_id" "$release_run_number" "$release_run_attempt" \
    "$release_envelope_digest" "$failure_phase" > "$temporary"
  chmod 0600 "$temporary"
  sudo install -o root -g root -m 0600 "$temporary" "$CONTRACT_RECOVERY_STATE_FILE"
  rm -f -- "$temporary"
}

release_pending_lease() {
  { exec 8>&-; } 2>/dev/null || true
  sudo rm -f -- "$BACKEND_ACTIVATION_LEASE_FILE" 2>/dev/null || true
}

read_state_value() {
  local file="$1"
  local key="$2"
  sudo sed -n "s/^${key}=//p" "$file"
}

write_generation_high_water() {
  local repository="$1" commit_sha="$2" run_id="$3" run_number="$4" run_attempt="$5" envelope_sha256="$6"
  local directory temporary
  [[ "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ && "$commit_sha" =~ ^[0-9a-f]{40}$ \
    && "$run_id" =~ ^[0-9]+$ && "$run_number" =~ ^[0-9]+$ && "$run_attempt" =~ ^[0-9]+$ \
    && "$envelope_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  directory="$(dirname "$GENERATION_HIGH_WATER_FILE")"
  sudo install -d -o root -g root -m 0700 "$directory"
  temporary="$(mktemp "$release_root_real/.backend-generation.XXXXXX")"
  printf 'repository=%s\ncommit_sha=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\n' \
    "$repository" "$commit_sha" "$run_id" "$run_number" "$run_attempt" "$envelope_sha256" > "$temporary"
  chmod 0600 "$temporary"
  sudo install -o root -g root -m 0600 "$temporary" "$GENERATION_HIGH_WATER_FILE"
  rm -f -- "$temporary"
}

authorize_release_generation() {
  local target_repository="$1" target_commit="$2" target_run_id="$3" target_run_number="$4" target_attempt="$5" target_envelope="$6"
  local current_run_number=0 current_attempt=0 authorization_id issued_at expires_at reason now line_count
  if sudo test -f "$GENERATION_HIGH_WATER_FILE"; then
    sudo test ! -L "$GENERATION_HIGH_WATER_FILE"
    [ "$(sudo stat -c %U:%G "$GENERATION_HIGH_WATER_FILE")" = "$GENERATION_STATE_OWNER" ]
    [ "$(sudo stat -c %a "$GENERATION_HIGH_WATER_FILE")" = 600 ]
    current_run_number="$(read_state_value "$GENERATION_HIGH_WATER_FILE" run_number)"
    current_attempt="$(read_state_value "$GENERATION_HIGH_WATER_FILE" run_attempt)"
    [[ "$current_run_number" =~ ^[0-9]+$ && "$current_attempt" =~ ^[0-9]+$ ]] || return 1
  fi
  if [ "$target_run_number" -gt "$current_run_number" ] \
    || { [ "$target_run_number" -eq "$current_run_number" ] && [ "$target_attempt" -gt "$current_attempt" ]; }; then
    write_generation_high_water "$target_repository" "$target_commit" "$target_run_id" "$target_run_number" "$target_attempt" "$target_envelope"
    return 0
  fi
  if ! sudo test -f "$ROLLBACK_AUTH_FILE" || sudo test -L "$ROLLBACK_AUTH_FILE" \
    || [ "$(sudo stat -c %U:%G "$ROLLBACK_AUTH_FILE")" != "$GENERATION_STATE_OWNER" ] \
    || [ "$(sudo stat -c %a "$ROLLBACK_AUTH_FILE")" != 600 ]; then
    echo "Release generation is not newer and no valid root break-glass authorization exists" >&2
    return 1
  fi
  line_count="$(sudo wc -l "$ROLLBACK_AUTH_FILE" | awk '{print $1}')"
  authorization_id="$(read_state_value "$ROLLBACK_AUTH_FILE" authorization_id 2>/dev/null || true)"
  issued_at="$(read_state_value "$ROLLBACK_AUTH_FILE" issued_at 2>/dev/null || true)"
  expires_at="$(read_state_value "$ROLLBACK_AUTH_FILE" expires_at 2>/dev/null || true)"
  reason="$(read_state_value "$ROLLBACK_AUTH_FILE" reason 2>/dev/null || true)"
  now="$(date +%s)"
  if [ "$line_count" != 10 ] \
    || ! sudo grep -Fqx -- "repository=$target_repository" "$ROLLBACK_AUTH_FILE" \
    || ! sudo grep -Fqx -- "target_commit=$target_commit" "$ROLLBACK_AUTH_FILE" \
    || ! sudo grep -Fqx -- "target_run_id=$target_run_id" "$ROLLBACK_AUTH_FILE" \
    || ! sudo grep -Fqx -- "target_run_number=$target_run_number" "$ROLLBACK_AUTH_FILE" \
    || ! sudo grep -Fqx -- "target_run_attempt=$target_attempt" "$ROLLBACK_AUTH_FILE" \
    || ! sudo grep -Fqx -- "release_envelope_sha256=$target_envelope" "$ROLLBACK_AUTH_FILE" \
    || [[ ! "$authorization_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$ ]] \
    || [[ ! "$issued_at" =~ ^[0-9]+$ || ! "$expires_at" =~ ^[0-9]+$ ]] \
    || [ "$issued_at" -gt "$now" ] || [ "$expires_at" -le "$now" ] || [ $((expires_at - issued_at)) -gt 3600 ] \
    || [ "${#reason}" -lt 8 ] || [ "${#reason}" -gt 256 ]; then
    echo "Release generation is not newer and no valid root break-glass authorization exists" >&2
    return 1
  fi
  sudo rm -f -- "$ROLLBACK_AUTH_FILE"
  echo "Root break-glass authorization $authorization_id consumed for backend release generation $target_run_number/$target_attempt" >&2
}

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
      if sudo chown root:root "$diagnostic_trimmed" && sudo chmod 0600 "$diagnostic_trimmed"; then
        sudo mv -Tf "$diagnostic_trimmed" "$diagnostic_file" || true
      fi
    fi
    sudo rm -f -- "$diagnostic_trimmed" 2>/dev/null || true
  fi
  prune_diagnostics
  echo "Backend activation diagnostic stored locally: $diagnostic_name (phase=$failure_phase)" >&2
}

wait_for_health() {
  local expected_commit="${1:-}"
  for _ in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null; then
      if [ -z "$expected_commit" ] || curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" | grep -Fq -- "\"commit\":\"$expected_commit\""; then
        return 0
      fi
    fi
    sleep "$HEALTH_DELAY_SECONDS"
  done
  return 1
}

wait_for_stability() {
  local expected_commit="$1"
  local success initial_pid initial_restarts initial_started current_pid current_restarts current_started
  initial_pid="$(sudo systemctl show "$SERVICE_NAME" --property=MainPID --value)"
  initial_restarts="$(sudo systemctl show "$SERVICE_NAME" --property=NRestarts --value)"
  initial_started="$(sudo systemctl show "$SERVICE_NAME" --property=ExecMainStartTimestampMonotonic --value)"
  [[ "$initial_pid" =~ ^[1-9][0-9]*$ && "$initial_restarts" =~ ^[0-9]+$ && "$initial_started" =~ ^[1-9][0-9]*$ ]] || return 1
  for success in $(seq 1 "$STABILITY_SUCCESS_COUNT"); do
    wait_for_health "$expected_commit" || return 1
    current_pid="$(sudo systemctl show "$SERVICE_NAME" --property=MainPID --value)"
    current_restarts="$(sudo systemctl show "$SERVICE_NAME" --property=NRestarts --value)"
    current_started="$(sudo systemctl show "$SERVICE_NAME" --property=ExecMainStartTimestampMonotonic --value)"
    [ "$current_pid" = "$initial_pid" ] && [ "$current_restarts" = "$initial_restarts" ] \
      && [ "$current_started" = "$initial_started" ] || return 1
    if [ "$success" -lt "$STABILITY_SUCCESS_COUNT" ]; then sleep "$STABILITY_DELAY_SECONDS"; fi
  done
}

rollback() {
  local original_status="$1"
  local actual_digest=""
  local state_commit=""
  local state_digest=""
  local state_run_id="0"
  local state_run_attempt="0"
  local state_run_number="0"
  local state_envelope_digest=""
  local state_api_contract_revision="legacy"
  if [ "$completed" = true ] || [ "$rollback_in_progress" = true ]; then
    return "$original_status"
  fi

  rollback_in_progress=true
  set +e
  echo "Backend activation failed; starting rollback" >&2
  if [ "$service_stopped" = true ] || [ "$activated" = true ]; then diagnose; fi

  if [ "$activation_verified" = true ]; then
    echo "Activation was already verified; leaving the healthy release active after a maintenance failure" >&2
  elif [ "$contract_migration" = true ] && [ "$contract_start_attempted" = true ]; then
    echo "Contract migration activation failed after the new service start was attempted; refusing to start the previous JAR" >&2
    sudo systemctl stop "$SERVICE_NAME" || true
    if ! write_contract_recovery_state; then
      echo "Contract migration recovery state could not be persisted" >&2
      diagnose
      exit 2
    fi
    echo "RECOVERY_REQUIRED=database_contract" >&2
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
            state_run_id="$(sudo sed -n 's/^run_id=//p' "$ROLLBACK_STATE_FILE")"
            state_run_attempt="$(sudo sed -n 's/^run_attempt=//p' "$ROLLBACK_STATE_FILE")"
            state_run_number="$(sudo sed -n 's/^run_number=//p' "$ROLLBACK_STATE_FILE")"
            state_envelope_digest="$(sudo sed -n 's/^release_envelope_sha256=//p' "$ROLLBACK_STATE_FILE")"
            state_api_contract_revision="$(sudo sed -n 's/^api_contract_revision=//p' "$ROLLBACK_STATE_FILE")"
          actual_digest="$(sudo sha256sum "$APP_DIR/app.jar.rollback" | awk '{print $1}')"
          if [[ ! "$state_commit" =~ ^[0-9a-f]{40}$ ]] || [[ ! "$state_digest" =~ ^[0-9a-f]{64}$ ]] \
            || [ "$actual_digest" != "$state_digest" ]; then
            echo "Rollback JAR does not match the recorded commit and digest" >&2
            restore_status=1
          else
            previous_commit="$state_commit"
            [[ "$state_run_id" =~ ^[0-9]+$ ]] && previous_run_id="$state_run_id"
            [[ "$state_run_attempt" =~ ^[0-9]+$ ]] && previous_run_attempt="$state_run_attempt"
            [[ "$state_run_number" =~ ^[0-9]+$ ]] && previous_run_number="$state_run_number"
            [[ "$state_envelope_digest" =~ ^[0-9a-f]{64}$ ]] && previous_envelope_digest="$state_envelope_digest"
            [[ "$state_api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
              && previous_api_contract_revision="$state_api_contract_revision"
            sudo install -m 0644 "$APP_DIR/app.jar.rollback" "$APP_DIR/app.jar"
            restore_status=$?
          fi
        fi
      else
        echo "No previous JAR is available for rollback" >&2
        sudo systemctl stop "$SERVICE_NAME" || true
        return "$original_status"
      fi
    else
      state_digest="$previous_digest"
      state_run_id="$previous_run_id"
      state_run_attempt="$previous_run_attempt"
      state_run_number="$previous_run_number"
      state_envelope_digest="$previous_envelope_digest"
      state_api_contract_revision="$previous_api_contract_revision"
    fi
    [[ "$previous_envelope_digest" =~ ^[0-9a-f]{64}$ ]] || previous_envelope_digest="$state_digest"
    if [ "$restore_status" -eq 0 ]; then
      write_pending_active_state "$previous_commit" "$state_digest" "$previous_run_id" "$previous_run_attempt" "$previous_run_number" \
        "${previous_envelope_digest:-$state_digest}" "$previous_api_contract_revision"
      restore_status=$?
    fi
    if [ "$restore_status" -eq 0 ]; then
      sudo systemctl daemon-reload
      sudo systemctl restart "$SERVICE_NAME"
      restore_status=$?
    fi
    if [ "$restore_status" -eq 0 ]; then
      wait_for_stability "$previous_commit"
      restore_status=$?
    fi
    if [ "$restore_status" -eq 0 ]; then
      actual_digest="$(sudo sha256sum "$APP_DIR/app.jar" | awk '{print $1}')"
      write_active_state "$previous_commit" "$actual_digest" "$previous_run_id" "$previous_run_attempt" "$previous_run_number" \
        "$previous_envelope_digest" "$previous_api_contract_revision" stable
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
  release_pending_lease
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

release_run_id="${EXPECTED_RUN_ID:-0}"
release_run_attempt="${EXPECTED_RUN_ATTEMPT:-0}"
release_run_number="${EXPECTED_RUN_NUMBER:-0}"
api_contract_revision="legacy"
release_repository="legacy/local"
release_envelope_digest=""
if [ -n "$EXPECTED_RUN_ID" ] || [ -n "$EXPECTED_RUN_ATTEMPT" ] || [ -n "$EXPECTED_RUN_NUMBER" ]; then
  [[ "$EXPECTED_RUN_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Expected run id is invalid" >&2; exit 64; }
  [[ "$EXPECTED_RUN_ATTEMPT" =~ ^[1-9][0-9]*$ ]] || { echo "Expected run attempt is invalid" >&2; exit 64; }
  [[ "$EXPECTED_RUN_NUMBER" =~ ^[1-9][0-9]*$ ]] || { echo "Expected run number is invalid" >&2; exit 64; }
  grep -Fqx -- "run_id=$EXPECTED_RUN_ID" "$staging_dir/RELEASE_METADATA"
  grep -Fqx -- "run_attempt=$EXPECTED_RUN_ATTEMPT" "$staging_dir/RELEASE_METADATA"
  grep -Fqx -- "run_number=$EXPECTED_RUN_NUMBER" "$staging_dir/RELEASE_METADATA"
  api_contract_revision="$(sed -n 's/^api_contract_revision=//p' "$staging_dir/RELEASE_METADATA")"
  release_repository="$(sed -n 's/^repository=//p' "$staging_dir/RELEASE_METADATA")"
  contract_migration="$(sed -n 's/^contract_migration=//p' "$staging_dir/RELEASE_METADATA")"
  [ -n "$contract_migration" ] || contract_migration=false
  [ -n "$release_repository" ] || release_repository="legacy/local"
  [[ "$release_repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "Release repository is invalid" >&2; exit 1; }
  [[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
    || { echo "Release API contract revision is invalid" >&2; exit 1; }
  case "$contract_migration" in true|false) ;; *) echo "Release contract migration flag is invalid" >&2; exit 1 ;; esac
fi

mapfile -d '' jars < <(find "$staging_dir" -maxdepth 1 -type f -name '*.jar' -print0)
if [ "${#jars[@]}" -ne 1 ]; then
  echo "Expected exactly one release JAR, found ${#jars[@]}" >&2
  exit 1
fi
test -f "$staging_dir/SHA256SUMS"
(cd "$staging_dir" && sha256sum --strict --check SHA256SUMS)
if [ -f "$staging_dir/RELEASE_ENVELOPE" ]; then
  release_envelope_digest="$(sha256sum "$staging_dir/RELEASE_ENVELOPE" | awk '{print $1}')"
else
  release_envelope_digest="$(sha256sum "$staging_dir/app.jar" | awk '{print $1}')"
fi
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
if sudo test -e "$CONTRACT_RECOVERY_STATE_FILE"; then
  echo "A previous contract migration recovery state still requires operator resolution" >&2
  exit 1
fi

if [ -f "$APP_DIR/app.jar" ]; then
  previous_commit="$(curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" \
    | sed -n 's/.*"commit":"\([0-9a-f]\{40\}\)".*/\1/p' || true)"
  previous_digest="$(sudo sha256sum "$APP_DIR/app.jar" | awk '{print $1}')"
  if [[ ! "$previous_commit" =~ ^[0-9a-f]{40}$ ]]; then
    if ! sudo test -f "$ACTIVE_STATE_FILE" || sudo test -L "$ACTIVE_STATE_FILE" \
      || [ "$(sudo stat -c %U:%G "$ACTIVE_STATE_FILE")" != "$ACTIVE_STATE_FILE_OWNER" ] \
      || [ "$(sudo stat -c %a "$ACTIVE_STATE_FILE")" != "$ACTIVE_STATE_FILE_MODE" ]; then
      echo "Current backend commit cannot be proven from health or a secured active state" >&2
      exit 1
    fi
    previous_commit="$(sudo sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
    recorded_active_digest="$(sudo sed -n 's/^jar_sha256=//p' "$ACTIVE_STATE_FILE")"
    if [[ ! "$previous_commit" =~ ^[0-9a-f]{40}$ ]] \
      || [[ ! "$recorded_active_digest" =~ ^[0-9a-f]{64}$ ]] \
      || [ "$previous_digest" != "$recorded_active_digest" ]; then
      echo "Current backend JAR does not match the secured active state" >&2
      exit 1
    fi
  fi
  if sudo test -f "$ACTIVE_STATE_FILE" && ! sudo test -L "$ACTIVE_STATE_FILE"; then
    previous_run_id="$(read_state_value "$ACTIVE_STATE_FILE" run_id)"
    previous_run_attempt="$(read_state_value "$ACTIVE_STATE_FILE" run_attempt)"
    previous_run_number="$(read_state_value "$ACTIVE_STATE_FILE" run_number)"
    previous_envelope_digest="$(read_state_value "$ACTIVE_STATE_FILE" release_envelope_sha256)"
    previous_api_contract_revision="$(read_state_value "$ACTIVE_STATE_FILE" api_contract_revision)"
    [[ "$previous_run_id" =~ ^[0-9]+$ ]] || previous_run_id=0
    [[ "$previous_run_attempt" =~ ^[0-9]+$ ]] || previous_run_attempt=0
    [[ "$previous_run_number" =~ ^[0-9]+$ ]] || previous_run_number=0
    [[ "$previous_envelope_digest" =~ ^[0-9a-f]{64}$ ]] || previous_envelope_digest="$previous_digest"
    [[ "$previous_api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || previous_api_contract_revision=legacy
  fi
  if [ -n "$EXPECTED_RUN_ID" ]; then
    if ! sudo test -f "$GENERATION_HIGH_WATER_FILE" && [ "$previous_run_number" -gt 0 ]; then
      write_generation_high_water "legacy/local" "$previous_commit" "$previous_run_id" "$previous_run_number" \
        "$previous_run_attempt" "$previous_envelope_digest"
    fi
  fi
  sudo install -m 0644 "$APP_DIR/app.jar" "$APP_DIR/app.jar.rollback"
  rollback_state_tmp="$(mktemp "$release_root_real/.backend-rollback-state.XXXXXX")"
  printf 'commit_sha=%s\njar_sha256=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\napi_contract_revision=%s\n' \
    "$previous_commit" "$previous_digest" "$previous_run_id" "$previous_run_number" "$previous_run_attempt" \
    "$previous_envelope_digest" "$previous_api_contract_revision" > "$rollback_state_tmp"
  chmod 0600 "$rollback_state_tmp"
  sudo install -m 0600 "$rollback_state_tmp" "$ROLLBACK_STATE_FILE"
  rm -f -- "$rollback_state_tmp"
  rollback_available=true
fi

if [ -n "$EXPECTED_RUN_ID" ]; then
  authorize_release_generation "$release_repository" "$EXPECTED_COMMIT" "$release_run_id" "$release_run_number" \
    "$release_run_attempt" "$release_envelope_digest"
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
active_digest="$(sudo sha256sum "$APP_DIR/app.jar" | awk '{print $1}')"
if [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  write_pending_active_state "$EXPECTED_COMMIT" "$active_digest" "$release_run_id" "$release_run_attempt" "$release_run_number" \
    "$release_envelope_digest" "$api_contract_revision"
fi
failure_phase="start-service"
sudo systemctl daemon-reload
contract_start_attempted=true
sudo systemctl start "$SERVICE_NAME"
sudo systemctl is-active --quiet "$SERVICE_NAME"
failure_phase="verify-health"
wait_for_stability "$EXPECTED_COMMIT"
if [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  write_active_state "$EXPECTED_COMMIT" "$active_digest" "$release_run_id" "$release_run_attempt" "$release_run_number" \
    "$release_envelope_digest" "$api_contract_revision" stable
fi
release_pending_lease
activation_verified=true
contract_start_attempted=false
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
  echo "CLEANUP_DEBT=backend_release_retention"
  echo "CLEANUP_DEBT=backend_release_retention" >&2
  "$CLEANUP_DEBT_WRITER" backend set release_retention \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
else
  "$CLEANUP_DEBT_WRITER" backend clear release_retention \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
fi
if ! sudo systemctl show "$SERVICE_NAME" \
  --property=ActiveState,SubState,Result,ExecMainCode,ExecMainStatus,NRestarts; then
  echo "CLEANUP_DEBT=backend_status_diagnostic"
  echo "CLEANUP_DEBT=backend_status_diagnostic" >&2
  "$CLEANUP_DEBT_WRITER" backend set status_diagnostic \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
else
  "$CLEANUP_DEBT_WRITER" backend clear status_diagnostic \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
fi
if ! rm -rf -- "$source_real"; then
  echo "CLEANUP_DEBT=backend_incoming_release"
  echo "CLEANUP_DEBT=backend_incoming_release" >&2
  "$CLEANUP_DEBT_WRITER" backend set incoming_release \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
else
  "$CLEANUP_DEBT_WRITER" backend clear incoming_release \
    || echo "CLEANUP_DEBT=backend_cleanup_metric" >&2
fi
echo "Backend release activated: $release_real"
