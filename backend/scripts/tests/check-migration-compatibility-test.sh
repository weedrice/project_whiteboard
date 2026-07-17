#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/backend/scripts/check-migration-compatibility.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

git -C "$fixture" init -q
git -C "$fixture" config user.email ci@example.invalid
git -C "$fixture" config user.name CI
mkdir -p "$fixture/backend/src/main/resources/db/migration" "$fixture/docs/ops" "$fixture/docs/design-notes"
printf 'CREATE TABLE sample (id bigint);\n' > "$fixture/backend/src/main/resources/db/migration/V1__base.sql"
printf '# applied contracts\n' > "$fixture/docs/ops/applied-contract-migrations.txt"
printf '# Contract design\n' > "$fixture/docs/design-notes/remove-sample.md"
git -C "$fixture" add .
git -C "$fixture" commit -qm base
base="$(git -C "$fixture" rev-parse HEAD)"

if (cd "$fixture" && bash "$script" missing HEAD); then
  echo "Expected a missing base commit to fail closed" >&2
  exit 1
fi

printf 'ALTER TABLE sample DROP CONSTRAINT sample_pkey;\n' > "$fixture/backend/src/main/resources/db/migration/V2__unsafe.sql"
git -C "$fixture" add .
git -C "$fixture" commit -qm unsafe
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected an unmarked destructive migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__backfilled_not_null.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
UPDATE sample SET name = 'legacy' WHERE name IS NULL;
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm safe-backfill
(cd "$fixture" && bash "$script" "$base" HEAD)

git -C "$fixture" reset -q --hard "$base"
printf '%s\n' 'CREATE INDEX sample_idx ON sample (id);' > "$fixture/backend/src/main/resources/db/migration/V2__missing_phase.sql"
git -C "$fixture" add .
git -C "$fixture" commit -qm missing-phase
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a new migration without a phase marker to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__unbounded_delete.sql" <<'SQL'
-- noviis:migration-phase backfill
DELETE FROM sample;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm unbounded-delete
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected an unbounded DELETE without a contract marker to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__contract.sql" <<'SQL'
-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/remove-sample.md
ALTER TABLE sample DROP CONSTRAINT sample_pkey;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm contract
output_file="$fixture/output"
(cd "$fixture" && GITHUB_OUTPUT="$output_file" bash "$script" "$base" HEAD)
grep -qx 'contract_migration=true' "$output_file"

printf 'V2__contract.sql https://github.com/weedrice/project_whiteboard/actions/runs/1\n' >> "$fixture/docs/ops/applied-contract-migrations.txt"
git -C "$fixture" add .
git -C "$fixture" commit -qm premature-allowlist
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected same-change contract acknowledgement to fail" >&2
  exit 1
fi

echo "Migration compatibility fixtures passed"
