#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-$APP_DIR/app.jar.active.state}"
[ "$#" -eq 1 ] || { echo "Exactly one expected commit SHA is required" >&2; exit 64; }
EXPECTED_COMMIT="$1"

[[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Expected commit SHA is invalid" >&2; exit 64; }
test -f "$APP_DIR/app.jar"
test ! -L "$APP_DIR/app.jar"
test -f "$ACTIVE_STATE_FILE"
test ! -L "$ACTIVE_STATE_FILE"
test "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = root:root
test "$(stat -c %a "$ACTIVE_STATE_FILE")" = 600

recorded_commit="$(sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
recorded_digest="$(sed -n 's/^jar_sha256=//p' "$ACTIVE_STATE_FILE")"
[[ "$recorded_digest" =~ ^[0-9a-f]{64}$ ]] || { echo "Active JAR digest is invalid" >&2; exit 1; }
test "$recorded_commit" = "$EXPECTED_COMMIT"
test "$(sha256sum "$APP_DIR/app.jar" | awk '{print $1}')" = "$recorded_digest"
curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null
curl -fsS --max-time 3 "${HEALTH_URL%/health}/info" | grep -Fq -- "\"commit\":\"$EXPECTED_COMMIT\""

echo "ACTIVATED_SHA=$EXPECTED_COMMIT"
