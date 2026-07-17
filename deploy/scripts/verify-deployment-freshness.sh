#!/usr/bin/env bash
set -euo pipefail

expected_sha="${1:-}"
latest_sha="${2:-}"
component="${3:-}"

case "$component" in backend|frontend) ;; *) echo "component must be backend or frontend" >&2; exit 64 ;; esac
for commit in "$expected_sha" "$latest_sha"; do
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo "deployment freshness requires 40-character commit SHAs" >&2; exit 64; }
  git cat-file -e "$commit^{commit}" 2>/dev/null || { echo "deployment freshness cannot prove commit: $commit" >&2; exit 1; }
done

if [ "$expected_sha" = "$latest_sha" ]; then
  echo "Deployment target is the latest main commit: $expected_sha"
  exit 0
fi

git merge-base --is-ancestor "$expected_sha" "$latest_sha" || {
  echo "Deployment target is not an ancestor of latest main" >&2
  exit 1
}

is_common_path() {
  case "$1" in
    .github/workflows/ci.yml|deploy/scripts/verify-release-provenance.sh|deploy/scripts/record-cleanup-debt.sh|deploy/sudoers/noviis-deploy) return 0 ;;
    *) return 1 ;;
  esac
}

is_component_path() {
  local path="$1"
  if is_common_path "$path"; then
    return 0
  fi
  case "$component:$path" in
    backend:backend/*|backend:.github/workflows/deploy-backend.yml|backend:deploy/scripts/activate-backend-release.sh|backend:deploy/scripts/verify-backend-start-state.sh|backend:deploy/systemd/*) return 0 ;;
    frontend:frontend/*|frontend:.github/workflows/deploy-frontend.yml|frontend:.github/workflows/seo-monitor.yml|frontend:deploy/scripts/activate-frontend-release.sh|frontend:deploy/scripts/verify-active-frontend-release.sh|frontend:backend/API명세서.md|frontend:backend/기능명세서.md) return 0 ;;
    *) return 1 ;;
  esac
}

relevant_paths=()
while IFS= read -r path; do
  [ -n "$path" ] || continue
  if is_component_path "$path"; then
    relevant_paths+=("$path")
  fi
done < <(git diff --name-only "$expected_sha" "$latest_sha")

if [ "${#relevant_paths[@]}" -gt 0 ]; then
  echo "Deployment target is stale for $component; newer main changed relevant paths:" >&2
  printf '  %s\n' "${relevant_paths[@]}" >&2
  exit 1
fi

echo "Deployment target remains current for $component; newer main changes are unrelated"
