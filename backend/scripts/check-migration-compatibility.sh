#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-HEAD^}"
head_ref="${2:-HEAD}"
migration_dir="backend/src/main/resources/db/migration"

if [[ "$base_ref" =~ ^0+$ ]] || ! git cat-file -e "$base_ref^{commit}" 2>/dev/null; then
  echo "Migration compatibility check skipped because the base commit is unavailable: $base_ref"
  exit 0
fi

mapfile -t changes < <(git diff --name-status --find-renames "$base_ref" "$head_ref" -- "$migration_dir/V*.sql")
for change in "${changes[@]}"; do
  status="${change%%$'\t'*}"
  case "$status" in
    A) ;;
    *)
      echo "Existing versioned migrations are immutable: $change" >&2
      exit 1
      ;;
  esac
done

for change in "${changes[@]}"; do
  file="${change#*$'\t'}"
  if grep -Eiq \
    '(^|[[:space:];])(DROP[[:space:]]+(TABLE|COLUMN)|ALTER[[:space:]]+TABLE[^;]*(RENAME|ALTER[[:space:]]+COLUMN[^;]*TYPE)|SET[[:space:]]+NOT[[:space:]]+NULL)' \
    "$file"; then
    if ! grep -Fqx -- '-- noviis:migration-phase contract' "$file"; then
      echo "Risky migration requires an explicit contract phase marker: $file" >&2
      exit 1
    fi
    design_doc="$(sed -n 's/^-- noviis:design-doc //p' "$file" | head -n 1)"
    if [ -z "$design_doc" ] || [ ! -f "$design_doc" ]; then
      echo "Contract migration requires an existing design document: $file" >&2
      exit 1
    fi
  fi
done

echo "Migration compatibility check passed"
