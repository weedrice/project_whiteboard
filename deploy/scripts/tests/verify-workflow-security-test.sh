#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ci="$project_root/.github/workflows/ci.yml"
seo="$project_root/.github/workflows/seo-monitor.yml"
deploy_backend="$project_root/.github/workflows/deploy-backend.yml"
deploy_frontend="$project_root/.github/workflows/deploy-frontend.yml"

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

for candidate_job in candidate-backend candidate-frontend; do
  section="$(job_section "$candidate_job" "$ci")"
  grep -Fq 'contents: read' <<< "$section"
  if grep -Eq 'id-token: write|attestations: write' <<< "$section"; then
    echo "$candidate_job must not receive signing credentials" >&2
    exit 1
  fi
done
for release_job in release-backend release-frontend; do
  section="$(job_section "$release_job" "$ci")"
  grep -Fq 'id-token: write' <<< "$section"
  grep -Fq 'attestations: write' <<< "$section"
done

trusted="$(job_section trusted-contract-evidence "$ci")"
grep -Fq "github.ref == 'refs/heads/main'" <<< "$trusted"
grep -Fq 'actions: read' <<< "$trusted"
grep -Fq 'deployments: read' <<< "$trusted"
grep -Fq 'VERIFY_CONTRACT_RUNS: "true"' <<< "$trusted"

contract_deploy_evidence="$(job_section contract-evidence "$deploy_backend")"
grep -Fq 'id-token: write' <<< "$contract_deploy_evidence"
backend_deploy="$(job_section deploy "$deploy_backend")"
if grep -Fq 'id-token: write' <<< "$backend_deploy"; then
  echo "Backend activation job must not receive OIDC token permission" >&2
  exit 1
fi
grep -Fq 'Reconcile backend activation from independent readback' <<< "$backend_deploy"
grep -Fq 'verify-noviis-backend' <<< "$backend_deploy"
frontend_deploy="$(job_section deploy "$deploy_frontend")"
grep -Fq 'Reconcile frontend activation from independent readback' <<< "$frontend_deploy"
grep -Fq 'verify-noviis-frontend' <<< "$frontend_deploy"
grep -Fq 'ACTIVATION_STDOUT' <<< "$frontend_deploy"
if grep -Fq 'ACTIVATOR_STDOUT" | sed -n '\''s/^ACTIVATED_RUN_' <<< "$frontend_deploy"; then
  echo "Frontend run identity must come from independent reconciliation, not activator transport output" >&2
  exit 1
fi
grep -Fq "steps.activation-result.outputs.current_run_activated == 'true'" <<< "$frontend_deploy"

preflight="$(job_section seo-preflight "$seo")"
grep -Fq 'EVENT_NAME: ${{ github.event_name }}' <<< "$preflight"
grep -Fq 'GIT_REF: ${{ github.ref }}' <<< "$preflight"
grep -Fq 'refs/heads/main' <<< "$preflight"
verify="$(job_section verify-endpoints "$seo")"
grep -Fq 'needs: seo-preflight' <<< "$verify"
grep -Fq "if: needs.seo-preflight.result == 'success'" <<< "$verify"

echo "Workflow permission and SEO preflight contracts passed"
