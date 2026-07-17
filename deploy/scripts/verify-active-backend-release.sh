#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-$APP_DIR/app.jar.active.state}"
EXPECTED_CONTRACT=""
if [ "$#" -eq 2 ] && [ "$1" = --contract ]; then
  EXPECTED_CONTRACT="$2"
  [[ "$EXPECTED_CONTRACT" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
    || { echo "Expected API contract revision is invalid" >&2; exit 64; }
  EXPECTED_COMMIT=""
elif [ "$#" -eq 1 ]; then
  EXPECTED_COMMIT="$1"
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Expected commit SHA is invalid" >&2; exit 64; }
else
  echo "Expected a commit SHA or --contract REVISION" >&2
  exit 64
fi
test -f "$APP_DIR/app.jar"
test ! -L "$APP_DIR/app.jar"
test -f "$ACTIVE_STATE_FILE"
test ! -L "$ACTIVE_STATE_FILE"
test "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = root:root
test "$(stat -c %a "$ACTIVE_STATE_FILE")" = 600

recorded_commit="$(sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
recorded_digest="$(sed -n 's/^jar_sha256=//p' "$ACTIVE_STATE_FILE")"
recorded_run_id="$(sed -n 's/^run_id=//p' "$ACTIVE_STATE_FILE")"
recorded_run_number="$(sed -n 's/^run_number=//p' "$ACTIVE_STATE_FILE")"
recorded_run_attempt="$(sed -n 's/^run_attempt=//p' "$ACTIVE_STATE_FILE")"
recorded_contract="$(sed -n 's/^api_contract_revision=//p' "$ACTIVE_STATE_FILE")"
recorded_phase="$(sed -n 's/^phase=//p' "$ACTIVE_STATE_FILE")"
[[ "$recorded_digest" =~ ^[0-9a-f]{64}$ ]] || { echo "Active JAR digest is invalid" >&2; exit 1; }
[[ "$recorded_run_id" =~ ^[0-9]+$ ]] || { echo "Active run id is invalid" >&2; exit 1; }
[[ "$recorded_run_number" =~ ^[0-9]+$ ]] || { echo "Active run number is invalid" >&2; exit 1; }
[[ "$recorded_run_attempt" =~ ^[0-9]+$ ]] || { echo "Active run attempt is invalid" >&2; exit 1; }
[[ "$recorded_contract" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || { echo "Active API contract revision is invalid" >&2; exit 1; }
test "$recorded_phase" = stable
if [ -n "$EXPECTED_COMMIT" ]; then
  test "$recorded_commit" = "$EXPECTED_COMMIT"
else
  EXPECTED_COMMIT="$recorded_commit"
fi
if [ -n "$EXPECTED_CONTRACT" ]; then test "$recorded_contract" = "$EXPECTED_CONTRACT"; fi
test "$(sha256sum "$APP_DIR/app.jar" | awk '{print $1}')" = "$recorded_digest"
curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null
curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" | grep -Fq -- "\"commit\":\"$EXPECTED_COMMIT\""

echo "ACTIVATED_SHA=$EXPECTED_COMMIT"
echo "ACTIVATED_RUN_ID=$recorded_run_id"
echo "ACTIVATED_RUN_NUMBER=$recorded_run_number"
echo "ACTIVATED_RUN_ATTEMPT=$recorded_run_attempt"
echo "ACTIVATED_API_CONTRACT=$recorded_contract"
