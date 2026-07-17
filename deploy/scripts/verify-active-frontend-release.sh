#!/usr/bin/env bash
set -euo pipefail

WEB_ROOT="${WEB_ROOT:-/var/www/app}"
RELEASE_ROOT="${RELEASE_ROOT:-/var/www/releases/frontend}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-/var/lib/noviis/deployment-state/frontend.active.state}"
ACTIVE_STATE_OWNER="${ACTIVE_STATE_OWNER:-root:root}"
ACTIVE_STATE_MODE="${ACTIVE_STATE_MODE:-600}"
[ "$#" -eq 4 ] || { echo "expected commit, run id, run number, and run attempt are required" >&2; exit 64; }
expected_commit="$1"
expected_run_id="$2"
expected_run_number="$3"
expected_run_attempt="$4"
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]]
[[ "$expected_run_id" =~ ^[1-9][0-9]*$ ]]
[[ "$expected_run_number" =~ ^[1-9][0-9]*$ ]]
[[ "$expected_run_attempt" =~ ^[1-9][0-9]*$ ]]

test -f "$ACTIVE_STATE_FILE"
test ! -L "$ACTIVE_STATE_FILE"
test "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = "$ACTIVE_STATE_OWNER"
test "$(stat -c %a "$ACTIVE_STATE_FILE")" = "$ACTIVE_STATE_MODE"
grep -Fqx -- "commit_sha=$expected_commit" "$ACTIVE_STATE_FILE"
grep -Fqx -- "run_id=$expected_run_id" "$ACTIVE_STATE_FILE"
grep -Fqx -- "run_number=$expected_run_number" "$ACTIVE_STATE_FILE"
grep -Fqx -- "run_attempt=$expected_run_attempt" "$ACTIVE_STATE_FILE"
grep -Fqx -- 'phase=stable' "$ACTIVE_STATE_FILE"
expected_envelope_digest="$(sed -n 's/^release_envelope_sha256=//p' "$ACTIVE_STATE_FILE")"
api_contract_revision="$(sed -n 's/^api_contract_revision=//p' "$ACTIVE_STATE_FILE")"
[[ "$expected_envelope_digest" =~ ^[0-9a-f]{64}$ ]]
[[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]

test -L "$WEB_ROOT"
site_real="$(readlink -f "$WEB_ROOT")"
release_root_real="$(realpath "$RELEASE_ROOT")"
case "$site_real/" in "$release_root_real"/*/site/) ;; *) echo "active frontend target is outside release root" >&2; exit 1 ;; esac
release_real="$(dirname "$site_real")"
test "$(tr -d '\r\n' < "$site_real/.noviis-release")" = "$expected_commit"
grep -Fqx -- "commit_sha=$expected_commit" "$release_real/RELEASE_METADATA"
grep -Fqx -- "run_id=$expected_run_id" "$release_real/RELEASE_METADATA"
grep -Fqx -- "run_number=$expected_run_number" "$release_real/RELEASE_METADATA"
grep -Fqx -- "run_attempt=$expected_run_attempt" "$release_real/RELEASE_METADATA"
test "$(sha256sum "$release_real/RELEASE_ENVELOPE" | awk '{print $1}')" = "$expected_envelope_digest"

echo "ACTIVATED_SHA=$expected_commit"
echo "ACTIVATED_RUN_ID=$expected_run_id"
echo "ACTIVATED_RUN_NUMBER=$expected_run_number"
echo "ACTIVATED_RUN_ATTEMPT=$expected_run_attempt"
echo "ACTIVATED_API_CONTRACT=$api_contract_revision"
