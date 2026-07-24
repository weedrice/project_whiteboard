#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-HEAD^}"
head_ref="${2:-HEAD}"
migration_dir="backend/src/main/resources/db/migration"
contract_allowlist="docs/ops/applied-contract-migrations.txt"
contract_marker_text='noviis:migration-phase contract'
legacy_markerless_migration='backend/src/main/resources/db/migration/V55__limit_verification_code_attempts.sql'
legacy_markerless_migration_sha256='a557b508c9aeb6141cac960de03353875c807364e0f17731187183d7e2276ffc'
contract_migration=false
new_migration=false

if [[ "$base_ref" =~ ^0+$ ]] || ! git cat-file -e "$base_ref^{commit}" 2>/dev/null; then
  echo "Migration compatibility check cannot prove the base commit: $base_ref" >&2
  exit 1
fi

migration_version() {
  local filename="${1##*/}"
  if [[ "$filename" =~ ^V([1-9][0-9]*)__[A-Za-z0-9][A-Za-z0-9_]*\.sql$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  echo "Versioned migration must use V<positive integer>__<description>.sql: $1" >&2
  return 1
}

decimal_greater_than() {
  local left="$1" right="$2"
  if [ "${#left}" -ne "${#right}" ]; then
    [ "${#left}" -gt "${#right}" ]
  else
    [[ "$left" > "$right" ]]
  fi
}

is_approved_legacy_markerless_migration() {
  local file="$1"
  local actual_sha256

  [ "$file" = "$legacy_markerless_migration" ] || return 1
  actual_sha256="$(git show "$head_ref:$file" | sha256sum | awk '{print $1}')"
  [ "$actual_sha256" = "$legacy_markerless_migration_sha256" ]
}

base_max_version=0
while IFS= read -r base_migration; do
  [ -n "$base_migration" ] || continue
  [[ "$base_migration" == "$migration_dir"/V*.sql ]] || continue
  version="$(migration_version "$base_migration")" || exit 1
  if decimal_greater_than "$version" "$base_max_version"; then
    base_max_version="$version"
  fi
done < <(git ls-tree -r --name-only "$base_ref" -- "$migration_dir")

declare -A head_versions=()
while IFS= read -r head_migration; do
  [ -n "$head_migration" ] || continue
  [[ "$head_migration" == "$migration_dir"/V*.sql ]] || continue
  version="$(migration_version "$head_migration")" || exit 1
  if [ -n "${head_versions[$version]:-}" ]; then
    echo "Duplicate Flyway migration version V$version: ${head_versions[$version]} and $head_migration" >&2
    exit 1
  fi
  head_versions[$version]="$head_migration"
done < <(git ls-tree -r --name-only "$head_ref" -- "$migration_dir")

forbidden_migration="$(git ls-tree -r --name-only "$head_ref" -- "$migration_dir" \
  | grep -E '/(R|U)__[^/]*\.sql$' | head -n 1 || true)"
if [ -n "$forbidden_migration" ]; then
  echo "Repeatable and undo Flyway migrations are not permitted: $forbidden_migration" >&2
  exit 1
fi
java_migration="$(git ls-tree -r --name-only "$head_ref" -- backend/src/main/java \
  | grep -E '/db/migration/[^/]+\.java$' | head -n 1 || true)"
if [ -n "$java_migration" ]; then
  echo "Java Flyway migrations are not permitted: $java_migration" >&2
  exit 1
fi

mapfile -t changes < <(git diff --name-status --find-renames "$base_ref" "$head_ref" -- "$migration_dir/V*.sql" "$migration_dir/V*.sql.conf")
for change in "${changes[@]}"; do
  status="${change%%$'\t'*}"
  case "$status" in
    A) ;;
    *)
      echo "Existing versioned migrations are immutable: $change" >&2
      exit 1
      ;;
  esac
  file="${change#*$'\t'}"
  if [[ "$file" = *.sql ]]; then
    version="$(migration_version "$file")" || exit 1
    if ! decimal_greater_than "$version" "$base_max_version"; then
      echo "New Flyway migration version V$version must be greater than base maximum V$base_max_version: $file" >&2
      exit 1
    fi
  fi
done

validate_online_indexes() {
  local file="$1"
  local sidecar="${file}.conf"
  local index_name table_name concurrent marker
  local online_index=false
  local created_tables

  created_tables="$(strip_sql_comments "$file" | awk '
    BEGIN { RS=";" }
    {
      statement=tolower($0)
      gsub(/[[:space:]]+/, " ", statement)
      sub(/^ /, "", statement)
      if (statement ~ /^create table /) {
        sub(/^create table /, "", statement)
        sub(/^if not exists /, "", statement)
        split(statement, parts, " ")
        print parts[1]
      }
    }
  ')"

  while IFS='|' read -r index_name table_name concurrent; do
    [ -n "$index_name" ] || continue
    if grep -Fqx -- "$table_name" <<< "$created_tables"; then
      continue
    fi
    online_index=true
    [ "$concurrent" = true ] || {
      echo "An index on an existing table must use CREATE INDEX CONCURRENTLY: $file ($index_name)" >&2
      return 1
    }
    marker="-- noviis:online-index $index_name"
    grep -Fqx -- "$marker" "$file" || {
      echo "Online index requires an exact marker: $file ($index_name)" >&2
      return 1
    }
  done < <(strip_sql_comments "$file" | awk '
    BEGIN { RS=";" }
    {
      statement=tolower($0)
      gsub(/[[:space:]]+/, " ", statement)
      sub(/^ /, "", statement)
      if (statement !~ /^create (unique )?index /) next
      concurrent=(statement ~ /^create (unique )?index concurrently / ? "true" : "false")
      work=statement
      sub(/^create /, "", work)
      sub(/^unique /, "", work)
      sub(/^index /, "", work)
      sub(/^concurrently /, "", work)
      sub(/^if not exists /, "", work)
      split(work, index_parts, " ")
      index_name=index_parts[1]
      on_position=index(work, " on ")
      if (on_position == 0) { print "__unparseable__||false"; next }
      target=substr(work, on_position + 4)
      split(target, table_parts, " ")
      table_name=table_parts[1]
      print index_name "|" table_name "|" concurrent
    }
  ')

  if [ "$online_index" = true ]; then
    if strip_sql_comments "$file" | grep -Eiq 'CREATE[[:space:]]+(UNIQUE[[:space:]]+)?INDEX[[:space:]]+CONCURRENTLY[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS'; then
      echo "Online index migration must not use IF NOT EXISTS: $file" >&2
      return 1
    fi
    grep -Eq "^[[:space:]]*SET[[:space:]]+lock_timeout[[:space:]]*=[[:space:]]*'([1-9]|10)s'[[:space:]]*;[[:space:]]*$" "$file" || {
      echo "Online index migration requires a lock_timeout between 1s and 10s: $file" >&2
      return 1
    }
    if ! { [ -f "$sidecar" ] && [ "$(tr -d '\r' < "$sidecar")" = 'executeInTransaction=false' ]; }; then
      echo "Online index migration requires an exact Flyway non-transactional sidecar: $sidecar" >&2
      return 1
    fi
  elif [ -f "$sidecar" ]; then
    echo "Flyway transaction sidecar is only allowed for a migration with an online index: $sidecar" >&2
    return 1
  fi
}

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

validate_redundant_index_drops() {
  local file="$1"
  local index_name marker replacement design_doc
  local found=false
  while IFS= read -r index_name; do
    [ -n "$index_name" ] || continue
    found=true
    marker="$(grep -E "^-- noviis:redundant-index ${index_name} replacement=[a-zA-Z0-9_.]+$" "$file" | head -n 1 || true)"
    if [ -z "$marker" ]; then
      echo "DROP INDEX requires an exact redundant-index replacement marker: $file ($index_name)" >&2
      return 1
    fi
    replacement="${marker##* replacement=}"
    [ "$replacement" != "$index_name" ] || {
      echo "DROP INDEX replacement must differ from the removed index: $file ($index_name)" >&2
      return 1
    }
  done < <(strip_sql_comments "$file" | awk '
    BEGIN { RS=";" }
    {
      statement=tolower($0)
      gsub(/[[:space:]]+/, " ", statement)
      sub(/^ /, "", statement)
      sub(/ $/, "", statement)
      if (statement !~ /^drop index /) next
      sub(/^drop index /, "", statement)
      sub(/^concurrently /, "", statement)
      sub(/^if exists /, "", statement)
      if (statement !~ /^[a-z0-9_.]+$/) { print "__UNPARSEABLE__"; next }
      print statement
    }
  ')
  if grep -Fq '__UNPARSEABLE__' < <(strip_sql_comments "$file" | awk 'BEGIN { RS=";" } { s=tolower($0); gsub(/[[:space:]]+/, " ", s); sub(/^ /, "", s); if (s ~ /^drop index /) { sub(/^drop index /, "", s); sub(/^concurrently /, "", s); sub(/^if exists /, "", s); if (s !~ /^[a-z0-9_.]+$/) print "__UNPARSEABLE__" } }'); then
    echo "DROP INDEX statement must remove exactly one statically named index: $file" >&2
    return 1
  fi
  if [ "$found" = true ]; then
    design_doc="$(sed -n 's/^-- noviis:design-doc //p' "$file" | head -n 1)"
    case "$design_doc" in docs/design-notes/*.md) ;; *) echo "DROP INDEX requires a tracked design document: $file" >&2; return 1 ;; esac
    git ls-files --error-unmatch "$design_doc" >/dev/null 2>&1 || {
      echo "DROP INDEX design document is not tracked: $design_doc" >&2
      return 1
    }
  fi
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

extract_sql_line_comments() {
  awk '
    BEGIN { in_block=0; in_string=0; quote=sprintf("%c", 39) }
    {
      for (character_index=1; character_index<=length($0); character_index++) {
        character=substr($0, character_index, 1)
        next_character=substr($0, character_index + 1, 1)
        if (in_block) {
          if (character == "*" && next_character == "/") { in_block=0; character_index++ }
          continue
        }
        if (in_string) {
          if (character == quote) {
            if (next_character == quote) character_index++
            else in_string=0
          }
          continue
        }
        if (character == quote) in_string=1
        else if (character == "/" && next_character == "*") { in_block=1; character_index++ }
        else if (character == "-" && next_character == "-") {
          comment=substr($0, character_index + 2)
          sub(/^[[:space:]]+/, "", comment)
          sub(/[[:space:]]+$/, "", comment)
          print comment
          break
        }
      }
    }
    END { if (in_block || in_string) exit 2 }
  ' "$1"
}

has_exact_line_comment() {
  extract_sql_line_comments "$1" | grep -Fqx -- "$2"
}

strip_sql_comments_and_strings() {
  strip_sql_comments "$1" | awk '
    BEGIN { in_string=0; quote=sprintf("%c", 39) }
    {
      output=""
      for (character_index=1; character_index<=length($0); character_index++) {
        character=substr($0, character_index, 1)
        next_character=substr($0, character_index + 1, 1)
        if (in_string) {
          if (character == quote) {
            if (next_character == quote) {
              character_index++
            } else {
              in_string=0
              output=output " "
            }
          }
          continue
        }
        if (character == quote) {
          in_string=1
          output=output " "
        } else {
          output=output character
        }
      }
      print output
    }
    END { if (in_string) exit 2 }
  '
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
  case "$file" in
    *.sql.conf)
      sql_file="${file%.conf}"
      if [ "$status" != A ] || ! git diff --name-status "$base_ref" "$head_ref" -- "$sql_file" | grep -q '^A[[:space:]]'; then
        echo "A Flyway sidecar may only be added with its new versioned migration: $file" >&2
        exit 1
      fi
      continue
      ;;
  esac
  if [ "$status" = A ]; then
    new_migration=true
    phase_count="$(extract_sql_line_comments "$file" | grep -Ec '^noviis:migration-phase (expand|backfill|contract)$' || true)"
    if [ "$phase_count" -ne 1 ] && ! is_approved_legacy_markerless_migration "$file"; then
      echo "New migration requires exactly one expand, backfill, or contract phase marker: $file" >&2
      exit 1
    fi
    validate_online_indexes "$file"
  fi
  sql_for_risk="$(strip_sql_comments_and_strings "$file" | tr '\r\n\t' '   ')" || {
    echo "Migration SQL cannot be safely classified: $file" >&2
    exit 1
  }
  risky=false
  if grep -Eiq '(^|[[:space:];])(DO[[:space:]]+\$|CALL[[:space:]]+|CREATE[[:space:]]+(OR[[:space:]]+REPLACE[[:space:]]+)?(FUNCTION|PROCEDURE)[[:space:]]+|EXECUTE[[:space:]]+)' <<< "$sql_for_risk" \
    || grep -Eq '\$[A-Za-z0-9_]*\$' "$file"; then
    risky=true
  fi
  if grep -Eiq '(^|[[:space:];])(DROP[[:space:]]+(TABLE|COLUMN|VIEW|TYPE|DOMAIN|SCHEMA|SEQUENCE|FUNCTION|PROCEDURE|TRIGGER|EVENT[[:space:]]+TRIGGER|POLICY|EXTENSION|OWNED)|TRUNCATE([[:space:]]+TABLE)?|(GRANT|REVOKE)([[:space:]]+|$)|ALTER[[:space:]]+DEFAULT[[:space:]]+PRIVILEGES|REASSIGN[[:space:]]+OWNED|CREATE[[:space:]]+(OR[[:space:]]+REPLACE[[:space:]]+)?((CONSTRAINT|EVENT)[[:space:]]+)?TRIGGER|ALTER[[:space:]]+(EVENT[[:space:]]+)?TRIGGER|CREATE[[:space:]]+POLICY|ALTER[[:space:]]+POLICY|CREATE[[:space:]]+TABLE[^;]*PARTITION[[:space:]]+OF|ALTER[[:space:]]+[^;]*OWNER[[:space:]]+TO|ALTER[[:space:]]+TABLE[^;]*(RENAME|DROP[[:space:]]+(COLUMN|CONSTRAINT|DEFAULT)|ALTER[[:space:]]+COLUMN[^;]*(TYPE|DROP[[:space:]]+DEFAULT)|(ENABLE|DISABLE)[[:space:]]+TRIGGER|(ENABLE|DISABLE|FORCE|NO[[:space:]]+FORCE)[[:space:]]+ROW[[:space:]]+LEVEL[[:space:]]+SECURITY|SET[[:space:]]+UNLOGGED|(ATTACH|DETACH)[[:space:]]+PARTITION)|ALTER[[:space:]]+TYPE[^;]*RENAME[[:space:]]+VALUE)' <<< "$sql_for_risk"; then
    risky=true
  fi
  if grep -Eiq '(^|[[:space:];])((CREATE|ALTER|DROP)[[:space:]]+(ROLE|USER|DATABASE|TABLESPACE|SERVER|FOREIGN[[:space:]]+DATA[[:space:]]+WRAPPER|USER[[:space:]]+MAPPING|EXTENSION)([[:space:]]+|$)|ALTER[[:space:]]+SYSTEM([[:space:]]+|$)|SET[[:space:]]+(ROLE|SESSION[[:space:]]+AUTHORIZATION)([[:space:]]+|$)|SECURITY[[:space:]]+LABEL([[:space:]]+|$)|COPY[^;]*[[:space:]]PROGRAM([[:space:]]+|$))' <<< "$sql_for_risk"; then
    risky=true
  fi
  if grep -Eiq '(^|[[:space:];])DROP[[:space:]]+INDEX' <<< "$sql_for_risk" \
    && ! has_exact_line_comment "$file" "$contract_marker_text" \
    && ! validate_redundant_index_drops "$file"; then
    risky=true
  fi
  if grep -Eiq 'SET[[:space:]]+NOT[[:space:]]+NULL' <<< "$sql_for_risk" && ! has_bound_not_null_backfills "$file"; then
    risky=true
  fi
  if grep -Eiq 'DELETE[[:space:]]+FROM' <<< "$sql_for_risk" && ! has_only_bounded_deletes "$file"; then
    risky=true
  fi

  if [ "$risky" = true ] && ! has_exact_line_comment "$file" "$contract_marker_text"; then
      echo "Risky migration requires an explicit contract phase marker: $file" >&2
      exit 1
  fi

  if has_exact_line_comment "$file" "$contract_marker_text"; then
    validate_contract_metadata "$file"
    contract_migration=true
  fi
done

if [ ! -f "$contract_allowlist" ]; then
  echo "Contract migration allowlist is missing: $contract_allowlist" >&2
  exit 1
fi

if ! base_allowlist="$(git show "$base_ref:$contract_allowlist" 2>/dev/null)"; then
  echo "Contract migration allowlist cannot be read from the base commit: $contract_allowlist" >&2
  exit 1
fi

while read -r base_migration_name base_extra; do
  [ -n "$base_migration_name" ] || continue
  [[ "$base_migration_name" = \#* ]] && continue
  [ -z "${base_extra:-}" ] || {
    echo "Base contract allowlist contains an invalid record: $base_migration_name" >&2
    exit 1
  }
  awk 'NF && $1 !~ /^#/ { print $1 }' "$contract_allowlist" | grep -Fqx -- "$base_migration_name" || {
    echo "Applied contract migration records are immutable: $base_migration_name" >&2
    exit 1
  }
done <<< "$base_allowlist"

while read -r migration_name extra; do
  [ -n "$migration_name" ] || continue
  [[ "$migration_name" = \#* ]] && continue
  case "$migration_name" in V*.sql) ;; *) echo "Invalid contract migration filename in allowlist: $migration_name" >&2; exit 1 ;; esac
  migration_file="$migration_dir/$migration_name"
  if ! git cat-file -e "$head_ref:$migration_file" 2>/dev/null \
    || ! has_exact_line_comment "$migration_file" "$contract_marker_text"; then
    echo "Contract allowlist entry must reference an existing contract migration: $migration_name" >&2
    exit 1
  fi
  if [ -n "${extra:-}" ]; then
    echo "Unexpected extra fields in contract allowlist entry: $migration_name" >&2
    exit 1
  fi
  if ! awk 'NF && $1 !~ /^#/ { print $1 }' <<< "$base_allowlist" | grep -Fqx -- "$migration_name" \
    && [ "$new_migration" = true ]; then
    echo "A contract migration cannot be marked applied in the same change as a new migration: $migration_name" >&2
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
done < <(
  for candidate in "$migration_dir"/V*.sql; do
    [ -f "$candidate" ] || continue
    has_exact_line_comment "$candidate" "$contract_marker_text" && printf '%s\n' "$candidate"
  done
)

for change in "${changes[@]}"; do
  status="${change%%$'\t'*}"
  file="${change#*$'\t'}"
  if [ "$status" = A ] && has_exact_line_comment "$file" "$contract_marker_text"; then
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
