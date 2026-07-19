#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/backend/scripts/check-migration-compatibility.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

git -C "$fixture" init -q
git -C "$fixture" config user.email ci@example.invalid
git -C "$fixture" config user.name CI
mkdir -p "$fixture/backend/src/main/resources/db/migration" "$fixture/docs/ops/contract-evidence" "$fixture/docs/design-notes"
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

cat > "$fixture/backend/src/main/resources/db/migration/V3__higher_base.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE TABLE higher_base (id bigint);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm higher-base
higher_base="$(git -C "$fixture" rev-parse HEAD)"
cat > "$fixture/backend/src/main/resources/db/migration/V2__out_of_order.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE TABLE out_of_order (id bigint);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm out-of-order
if (cd "$fixture" && bash "$script" "$higher_base" HEAD); then
  echo "Expected a migration below the base maximum version to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V1__duplicate.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE TABLE duplicate_version (id bigint);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm duplicate-version
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a duplicate Flyway version to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__privileged.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE EXTENSION hstore;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm privileged-ddl
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected privileged DDL outside a contract migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"

printf 'CREATE TABLE sample (id bigint, changed boolean);\n' > "$fixture/backend/src/main/resources/db/migration/V1__base.sql"
git -C "$fixture" add .
git -C "$fixture" commit -qm modified-existing-migration
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a modified versioned migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
git -C "$fixture" rm -q backend/src/main/resources/db/migration/V1__base.sql
git -C "$fixture" commit -qm deleted-existing-migration
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a deleted versioned migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"

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
cat > "$fixture/backend/src/main/resources/db/migration/V2__unproven_drop_index.sql" <<'SQL'
-- noviis:migration-phase expand
DROP INDEX IF EXISTS sample_idx;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm unproven-drop-index
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected DROP INDEX without replacement evidence to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__proven_drop_index.sql" <<'SQL'
-- noviis:migration-phase expand
-- noviis:design-doc docs/design-notes/remove-sample.md
-- noviis:redundant-index sample_idx replacement=sample_pkey
DROP INDEX IF EXISTS sample_idx;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm proven-drop-index
(cd "$fixture" && bash "$script" "$base" HEAD)

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__unrelated_backfill.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
UPDATE other_table SET name = 'legacy' WHERE name IS NULL;
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm unrelated-backfill
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a SET NOT NULL with an unrelated backfill to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__commented_backfill.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
-- UPDATE sample SET name = 'legacy' WHERE name IS NULL;
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm commented-backfill
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a commented-out backfill not to satisfy SET NOT NULL" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__block_commented_backfill.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
/* UPDATE sample SET name = 'legacy' WHERE name IS NULL; */
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm block-commented-backfill
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a block-commented backfill not to satisfy SET NOT NULL" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__dollar_quoted_backfill.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
DO $$ BEGIN
  RAISE NOTICE 'UPDATE sample SET name = legacy WHERE name IS NULL;';
END $$;
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm dollar-quoted-backfill
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a dollar-quoted pseudo-backfill not to satisfy SET NOT NULL" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__partial_backfill.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN name varchar(20);
UPDATE sample SET name = 'legacy' WHERE name IS NULL AND id < 0;
ALTER TABLE sample ALTER COLUMN name SET NOT NULL;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm partial-backfill
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a partial backfill not to satisfy SET NOT NULL" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
printf '%s\n' 'CREATE INDEX sample_idx ON sample (id);' > "$fixture/backend/src/main/resources/db/migration/V2__missing_phase.sql"
git -C "$fixture" add .
git -C "$fixture" commit -qm missing-phase
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a new migration without a phase marker to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V55__limit_verification_code_attempts.sql" <<'SQL'
ALTER TABLE verification_codes
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE verification_codes
    ADD CONSTRAINT chk_verification_codes_failed_attempts
        CHECK (failed_attempts BETWEEN 0 AND 5);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm approved-legacy-markerless-migration
(cd "$fixture" && bash "$script" "$base" HEAD)

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V55__limit_verification_code_attempts.sql" <<'SQL'
ALTER TABLE verification_codes
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE verification_codes
    ADD CONSTRAINT chk_verification_codes_failed_attempts
        CHECK (failed_attempts BETWEEN 0 AND 6);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm modified-legacy-markerless-migration
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a modified legacy markerless migration to fail checksum validation" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__blocking_index.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE INDEX sample_idx ON sample (id);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm blocking-index
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected an index on an existing table without CONCURRENTLY to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__online_index_without_sidecar.sql" <<'SQL'
-- noviis:migration-phase expand
-- noviis:online-index sample_idx
SET lock_timeout = '5s';
CREATE INDEX CONCURRENTLY sample_idx ON sample (id);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm online-index-without-sidecar
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected an online index without a Flyway sidecar to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__online_index_if_not_exists.sql" <<'SQL'
-- noviis:migration-phase expand
-- noviis:online-index sample_idx
SET lock_timeout = '5s';
CREATE INDEX CONCURRENTLY IF NOT EXISTS sample_idx ON sample (id);
SQL
printf '%s\n' 'executeInTransaction=false' > "$fixture/backend/src/main/resources/db/migration/V2__online_index_if_not_exists.sql.conf"
git -C "$fixture" add .
git -C "$fixture" commit -qm online-index-if-not-exists
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected IF NOT EXISTS on an online index to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
printf '%s\n' 'executeInTransaction=false' > "$fixture/backend/src/main/resources/db/migration/V2__orphan.sql.conf"
git -C "$fixture" add .
git -C "$fixture" commit -qm orphan-sidecar
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a Flyway sidecar without its new migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__online_index.sql" <<'SQL'
-- noviis:migration-phase expand
-- noviis:online-index sample_idx
SET lock_timeout = '5s';
CREATE INDEX CONCURRENTLY sample_idx ON sample (id);
SQL
printf '%s\n' 'executeInTransaction=false' > "$fixture/backend/src/main/resources/db/migration/V2__online_index.sql.conf"
git -C "$fixture" add .
git -C "$fixture" commit -qm online-index
(cd "$fixture" && bash "$script" "$base" HEAD)

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__new_table_index.sql" <<'SQL'
-- noviis:migration-phase expand
CREATE TABLE sample_child (id bigint PRIMARY KEY);
CREATE INDEX idx_sample_child_id ON sample_child (id);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm new-table-index
(cd "$fixture" && bash "$script" "$base" HEAD)

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
cat > "$fixture/backend/src/main/resources/db/migration/V2__tautology_delete.sql" <<'SQL'
-- noviis:migration-phase backfill
-- noviis:bounded-delete sample
DELETE FROM sample WHERE TRUE;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm tautology-delete
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a tautology DELETE to fail even with a bounded marker" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__lowercase_unbounded_delete.sql" <<'SQL'
-- noviis:migration-phase backfill
-- noviis:bounded-delete sample
delete from sample where id > 0;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm lowercase-unbounded-delete
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a lowercase DELETE without a bounded LIMIT subquery to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__bounded_delete.sql" <<'SQL'
-- noviis:migration-phase backfill
-- noviis:bounded-delete sample
DELETE FROM sample WHERE id IN (SELECT id FROM sample WHERE id < 0 LIMIT 100);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm bounded-delete
(cd "$fixture" && bash "$script" "$base" HEAD)

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__dynamic_expand.sql" <<'SQL'
-- noviis:migration-phase expand
DO $$ BEGIN
  EXECUTE 'CREATE TABLE hidden_change (id bigint)';
END $$;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm dynamic-expand
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected dynamic SQL without a contract marker to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__safe_text.sql" <<'SQL'
-- noviis:migration-phase expand
-- REVOKE ALL ON sample FROM public;
INSERT INTO sample (id) VALUES (1);
SELECT 'ALTER TABLE sample DISABLE TRIGGER ALL';
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm safe-text
(cd "$fixture" && bash "$script" "$base" HEAD)

for dangerous_statement in \
  'GRANT SELECT ON sample TO public;' \
  'REVOKE SELECT ON sample FROM public;' \
  'ALTER DEFAULT PRIVILEGES GRANT SELECT ON TABLES TO public;' \
  'ALTER TABLE sample OWNER TO other_owner;' \
  'ALTER TYPE sample_type OWNER TO other_owner;' \
  'REASSIGN OWNED BY legacy_role TO current_role;' \
  'DROP OWNED BY legacy_role;' \
  'CREATE TRIGGER sample_trigger BEFORE INSERT ON sample EXECUTE FUNCTION sample_trigger();' \
  'CREATE EVENT TRIGGER sample_ddl_trigger ON ddl_command_start EXECUTE FUNCTION sample_trigger();' \
  'ALTER TABLE sample ENABLE TRIGGER ALL;' \
  'ALTER TABLE sample DISABLE TRIGGER ALL;' \
  'CREATE POLICY sample_policy ON sample USING (true);' \
  'ALTER POLICY sample_policy ON sample USING (false);' \
  'ALTER TABLE sample ENABLE ROW LEVEL SECURITY;' \
  'ALTER TABLE sample SET UNLOGGED;' \
  'ALTER TABLE sample ATTACH PARTITION sample_new FOR VALUES FROM (1) TO (2);' \
  'CREATE TABLE sample_new PARTITION OF sample FOR VALUES FROM (1) TO (2);' \
  'ALTER TABLE sample DETACH PARTITION sample_old;'; do
  git -C "$fixture" reset -q --hard "$base"
  {
    printf '%s\n' '-- noviis:migration-phase expand'
    printf '%s\n' "$dangerous_statement"
  } > "$fixture/backend/src/main/resources/db/migration/V2__new_risky_operation.sql"
  git -C "$fixture" add .
  git -C "$fixture" commit -qm risky-operation
  if (cd "$fixture" && bash "$script" "$base" HEAD); then
    echo "Expected a newly classified risky statement to require a contract marker: $dangerous_statement" >&2
    exit 1
  fi
done

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__multiline_risky_operation.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample
  ATTACH PARTITION sample_new
  FOR VALUES FROM (1) TO (2);
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm multiline-risky-operation
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected multiline risky SQL to require a contract marker" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
cat > "$fixture/backend/src/main/resources/db/migration/V2__hidden_contract_marker.sql" <<'SQL'
-- noviis:migration-phase expand
/*
-- noviis:migration-phase contract
*/
DROP TABLE sample;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm hidden-contract-marker
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a contract marker hidden inside a block comment to be ignored" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
printf '%s\n' 'SELECT 1;' > "$fixture/backend/src/main/resources/db/migration/R__repeatable.sql"
git -C "$fixture" add .
git -C "$fixture" commit -qm repeatable
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a repeatable migration to be rejected" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$base"
mkdir -p "$fixture/backend/src/main/java/com/example/db/migration"
printf '%s\n' 'package com.example.db.migration; class V2Migration {}' \
  > "$fixture/backend/src/main/java/com/example/db/migration/V2Migration.java"
git -C "$fixture" add .
git -C "$fixture" commit -qm java-migration
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected a Java migration to be rejected" >&2
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

contract_commit="$(git -C "$fixture" rev-parse HEAD)"
evidence_file='docs/ops/contract-evidence/V2__contract.evidence'
cat > "$fixture/$evidence_file" <<'EOF'
migration=V2__contract.sql
repository=weedrice/project_whiteboard
run_url=https://github.com/weedrice/project_whiteboard/actions/runs/1
deployed_sha=0123456789abcdef0123456789abcdef01234567
EOF
printf 'V2__contract.sql https://github.com/weedrice/project_whiteboard/actions/runs/1 0123456789abcdef0123456789abcdef01234567 %s\n' "$evidence_file" >> "$fixture/docs/ops/applied-contract-migrations.txt"
git -C "$fixture" add .
git -C "$fixture" commit -qm premature-allowlist
allowlist_commit="$(git -C "$fixture" rev-parse HEAD)"
if (cd "$fixture" && bash "$script" "$base" HEAD); then
  echo "Expected same-change contract acknowledgement to fail" >&2
  exit 1
fi

fake_bin="$fixture/fake-bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/gh" <<'EOF'
#!/usr/bin/env bash
endpoint=""
for argument in "$@"; do
  case "$argument" in repos/weedrice/project_whiteboard/*) endpoint="$argument"; break ;; esac
done
case "$endpoint" in
  repos/weedrice/project_whiteboard/actions/runs/1)
    printf 'success\tmain\tworkflow_dispatch\t.github/workflows/ci.yml\t%s\n' \
      "${GH_RUN_SHA:-0123456789abcdef0123456789abcdef01234567}"
    ;;
  repos/weedrice/project_whiteboard/actions/runs/1/jobs\?*)
    case "${GH_BACKEND_JOB_MODE:-success}" in
      success) printf '42\tdeploy-backend / Deploy Backend / deploy\tsuccess\thttps://github.com/weedrice/project_whiteboard/actions/runs/1/job/42\n' ;;
      wrong-run) printf '42\tdeploy-backend / Deploy Backend / deploy\tsuccess\thttps://github.com/weedrice/project_whiteboard/actions/runs/999/job/42\n' ;;
      frontend-only) printf '84\tdeploy-frontend / Deploy Frontend / deploy\tsuccess\thttps://github.com/weedrice/project_whiteboard/actions/runs/1/job/84\n' ;;
      missing) ;;
    esac
    ;;
  repos/weedrice/project_whiteboard/deployments\?*) printf '99\n' ;;
  repos/weedrice/project_whiteboard/deployments/99/statuses\?*)
    if [ "${GH_BOUND_BACKEND_STATUS:-true}" = true ]; then printf '123\n'; fi
    ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$fake_bin/gh"

(cd "$fixture" && PATH="$fake_bin:$PATH" VERIFY_CONTRACT_RUNS=true bash "$script" "$contract_commit" HEAD)
if (cd "$fixture" && GH_RUN_SHA=ffffffffffffffffffffffffffffffffffffffff \
  PATH="$fake_bin:$PATH" VERIFY_CONTRACT_RUNS=true bash "$script" "$contract_commit" HEAD); then
  echo "Expected a deployment run for another commit to fail" >&2
  exit 1
fi
if (cd "$fixture" && GH_BOUND_BACKEND_STATUS=false \
  PATH="$fake_bin:$PATH" VERIFY_CONTRACT_RUNS=true bash "$script" "$contract_commit" HEAD); then
  echo "Expected a production deployment not bound to the backend job to fail" >&2
  exit 1
fi
if (cd "$fixture" && GH_BACKEND_JOB_MODE=frontend-only \
  PATH="$fake_bin:$PATH" VERIFY_CONTRACT_RUNS=true bash "$script" "$contract_commit" HEAD); then
  echo "Expected a frontend-only production deployment to fail" >&2
  exit 1
fi
if (cd "$fixture" && GH_BACKEND_JOB_MODE=wrong-run \
  PATH="$fake_bin:$PATH" VERIFY_CONTRACT_RUNS=true bash "$script" "$contract_commit" HEAD); then
  echo "Expected a backend job URL for another run to fail" >&2
  exit 1
fi

cat > "$fixture/backend/src/main/resources/db/migration/V3__unrelated_expand.sql" <<'SQL'
-- noviis:migration-phase expand
ALTER TABLE sample ADD COLUMN unrelated_value bigint;
SQL
git -C "$fixture" add .
git -C "$fixture" commit -qm migration-with-contract-evidence
if (cd "$fixture" && bash "$script" "$contract_commit" HEAD); then
  echo "Expected contract evidence added with an unrelated new migration to fail" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$allowlist_commit"
printf '\nextra=mutable\n' >> "$fixture/$evidence_file"
git -C "$fixture" add .
git -C "$fixture" commit -qm modified-contract-evidence
if (cd "$fixture" && bash "$script" "$allowlist_commit" HEAD); then
  echo "Expected a reviewed contract evidence manifest to be immutable" >&2
  exit 1
fi

git -C "$fixture" reset -q --hard "$allowlist_commit"
printf '# applied contracts\n' > "$fixture/docs/ops/applied-contract-migrations.txt"
git -C "$fixture" add .
git -C "$fixture" commit -qm removed-contract-allowlist-entry
if (cd "$fixture" && bash "$script" "$allowlist_commit" HEAD); then
  echo "Expected an applied contract allowlist entry to be immutable" >&2
  exit 1
fi

echo "Migration compatibility fixtures passed"
