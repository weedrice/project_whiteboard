#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
writer="$project_root/deploy/scripts/record-cleanup-debt.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
incoming="$fixture/incoming"
textfiles="$fixture/textfiles"
mkdir -p "$incoming/old-release" "$incoming/new-release"
touch -d '2 hours ago' "$incoming/old-release"

run_reconcile() {
  TEXTFILE_DIR="$textfiles" \
    NOVIIS_CLEANUP_TEST_MODE=true \
    NOVIIS_CLEANUP_TEST_INCOMING_ROOT="$incoming" \
    "$writer" backend reconcile incoming_release
}

if run_reconcile; then
  echo "Orphans must keep incoming cleanup debt active" >&2
  exit 1
else
  [ "$?" -eq 2 ]
fi
metrics="$textfiles/noviis_deployment_cleanup_backend_incoming_release.prom"
grep -Fq 'noviis_deployment_cleanup_debt{component="backend",debt="incoming_release"} 1' "$metrics"
grep -Fq 'noviis_deployment_incoming_orphans{component="backend"} 2' "$metrics"
awk '/noviis_deployment_incoming_orphan_oldest_age_seconds/ && $1 !~ /^#/ { if ($2 < 7000) exit 1; found=1 } END { exit found ? 0 : 1 }' "$metrics"

rm -rf "$incoming/new-release"
if run_reconcile; then
  echo "A later successful cleanup must not hide an older orphan" >&2
  exit 1
else
  [ "$?" -eq 2 ]
fi
grep -Fq 'noviis_deployment_incoming_orphans{component="backend"} 1' "$metrics"

rm -rf "$incoming/old-release"
run_reconcile
grep -Fq 'noviis_deployment_cleanup_debt{component="backend",debt="incoming_release"} 0' "$metrics"
grep -Fq 'noviis_deployment_incoming_orphans{component="backend"} 0' "$metrics"

echo "Cleanup debt reconciliation fixtures passed"
