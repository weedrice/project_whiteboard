#!/usr/bin/env bash
set -Eeuo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "Run this root-owned helper through sudo" >&2
  exit 1
fi

CURL_BIN="${CURL_BIN:-curl}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
MV_BIN="${MV_BIN:-mv}"
GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
GRAFANA_PASSWORD_API_URL="${GRAFANA_PASSWORD_API_URL:-http://127.0.0.1:3000/api/user/password}"
GRAFANA_ENV_FILE="${GRAFANA_ENV_FILE:-/etc/noviis/monitoring.env}"

fail() {
  echo "Grafana password rotation failed: $*" >&2
  exit 1
}

[ -f "$GRAFANA_ENV_FILE" ] || fail "monitoring environment file must be a regular file"
[ ! -L "$GRAFANA_ENV_FILE" ] || fail "monitoring environment file must not be a symlink"
[ "$(stat -c %U:%G "$GRAFANA_ENV_FILE")" = root:root ] \
  || fail "monitoring environment file must be owned by root:root"
[ "$(stat -c %a "$GRAFANA_ENV_FILE")" = 600 ] \
  || fail "monitoring environment file must have mode 0600"

work_dir="$(mktemp -d)"
chmod 0700 "$work_dir"
next_env_file=""
trap 'rm -rf -- "$work_dir"; if [ -n "$next_env_file" ]; then rm -f -- "$next_env_file"; fi' EXIT INT TERM HUP
old_file="$work_dir/old-password"
new_file="$work_dir/new-password"
payload_file="$work_dir/payload.json"
curl_config="$work_dir/curl.conf"
rollback_payload_file="$work_dir/rollback-payload.json"
rollback_curl_config="$work_dir/rollback-curl.conf"
next_env_file="$(mktemp "${GRAFANA_ENV_FILE}.next.XXXXXX")"
chmod 0600 "$next_env_file"
chown root:root "$next_env_file"

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
  "$rollback_payload_file" "$rollback_curl_config" "$GRAFANA_ADMIN_USER" \
  "$GRAFANA_PASSWORD_API_URL" "$GRAFANA_ENV_FILE" "$next_env_file" <<'PY'
from pathlib import Path
import json
import shlex
import sys

(
    old_path,
    new_path,
    payload_path,
    config_path,
    rollback_payload_path,
    rollback_config_path,
    username,
    url,
    env_path,
    next_env_path,
) = sys.argv[1:]
old_password = Path(old_path).read_text(encoding="utf-8")
new_password = Path(new_path).read_text(encoding="utf-8")

def write_request(request_payload_path, request_config_path, current_password, requested_password):
    Path(request_payload_path).write_text(json.dumps({
        "oldPassword": current_password,
        "newPassword": requested_password,
        "confirmNew": requested_password,
    }), encoding="utf-8")
    config = "\n".join([
        "silent",
        "show-error",
        "fail-with-body",
        'request = "PUT"',
        f"url = {json.dumps(url)}",
        f"user = {json.dumps(username + ':' + current_password)}",
        'header = "Content-Type: application/json"',
        f"data-binary = {json.dumps('@' + request_payload_path)}",
        "",
    ])
    Path(request_config_path).write_text(config, encoding="utf-8")

env_lines = Path(env_path).read_text(encoding="utf-8").splitlines()
password_indexes = []
configured_password = None
for index, line in enumerate(env_lines):
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        continue
    key, raw_value = stripped.split("=", 1)
    if key.strip() != "GF_SECURITY_ADMIN_PASSWORD":
        continue
    parsed = shlex.split(raw_value, comments=False, posix=True)
    if len(parsed) != 1:
        raise SystemExit("GF_SECURITY_ADMIN_PASSWORD must contain exactly one quoted or unquoted value")
    password_indexes.append(index)
    configured_password = parsed[0]

if len(password_indexes) != 1:
    raise SystemExit("monitoring environment must contain exactly one GF_SECURITY_ADMIN_PASSWORD entry")
if configured_password != old_password:
    raise SystemExit("current password does not match the monitoring environment source of truth")

quoted_new_password = '"' + new_password.replace('\\', '\\\\').replace('"', '\\"') + '"'
env_lines[password_indexes[0]] = f"GF_SECURITY_ADMIN_PASSWORD={quoted_new_password}"
Path(next_env_path).write_text("\n".join(env_lines) + "\n", encoding="utf-8")

write_request(payload_path, config_path, old_password, new_password)
write_request(rollback_payload_path, rollback_config_path, new_password, old_password)
PY
chmod 0600 "$old_file" "$new_file" "$payload_file" "$curl_config" \
  "$rollback_payload_file" "$rollback_curl_config" "$next_env_file"
chown root:root "$next_env_file"

"$CURL_BIN" --config "$curl_config" >/dev/null
if "$MV_BIN" -Tf "$next_env_file" "$GRAFANA_ENV_FILE"; then
  next_env_file=""
  echo "Grafana administrator password and monitoring environment source of truth rotated"
  exit 0
fi

echo "Monitoring environment update failed; attempting to revert the Grafana API password" >&2
if "$CURL_BIN" --config "$rollback_curl_config" >/dev/null; then
  fail "environment update failed and the Grafana API password was reverted"
fi

recovery_file="$(mktemp "${GRAFANA_ENV_FILE}.rotation-recovery.XXXXXX")"
install -o root -g root -m 0600 "$next_env_file" "$recovery_file"
rm -f -- "$next_env_file"
next_env_file=""
echo "Grafana password rollback also failed; current root-only recovery state: $recovery_file" >&2
exit 2
