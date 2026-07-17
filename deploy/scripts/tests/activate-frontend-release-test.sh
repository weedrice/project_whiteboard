#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/activate-frontend-release.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

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
cat "$WEB_ROOT/.noviis-release"
EOF
chmod +x "$fake_bin"/*

make_release() {
  local name="$1"
  local commit="$2"
  local release="$incoming_root/$name"
  local source="$fixture/source-$name"
  mkdir -p "$release" "$source/assets"
  printf '<div id="app"><script src="/assets/app.js"></script></div>\n' > "$source/index.html"
  printf 'User-agent: *\n' > "$source/robots.txt"
  printf '<urlset></urlset>\n' > "$source/sitemap.xml"
  printf '%s\n' "$commit" > "$source/.noviis-release"
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
  PATH="$fake_bin:$PATH" \
  bash "$script" "$1" "${3:-activate}" "$2"
}

old_release="$(make_release old old-commit)"
run_activation "$old_release" old-commit
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

echo "Frontend activation fixtures passed"
