#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
RELEASE_ROOT="${RELEASE_ROOT:-$APP_DIR/releases}"
SERVICE_NAME="${SERVICE_NAME:-app}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-30}"
HEALTH_DELAY_SECONDS="${HEALTH_DELAY_SECONDS:-2}"
LOG_DIR="${LOG_DIR:-/opt/app/logs}"
ENV_FILE="${ENV_FILE:-/etc/noviis/app.env}"
RELEASE_DIR="${1:?release directory is required}"

activated=false
rollback_available=false
rollback_in_progress=false
completed=false

diagnose() {
  sudo systemctl status "$SERVICE_NAME" --no-pager || true
  sudo journalctl -u "$SERVICE_NAME" -n 80 --no-pager || true
  sudo tail -n 120 "$LOG_DIR/whiteboard-active.log" || true
  sudo tail -n 120 "$LOG_DIR/whiteboard-error.log" || true
}

wait_for_health() {
  local attempt
  for attempt in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null; then
      return 0
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

  if [ "$activated" = true ] && [ "$rollback_available" = true ]; then
    sudo install -m 0644 "$APP_DIR/app.jar.rollback" "$APP_DIR/app.jar"
    local restore_status=$?
    if [ "$restore_status" -eq 0 ]; then
      sudo systemctl daemon-reload
      sudo systemctl restart "$SERVICE_NAME"
      restore_status=$?
    fi
    if [ "$restore_status" -eq 0 ]; then
      wait_for_health
      restore_status=$?
    fi
    if [ "$restore_status" -ne 0 ]; then
      echo "Backend rollback failed" >&2
      diagnose
      exit 2
    fi
    echo "Backend rollback completed" >&2
  elif [ "$activated" = true ]; then
    echo "No previous JAR is available for rollback" >&2
    sudo systemctl stop "$SERVICE_NAME" || true
  fi

  exit "$original_status"
}

trap 'rollback $?' ERR

release_root_real="$(realpath "$RELEASE_ROOT")"
release_real="$(realpath "$RELEASE_DIR")"
case "$release_real/" in
  "$release_root_real"/*/) ;;
  *) echo "Release directory is outside release root: $release_real" >&2; exit 1 ;;
esac

mapfile -d '' jars < <(find "$release_real" -maxdepth 1 -type f -name '*.jar' -print0)
if [ "${#jars[@]}" -ne 1 ]; then
  echo "Expected exactly one release JAR, found ${#jars[@]}" >&2
  exit 1
fi

test -d "$LOG_DIR"
test -w "$LOG_DIR"
sudo test -r "$ENV_FILE"
sudo test -w "$APP_DIR"

if [ -f "$APP_DIR/app.jar" ]; then
  sudo install -m 0644 "$APP_DIR/app.jar" "$APP_DIR/app.jar.rollback"
  rollback_available=true
fi

sudo install -m 0644 "${jars[0]}" "$APP_DIR/app.jar.next"
sudo systemctl stop "$SERVICE_NAME"
if sudo systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "Service remained active after stop" >&2
  exit 1
fi
sudo mv "$APP_DIR/app.jar.next" "$APP_DIR/app.jar"
activated=true
sudo systemctl daemon-reload
sudo systemctl start "$SERVICE_NAME"
sudo systemctl is-active --quiet "$SERVICE_NAME"
wait_for_health

declare -A keep=()
keep["$release_real"]=1
keep_count=1
mapfile -d '' releases < <(
  find "$release_root_real" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\0' | sort -z -nr
)
for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  if [ "$path_real" = "$release_real" ]; then
    continue
  fi
  if [ "$keep_count" -lt 5 ]; then
    keep["$path_real"]=1
    keep_count=$((keep_count + 1))
  fi
done

for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  case "$path_real/" in
    "$release_root_real"/*/) ;;
    *) echo "Refusing to remove path outside release root: $path_real" >&2; exit 1 ;;
  esac
  if [ -z "${keep[$path_real]+x}" ]; then
    rm -rf -- "$path_real"
  fi
done

sudo systemctl status "$SERVICE_NAME" --no-pager
completed=true
echo "Backend release activated: $release_real"
