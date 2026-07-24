#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
validator="$project_root/deploy/scripts/tests/verify-workflow-security.mjs"
node "$validator" "$project_root"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/.github/workflows" "$fixture/deploy"
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"
cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
cp "$project_root/.github/workflows/deploy-frontend.yml" "$fixture/.github/workflows/deploy-frontend.yml"
cp "$project_root/.github/workflows/seo-monitor.yml" "$fixture/.github/workflows/seo-monitor.yml"
cp "$project_root/deploy/release-freshness-paths.txt" "$fixture/deploy/release-freshness-paths.txt"

sed -i '0,/queue: single/s//queue: max/' "$fixture/.github/workflows/deploy-backend.yml"
if node "$validator" "$fixture"; then
  echo "Expected an invalid deployment queue policy to fail" >&2
  exit 1
fi

cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
sed -i '/deploy\/scripts\/verify-release-provenance.sh/d' "$fixture/deploy/release-freshness-paths.txt"
if node "$validator" "$fixture"; then
  echo "Expected a missing common freshness boundary to fail" >&2
  exit 1
fi
cp "$project_root/deploy/release-freshness-paths.txt" "$fixture/deploy/release-freshness-paths.txt"

sed -i "/docs\/ops\/api-contract-revision.txt/d" "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected an API contract revision path outside the full change matrix to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i '/^  candidate-frontend:$/,/^  release-frontend:$/s/!cancelled() &&/true \&\&/' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected a post-gate job without an explicit status function to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i 's/inputs\.allow_contract_migration/true/g' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected a contract deployment without explicit manual approval to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i '/^  backend-test:$/,/^  frontend-test:$/s/^    steps:$/    permissions:\n      id-token: write\n    steps:/' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected an unapproved CI OIDC job to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i '/environment: production-seo/d' "$fixture/.github/workflows/seo-monitor.yml"
if node "$validator" "$fixture"; then
  echo "Expected scheduled SEO submission without its environment to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/seo-monitor.yml" "$fixture/.github/workflows/seo-monitor.yml"

sed -i '/SEO_VERIFY_ROTATION_SEED:/d' "$fixture/.github/workflows/seo-monitor.yml"
if node "$validator" "$fixture"; then
  echo "Expected scheduled SEO verification without a rotation seed to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/seo-monitor.yml" "$fixture/.github/workflows/seo-monitor.yml"

sed -i '0,/runs-on: ubuntu-24.04/s//runs-on: ubuntu-latest/' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected an unpinned Ubuntu runner image to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i '/ref: \${{ github.sha }}/d' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected a release repository script checkout without an exact ref to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

sed -i '/sparse-checkout: deploy\/scripts\/download-attestation-with-retry.sh/d' "$fixture/.github/workflows/ci.yml"
if node "$validator" "$fixture"; then
  echo "Expected a release repository script checkout without the helper path to fail" >&2
  exit 1
fi
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"

cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
sed -i '/^  deploy:$/,/^    steps:$/s/^      attestations: read$/      attestations: read\n      id-token: write/' "$fixture/.github/workflows/deploy-backend.yml"
if node "$validator" "$fixture"; then
  echo "Expected activation OIDC permission to fail" >&2
  exit 1
fi

echo "Workflow AST negative fixtures passed"
