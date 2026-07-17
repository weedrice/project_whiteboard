#!/usr/bin/env bash
set -Eeuo pipefail

RELEASE_ROOT="${RELEASE_ROOT:-/var/www/releases/frontend}"
WEB_ROOT="${WEB_ROOT:-/var/www/app}"
HEALTH_URL="${HEALTH_URL:-https://noviis.kr/.noviis-release}"
INTERNAL_HEALTH_HOST="${INTERNAL_HEALTH_HOST:-noviis.kr}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
RELEASE_DIR="${1:?release directory is required}"
MODE="${2:-activate}"
EXPECTED_COMMIT="${3:-${EXPECTED_COMMIT:-}}"

switched=false
verified=false

release_root_real="$(realpath "$RELEASE_ROOT")"
release_real="$(realpath "$RELEASE_DIR")"
case "$release_real/" in
  "$release_root_real"/*/) ;;
  *) echo "Release directory is outside release root: $release_real" >&2; exit 1 ;;
esac

previous_file="$release_real/PREVIOUS_TARGET"
activated_file="$release_real/ACTIVATED"

restore_previous() {
  local previous_target=""
  if [ -f "$previous_file" ]; then
    previous_target="$(cat "$previous_file")"
  fi

  if [ -n "$previous_target" ]; then
    previous_target="$(realpath "$previous_target")"
    case "$previous_target/" in
      "$release_root_real"/*/site/) ;;
      *) echo "Refusing to restore target outside release root: $previous_target" >&2; return 2 ;;
    esac
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
    if ! restore_previous; then
      echo "Frontend rollback failed" >&2
      status=2
    fi
  fi
  exit "$status"
}

trap on_exit EXIT
trap 'exit 130' INT TERM HUP

if [ "$MODE" = rollback ]; then
  test -f "$activated_file"
  restore_previous
  echo "Frontend release rolled back: $release_real"
  exit 0
fi
if [ "$MODE" != activate ]; then
  echo "Unsupported mode: $MODE" >&2
  exit 1
fi

test -f "$release_real/frontend-release.tar.gz"
test -f "$release_real/SHA256SUMS"
(cd "$release_real" && sha256sum --strict --check SHA256SUMS)

site_dir="$release_real/site"
test ! -e "$site_dir"
mkdir -m 0755 "$site_dir"
tar -xzf "$release_real/frontend-release.tar.gz" -C "$site_dir" --no-same-owner --no-same-permissions
test -f "$site_dir/index.html"
test -f "$site_dir/robots.txt"
test -f "$site_dir/sitemap.xml"
test -f "$site_dir/.noviis-release"
grep -q assets "$site_dir/index.html"

release_commit="$(tr -d '\r\n' < "$site_dir/.noviis-release")"
if [ -n "$EXPECTED_COMMIT" ] && [ "$release_commit" != "$EXPECTED_COMMIT" ]; then
  echo "Frontend release commit mismatch" >&2
  exit 1
fi

if grep -Eq '<loc>[^<]+/board/[^<]+/post/[0-9]+/?</loc>' "$site_dir/sitemap.xml"; then
  mapfile -d '' post_indexes < <(find "$site_dir/board" -path '*/post/*/index.html' -type f -print0 2>/dev/null || true)
  if [ "${#post_indexes[@]}" -eq 0 ]; then
    echo "No pre-rendered post pages found in release directory" >&2
    exit 1
  fi
  for post_index in "${post_indexes[@]}"; do
    grep -qi 'rel="canonical"' "$post_index"
    grep -qi 'application/ld+json' "$post_index"
  done
fi

if [ -L "$WEB_ROOT" ]; then
  previous_target="$(readlink -f "$WEB_ROOT")"
  case "$previous_target/" in
    "$release_root_real"/*/site/) ;;
    *) echo "Existing frontend target is outside release root: $previous_target" >&2; exit 1 ;;
  esac
elif [ -e "$WEB_ROOT" ]; then
  echo "Frontend web root must be absent or a managed symlink: $WEB_ROOT" >&2
  exit 1
else
  previous_target=""
fi
printf '%s\n' "$previous_target" > "$previous_file"

sudo ln -sfn "$site_dir" "$WEB_ROOT.next"
sudo mv -Tf "$WEB_ROOT.next" "$WEB_ROOT"
switched=true
printf '%s\n' "$release_commit" > "$activated_file"

internal_commit="$(curl -fsS --max-time 10 --resolve "$INTERNAL_HEALTH_HOST:443:127.0.0.1" "$HEALTH_URL" | tr -d '\r\n')"
if [ "$internal_commit" != "$release_commit" ]; then
  echo "Frontend internal health endpoint returned a different release commit" >&2
  exit 1
fi
public_commit="$(curl -fsS --max-time 10 "$HEALTH_URL" | tr -d '\r\n')"
if [ "$public_commit" != "$release_commit" ]; then
  echo "Frontend health endpoint returned a different release commit" >&2
  exit 1
fi
verified=true

declare -A keep=()
keep["$release_real"]=1
keep_count=1
if [ -n "$previous_target" ]; then
  previous_release="$(dirname "$previous_target")"
  case "$previous_release/" in
    "$release_root_real"/*/) keep["$previous_release"]=1; keep_count=$((keep_count + 1)) ;;
    *) echo "Previous release escaped the release root: $previous_release" >&2; exit 1 ;;
  esac
fi
mapfile -d '' releases < <(find "$release_root_real" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\0' | sort -z -nr)
for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  [ -n "${keep[$path_real]+x}" ] && continue
  if [ "$keep_count" -lt "$KEEP_RELEASES" ]; then
    keep["$path_real"]=1
    keep_count=$((keep_count + 1))
  fi
done
for entry in "${releases[@]}"; do
  path="${entry#* }"
  path_real="$(realpath "$path")"
  case "$path_real/" in
    "$release_root_real"/*/) ;;
    *) echo "Refusing to remove path outside release root: $path_real" >&2; exit 1 ;;
  esac
  if [ -z "${keep[$path_real]+x}" ]; then
    rm -rf -- "$path_real"
  fi
done

echo "Frontend release activated: $release_real"
