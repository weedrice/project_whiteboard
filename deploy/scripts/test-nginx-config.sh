#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
temporary_root="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
fixture="$(mktemp -d "$temporary_root/noviis-nginx-test.XXXXXX")"
cleanup() {
  case "$fixture" in "$temporary_root"/noviis-nginx-test.*) rm -rf -- "$fixture" ;; esac
}
trap cleanup EXIT

mkdir -p "$fixture/conf.d" "$fixture/snippets" "$fixture/letsencrypt/live/noviis.kr" "$fixture/site"
cp "$project_root/deploy/nginx/noviis.conf" "$fixture/conf.d/default.conf"
cp "$project_root/deploy/nginx/security-headers.conf" "$fixture/snippets/noviis-security-headers.conf"
sed -i 's#ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;##' "$fixture/conf.d/default.conf"
# An additional loopback listener exercises the original locations without TLS client dependencies.
sed -i '/listen 443 ssl http2;/a\    listen 8080;' "$fixture/conf.d/default.conf"
touch "$fixture/letsencrypt/options-ssl-nginx.conf"
MSYS2_ARG_CONV_EXCL='/CN=' openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=noviis.invalid' \
  -keyout "$fixture/letsencrypt/live/noviis.kr/privkey.pem" \
  -out "$fixture/letsencrypt/live/noviis.kr/fullchain.pem" >/dev/null 2>&1
chmod 0644 "$fixture/letsencrypt/live/noviis.kr/privkey.pem"
printf '<!doctype html><div id="app">nginx-fixture</div>\n' > "$fixture/site/index.html"
printf 'fixture-release\n' > "$fixture/site/.noviis-release"
printf '// fixture\n' > "$fixture/site/service-worker.js"
printf '{}\n' > "$fixture/site/manifest.webmanifest"
for area in assets js css img fonts; do
  mkdir -p "$fixture/site/$area"
  printf 'static-fixture\n' > "$fixture/site/$area/present-AbCdEfGh.js"
done

docker_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi
}

MSYS_NO_PATHCONV=1 docker run --rm -i --network none --read-only --user nginx --cap-drop ALL \
  --security-opt no-new-privileges:true --sysctl net.ipv4.ip_unprivileged_port_start=0 \
  --tmpfs /var/cache/nginx:uid=101,gid=101,mode=0755 --tmpfs /var/run:uid=101,gid=101,mode=0755 --tmpfs /tmp \
  -v "$(docker_path "$fixture/conf.d/default.conf"):/etc/nginx/conf.d/default.conf:ro" \
  -v "$(docker_path "$fixture/snippets"):/etc/nginx/snippets:ro" \
  -v "$(docker_path "$fixture/letsencrypt"):/etc/letsencrypt:ro" \
  -v "$(docker_path "$fixture/site"):/var/www/app:ro" \
  nginx:1.27.4-alpine@sha256:4ff102c5d78d254a6f0da062b3cf39eaf07f01eec0927fd21e219d0af8bc0591 \
  sh -eu <<'CONTAINER'
nginx -t
nginx
trap 'nginx -s quit' EXIT

assert_response() {
  path="$1" status="$2" cache_control="$3"
  printf 'GET %s HTTP/1.1\r\nHost: noviis.kr\r\nConnection: close\r\n\r\n' "$path" \
    | nc -w 2 127.0.0.1 8080 > /tmp/response
  tr -d '\r' < /tmp/response > /tmp/normalized
  test "$(awk 'NR == 1 { print $2 }' /tmp/normalized)" = "$status"
  grep -Fqx "Cache-Control: $cache_control" /tmp/normalized
  grep -Fqx 'X-Content-Type-Options: nosniff' /tmp/normalized
  grep -Fqx 'X-Frame-Options: DENY' /tmp/normalized
  grep -Fqx 'Referrer-Policy: strict-origin-when-cross-origin' /tmp/normalized
  grep -Fqx 'Permissions-Policy: camera=(), microphone=(), geolocation=()' /tmp/normalized
  grep -Fq "Content-Security-Policy: default-src 'self';" /tmp/normalized
  if [ "$status" = 404 ]; then
    ! grep -Fq 'nginx-fixture' /tmp/normalized
  fi
  printf 'PASS %s: HTTP %s, cache policy, security headers\n' "$path" "$status"
}

for area in assets js css img fonts; do
  assert_response "/$area/present-AbCdEfGh.js" 200 'public, max-age=2592000, immutable'
  assert_response "/$area/missing-AbCdEfGh.js" 404 'no-store'
done
for path in / /index.html /unvisited-spa-route; do
  assert_response "$path" 200 'no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0'
  grep -Fq 'nginx-fixture' /tmp/normalized
done
assert_response /service-worker.js 200 'no-cache, no-store, must-revalidate'
assert_response /.noviis-release 200 'no-store, max-age=0'
assert_response /manifest.webmanifest 200 'no-cache, must-revalidate'
printf 'Nginx syntax and 16 HTTP contract cases passed.\n'
CONTAINER
