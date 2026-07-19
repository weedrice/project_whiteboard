#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/app/backend}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-$APP_DIR/app.jar.active.state}"
BACKEND_ACTIVATION_LEASE_FILE="${BACKEND_ACTIVATION_LEASE_FILE:-/run/lock/noviis-backend-activation.lease}"

fail() {
  echo "Backend start-state verification failed: $*" >&2
  exit 1
}

[ "$EUID" -eq 0 ] || fail "verifier must run as root"
if ! { [ -f "$APP_DIR/app.jar" ] && [ ! -L "$APP_DIR/app.jar" ]; }; then
  fail "application JAR is missing or unsafe"
fi
if ! { [ -f "$ACTIVE_STATE_FILE" ] && [ ! -L "$ACTIVE_STATE_FILE" ]; }; then
  fail "active state is missing or unsafe"
fi
[ "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = root:root ] || fail "active state owner is invalid"
[ "$(stat -c %a "$ACTIVE_STATE_FILE")" = 600 ] || fail "active state mode is invalid"

commit_sha="$(sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
jar_sha256="$(sed -n 's/^jar_sha256=//p' "$ACTIVE_STATE_FILE")"
run_id="$(sed -n 's/^run_id=//p' "$ACTIVE_STATE_FILE")"
run_number="$(sed -n 's/^run_number=//p' "$ACTIVE_STATE_FILE")"
run_attempt="$(sed -n 's/^run_attempt=//p' "$ACTIVE_STATE_FILE")"
phase="$(sed -n 's/^phase=//p' "$ACTIVE_STATE_FILE")"
activation_nonce="$(sed -n 's/^activation_nonce=//p' "$ACTIVE_STATE_FILE")"
activation_issued_at="$(sed -n 's/^activation_issued_at=//p' "$ACTIVE_STATE_FILE")"
activation_expires_at="$(sed -n 's/^activation_expires_at=//p' "$ACTIVE_STATE_FILE")"
[[ "$commit_sha" =~ ^[0-9a-f]{40}$ ]] || fail "commit SHA is invalid"
[[ "$jar_sha256" =~ ^[0-9a-f]{64}$ ]] || fail "JAR digest is invalid"
[[ "$run_id" =~ ^[0-9]+$ ]] || fail "run id is invalid"
[[ "$run_number" =~ ^[0-9]+$ ]] || fail "run number is invalid"
[[ "$run_attempt" =~ ^[0-9]+$ ]] || fail "run attempt is invalid"
[[ "$phase" = "pending" || "$phase" = "stable" ]] || fail "activation phase is invalid"
if [ "$phase" = pending ]; then
  [[ "$activation_nonce" =~ ^[0-9a-f]{32}$ ]] || fail "pending activation nonce is invalid"
  [[ "$activation_issued_at" =~ ^[0-9]+$ && "$activation_expires_at" =~ ^[0-9]+$ ]] \
    || fail "pending activation lease timestamps are invalid"
  if ! { [ "$activation_issued_at" -le "$(date +%s)" ] && [ "$activation_expires_at" -gt "$(date +%s)" ]; }; then
    fail "pending activation lease is expired or not active"
  fi
  command -v flock >/dev/null 2>&1 || fail "flock is required for pending activation verification"
  if ! { [ -f "$BACKEND_ACTIVATION_LEASE_FILE" ] && [ ! -L "$BACKEND_ACTIVATION_LEASE_FILE" ]; }; then
    fail "pending activation lease file is missing or unsafe"
  fi
  if ! { [ "$(stat -c %U:%G "$BACKEND_ACTIVATION_LEASE_FILE")" = root:root ] && [ "$(stat -c %a "$BACKEND_ACTIVATION_LEASE_FILE")" = 600 ]; }; then
    fail "pending activation lease file permissions are invalid"
  fi
  [ "$(tr -d '\r\n' < "$BACKEND_ACTIVATION_LEASE_FILE")" = "$activation_nonce" ] \
    || fail "pending activation lease nonce does not match"
  if flock -n "$BACKEND_ACTIVATION_LEASE_FILE" true; then
    fail "pending activation has no matching live activator lease"
  fi
else
  [ -n "$activation_nonce" ] || activation_nonce=none
  [ -n "$activation_issued_at" ] || activation_issued_at=0
  [ -n "$activation_expires_at" ] || activation_expires_at=0
  if ! { [ "$activation_nonce" = none ] && [ "$activation_issued_at" = 0 ] && [ "$activation_expires_at" = 0 ]; }; then
    fail "stable activation unexpectedly contains a lease"
  fi
fi
[ "$(sha256sum "$APP_DIR/app.jar" | awk '{print $1}')" = "$jar_sha256" ] || fail "JAR digest does not match active state"

echo "Backend start state verified: $commit_sha ($phase, run $run_id, generation $run_number/$run_attempt)"
