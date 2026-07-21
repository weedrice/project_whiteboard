#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/conf.d" "$fixture/snippets" "$fixture/letsencrypt/live/noviis.kr"
cp "$project_root/deploy/nginx/noviis.conf" "$fixture/conf.d/default.conf"
cp "$project_root/deploy/nginx/security-headers.conf" "$fixture/snippets/noviis-security-headers.conf"
sed -i 's#ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;##' "$fixture/conf.d/default.conf"
touch "$fixture/letsencrypt/options-ssl-nginx.conf"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=noviis.invalid' \
  -keyout "$fixture/letsencrypt/live/noviis.kr/privkey.pem" \
  -out "$fixture/letsencrypt/live/noviis.kr/fullchain.pem" >/dev/null 2>&1
chmod 0644 "$fixture/letsencrypt/live/noviis.kr/privkey.pem"

docker run --rm --network none --read-only --cap-drop ALL --cap-add CHOWN \
  --tmpfs /var/cache/nginx --tmpfs /var/run --tmpfs /tmp \
  -v "$fixture/conf.d/default.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v "$fixture/snippets:/etc/nginx/snippets:ro" \
  -v "$fixture/letsencrypt:/etc/letsencrypt:ro" \
  nginx:1.27.4-alpine@sha256:4ff102c5d78d254a6f0da062b3cf39eaf07f01eec0927fd21e219d0af8bc0591 \
  nginx -t
