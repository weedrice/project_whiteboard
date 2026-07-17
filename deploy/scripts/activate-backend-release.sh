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
ENV_FILE="${ENV_FILE:-/etc/noviis/app.env}"
ENV_FILE_OWNER="${ENV_FILE_OWNER:-root:root}"
ENV_FILE_MODE="${ENV_FILE_MODE:-600}"
SOURCE_DIR="${1:?incoming release directory is required}"
EXPECTED_COMMIT="${2:-${EXPECTED_COMMIT:-}}"

activated=false
service_stopped=false
rollback_available=false
rollback_in_progress=false
completed=false
activation_verified=false
staging_dir=""
release_real=""

diagnose() {
  sudo systemctl status "$SERVICE_NAME" --no-pager || true
  sudo journalctl -u "$SERVICE_NAME" -n 80 --no-pager || true
  sudo tail -n 120 "$LOG_DIR/whiteboard-active.log" || true
  sudo tail -n 120 "$LOG_DIR/whiteboard-error.log" || true
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
  if [ "$completed" = true ] || [ "$rollback_in_progress" = true ]; then
    return "$original_status"
  fi

  rollback_in_progress=true
  set +e
  echo "Backend activation failed; starting rollback" >&2
  diagnose

  if [ "$activation_verified" = true ]; then
    echo "Activation was already verified; leaving the healthy release active after a maintenance failure" >&2
  elif [ "$service_stopped" = true ]; then
    local restore_status=0
    if [ "$activated" = true ]; then
      if [ "$rollback_available" = true ]; then
        sudo install -m 0644 "$APP_DIR/app.jar.rollback" "$APP_DIR/app.jar"
        restore_status=$?
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
      wait_for_health ""
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
  sudo install -m 0644 "$APP_DIR/app.jar" "$APP_DIR/app.jar.rollback"
  rollback_available=true
fi

sudo install -m 0644 "${jars[0]}" "$APP_DIR/app.jar.next"
sudo systemctl stop "$SERVICE_NAME"
service_stopped=true
if sudo systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "Service remained active after stop" >&2
  exit 1
fi
sudo mv "$APP_DIR/app.jar.next" "$APP_DIR/app.jar"
activated=true
sudo systemctl daemon-reload
sudo systemctl start "$SERVICE_NAME"
sudo systemctl is-active --quiet "$SERVICE_NAME"
wait_for_health "$EXPECTED_COMMIT"
activation_verified=true

declare -A keep=()
keep["$release_real"]=1
keep_count=1
mapfile -d '' releases < <(find "$release_root_real" -mindepth 1 -maxdepth 1 -type d ! -name '.backend-release.*' -printf '%T@ %p\0' | sort -z -nr)
for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  [ "$path_real" = "$release_real" ] && continue
  if [ "$keep_count" -lt 5 ]; then
    keep["$path_real"]=1
    keep_count=$((keep_count + 1))
  fi
done
for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  case "$path_real/" in "$release_root_real"/*/) ;; *) echo "Refusing to remove path outside release root: $path_real" >&2; exit 1 ;; esac
  if [ -z "${keep[$path_real]+x}" ]; then rm -rf -- "$path_real"; fi
done

sudo systemctl status "$SERVICE_NAME" --no-pager
rm -rf -- "$source_real"
completed=true
echo "Backend release activated: $release_real"
