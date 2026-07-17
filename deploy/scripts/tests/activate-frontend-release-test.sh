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
if [ -f "$STATE_DIR/fail_health" ]; then exit 1; fi
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
  PATH="$fake_bin:$PATH" \
  bash "$script" "$1" "${3:-activate}" "$2"
}

old_release="$(make_release old old-commit)"
old_output="$(run_activation "$old_release" old-commit)"
grep -Fqx 'ACTIVATED_SHA=old-commit' <<< "$old_output"
test "$(readlink -f "$web_root")" = "$release_root/old/site"

new_release="$(make_release new new-commit)"
touch "$fixture/fail_health"
if run_activation "$new_release" new-commit; then
  echo "Expected frontend health failure" >&2
  exit 1
fi
test "$(readlink -f "$web_root")" = "$release_root/old/site"
rm "$fixture/fail_health"

retry_release="$(make_release retry retry-commit)"
run_activation "$retry_release" retry-commit
test "$(readlink -f "$web_root")" = "$release_root/retry/site"
run_activation "$release_root/retry" retry-commit rollback
test "$(readlink -f "$web_root")" = "$release_root/old/site"

victim="$fixture/victim"
printf 'unchanged\n' > "$victim"
safe_release="$(make_release symlink-safe safe-commit)"
ln -s "$victim" "$safe_release/ACTIVATED"
run_activation "$safe_release" safe-commit
grep -qx unchanged "$victim"

for index in 1 2 3 4 5 6; do
  mkdir -p "$release_root/archive-$index"
  touch -d "2026-06-$((10 + index))" "$release_root/archive-$index"
done
mkdir -p "$release_root/cleanup-victim"
touch -d '2020-01-01' "$release_root/cleanup-victim"
touch "$fixture/fail_cleanup"
cleanup_release="$(make_release cleanup cleanup-commit)"
cleanup_output="$(run_activation "$cleanup_release" cleanup-commit 2>&1)"
grep -Fqx 'ACTIVATED_SHA=cleanup-commit' <<< "$cleanup_output"
grep -Fq 'CLEANUP_DEBT=frontend_release_retention' <<< "$cleanup_output"
test -d "$release_root/cleanup-victim"
rm "$fixture/fail_cleanup"

touch "$fixture/arm_fail_cleanup_listing"
listing_release="$(make_release listing listing-commit)"
listing_output="$(run_activation "$listing_release" listing-commit 2>&1)"
grep -Fqx 'ACTIVATED_SHA=listing-commit' <<< "$listing_output"
grep -Fq 'CLEANUP_DEBT=frontend_release_retention' <<< "$listing_output"
rm "$fixture/arm_fail_cleanup_listing" "$fixture/fail_cleanup_listing"

echo "Frontend activation fixtures passed"
