#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/verify-active-frontend-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
release="$fixture/releases/release-1"
site="$release/site"
web_root="$fixture/app"
state="$fixture/frontend.active.state"
commit=0123456789abcdef0123456789abcdef01234567
mkdir -p "$site"
printf '%s\n' "$commit" > "$site/.noviis-release"
printf 'commit_sha=%s\nrun_id=9000\nrun_number=20\nrun_attempt=1\napi_contract_revision=test-v1\n' "$commit" > "$release/RELEASE_METADATA"
printf 'envelope\n' > "$release/RELEASE_ENVELOPE"
digest="$(sha256sum "$release/RELEASE_ENVELOPE" | awk '{print $1}')"
printf 'commit_sha=%s\nrun_id=9000\nrun_number=20\nrun_attempt=1\nrelease_envelope_sha256=%s\napi_contract_revision=test-v1\nphase=stable\n' \
  "$commit" "$digest" > "$state"
chmod 0600 "$state"
ln -s "$site" "$web_root"
if [ ! -L "$web_root" ]; then
  echo "Frontend active-state verification fixtures skipped because native symbolic links are unavailable"
  exit 0
fi

invoke() {
  WEB_ROOT="$web_root" RELEASE_ROOT="$fixture/releases" ACTIVE_STATE_FILE="$state" \
    ACTIVE_STATE_OWNER="$(stat -c %U:%G "$state")" \
    ACTIVE_STATE_MODE="$(stat -c %a "$state")" \
    bash "$script" "$commit" 9000 20 1
}

output="$(invoke)"
grep -Fqx "ACTIVATED_SHA=$commit" <<< "$output"
grep -Fqx 'ACTIVATED_RUN_NUMBER=20' <<< "$output"

printf 'tampered\n' >> "$release/RELEASE_ENVELOPE"
if invoke; then
  echo "Expected independent frontend readback to reject a changed release envelope" >&2
  exit 1
fi

echo "Frontend active-state verification fixtures passed"
