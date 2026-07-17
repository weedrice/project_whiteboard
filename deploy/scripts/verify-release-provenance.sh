#!/usr/bin/env bash
set -euo pipefail

REPOSITORY="${REPOSITORY:-weedrice/project_whiteboard}"
SIGNER_WORKFLOW="${SIGNER_WORKFLOW:-github.com/weedrice/project_whiteboard/.github/workflows/ci.yml}"
TRUSTED_ROOT_FILE="${TRUSTED_ROOT_FILE:-/etc/noviis/github-attestation-trusted-root.jsonl}"
ALLOW_NON_ROOT_TEST="${ALLOW_NON_ROOT_TEST:-false}"

release_dir="${1:?release directory is required}"
expected_commit="${2:?expected commit SHA is required}"
release_type="${3:?release type is required (backend or frontend)}"

fail() {
  echo "Release provenance verification failed: $*" >&2
  exit 1
}

if [ "$ALLOW_NON_ROOT_TEST" != true ]; then
  [ "$EUID" -eq 0 ] || fail "verifier must run as root"
  [ "$(stat -c %U:%G "$0")" = root:root ] || fail "verifier must be owned by root:root"
  verifier_mode="$(stat -c %a "$0")"
  (( (8#$verifier_mode & 8#022) == 0 )) || fail "verifier must not be group- or world-writable"
fi

[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] || fail "expected commit must be a lowercase 40-character SHA"
[ -d "$release_dir" ] || fail "release directory does not exist"
[ ! -L "$release_dir" ] || fail "release directory must not be a symlink"
release_real="$(realpath "$release_dir")"

case "$release_type" in
  backend)
    payload_name=app.jar
    expected_files=(PROVENANCE_BUNDLE.jsonl RELEASE_ENVELOPE RELEASE_METADATA SHA256SUMS app.jar sbom.spdx.json)
    envelope_files=(app.jar RELEASE_METADATA sbom.spdx.json SHA256SUMS)
    ;;
  frontend)
    payload_name=frontend-release.tar.gz
    expected_files=(PROVENANCE_BUNDLE.jsonl RELEASE_ENVELOPE RELEASE_METADATA SHA256SUMS frontend-release.tar.gz sbom.spdx.json)
    envelope_files=(frontend-release.tar.gz RELEASE_METADATA sbom.spdx.json SHA256SUMS)
    ;;
  *) fail "unsupported release type: $release_type" ;;
esac

mapfile -t actual_files < <(find "$release_real" -mindepth 1 -maxdepth 1 -printf '%f\n' | LC_ALL=C sort)
if [ "${#actual_files[@]}" -ne "${#expected_files[@]}" ]; then
  fail "release contains an unexpected number of files"
fi
for index in "${!expected_files[@]}"; do
  [ "${actual_files[$index]}" = "${expected_files[$index]}" ] || fail "release file allowlist mismatch"
done
for name in "${expected_files[@]}"; do
  path="$release_real/$name"
  [ -f "$path" ] || fail "$name must be a regular file"
  [ ! -L "$path" ] || fail "$name must not be a symlink"
done

check_max_size() {
  local name="$1"
  local maximum="$2"
  local size
  size="$(stat -c %s "$release_real/$name")"
  [ "$size" -gt 0 ] || fail "$name must not be empty"
  [ "$size" -le "$maximum" ] || fail "$name exceeds its size limit"
}

check_max_size SHA256SUMS 16384
check_max_size RELEASE_ENVELOPE 32768
check_max_size sbom.spdx.json 67108864
check_max_size PROVENANCE_BUNDLE.jsonl 16777216
check_max_size RELEASE_METADATA 16384
grep -Fqx -- "commit_sha=$expected_commit" "$release_real/RELEASE_METADATA" \
  || fail "release metadata commit does not match"
grep -Fqx -- "repository=$REPOSITORY" "$release_real/RELEASE_METADATA" \
  || fail "release metadata repository does not match"
run_id="$(sed -n 's/^run_id=//p' "$release_real/RELEASE_METADATA")"
run_number="$(sed -n 's/^run_number=//p' "$release_real/RELEASE_METADATA")"
run_attempt="$(sed -n 's/^run_attempt=//p' "$release_real/RELEASE_METADATA")"
api_contract_revision="$(sed -n 's/^api_contract_revision=//p' "$release_real/RELEASE_METADATA")"
[[ "$run_id" =~ ^[1-9][0-9]*$ ]] || fail "release metadata run id is invalid"
[[ "$run_number" =~ ^[1-9][0-9]*$ ]] || fail "release metadata run number is invalid"
[[ "$run_attempt" =~ ^[1-9][0-9]*$ ]] || fail "release metadata run attempt is invalid"
[[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
  || fail "release metadata API contract revision is invalid"
if [ "$release_type" = backend ]; then
  check_max_size app.jar 536870912
else
  check_max_size frontend-release.tar.gz 268435456
fi

(cd "$release_real" && sha256sum --strict --check SHA256SUMS >/dev/null) \
  || fail "checksum manifest verification failed"

grep -Fqx -- "release_type=$release_type" "$release_real/RELEASE_ENVELOPE" \
  || fail "release envelope type does not match"
grep -Fqx -- "commit_sha=$expected_commit" "$release_real/RELEASE_ENVELOPE" \
  || fail "release envelope commit does not match"
if ! (cd "$release_real" && cmp -s \
  <(tail -n +3 RELEASE_ENVELOPE) \
  <(sha256sum "${envelope_files[@]}")); then
  fail "release envelope is not bound to the exact payload, metadata, SBOM, and checksum manifest"
fi

[ -f "$TRUSTED_ROOT_FILE" ] || fail "trusted root is missing"
[ -s "$TRUSTED_ROOT_FILE" ] || fail "trusted root is empty"
[ ! -L "$TRUSTED_ROOT_FILE" ] || fail "trusted root must not be a symlink"
if [ "$ALLOW_NON_ROOT_TEST" != true ]; then
  [ "$(stat -c %U:%G "$TRUSTED_ROOT_FILE")" = root:root ] || fail "trusted root must be owned by root:root"
  trusted_root_mode="$(stat -c %a "$TRUSTED_ROOT_FILE")"
  (( (8#$trusted_root_mode & 8#022) == 0 )) || fail "trusted root must not be group- or world-writable"
fi

gh_path="$(command -v gh)" || fail "GitHub CLI is not installed"
if [ "$ALLOW_NON_ROOT_TEST" != true ]; then
  [ "$(stat -c %U "$gh_path")" = root ] || fail "GitHub CLI must be owned by root"
  gh_mode="$(stat -c %a "$gh_path")"
  (( (8#$gh_mode & 8#022) == 0 )) || fail "GitHub CLI must not be group- or world-writable"
fi

verify_attestation() {
  local subject_name="$1"
  local predicate_type="$2"
  "$gh_path" attestation verify "$release_real/$subject_name" \
    --bundle "$release_real/PROVENANCE_BUNDLE.jsonl" \
    --custom-trusted-root "$TRUSTED_ROOT_FILE" \
    --repo "$REPOSITORY" \
    --signer-workflow "$SIGNER_WORKFLOW" \
    --source-ref refs/heads/main \
    --source-digest "$expected_commit" \
    --predicate-type "$predicate_type" \
    --deny-self-hosted-runners >/dev/null
}

verify_attestation RELEASE_ENVELOPE https://slsa.dev/provenance/v1 \
  || fail "build provenance attestation is invalid"
verify_attestation "$payload_name" https://spdx.dev/Document/v2.3 \
  || fail "SBOM attestation is invalid"

echo "Release provenance verified: $release_type $expected_commit"
