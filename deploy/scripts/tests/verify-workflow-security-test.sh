#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
validator="$project_root/deploy/scripts/tests/verify-workflow-security.mjs"
node "$validator" "$project_root"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/.github/workflows"
cp "$project_root/.github/workflows/ci.yml" "$fixture/.github/workflows/ci.yml"
cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
cp "$project_root/.github/workflows/deploy-frontend.yml" "$fixture/.github/workflows/deploy-frontend.yml"
cp "$project_root/.github/workflows/seo-monitor.yml" "$fixture/.github/workflows/seo-monitor.yml"

sed -i '0,/queue: max/s//queue: latest/' "$fixture/.github/workflows/deploy-backend.yml"
if node "$validator" "$fixture"; then
  echo "Expected an invalid deployment queue policy to fail" >&2
  exit 1
fi

cp "$project_root/.github/workflows/deploy-backend.yml" "$fixture/.github/workflows/deploy-backend.yml"
sed -i '/^  deploy:$/,/^    steps:$/s/^      attestations: read$/      attestations: read\n      id-token: write/' "$fixture/.github/workflows/deploy-backend.yml"
if node "$validator" "$fixture"; then
  echo "Expected activation OIDC permission to fail" >&2
  exit 1
fi

echo "Workflow AST negative fixtures passed"
