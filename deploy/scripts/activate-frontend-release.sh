#!/usr/bin/env bash
set -Eeuo pipefail

INCOMING_ROOT="${INCOMING_ROOT:-/var/www/incoming/frontend}"
RELEASE_ROOT="${RELEASE_ROOT:-/var/www/releases/frontend}"
WEB_ROOT="${WEB_ROOT:-/var/www/app}"
HEALTH_URL="${HEALTH_URL:-https://noviis.kr/.noviis-release}"
INTERNAL_HEALTH_HOST="${INTERNAL_HEALTH_HOST:-noviis.kr}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
PROVENANCE_VERIFIER="${PROVENANCE_VERIFIER:-/usr/local/sbin/verify-noviis-release}"
SOURCE_DIR="${1:?incoming release directory is required}"
MODE="${2:-activate}"
EXPECTED_COMMIT="${3:-${EXPECTED_COMMIT:-}}"

switched=false
verified=false
staging_dir=""
release_real=""

write_state() {
  local destination="$1"
  local value="$2"
  local temporary
  temporary="$(mktemp)"
  printf '%s\n' "$value" > "$temporary"
  chmod 0644 "$temporary"
  mv -Tf "$temporary" "$destination"
}

restore_previous() {
  local previous_target=""
  local previous_file="$release_real/PREVIOUS_TARGET"
  local activated_file="$release_real/ACTIVATED"
  if [ -f "$previous_file" ] && [ ! -L "$previous_file" ]; then previous_target="$(cat "$previous_file")"; fi
  if [ -n "$previous_target" ]; then
    previous_target="$(realpath "$previous_target")"
    case "$previous_target/" in "$release_root_real"/*/site/) ;; *) echo "Refusing to restore target outside release root: $previous_target" >&2; return 2 ;; esac
    sudo ln -sfn "$previous_target" "$WEB_ROOT.rollback"
    sudo mv -Tf "$WEB_ROOT.rollback" "$WEB_ROOT"
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
(cd "$staging_dir" && sha256sum --strict --check SHA256SUMS)

while IFS= read -r member; do
  case "$member" in /*|../*|*/../*|*/..) echo "Unsafe archive path: $member" >&2; exit 1 ;; esac
done < <(tar -tzf "$staging_dir/frontend-release.tar.gz")
if tar -tvzf "$staging_dir/frontend-release.tar.gz" | awk '$1 !~ /^[-d]/ { bad=1 } END { exit bad ? 0 : 1 }'; then
  echo "Frontend archive contains links or unsupported entry types" >&2
  exit 1
fi

site_dir="$staging_dir/site"
mkdir -m 0755 "$site_dir"
tar -xzf "$staging_dir/frontend-release.tar.gz" -C "$site_dir" --no-same-owner --no-same-permissions
test -f "$site_dir/index.html"
test -f "$site_dir/robots.txt"
test -f "$site_dir/sitemap.xml"
test -f "$site_dir/.noviis-release"
grep -q assets "$site_dir/index.html"
release_commit="$(tr -d '\r\n' < "$site_dir/.noviis-release")"
if [ -n "$EXPECTED_COMMIT" ] && [ "$release_commit" != "$EXPECTED_COMMIT" ]; then echo "Frontend release commit mismatch" >&2; exit 1; fi

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

sudo ln -sfn "$site_dir" "$WEB_ROOT.next"
sudo mv -Tf "$WEB_ROOT.next" "$WEB_ROOT"
switched=true
write_state "$release_real/ACTIVATED" "$release_commit"

internal_commit="$(curl -fsS --max-time 10 --resolve "$INTERNAL_HEALTH_HOST:443:127.0.0.1" "$HEALTH_URL" | tr -d '\r\n')"
if [ "$internal_commit" != "$release_commit" ]; then echo "Frontend internal health endpoint returned a different release commit" >&2; exit 1; fi
public_commit="$(curl -fsS --max-time 10 "$HEALTH_URL" | tr -d '\r\n')"
if [ "$public_commit" != "$release_commit" ]; then echo "Frontend health endpoint returned a different release commit" >&2; exit 1; fi
verified=true

declare -A keep=()
keep["$release_real"]=1
keep_count=1
if [ -n "$previous_target" ]; then previous_release="$(dirname "$previous_target")"; keep["$previous_release"]=1; keep_count=$((keep_count + 1)); fi
mapfile -d '' releases < <(find "$release_root_real" -mindepth 1 -maxdepth 1 -type d ! -name '.frontend-release.*' -printf '%T@ %p\0' | sort -z -nr)
for entry in "${releases[@]}"; do
  path_real="$(realpath "${entry#* }")"
  [ -n "${keep[$path_real]+x}" ] && continue
  if [ "$keep_count" -lt "$KEEP_RELEASES" ]; then keep["$path_real"]=1; keep_count=$((keep_count + 1)); fi
done
for entry in "${releases[@]}"; do
  path_real="$(realpath "${entry#* }")"
  case "$path_real/" in "$release_root_real"/*/) ;; *) echo "Refusing to remove path outside release root: $path_real" >&2; exit 1 ;; esac
  if [ -z "${keep[$path_real]+x}" ]; then rm -rf -- "$path_real"; fi
done

rm -rf -- "$source_real"
echo "Frontend release activated: $release_real"
