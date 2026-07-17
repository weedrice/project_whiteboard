#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/activate-frontend-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

provenance_verifier="$fixture/verify-release"
cat > "$provenance_verifier" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$provenance_verifier"

release_root="$fixture/releases"
incoming_root="$fixture/incoming"
web_root="$fixture/app"
fake_bin="$fixture/bin"
mkdir -p "$release_root" "$incoming_root" "$fake_bin"

if ! ln -s "$release_root" "$fixture/symlink-probe" 2>/dev/null || [ ! -L "$fixture/symlink-probe" ]; then
  echo "Frontend activation fixtures skipped because native symbolic links are unavailable"
  exit 0
fi
rm "$fixture/symlink-probe"

cat > "$fake_bin/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF
cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
if [ -f "$STATE_DIR/fail_health" ] \
  && [ "$(tr -d '\r\n' < "$WEB_ROOT/.noviis-release")" = "$(cat "$STATE_DIR/fail_health")" ]; then exit 1; fi
if [ -f "$STATE_DIR/arm_fail_cleanup_listing" ]; then touch "$STATE_DIR/fail_cleanup_listing"; fi
cat "$WEB_ROOT/.noviis-release"
EOF
cat > "$fake_bin/rm" <<'EOF'
#!/usr/bin/env bash
for argument in "$@"; do
  if [ -f "$STATE_DIR/fail_cleanup" ] && [ "$argument" = "$RELEASE_ROOT/cleanup-victim" ]; then exit 1; fi
done
exec /usr/bin/rm "$@"
EOF
cat > "$fake_bin/find" <<'EOF'
#!/usr/bin/env bash
if [ -f "$STATE_DIR/fail_cleanup_listing" ] && [ "${1:-}" = "$RELEASE_ROOT" ]; then exit 1; fi
exec /usr/bin/find "$@"
EOF
cat > "$fake_bin/mktemp" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$STATE_DIR/mktemp.log"
exec /usr/bin/mktemp "$@"
EOF
chmod +x "$fake_bin"/*

make_release() {
  local name="$1"
  local commit="$2"
  local release="$incoming_root/$name"
  local source="$fixture/source-$name"
  mkdir -p "$release" "$source/assets" "$source/board/test/post/1"
  printf '<div id="app"><script src="/assets/app.js"></script></div>\n' > "$source/index.html"
  printf 'User-agent: *\n' > "$source/robots.txt"
  printf '<urlset><url><loc>https://noviis.kr/board/test/post/1/</loc></url></urlset>\n' > "$source/sitemap.xml"
  printf '<link rel="canonical"><script type="application/ld+json">{}</script>\n' > "$source/board/test/post/1/index.html"
  printf '%s\n' "$commit" > "$source/.noviis-release"
  printf '{\n  "commitSha": "%s",\n  "urlCount": 1,\n  "postUrlCount": 1,\n  "prerenderCount": 1\n}\n' "$commit" > "$source/.noviis-seo-release.json"
  printf 'asset\n' > "$source/assets/app.js"
  tar -czf "$release/frontend-release.tar.gz" -C "$source" .
  (cd "$release" && sha256sum frontend-release.tar.gz > SHA256SUMS)
  printf '%s\n' "$release"
}

run_activation() {
  RELEASE_ROOT="$release_root" \
  INCOMING_ROOT="$incoming_root" \
  WEB_ROOT="$web_root" \
  HEALTH_URL=http://fixture/.noviis-release \
  STATE_DIR="$fixture" \
  PROVENANCE_VERIFIER="$provenance_verifier" \
  DEPLOY_LOCK_FILE="$fixture/noviis-deploy.lock" \
  PATH="$fake_bin:$PATH" \
  bash "$script" "$1" "${3:-activate}" "$2"
}

old_commit=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
new_commit=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
retry_commit=cccccccccccccccccccccccccccccccccccccccc
safe_commit=dddddddddddddddddddddddddddddddddddddddd
cleanup_commit=eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee
listing_commit=ffffffffffffffffffffffffffffffffffffffff

lock_release="$(make_release lock "$new_commit")"
(
  exec 8>"$fixture/noviis-deploy.lock"
  flock 8
  if run_activation "$lock_release" "$new_commit"; then
    echo "Expected concurrent frontend activation to be rejected" >&2
    exit 1
  fi
  test ! -e "$web_root"
)

old_release="$(make_release old "$old_commit")"
old_output="$(run_activation "$old_release" "$old_commit")"
grep -Fqx "ACTIVATED_SHA=$old_commit" <<< "$old_output"
test "$(readlink -f "$web_root")" = "$release_root/old/site"

file_limit_release="$(make_release file-limit "$new_commit")"
if ARCHIVE_MAX_FILES=1 run_activation "$file_limit_release" "$new_commit"; then
  echo "Expected frontend archive file-count limit to reject extraction" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/old/site"

size_limit_release="$(make_release size-limit "$new_commit")"
if ARCHIVE_MAX_SINGLE_FILE_BYTES=1 run_activation "$size_limit_release" "$new_commit"; then
  echo "Expected frontend archive single-file size limit to reject extraction" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/old/site"

new_release="$(make_release new "$new_commit")"
printf '%s\n' "$new_commit" > "$fixture/fail_health"
if run_activation "$new_release" "$new_commit"; then
  echo "Expected frontend health failure" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/old/site"
rm "$fixture/fail_health"

retry_release="$(make_release retry "$retry_commit")"
run_activation "$retry_release" "$retry_commit"
test "$(readlink -f "$web_root")" = "$release_root/retry/site"
grep -Fq "$release_root/retry/.state." "$fixture/mktemp.log"
if find "$release_root/retry" -maxdepth 1 -name '.state.*' | grep -q .; then
  echo "Expected frontend state temporary files to be cleaned" >&2
  exit 1
fi
if run_activation "$release_root/retry" "$old_commit" rollback; then
  echo "Expected rollback with a mismatched activation commit to fail" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/retry/site"
printf '%s\n' "$old_commit" > "$fixture/fail_health"
if run_activation "$release_root/retry" "$retry_commit" rollback; then
  echo "Expected rollback health verification failure" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/old/site"
rm "$fixture/fail_health"
run_activation "$release_root/retry" "$retry_commit" rollback
test "$(readlink -f "$web_root")" = "$release_root/old/site"

victim="$fixture/victim"
printf 'unchanged\n' > "$victim"
safe_release="$(make_release symlink-safe "$safe_commit")"
ln -s "$victim" "$safe_release/ACTIVATED"
run_activation "$safe_release" "$safe_commit"
grep -qx unchanged "$victim"

for index in 1 2 3 4 5 6; do
  mkdir -p "$release_root/archive-$index"
  touch -d "2026-06-$((10 + index))" "$release_root/archive-$index"
done
mkdir -p "$release_root/cleanup-victim"
touch -d '2020-01-01' "$release_root/cleanup-victim"
touch "$fixture/fail_cleanup"
cleanup_release="$(make_release cleanup "$cleanup_commit")"
cleanup_output="$(run_activation "$cleanup_release" "$cleanup_commit" 2>&1)"
grep -Fqx "ACTIVATED_SHA=$cleanup_commit" <<< "$cleanup_output"
grep -Fq 'CLEANUP_DEBT=frontend_release_retention' <<< "$cleanup_output"
test -d "$release_root/cleanup-victim"
rm "$fixture/fail_cleanup"

touch "$fixture/arm_fail_cleanup_listing"
listing_release="$(make_release listing "$listing_commit")"
listing_output="$(run_activation "$listing_release" "$listing_commit" 2>&1)"
grep -Fqx "ACTIVATED_SHA=$listing_commit" <<< "$listing_output"
grep -Fq 'CLEANUP_DEBT=frontend_release_retention' <<< "$listing_output"
rm "$fixture/arm_fail_cleanup_listing" "$fixture/fail_cleanup_listing"

echo "Frontend activation fixtures passed"
