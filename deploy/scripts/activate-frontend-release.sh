#!/usr/bin/env bash
set -Eeuo pipefail

INCOMING_ROOT="${INCOMING_ROOT:-/var/www/incoming/frontend}"
RELEASE_ROOT="${RELEASE_ROOT:-/var/www/releases/frontend}"
WEB_ROOT="${WEB_ROOT:-/var/www/app}"
HEALTH_URL="${HEALTH_URL:-https://noviis.kr/.noviis-release}"
INTERNAL_HEALTH_HOST="${INTERNAL_HEALTH_HOST:-noviis.kr}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
ARCHIVE_MAX_FILES="${ARCHIVE_MAX_FILES:-10000}"
ARCHIVE_MAX_SINGLE_FILE_BYTES="${ARCHIVE_MAX_SINGLE_FILE_BYTES:-67108864}"
ARCHIVE_MAX_TOTAL_BYTES="${ARCHIVE_MAX_TOTAL_BYTES:-1073741824}"
ARCHIVE_FREE_SPACE_HEADROOM_BYTES="${ARCHIVE_FREE_SPACE_HEADROOM_BYTES:-67108864}"
PROVENANCE_VERIFIER="${PROVENANCE_VERIFIER:-/usr/local/sbin/verify-noviis-release}"
DEPLOY_LOCK_FILE="${DEPLOY_LOCK_FILE:-/run/lock/noviis-deploy.lock}"
SOURCE_DIR="${1:?incoming release directory is required}"
MODE="${2:-activate}"
EXPECTED_COMMIT="${3:-${EXPECTED_COMMIT:-}}"
EXPECTED_RUN_ID="${4:-${EXPECTED_RUN_ID:-}}"
EXPECTED_RUN_ATTEMPT="${5:-${EXPECTED_RUN_ATTEMPT:-}}"
EXPECTED_RUN_NUMBER="${6:-${EXPECTED_RUN_NUMBER:-}}"
ACTIVE_STATE_FILE="${ACTIVE_STATE_FILE:-/var/lib/noviis/deployment-state/frontend.active.state}"
ACTIVE_STATE_OWNER="${ACTIVE_STATE_OWNER:-root:root}"
ACTIVE_STATE_MODE="${ACTIVE_STATE_MODE:-600}"
ROLLBACK_AUTH_FILE="${ROLLBACK_AUTH_FILE:-/var/lib/noviis/deployment-state/frontend-rollback.allow}"
GENERATION_HIGH_WATER_FILE="${GENERATION_HIGH_WATER_FILE:-/var/lib/noviis/deployment-state/frontend-generation.state}"
CLEANUP_DEBT_WRITER="${CLEANUP_DEBT_WRITER:-/usr/local/sbin/record-noviis-cleanup-debt}"

for numeric_limit in ARCHIVE_MAX_FILES ARCHIVE_MAX_SINGLE_FILE_BYTES ARCHIVE_MAX_TOTAL_BYTES ARCHIVE_FREE_SPACE_HEADROOM_BYTES; do
  numeric_value="${!numeric_limit}"
  [[ "$numeric_value" =~ ^[1-9][0-9]*$ ]] || { echo "$numeric_limit must be a positive integer" >&2; exit 64; }
done

command -v flock >/dev/null 2>&1 || { echo "flock is required for activation locking" >&2; exit 69; }
exec 9>"$DEPLOY_LOCK_FILE"
if ! flock -n 9; then
  echo "Another NoviIs activation is already running" >&2
  exit 75
fi

switched=false
verified=false
staging_dir=""
release_real=""

write_state() {
  local destination="$1"
  local value="$2"
  local temporary
  local destination_directory
  destination_directory="$(dirname "$destination")"
  temporary="$(mktemp "$destination_directory/.state.XXXXXX")"
  if ! printf '%s\n' "$value" > "$temporary" || ! chmod 0644 "$temporary"; then
    rm -f -- "$temporary"
    return 1
  fi
  if command -v sync >/dev/null 2>&1; then sync -f "$temporary" 2>/dev/null || true; fi
  if ! mv -Tf "$temporary" "$destination"; then
    rm -f -- "$temporary"
    return 1
  fi
  if command -v sync >/dev/null 2>&1; then sync -f "$destination_directory" 2>/dev/null || true; fi
}

write_active_state() {
  local commit_sha="$1" run_id="$2" run_number="$3" run_attempt="$4" envelope_sha256="$5" api_contract_revision="$6" phase="$7"
  local state_dir state_tmp
  [[ "$commit_sha" =~ ^[0-9a-f]{40}$ ]] || return 1
  [[ "$run_id" =~ ^[0-9]+$ ]] || return 1
  [[ "$run_number" =~ ^[0-9]+$ ]] || return 1
  [[ "$run_attempt" =~ ^[0-9]+$ ]] || return 1
  [[ "$envelope_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  [[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || return 1
  [[ "$phase" = pending || "$phase" = stable ]] || return 1
  state_dir="$(dirname "$ACTIVE_STATE_FILE")"
  install -d -m 0700 "$state_dir"
  state_tmp="$(mktemp "$state_dir/.frontend-active.XXXXXX")"
  printf 'commit_sha=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\napi_contract_revision=%s\nphase=%s\n' \
    "$commit_sha" "$run_id" "$run_number" "$run_attempt" "$envelope_sha256" "$api_contract_revision" "$phase" > "$state_tmp"
  chmod 0600 "$state_tmp"
  mv -Tf "$state_tmp" "$ACTIVE_STATE_FILE"
}

write_generation_high_water() {
  local repository="$1" commit_sha="$2" run_id="$3" run_number="$4" run_attempt="$5" envelope_sha256="$6"
  local state_dir state_tmp
  [[ "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ && "$commit_sha" =~ ^[0-9a-f]{40}$ \
    && "$run_id" =~ ^[0-9]+$ && "$run_number" =~ ^[0-9]+$ && "$run_attempt" =~ ^[0-9]+$ \
    && "$envelope_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  state_dir="$(dirname "$GENERATION_HIGH_WATER_FILE")"
  install -d -m 0700 "$state_dir"
  state_tmp="$(mktemp "$state_dir/.frontend-generation.XXXXXX")"
  printf 'repository=%s\ncommit_sha=%s\nrun_id=%s\nrun_number=%s\nrun_attempt=%s\nrelease_envelope_sha256=%s\n' \
    "$repository" "$commit_sha" "$run_id" "$run_number" "$run_attempt" "$envelope_sha256" > "$state_tmp"
  chmod 0600 "$state_tmp"
  mv -Tf "$state_tmp" "$GENERATION_HIGH_WATER_FILE"
}

authorize_release_generation() {
  local target_repository="$1" target_commit="$2" target_run_id="$3" target_run_number="$4" target_attempt="$5" target_envelope="$6"
  local current_run_number=0 current_attempt=0 authorization_id issued_at expires_at reason now line_count
  if [ -f "$GENERATION_HIGH_WATER_FILE" ]; then
    test ! -L "$GENERATION_HIGH_WATER_FILE"
    test "$(stat -c %U:%G "$GENERATION_HIGH_WATER_FILE")" = "$ACTIVE_STATE_OWNER"
    test "$(stat -c %a "$GENERATION_HIGH_WATER_FILE")" = 600
    current_run_number="$(sed -n 's/^run_number=//p' "$GENERATION_HIGH_WATER_FILE")"
    current_attempt="$(sed -n 's/^run_attempt=//p' "$GENERATION_HIGH_WATER_FILE")"
    [[ "$current_run_number" =~ ^[0-9]+$ && "$current_attempt" =~ ^[0-9]+$ ]] || return 1
  fi
  if [ "$target_run_number" -gt "$current_run_number" ] \
    || { [ "$target_run_number" -eq "$current_run_number" ] && [ "$target_attempt" -gt "$current_attempt" ]; }; then
    write_generation_high_water "$target_repository" "$target_commit" "$target_run_id" "$target_run_number" "$target_attempt" "$target_envelope"
    return 0
  fi
  if [ ! -f "$ROLLBACK_AUTH_FILE" ] || [ -L "$ROLLBACK_AUTH_FILE" ] \
    || [ "$(stat -c %U:%G "$ROLLBACK_AUTH_FILE")" != "$ACTIVE_STATE_OWNER" ] \
    || [ "$(stat -c %a "$ROLLBACK_AUTH_FILE")" != 600 ]; then
    echo "Release generation is not newer and no valid root break-glass authorization exists" >&2
    return 1
  fi
  line_count="$(wc -l < "$ROLLBACK_AUTH_FILE")"
  authorization_id="$(sed -n 's/^authorization_id=//p' "$ROLLBACK_AUTH_FILE" 2>/dev/null || true)"
  issued_at="$(sed -n 's/^issued_at=//p' "$ROLLBACK_AUTH_FILE" 2>/dev/null || true)"
  expires_at="$(sed -n 's/^expires_at=//p' "$ROLLBACK_AUTH_FILE" 2>/dev/null || true)"
  reason="$(sed -n 's/^reason=//p' "$ROLLBACK_AUTH_FILE" 2>/dev/null || true)"
  now="$(date +%s)"
  if [ "$line_count" != 10 ] \
    || ! grep -Fqx -- "repository=$target_repository" "$ROLLBACK_AUTH_FILE" \
    || ! grep -Fqx -- "target_commit=$target_commit" "$ROLLBACK_AUTH_FILE" \
    || ! grep -Fqx -- "target_run_id=$target_run_id" "$ROLLBACK_AUTH_FILE" \
    || ! grep -Fqx -- "target_run_number=$target_run_number" "$ROLLBACK_AUTH_FILE" \
    || ! grep -Fqx -- "target_run_attempt=$target_attempt" "$ROLLBACK_AUTH_FILE" \
    || ! grep -Fqx -- "release_envelope_sha256=$target_envelope" "$ROLLBACK_AUTH_FILE" \
    || [[ ! "$authorization_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$ ]] \
    || [[ ! "$issued_at" =~ ^[0-9]+$ || ! "$expires_at" =~ ^[0-9]+$ ]] \
    || [ "$issued_at" -gt "$now" ] || [ "$expires_at" -le "$now" ] || [ $((expires_at - issued_at)) -gt 3600 ] \
    || [ "${#reason}" -lt 8 ] || [ "${#reason}" -gt 256 ]; then
    echo "Release generation is not newer and no valid root break-glass authorization exists" >&2
    return 1
  fi
  rm -f -- "$ROLLBACK_AUTH_FILE"
  echo "Root break-glass authorization $authorization_id consumed for frontend release generation $target_run_number/$target_attempt" >&2
}

verify_frontend_commit() {
  local expected_commit="$1"
  local internal_commit public_commit
  internal_commit="$(curl -fsS --max-time 10 --resolve "$INTERNAL_HEALTH_HOST:443:127.0.0.1" "$HEALTH_URL" | tr -d '\r\n')" || return 1
  [ "$internal_commit" = "$expected_commit" ] || return 1
  public_commit="$(curl -fsS --max-time 10 "$HEALTH_URL" | tr -d '\r\n')" || return 1
  [ "$public_commit" = "$expected_commit" ]
}

restore_previous() {
  local previous_target=""
  local previous_commit=""
  local previous_file="$release_real/PREVIOUS_TARGET"
  local previous_commit_file="$release_real/PREVIOUS_COMMIT"
  local activated_file="$release_real/ACTIVATED"
  if [ -f "$previous_file" ] && [ ! -L "$previous_file" ]; then previous_target="$(cat "$previous_file")"; fi
  if [ -n "$previous_target" ]; then
    if [ ! -f "$previous_commit_file" ] || [ -L "$previous_commit_file" ]; then
      echo "Previous frontend release commit record is missing or unsafe" >&2
      return 2
    fi
    previous_commit="$(tr -d '\r\n' < "$previous_commit_file")"
    [[ "$previous_commit" =~ ^[0-9a-f]{40}$ ]] || { echo "Previous frontend commit is invalid" >&2; return 2; }
    previous_target="$(realpath "$previous_target")"
    case "$previous_target/" in "$release_root_real"/*/site/) ;; *) echo "Refusing to restore target outside release root: $previous_target" >&2; return 2 ;; esac
    [ "$(tr -d '\r\n' < "$previous_target/.noviis-release")" = "$previous_commit" ] \
      || { echo "Previous frontend target does not match its recorded commit" >&2; return 2; }
    sudo ln -sfn "$previous_target" "$WEB_ROOT.rollback"
    sudo mv -Tf "$WEB_ROOT.rollback" "$WEB_ROOT"
    verify_frontend_commit "$previous_commit" \
      || { echo "Restored frontend target did not serve the recorded commit" >&2; return 2; }
    previous_release="$(dirname "$previous_target")"
    if [ -f "$previous_release/RELEASE_METADATA" ]; then
      previous_run_id="$(sed -n 's/^run_id=//p' "$previous_release/RELEASE_METADATA")"
      previous_run_number="$(sed -n 's/^run_number=//p' "$previous_release/RELEASE_METADATA")"
      previous_run_attempt="$(sed -n 's/^run_attempt=//p' "$previous_release/RELEASE_METADATA")"
      previous_contract="$(sed -n 's/^api_contract_revision=//p' "$previous_release/RELEASE_METADATA")"
      previous_envelope_digest="$(sha256sum "$previous_release/RELEASE_ENVELOPE" | awk '{print $1}')"
      write_active_state "$previous_commit" "$previous_run_id" "$previous_run_number" "$previous_run_attempt" \
        "$previous_envelope_digest" "$previous_contract" stable
    else
      rm -f -- "$ACTIVE_STATE_FILE"
    fi
  else
    sudo test -L "$WEB_ROOT" && sudo rm -f -- "$WEB_ROOT"
  fi
  rm -f -- "$activated_file"
}

on_exit() {
  local status="$?"
  trap - EXIT INT TERM HUP
  if [ "$status" -ne 0 ] && [ "$switched" = true ] && [ "$verified" = false ]; then
    echo "Frontend activation failed; restoring the previous target" >&2
    if ! restore_previous; then echo "Frontend rollback failed" >&2; status=2; fi
  fi
  if [ -n "$staging_dir" ] && [ -d "$staging_dir" ]; then rm -rf -- "$staging_dir"; fi
  exit "$status"
}

trap on_exit EXIT
trap 'exit 130' INT TERM HUP

release_root_real="$(realpath "$RELEASE_ROOT")"

if [ "$MODE" = rollback ]; then
  release_real="$(realpath "$SOURCE_DIR")"
  case "$release_real/" in "$release_root_real"/*/) ;; *) echo "Rollback release is outside release root: $release_real" >&2; exit 1 ;; esac
  test -f "$release_real/ACTIVATED"
  test ! -L "$release_real/ACTIVATED"
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "Rollback expected commit is invalid" >&2; exit 1; }
  [ "$(tr -d '\r\n' < "$release_real/ACTIVATED")" = "$EXPECTED_COMMIT" ] \
    || { echo "Rollback release does not match the expected commit" >&2; exit 1; }
  restore_previous
  echo "Frontend release rolled back: $release_real"
  exit 0
fi
if [ "$MODE" != activate ]; then echo "Unsupported mode: $MODE" >&2; exit 1; fi
incoming_root_real="$(realpath "$INCOMING_ROOT")"
source_real="$(realpath "$SOURCE_DIR")"
case "$source_real/" in "$incoming_root_real"/*/) ;; *) echo "Incoming release directory is outside incoming root: $source_real" >&2; exit 1 ;; esac
release_id="$(basename "$source_real")"
if [[ ! "$release_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]]; then echo "Invalid release identifier: $release_id" >&2; exit 1; fi
release_real="$release_root_real/$release_id"
test ! -e "$release_real"

staging_dir="$(mktemp -d "$release_root_real/.frontend-release.XXXXXX")"
chmod 0755 "$staging_dir"
file_count=0
while IFS= read -r -d '' source_file; do
  test ! -L "$source_file"
  install -m 0644 "$source_file" "$staging_dir/$(basename "$source_file")"
  file_count=$((file_count + 1))
done < <(find "$source_real" -mindepth 1 -maxdepth 1 -type f -print0)
test "$file_count" -gt 0
"$PROVENANCE_VERIFIER" "$staging_dir" "$EXPECTED_COMMIT" frontend
test -f "$staging_dir/frontend-release.tar.gz"
test -f "$staging_dir/SHA256SUMS"
test -f "$staging_dir/RELEASE_METADATA"
(cd "$staging_dir" && sha256sum --strict --check SHA256SUMS)
release_run_id="$(sed -n 's/^run_id=//p' "$staging_dir/RELEASE_METADATA")"
release_run_number="$(sed -n 's/^run_number=//p' "$staging_dir/RELEASE_METADATA")"
release_run_attempt="$(sed -n 's/^run_attempt=//p' "$staging_dir/RELEASE_METADATA")"
api_contract_revision="$(sed -n 's/^api_contract_revision=//p' "$staging_dir/RELEASE_METADATA")"
release_repository="$(sed -n 's/^repository=//p' "$staging_dir/RELEASE_METADATA")"
[ -n "$release_repository" ] || release_repository="legacy/local"
[[ "$release_run_id" =~ ^[1-9][0-9]*$ ]] || { echo "Frontend release run id is invalid" >&2; exit 1; }
[[ "$release_run_number" =~ ^[1-9][0-9]*$ ]] || { echo "Frontend release run number is invalid" >&2; exit 1; }
[[ "$release_run_attempt" =~ ^[1-9][0-9]*$ ]] || { echo "Frontend release run attempt is invalid" >&2; exit 1; }
[[ "$api_contract_revision" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
  || { echo "Frontend API contract revision is invalid" >&2; exit 1; }
[[ "$release_repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] \
  || { echo "Frontend release repository is invalid" >&2; exit 1; }
if [ -n "$EXPECTED_RUN_ID" ]; then test "$release_run_id" = "$EXPECTED_RUN_ID"; fi
if [ -n "$EXPECTED_RUN_NUMBER" ]; then test "$release_run_number" = "$EXPECTED_RUN_NUMBER"; fi
if [ -n "$EXPECTED_RUN_ATTEMPT" ]; then test "$release_run_attempt" = "$EXPECTED_RUN_ATTEMPT"; fi
release_envelope_digest="$(sha256sum "$staging_dir/RELEASE_ENVELOPE" | awk '{print $1}')"
if [ -f "$ACTIVE_STATE_FILE" ]; then
  test ! -L "$ACTIVE_STATE_FILE"
  test "$(stat -c %U:%G "$ACTIVE_STATE_FILE")" = "$ACTIVE_STATE_OWNER"
  test "$(stat -c %a "$ACTIVE_STATE_FILE")" = "$ACTIVE_STATE_MODE"
  current_run_id="$(sed -n 's/^run_id=//p' "$ACTIVE_STATE_FILE")"
  current_run_number="$(sed -n 's/^run_number=//p' "$ACTIVE_STATE_FILE")"
  current_run_attempt="$(sed -n 's/^run_attempt=//p' "$ACTIVE_STATE_FILE")"
  current_commit="$(sed -n 's/^commit_sha=//p' "$ACTIVE_STATE_FILE")"
  current_envelope="$(sed -n 's/^release_envelope_sha256=//p' "$ACTIVE_STATE_FILE")"
  [[ "$current_run_id" =~ ^[0-9]+$ ]] || { echo "Current frontend run id is invalid" >&2; exit 1; }
  [[ "$current_run_number" =~ ^[0-9]+$ ]] || { echo "Current frontend run number is invalid" >&2; exit 1; }
  [[ "$current_run_attempt" =~ ^[0-9]+$ ]] || { echo "Current frontend run attempt is invalid" >&2; exit 1; }
  [[ "$current_commit" =~ ^[0-9a-f]{40}$ && "$current_envelope" =~ ^[0-9a-f]{64}$ ]] \
    || { echo "Current frontend release identity is invalid" >&2; exit 1; }
  if [ ! -f "$GENERATION_HIGH_WATER_FILE" ] && [ "$current_run_number" -gt 0 ]; then
    write_generation_high_water "legacy/local" "$current_commit" "$current_run_id" "$current_run_number" \
      "$current_run_attempt" "$current_envelope"
  fi
fi
while IFS= read -r member; do
  case "$member" in /*|../*|*/../*|*/..) echo "Unsafe archive path: $member" >&2; exit 1 ;; esac
done < <(tar -tzf "$staging_dir/frontend-release.tar.gz")
archive_listing="$staging_dir/archive-listing.txt"
tar -tvzf "$staging_dir/frontend-release.tar.gz" > "$archive_listing"
if awk '$1 !~ /^[-d]/ { bad=1 } END { exit bad ? 0 : 1 }' "$archive_listing"; then
  echo "Frontend archive contains links or unsupported entry types" >&2
  exit 1
fi
archive_stats="$(awk '
  $1 ~ /^-/ {
    files += 1
    total += $3
    if ($3 > max_file) max_file = $3
  }
  END { printf "%d %d %d\n", files, total, max_file }
' "$archive_listing")"
read -r archive_file_count archive_total_bytes archive_max_file_bytes <<< "$archive_stats"
if [ "$archive_file_count" -gt "$ARCHIVE_MAX_FILES" ]; then
  echo "Frontend archive contains too many files: $archive_file_count" >&2
  exit 1
fi
if [ "$archive_max_file_bytes" -gt "$ARCHIVE_MAX_SINGLE_FILE_BYTES" ]; then
  echo "Frontend archive contains a file larger than the extraction limit" >&2
  exit 1
fi
if [ "$archive_total_bytes" -gt "$ARCHIVE_MAX_TOTAL_BYTES" ]; then
  echo "Frontend archive expands beyond the total extraction limit" >&2
  exit 1
fi
available_bytes="$(df -PB1 "$release_root_real" | awk 'NR == 2 { print $4 }')"
required_bytes=$((archive_total_bytes + ARCHIVE_FREE_SPACE_HEADROOM_BYTES))
if [[ ! "$available_bytes" =~ ^[0-9]+$ ]] || [ "$available_bytes" -lt "$required_bytes" ]; then
  echo "Frontend release root does not have sufficient extraction headroom" >&2
  exit 1
fi
rm -f -- "$archive_listing"

site_dir="$staging_dir/site"
mkdir -m 0755 "$site_dir"
tar -xzf "$staging_dir/frontend-release.tar.gz" -C "$site_dir" --no-same-owner --no-same-permissions
test -f "$site_dir/index.html"
test -f "$site_dir/robots.txt"
test -f "$site_dir/sitemap.xml"
test -f "$site_dir/.noviis-release"
test -f "$site_dir/.noviis-seo-release.json"
grep -q assets "$site_dir/index.html"
release_commit="$(tr -d '\r\n' < "$site_dir/.noviis-release")"
[[ "$release_commit" =~ ^[0-9a-f]{40}$ ]] || { echo "Frontend release commit is invalid" >&2; exit 1; }
if [ -n "$EXPECTED_COMMIT" ] && [ "$release_commit" != "$EXPECTED_COMMIT" ]; then echo "Frontend release commit mismatch" >&2; exit 1; fi
grep -Fq -- "\"commitSha\": \"$release_commit\"" "$site_dir/.noviis-seo-release.json"
grep -Eq -- '"postUrlCount": [1-9][0-9]*' "$site_dir/.noviis-seo-release.json"
grep -Eq -- '"prerenderCount": [1-9][0-9]*' "$site_dir/.noviis-seo-release.json"

if grep -Eq '<loc>[^<]+/board/[^<]+/post/[0-9]+/?</loc>' "$site_dir/sitemap.xml"; then
  mapfile -d '' post_indexes < <(find "$site_dir/board" -path '*/post/*/index.html' -type f -print0 2>/dev/null || true)
  if [ "${#post_indexes[@]}" -eq 0 ]; then echo "No pre-rendered post pages found in release directory" >&2; exit 1; fi
  for post_index in "${post_indexes[@]}"; do grep -qi 'rel="canonical"' "$post_index"; grep -qi 'application/ld+json' "$post_index"; done
fi

mv -T "$staging_dir" "$release_real"
staging_dir=""
site_dir="$release_real/site"

if [ -L "$WEB_ROOT" ]; then
  previous_target="$(readlink -f "$WEB_ROOT")"
  case "$previous_target/" in "$release_root_real"/*/site/) ;; *) echo "Existing frontend target is outside release root: $previous_target" >&2; exit 1 ;; esac
elif [ -e "$WEB_ROOT" ]; then
  echo "Frontend web root must be absent or a managed symlink: $WEB_ROOT" >&2
  exit 1
else
  previous_target=""
fi
write_state "$release_real/PREVIOUS_TARGET" "$previous_target"
if [ -n "$previous_target" ]; then
  previous_commit="$(tr -d '\r\n' < "$previous_target/.noviis-release")"
  [[ "$previous_commit" =~ ^[0-9a-f]{40}$ ]] || { echo "Existing frontend commit is invalid" >&2; exit 1; }
  write_state "$release_real/PREVIOUS_COMMIT" "$previous_commit"
fi

authorize_release_generation "$release_repository" "$release_commit" "$release_run_id" "$release_run_number" \
  "$release_run_attempt" "$release_envelope_digest"

sudo ln -sfn "$site_dir" "$WEB_ROOT.next"
sudo mv -Tf "$WEB_ROOT.next" "$WEB_ROOT"
switched=true
write_state "$release_real/ACTIVATED" "$release_commit"
write_active_state "$release_commit" "$release_run_id" "$release_run_number" "$release_run_attempt" \
  "$release_envelope_digest" "$api_contract_revision" pending

if ! verify_frontend_commit "$release_commit"; then echo "Frontend health endpoints returned a different release commit" >&2; exit 1; fi
verified=true
write_active_state "$release_commit" "$release_run_id" "$release_run_number" "$release_run_attempt" \
  "$release_envelope_digest" "$api_contract_revision" stable
echo "ACTIVATED_SHA=$release_commit"
echo "ACTIVATED_RUN_ID=$release_run_id"
echo "ACTIVATED_RUN_NUMBER=$release_run_number"
echo "ACTIVATED_RUN_ATTEMPT=$release_run_attempt"
echo "ACTIVATED_API_CONTRACT=$api_contract_revision"

cleanup_releases() {
  declare -A keep=()
  keep["$release_real"]=1
  local keep_count=1 previous_release entry path_real
  local failed=false
  local listing
  local -a releases=()
  if [ -n "$previous_target" ]; then previous_release="$(dirname "$previous_target")"; keep["$previous_release"]=1; keep_count=$((keep_count + 1)); fi
  if ! listing="$(mktemp "$release_root_real/.frontend-cleanup-list.XXXXXX")"; then
    return 1
  fi
  if ! chmod 0600 "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! find "$release_root_real" -mindepth 1 -maxdepth 1 -type d ! -name '.frontend-release.*' -printf '%T@ %p\0' | sort -z -nr > "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! mapfile -d '' releases < "$listing"; then
    rm -f -- "$listing"
    return 1
  fi
  if ! rm -f -- "$listing"; then
    return 1
  fi
  for entry in "${releases[@]}"; do
    if ! path_real="$(realpath "${entry#* }")"; then failed=true; continue; fi
    [ -n "${keep[$path_real]+x}" ] && continue
    if [ "$keep_count" -lt "$KEEP_RELEASES" ]; then keep["$path_real"]=1; keep_count=$((keep_count + 1)); fi
  done
  for entry in "${releases[@]}"; do
    if ! path_real="$(realpath "${entry#* }")"; then failed=true; continue; fi
    case "$path_real/" in "$release_root_real"/*/) ;; *) echo "Refusing to remove path outside release root: $path_real" >&2; return 1 ;; esac
    if [ -z "${keep[$path_real]+x}" ] && ! rm -rf -- "$path_real"; then failed=true; fi
  done
  [ "$failed" = false ]
}

if ! cleanup_releases; then
  echo "CLEANUP_DEBT=frontend_release_retention"
  echo "CLEANUP_DEBT=frontend_release_retention" >&2
  "$CLEANUP_DEBT_WRITER" frontend set release_retention \
    || echo "CLEANUP_DEBT=frontend_cleanup_metric" >&2
else
  "$CLEANUP_DEBT_WRITER" frontend clear release_retention \
    || echo "CLEANUP_DEBT=frontend_cleanup_metric" >&2
fi
if ! rm -rf -- "$source_real"; then
  echo "CLEANUP_DEBT=frontend_incoming_release"
  echo "CLEANUP_DEBT=frontend_incoming_release" >&2
  "$CLEANUP_DEBT_WRITER" frontend set incoming_release \
    || echo "CLEANUP_DEBT=frontend_cleanup_metric" >&2
else
  "$CLEANUP_DEBT_WRITER" frontend clear incoming_release \
    || echo "CLEANUP_DEBT=frontend_cleanup_metric" >&2
fi
echo "Frontend release activated: $release_real"
