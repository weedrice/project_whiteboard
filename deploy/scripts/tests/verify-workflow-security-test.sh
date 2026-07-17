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

sed -i '0,/queue: max/s//queue: latest/' "$fixture/.github/workflows/deploy-backend.yml"
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

cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
sed -i '/^  deploy:$/,/^    steps:$/s/^      attestations: read$/      attestations: read\n      id-token: write/' "$fixture/.github/workflows/deploy-backend.yml"
if node "$validator" "$fixture"; then
  echo "Expected activation OIDC permission to fail" >&2
  exit 1
fi

echo "Workflow AST negative fixtures passed"
