#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-$APP_DIR/app.jar.active.state}"

fail() {
  echo "Backend start-state verification failed: $*" >&2
  exit 1
}

[ "$EUID" -eq 0 ] || fail "verifier must run as root"
[ -f "$APP_DIR/app.jar" ] && [ ! -L "$APP_DIR/app.jar" ] || fail "application JAR is missing or unsafe"
[ -f "$ACTIVE_STATE_FILE" ] && [ ! -L "$ACTIVE_STATE_FILE" ] || fail "active state is missing or unsafe"
[ "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = root:root ] || fail "active state owner is invalid"
[ "$(stat -c %a "$ACTIVE_STATE_FILE")" = 600 ] || fail "active state mode is invalid"

commit_sha="$(sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
jar_sha256="$(sed -n 's/^jar_sha256=//p' "$ACTIVE_STATE_FILE")"
run_id="$(sed -n 's/^run_id=//p' "$ACTIVE_STATE_FILE")"
run_number="$(sed -n 's/^run_number=//p' "$ACTIVE_STATE_FILE")"
run_attempt="$(sed -n 's/^run_attempt=//p' "$ACTIVE_STATE_FILE")"
phase="$(sed -n 's/^phase=//p' "$ACTIVE_STATE_FILE")"
[[ "$commit_sha" =~ ^[0-9a-f]{40}$ ]] || fail "commit SHA is invalid"
[[ "$jar_sha256" =~ ^[0-9a-f]{64}$ ]] || fail "JAR digest is invalid"
[[ "$run_id" =~ ^[0-9]+$ ]] || fail "run id is invalid"
[[ "$run_number" =~ ^[0-9]+$ ]] || fail "run number is invalid"
[[ "$run_attempt" =~ ^[0-9]+$ ]] || fail "run attempt is invalid"
[[ "$phase" = pending || "$phase" = stable ]] || fail "activation phase is invalid"
[ "$(sha256sum "$APP_DIR/app.jar" | awk '{print $1}')" = "$jar_sha256" ] || fail "JAR digest does not match active state"

echo "Backend start state verified: $commit_sha ($phase, run $run_id, generation $run_number/$run_attempt)"
