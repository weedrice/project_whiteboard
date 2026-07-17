#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
verifier="$project_root/deploy/scripts/verify-backend-start-state.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
app_dir="$fixture/app"
state_file="$app_dir/app.jar.active.state"
lock_file="$fixture/deploy.lock"
lease_file="$fixture/backend-activation.lease"
mkdir -p "$app_dir"
printf 'verified jar\n' > "$app_dir/app.jar"
digest="$(sha256sum "$app_dir/app.jar" | awk '{print $1}')"
commit=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa

write_state() {
  local phase="$1" nonce="$2" issued="$3" expires="$4"
  local source="$fixture/state.source"
  printf 'commit_sha=%s\njar_sha256=%s\nrun_id=10\nrun_number=10\nrun_attempt=1\nphase=%s\nactivation_nonce=%s\nactivation_issued_at=%s\nactivation_expires_at=%s\n' \
    "$commit" "$digest" "$phase" "$nonce" "$issued" "$expires" > "$source"
  sudo install -o root -g root -m 0600 "$source" "$state_file"
}

write_state stable none 0 0
sudo APP_DIR="$app_dir" ACTIVE_STATE_FILE="$state_file" DEPLOY_LOCK_FILE="$lock_file" bash "$verifier" >/dev/null

now="$(date +%s)"
write_state pending 0123456789abcdef0123456789abcdef "$now" "$((now + 60))"
if sudo APP_DIR="$app_dir" ACTIVE_STATE_FILE="$state_file" BACKEND_ACTIVATION_LEASE_FILE="$lease_file" bash "$verifier"; then
  echo "Expected an orphaned pending state to be rejected" >&2
  exit 1
fi

printf '0123456789abcdef0123456789abcdef\n' > "$fixture/lease.source"
sudo install -o root -g root -m 0600 "$fixture/lease.source" "$lease_file"
(
  exec 8<>"$lease_file"
  flock 8
  sleep 10
) &
lock_holder=$!
sleep 0.1
sudo APP_DIR="$app_dir" ACTIVE_STATE_FILE="$state_file" BACKEND_ACTIVATION_LEASE_FILE="$lease_file" bash "$verifier" >/dev/null
kill "$lock_holder"
wait "$lock_holder" 2>/dev/null || true

write_state pending 0123456789abcdef0123456789abcdef "$((now - 120))" "$((now - 60))"
if sudo APP_DIR="$app_dir" ACTIVE_STATE_FILE="$state_file" BACKEND_ACTIVATION_LEASE_FILE="$lease_file" bash "$verifier"; then
  echo "Expected an expired pending state to be rejected" >&2
  exit 1
fi

echo "Backend start-state verifier fixtures passed"
