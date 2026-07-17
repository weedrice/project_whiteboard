#!/usr/bin/env bash
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "Run this root-owned helper through sudo" >&2
  exit 1
fi

CURL_BIN="${CURL_BIN:-curl}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
GRAFANA_PASSWORD_API_URL="${GRAFANA_PASSWORD_API_URL:-http://127.0.0.1:3000/api/user/password}"

work_dir="$(mktemp -d)"
chmod 0700 "$work_dir"
trap 'rm -rf -- "$work_dir"' EXIT INT TERM HUP
old_file="$work_dir/old-password"
new_file="$work_dir/new-password"
payload_file="$work_dir/payload.json"
curl_config="$work_dir/curl.conf"

read -r -s -p 'Current Grafana administrator password: ' old_password
printf '\n' >&2
read -r -s -p 'New Grafana administrator password: ' new_password
printf '\n' >&2
read -r -s -p 'Confirm new Grafana administrator password: ' confirmed_password
printf '\n' >&2

if [ "${#new_password}" -lt 16 ]; then
  echo "Grafana administrator password must contain at least 16 characters" >&2
  exit 1
fi
if [ "$new_password" != "$confirmed_password" ]; then
  echo "New Grafana administrator passwords do not match" >&2
  exit 1
fi

umask 077
printf '%s' "$old_password" > "$old_file"
printf '%s' "$new_password" > "$new_file"
unset old_password new_password confirmed_password

"$PYTHON_BIN" - "$old_file" "$new_file" "$payload_file" "$curl_config" \
  "$GRAFANA_ADMIN_USER" "$GRAFANA_PASSWORD_API_URL" <<'PY'
from pathlib import Path
import json
import sys

old_path, new_path, payload_path, config_path, username, url = sys.argv[1:]
old_password = Path(old_path).read_text(encoding="utf-8")
new_password = Path(new_path).read_text(encoding="utf-8")
Path(payload_path).write_text(json.dumps({
    "oldPassword": old_password,
    "newPassword": new_password,
    "confirmNew": new_password,
}), encoding="utf-8")
config = "\n".join([
    "silent",
    "show-error",
    "fail-with-body",
    'request = "PUT"',
    f"url = {json.dumps(url)}",
    f"user = {json.dumps(username + ':' + old_password)}",
    'header = "Content-Type: application/json"',
    f"data-binary = {json.dumps('@' + payload_path)}",
    "",
])
Path(config_path).write_text(config, encoding="utf-8")
PY
chmod 0600 "$old_file" "$new_file" "$payload_file" "$curl_config"

"$CURL_BIN" --config "$curl_config" >/dev/null
echo "Grafana administrator password rotated"
