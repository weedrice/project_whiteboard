#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudoers matrix fixture must run as root" >&2
  exit 1
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
fixture="$(mktemp -d)"
installed_fixture="/etc/sudoers.d/noviis-ci-matrix"
created_command_stubs=()
cleanup() {
  rm -f "$installed_fixture"
  for command_stub in "${created_command_stubs[@]}"; do
    rm -f -- "$command_stub"
  done
  rm -rf "$fixture"
}
trap cleanup EXIT
test_user="nobody"

# Recent sudo versions require each command path to exist even for list-only checks.
for command_path in \
  /usr/local/sbin/activate-noviis-backend \
  /usr/local/sbin/verify-noviis-backend \
  /usr/local/sbin/activate-noviis-frontend \
  /usr/local/sbin/verify-noviis-frontend \
  /usr/local/sbin/record-noviis-cleanup-debt; do
  if [ ! -e "$command_path" ] && [ ! -L "$command_path" ]; then
    ln -s /bin/true "$command_path"
    created_command_stubs+=("$command_path")
  fi
done

sed "s/^noviis-deploy /$test_user /" "$project_root/deploy/sudoers/noviis-deploy" > "$fixture/noviis-deploy"
chmod 0440 "$fixture/noviis-deploy"
visudo -cf "$fixture/noviis-deploy"
install -o root -g root -m 0440 "$fixture/noviis-deploy" "$installed_fixture"
visudo -cf "$installed_fixture"

allowed=(
  "/usr/local/sbin/activate-noviis-backend /opt/app/backend/incoming/release-1 0123456789abcdef0123456789abcdef01234567 100 1 20"
  "/usr/local/sbin/verify-noviis-backend 0123456789abcdef"
  "/usr/local/sbin/verify-noviis-backend --contract 2026-07-public-v1"
  "/usr/local/sbin/activate-noviis-frontend /var/www/incoming/frontend/release-1 activate 0123456789abcdef0123456789abcdef01234567 100 1 20"
  "/usr/local/sbin/verify-noviis-frontend 0123456789abcdef0123456789abcdef01234567 100 20 1"
  "/usr/local/sbin/activate-noviis-frontend /var/www/releases/frontend/release-1 rollback 0123456789abcdef"
  "/usr/local/sbin/record-noviis-cleanup-debt backend reconcile incoming_release"
  "/usr/local/sbin/record-noviis-cleanup-debt frontend reconcile incoming_release"
)
denied=(
  "/bin/sh"
  "/usr/local/sbin/activate-noviis-backend /tmp/release 0123456789abcdef"
  "/usr/local/sbin/verify-noviis-backend not-a-sha"
  "/usr/local/sbin/activate-noviis-backend /opt/app/backend/incoming/release-1 0123456789abcdef0123456789abcdef01234567 100 1"
  "/usr/local/sbin/record-noviis-cleanup-debt backend set release_retention"
  "/usr/local/sbin/record-noviis-cleanup-debt backend clear incoming_release"
  "/usr/local/sbin/record-noviis-cleanup-debt frontend set incoming_release"
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
