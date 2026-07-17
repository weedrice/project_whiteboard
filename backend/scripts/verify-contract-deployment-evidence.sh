#!/usr/bin/env bash
set -euo pipefail

snapshot_id="${1:?RDS snapshot identifier is required}"
expected_database="${2:?production RDS DB identifier is required}"
deployment_sha="${3:?deployment commit SHA is required}"
maximum_age_seconds="${MAX_SNAPSHOT_AGE_SECONDS:-86400}"

fail() {
  echo "Contract deployment evidence verification failed: $*" >&2
  exit 1
}

[[ "$snapshot_id" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{2,254}$ ]] || fail "invalid snapshot identifier"
[[ "$expected_database" =~ ^[A-Za-z][A-Za-z0-9-]{0,62}$ ]] || fail "invalid production DB identifier"
[[ "$deployment_sha" =~ ^[0-9a-f]{40}$ ]] || fail "invalid deployment commit SHA"
[[ "$maximum_age_seconds" =~ ^[1-9][0-9]*$ ]] || fail "invalid snapshot maximum age"

snapshot_record="$(aws rds describe-db-snapshots \
  --db-snapshot-identifier "$snapshot_id" \
  --snapshot-type manual \
  --query 'DBSnapshots[0].[Status,DBInstanceIdentifier,SnapshotCreateTime,SnapshotType]' \
  --output text)" || fail "snapshot lookup failed"

IFS=$'\t' read -r status database_identifier created_at snapshot_type extra <<< "$snapshot_record"
[ -z "${extra:-}" ] || fail "snapshot lookup returned an unexpected shape"
[ "$status" = available ] || fail "snapshot is not available"
[ "$database_identifier" = "$expected_database" ] || fail "snapshot belongs to a different database"
[ "$snapshot_type" = manual ] || fail "snapshot is not a manual pre-deployment snapshot"

created_epoch="$(date -u -d "$created_at" +%s 2>/dev/null)" || fail "snapshot creation time is invalid"
now_epoch="$(date -u +%s)"
[ "$created_epoch" -le "$now_epoch" ] || fail "snapshot was created in the future"
age_seconds=$((now_epoch - created_epoch))
[ "$age_seconds" -le "$maximum_age_seconds" ] || fail "snapshot is older than the permitted pre-deployment window"

printf 'snapshot_id=%s\ndatabase_identifier=%s\ndeployment_sha=%s\n' \
  "$snapshot_id" "$database_identifier" "$deployment_sha"
