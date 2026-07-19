#!/usr/bin/env bash
set -euo pipefail

expected_sha="${1:-}"
latest_sha="${2:-}"
component="${3:-}"
manifest="${NOVIIS_FRESHNESS_MANIFEST:-deploy/release-freshness-paths.txt}"

case "$component" in backend|frontend) ;; *) echo "component must be backend or frontend" >&2; exit 64 ;; esac
for commit in "$expected_sha" "$latest_sha"; do
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo "deployment freshness requires 40-character commit SHAs" >&2; exit 64; }
  git cat-file -e "$commit^{commit}" 2>/dev/null || { echo "deployment freshness cannot prove commit: $commit" >&2; exit 1; }
done

[ -f "$manifest" ] || { echo "deployment freshness manifest is missing: $manifest" >&2; exit 1; }
manifest_entries=()
while read -r scope pattern extra; do
  [ -n "${scope:-}" ] || continue
  [[ "$scope" = \#* ]] && continue
  case "$scope" in common|backend|frontend) ;; *) echo "invalid freshness manifest scope: $scope" >&2; exit 1 ;; esac
  if [ -z "${pattern:-}" ] || [ -n "${extra:-}" ]; then
    echo "invalid freshness manifest entry: $scope ${pattern:-}" >&2
    exit 1
  fi
  manifest_entries+=("$scope|$pattern")
done < "$manifest"
[ "${#manifest_entries[@]}" -gt 0 ] || { echo "deployment freshness manifest is empty" >&2; exit 1; }

if [ "$expected_sha" = "$latest_sha" ]; then
  echo "Deployment target is the latest main commit: $expected_sha"
  exit 0
fi

git merge-base --is-ancestor "$expected_sha" "$latest_sha" || {
  echo "Deployment target is not an ancestor of latest main" >&2
  exit 1
}

is_component_path() {
  local path="$1"
  local entry scope pattern
  for entry in "${manifest_entries[@]}"; do
    scope="${entry%%|*}"
    pattern="${entry#*|}"
    # Manifest entries intentionally use Bash glob matching.
    # shellcheck disable=SC2053
    if { [ "$scope" = common ] || [ "$scope" = "$component" ]; } && [[ "$path" == $pattern ]]; then
      return 0
    fi
  done
  return 1
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
