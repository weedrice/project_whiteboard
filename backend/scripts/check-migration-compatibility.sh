#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-HEAD^}"
head_ref="${2:-HEAD}"
migration_dir="backend/src/main/resources/db/migration"
contract_allowlist="docs/ops/applied-contract-migrations.txt"
contract_marker='-- noviis:migration-phase contract'
contract_migration=false

if [[ "$base_ref" =~ ^0+$ ]] || ! git cat-file -e "$base_ref^{commit}" 2>/dev/null; then
  echo "Migration compatibility check cannot prove the base commit: $base_ref" >&2
  exit 1
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

validate_contract_metadata() {
  local file="$1"
  local design_doc

  design_doc="$(sed -n 's/^-- noviis:design-doc //p' "$file" | head -n 1)"
  case "$design_doc" in
    docs/design-notes/*.md) ;;
    *)
      echo "Contract migration design document must be a tracked Markdown file under docs/design-notes: $file" >&2
      exit 1
      ;;
  esac
  if ! git ls-files --error-unmatch "$design_doc" >/dev/null 2>&1; then
    echo "Contract migration design document is not tracked: $design_doc" >&2
    exit 1
  fi
}

for change in "${changes[@]}"; do
  file="${change#*$'\t'}"
  status="${change%%$'\t'*}"
  if [ "$status" = A ]; then
    phase_count="$(grep -Ec '^-- noviis:migration-phase (expand|backfill|contract)$' "$file" || true)"
    if [ "$phase_count" -ne 1 ]; then
      echo "New migration requires exactly one expand, backfill, or contract phase marker: $file" >&2
      exit 1
    fi
  fi
  risky=false
  if grep -Eiq '(^|[[:space:];])(DROP[[:space:]]+(TABLE|COLUMN)|TRUNCATE([[:space:]]+TABLE)?|ALTER[[:space:]]+TABLE[^;]*(RENAME|DROP[[:space:]]+(CONSTRAINT|DEFAULT)|ALTER[[:space:]]+COLUMN[^;]*(TYPE|DROP[[:space:]]+DEFAULT)))' "$file"; then
    risky=true
  fi
  if grep -Eiq 'SET[[:space:]]+NOT[[:space:]]+NULL' "$file" && ! grep -Eiq '(^|[[:space:];])UPDATE[[:space:]]+' "$file"; then
    risky=true
  fi
  if awk 'BEGIN { IGNORECASE=1; RS=";" } /DELETE[[:space:]]+FROM/ && !/WHERE[[:space:]]/' "$file" | grep -q .; then
    risky=true
  fi

  if [ "$risky" = true ] && ! grep -Fqx -- "$contract_marker" "$file"; then
      echo "Risky migration requires an explicit contract phase marker: $file" >&2
      exit 1
  fi

  if grep -Fqx -- "$contract_marker" "$file"; then
    validate_contract_metadata "$file"
    contract_migration=true
  fi
done

if [ ! -f "$contract_allowlist" ]; then
  echo "Contract migration allowlist is missing: $contract_allowlist" >&2
  exit 1
fi

while read -r migration_name deployment_run extra; do
  [ -n "$migration_name" ] || continue
  [[ "$migration_name" = \#* ]] && continue
  case "$migration_name" in V*.sql) ;; *) echo "Invalid contract migration filename in allowlist: $migration_name" >&2; exit 1 ;; esac
  case "$deployment_run" in https://github.com/weedrice/project_whiteboard/actions/runs/[0-9]*) ;; *) echo "Contract allowlist entry requires this repository's GitHub deployment run URL: $migration_name" >&2; exit 1 ;; esac
  if [ -n "${extra:-}" ]; then
    echo "Unexpected extra fields in contract allowlist entry: $migration_name" >&2
    exit 1
  fi
done < "$contract_allowlist"

while IFS= read -r contract_file; do
  [ -n "$contract_file" ] || continue
  validate_contract_metadata "$contract_file"
  migration_name="${contract_file#"$migration_dir/"}"
  if ! awk 'NF && $1 !~ /^#/ { print $1 }' "$contract_allowlist" | grep -Fqx -- "$migration_name"; then
    contract_migration=true
  fi
done < <(grep -Fl -- "$contract_marker" "$migration_dir"/V*.sql 2>/dev/null || true)

for change in "${changes[@]}"; do
  status="${change%%$'\t'*}"
  file="${change#*$'\t'}"
  if [ "$status" = A ] && grep -Fqx -- "$contract_marker" "$file"; then
    migration_name="${file#"$migration_dir/"}"
    if awk 'NF && $1 !~ /^#/ { print $1 }' "$contract_allowlist" | grep -Fqx -- "$migration_name"; then
      echo "A contract migration cannot be marked applied in the same change that introduces it: $file" >&2
      exit 1
    fi
  fi
done

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "contract_migration=$contract_migration" >> "$GITHUB_OUTPUT"
fi

echo "Migration compatibility check passed"
