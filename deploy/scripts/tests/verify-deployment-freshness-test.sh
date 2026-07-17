#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
verifier="$project_root/deploy/scripts/verify-deployment-freshness.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

git -C "$fixture" init -q
git -C "$fixture" config user.email test@example.com
git -C "$fixture" config user.name test
mkdir -p "$fixture/docs" "$fixture/frontend/src" "$fixture/backend/src" "$fixture/deploy/scripts"
cp "$project_root/deploy/release-freshness-paths.txt" "$fixture/deploy/release-freshness-paths.txt"
printf 'base\n' > "$fixture/README.md"
git -C "$fixture" add .
git -C "$fixture" commit -qm base
base="$(git -C "$fixture" rev-parse HEAD)"

printf 'docs\n' > "$fixture/docs/note.md"
git -C "$fixture" add .
git -C "$fixture" commit -qm docs
docs="$(git -C "$fixture" rev-parse HEAD)"
(cd "$fixture" && "$verifier" "$base" "$docs" backend)
(cd "$fixture" && "$verifier" "$base" "$docs" frontend)

printf 'frontend\n' > "$fixture/frontend/src/app.ts"
git -C "$fixture" add .
git -C "$fixture" commit -qm frontend
frontend="$(git -C "$fixture" rev-parse HEAD)"
if (cd "$fixture" && "$verifier" "$docs" "$frontend" frontend); then
  echo "Frontend changes must reject an older frontend release" >&2
  exit 1
fi
(cd "$fixture" && "$verifier" "$docs" "$frontend" backend)

printf 'backend\n' > "$fixture/backend/src/App.java"
git -C "$fixture" add .
git -C "$fixture" commit -qm backend
backend="$(git -C "$fixture" rev-parse HEAD)"
if (cd "$fixture" && "$verifier" "$frontend" "$backend" backend); then
  echo "Backend changes must reject an older backend release" >&2
  exit 1
fi
(cd "$fixture" && "$verifier" "$frontend" "$backend" frontend)

printf 'common\n' > "$fixture/deploy/scripts/verify-release-provenance.sh"
git -C "$fixture" add .
git -C "$fixture" commit -qm common
common="$(git -C "$fixture" rev-parse HEAD)"
for component in backend frontend; do
  if (cd "$fixture" && "$verifier" "$backend" "$common" "$component"); then
    echo "Common release verification changes must reject an older $component release" >&2
    exit 1
  fi
done

if (cd "$fixture" && "$verifier" "$common" "$base" backend); then
  echo "A non-ancestor latest commit must fail closed" >&2
  exit 1
fi

if (cd "$fixture" && NOVIIS_FRESHNESS_MANIFEST=missing.txt "$verifier" "$base" "$docs" backend); then
  echo "A missing canonical manifest must fail closed" >&2
  exit 1
fi

echo "Deployment freshness fixtures passed"
