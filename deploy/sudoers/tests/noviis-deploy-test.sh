#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudoers matrix fixture must run as root" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
fixture="$(mktemp -d)"
installed_fixture="/etc/sudoers.d/noviis-ci-matrix"
trap 'rm -f "$installed_fixture"; rm -rf "$fixture"' EXIT
test_user="nobody"
sed "s/^noviis-deploy /$test_user /" "$project_root/deploy/sudoers/noviis-deploy" > "$fixture/noviis-deploy"
chmod 0440 "$fixture/noviis-deploy"
visudo -cf "$fixture/noviis-deploy"
install -o root -g root -m 0440 "$fixture/noviis-deploy" "$installed_fixture"
visudo -cf "$installed_fixture"

allowed=(
  "/usr/local/sbin/activate-noviis-backend /opt/app/backend/incoming/release-1 0123456789abcdef"
  "/usr/local/sbin/activate-noviis-frontend /var/www/incoming/frontend/release-1 activate 0123456789abcdef"
  "/usr/local/sbin/activate-noviis-frontend /var/www/releases/frontend/release-1 rollback 0123456789abcdef"
)
denied=(
  "/bin/sh"
  "/usr/local/sbin/activate-noviis-backend /tmp/release 0123456789abcdef"
  "/usr/local/sbin/activate-noviis-frontend /var/www/incoming/frontend/release-1 rollback 0123456789abcdef"
  "/usr/local/sbin/activate-noviis-frontend /var/www/releases/frontend/release-1 activate 0123456789abcdef"
)

for command in "${allowed[@]}"; do
  read -r -a arguments <<< "$command"
  sudo -n -l -U "$test_user" -- "${arguments[@]}" >/dev/null
done
for command in "${denied[@]}"; do
  read -r -a arguments <<< "$command"
  if sudo -n -l -U "$test_user" -- "${arguments[@]}" >/dev/null 2>&1; then
    echo "unexpected sudo permission: $command" >&2
    exit 1
  fi
done

echo "sudoers allow/deny matrix passed"
