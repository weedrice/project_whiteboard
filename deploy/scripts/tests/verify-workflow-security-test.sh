#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ci="$project_root/.github/workflows/ci.yml"
seo="$project_root/.github/workflows/seo-monitor.yml"

job_section() {
  local name="$1"
  local file="$2"
  awk -v job="  ${name}:" '
    $0 == job { capture=1; print; next }
    capture && /^  [A-Za-z0-9_-]+:/ { exit }
    capture { print }
  ' "$file"
}

for untrusted_job in backend-postgres-migration ops-config-test; do
  section="$(job_section "$untrusted_job" "$ci")"
  grep -Fq 'contents: read' <<< "$section"
  if grep -Eq 'actions: read|deployments: read|GH_TOKEN:' <<< "$section"; then
    echo "$untrusted_job must not receive repository evidence permissions" >&2
    exit 1
  fi
done

trusted="$(job_section trusted-contract-evidence "$ci")"
grep -Fq "github.ref == 'refs/heads/main'" <<< "$trusted"
grep -Fq 'actions: read' <<< "$trusted"
grep -Fq 'deployments: read' <<< "$trusted"
grep -Fq 'VERIFY_CONTRACT_RUNS: "true"' <<< "$trusted"

preflight="$(job_section seo-preflight "$seo")"
grep -Fq 'EVENT_NAME: ${{ github.event_name }}' <<< "$preflight"
grep -Fq 'GIT_REF: ${{ github.ref }}' <<< "$preflight"
grep -Fq 'refs/heads/main' <<< "$preflight"
verify="$(job_section verify-endpoints "$seo")"
grep -Fq 'needs: seo-preflight' <<< "$verify"
grep -Fq "if: needs.seo-preflight.result == 'success'" <<< "$verify"

echo "Workflow permission and SEO preflight contracts passed"
