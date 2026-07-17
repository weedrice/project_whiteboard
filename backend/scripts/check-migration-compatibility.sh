#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-HEAD^}"
head_ref="${2:-HEAD}"
migration_dir="backend/src/main/resources/db/migration"
contract_allowlist="docs/ops/applied-contract-migrations.txt"
contract_marker='-- noviis:migration-phase contract'
contract_migration=false
repository="${CONTRACT_EVIDENCE_REPOSITORY:-weedrice/project_whiteboard}"
verify_contract_runs="${VERIFY_CONTRACT_RUNS:-false}"

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

verify_contract_deployment_run() {
  local run_id="$1"
  local deployment_sha="$2"
  local run_data
  local conclusion
  local branch
  local event
  local workflow_path
  local head_sha
  local backend_job
  local backend_job_id
  local backend_job_name
  local backend_job_conclusion
  local backend_job_url
  local deployment_ids
  local deployment_id
  local matching_status

  command -v gh >/dev/null 2>&1 || {
    echo "GitHub CLI is required to verify contract deployment evidence" >&2
    exit 1
  }
  run_data="$(gh api "repos/$repository/actions/runs/$run_id" \
    --jq '[.conclusion, .head_branch, .event, .path, .head_sha] | @tsv')" || {
      echo "Contract deployment run cannot be read: $run_id" >&2
      exit 1
    }
  IFS=$'\t' read -r conclusion branch event workflow_path head_sha <<< "$run_data"
  if [ "$conclusion" != success ] || [ "$branch" != main ] || [ "$event" != workflow_dispatch ] \
    || [ "$workflow_path" != .github/workflows/ci.yml ] || [ "$head_sha" != "$deployment_sha" ]; then
    echo "Contract deployment run does not match the required successful manual main deployment: $run_id" >&2
    exit 1
  fi

  backend_job="$(gh api --paginate "repos/$repository/actions/runs/$run_id/jobs?per_page=100" \
    --jq '.jobs[] | select((.name == "deploy-backend / deploy" or .name == "deploy-backend / Deploy Backend / deploy") and .conclusion == "success") | [.id, .name, .conclusion, .html_url] | @tsv')" || {
      echo "Backend deployment job cannot be read for run: $run_id" >&2
      exit 1
    }
  if [ "$(printf '%s\n' "$backend_job" | sed '/^$/d' | wc -l)" -ne 1 ]; then
    echo "Contract deployment must contain exactly one successful backend deployment job: $run_id" >&2
    exit 1
  fi
  IFS=$'\t' read -r backend_job_id backend_job_name backend_job_conclusion backend_job_url <<< "$backend_job"
  [[ "$backend_job_id" =~ ^[0-9]+$ ]] || {
    echo "Backend deployment job id is invalid for run: $run_id" >&2
    exit 1
  }
  [ "$backend_job_conclusion" = success ] || {
    echo "Backend deployment job is not successful for run: $run_id" >&2
    exit 1
  }
  case "$backend_job_name" in
    "deploy-backend / deploy"|"deploy-backend / Deploy Backend / deploy") ;;
    *)
      echo "Production deployment evidence belongs to a different job: $backend_job_name" >&2
      exit 1
      ;;
  esac
  case "$backend_job_url" in
    "https://github.com/$repository/actions/runs/$run_id/job/$backend_job_id"|\
    "https://github.com/$repository/runs/$run_id/jobs/$backend_job_id") ;;
    *)
      echo "Backend deployment job URL does not belong to the recorded run: $run_id" >&2
      exit 1
      ;;
  esac

  deployment_ids="$(gh api "repos/$repository/deployments?sha=$deployment_sha&environment=production&per_page=100" \
    --jq '.[] | select(.environment == "production") | .id')" || {
      echo "Production environment deployments cannot be read for run: $run_id" >&2
      exit 1
    }
  [ -n "$deployment_ids" ] || {
    echo "Contract deployment has no production environment record: $run_id" >&2
    exit 1
  }

  matching_status=false
  while read -r deployment_id; do
    [ -n "$deployment_id" ] || continue
    [[ "$deployment_id" =~ ^[0-9]+$ ]] || continue
    if gh api "repos/$repository/deployments/$deployment_id/statuses?per_page=100" \
      --jq ".[] | select(.state == \"success\" and .log_url == \"$backend_job_url\") | .id" \
      | grep -Eq '^[0-9]+$'; then
      matching_status=true
      break
    fi
  done <<< "$deployment_ids"
  [ "$matching_status" = true ] || {
    echo "No successful production deployment status is bound to backend job $backend_job_name in run $run_id" >&2
    exit 1
  }
}

strip_sql_comments() {
  local file="$1"
  awk '
    BEGIN { in_block=0; in_string=0; quote=sprintf("%c", 39) }
    {
      line=$0
      output=""
      for (character_index=1; character_index<=length(line); character_index++) {
        character=substr(line, character_index, 1)
        next_character=substr(line, character_index + 1, 1)
        if (in_block) {
          if (character == "*" && next_character == "/") {
            in_block=0
            character_index++
          }
          continue
        }
        if (in_string) {
          output=output character
          if (character == quote) {
            if (next_character == quote) {
              output=output next_character
              character_index++
            } else {
              in_string=0
            }
          }
          continue
        }
        if (character == quote) {
          in_string=1
          output=output character
        } else if (character == "-" && next_character == "-") {
          break
        } else if (character == "/" && next_character == "*") {
          in_block=1
          character_index++
        } else {
          output=output character
        }
      }
      print output
    }
    END { if (in_block || in_string) exit 2 }
  ' "$file"
}

has_bound_not_null_backfills() {
  local file="$1"
  if grep -Eq '\$[A-Za-z0-9_]*\$' "$file"; then
    return 1
  fi
  strip_sql_comments "$file" | awk '
    BEGIN { RS=";"; valid=1 }
    {
      statement=tolower($0)
      gsub(/[[:space:]]+/, " ", statement)
      sub(/^ /, "", statement)
      sub(/ $/, "", statement)
      if (statement == "") next
      if (statement ~ /set not null/) {
        if (statement !~ /^alter table [a-z0-9_.]+ alter column [a-z0-9_]+ set not null$/) {
          valid=0
        } else {
          target_statement=statement
          sub(/^alter table /, "", target_statement)
          split(target_statement, table_parts, " ")
          table_name=table_parts[1]
          target_statement=statement
          sub(/^alter table [a-z0-9_.]+ alter column /, "", target_statement)
          split(target_statement, column_parts, " ")
          column_name=column_parts[1]
          found=0
          for (statement_index=1; statement_index<NR; statement_index++) {
            update_statement=statements[statement_index]
            update_prefix="update " table_name " set "
            where_position=index(update_statement, " where ")
            if (index(update_statement, update_prefix) != 1 || where_position == 0) continue
            assignments=substr(update_statement, length(update_prefix) + 1, where_position - length(update_prefix) - 1)
            predicate=substr(update_statement, where_position + 7)
            assigns_column=(assignments ~ ("(^|, )" column_name "[ ]*="))
            limits_exactly_null_rows=(predicate == column_name " is null" || predicate == "(" column_name " is null)")
            if (assigns_column && limits_exactly_null_rows) {
              found=1
            }
          }
          if (!found) valid=0
        }
      }
      statements[NR]=statement
    }
    END { exit valid ? 0 : 1 }
  '
}

has_only_bounded_deletes() {
  local file="$1"
  local table_name
  local delete_tables

  if grep -Eq '\$[A-Za-z0-9_]*\$' "$file"; then
    return 1
  fi

  delete_tables="$(strip_sql_comments "$file" | awk '
    BEGIN { RS=";"; invalid=0 }
    {
      statement=tolower($0)
      gsub(/[[:space:]]+/, " ", statement)
      sub(/^ /, "", statement)
      sub(/ $/, "", statement)
      if (statement !~ /delete from/) next
      if (statement !~ /^delete from [a-z0-9_.]+/) {
        invalid=1
        next
      }
      delete_statement=statement
      sub(/^delete from /, "", delete_statement)
      split(delete_statement, delete_parts, " ")
      table_name=delete_parts[1]
      where_position=index(statement, " where ")
      if (where_position == 0) {
        invalid=1
        next
      }
      predicate=substr(statement, where_position + 7)
      sub(/^[[:space:]]+/, "", predicate)
      if (predicate !~ /^[a-z0-9_.]+ in [(]/ || predicate !~ / limit [1-9][0-9]*[)]$/) {
        invalid=1
        next
      }
      print table_name
    }
    END { if (invalid) exit 2 }
  ')" || return 1

  while IFS= read -r table_name; do
    [ -n "$table_name" ] || continue
    grep -Fqx -- "-- noviis:bounded-delete $table_name" "$file" || return 1
  done <<< "$delete_tables"
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
  if grep -Eiq 'SET[[:space:]]+NOT[[:space:]]+NULL' "$file" && ! has_bound_not_null_backfills "$file"; then
    risky=true
  fi
  if grep -Eiq 'DELETE[[:space:]]+FROM' "$file" && ! has_only_bounded_deletes "$file"; then
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

while read -r migration_name deployment_run deployment_sha extra; do
  [ -n "$migration_name" ] || continue
  [[ "$migration_name" = \#* ]] && continue
  case "$migration_name" in V*.sql) ;; *) echo "Invalid contract migration filename in allowlist: $migration_name" >&2; exit 1 ;; esac
  case "$deployment_run" in https://github.com/weedrice/project_whiteboard/actions/runs/[0-9]*) ;; *) echo "Contract allowlist entry requires this repository's GitHub deployment run URL: $migration_name" >&2; exit 1 ;; esac
  if [[ ! "$deployment_sha" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Contract allowlist entry requires the deployed 40-character commit SHA: $migration_name" >&2
    exit 1
  fi
  if [ -n "${extra:-}" ]; then
    echo "Unexpected extra fields in contract allowlist entry: $migration_name" >&2
    exit 1
  fi
  if [ "$verify_contract_runs" = true ]; then
    run_id="${deployment_run##*/}"
    verify_contract_deployment_run "$run_id" "$deployment_sha"
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
