#!/usr/bin/env bash
set -euo pipefail

snapshot_id="${1:?RDS snapshot identifier is required}"
expected_database="${2:?production RDS DB identifier is required}"
deployment_sha="${3:?deployment commit SHA is required}"
change_ticket="${4:?approved change ticket is required}"
maximum_age_seconds="${MAX_SNAPSHOT_AGE_SECONDS:-86400}"
expected_account="${AWS_EXPECTED_ACCOUNT_ID:?expected AWS account id is required}"
expected_region="${AWS_REGION:?expected AWS region is required}"
expected_kms_key="${RDS_SNAPSHOT_KMS_KEY_ARN:?expected snapshot KMS key ARN is required}"
expected_engine_major="${RDS_ENGINE_MAJOR_VERSION:?expected RDS engine major version is required}"

fail() {
  echo "Contract deployment evidence verification failed: $*" >&2
  exit 1
}

[[ "$snapshot_id" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{2,254}$ ]] || fail "invalid snapshot identifier"
[[ "$expected_database" =~ ^[A-Za-z][A-Za-z0-9-]{0,62}$ ]] || fail "invalid production DB identifier"
[[ "$deployment_sha" =~ ^[0-9a-f]{40}$ ]] || fail "invalid deployment commit SHA"
[[ "$change_ticket" =~ ^[A-Za-z0-9][A-Za-z0-9._:/-]{2,127}$ ]] || fail "invalid change ticket"
[[ "$maximum_age_seconds" =~ ^[1-9][0-9]*$ ]] || fail "invalid snapshot maximum age"
[[ "$expected_account" =~ ^[0-9]{12}$ ]] || fail "invalid expected AWS account id"
[[ "$expected_region" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]] || fail "invalid expected AWS region"
[[ "$expected_kms_key" = arn:aws*:kms:"$expected_region":"$expected_account":key/* ]] || fail "invalid expected KMS key ARN"
[[ "$expected_engine_major" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "invalid expected engine major version"

actual_account="$(aws sts get-caller-identity --query Account --output text)" || fail "AWS account lookup failed"
[ "$actual_account" = "$expected_account" ] || fail "credentials belong to a different AWS account"

snapshot_record="$(aws rds describe-db-snapshots \
  --db-snapshot-identifier "$snapshot_id" \
  --snapshot-type manual \
  --query 'DBSnapshots[0].[Status,DBInstanceIdentifier,SnapshotCreateTime,SnapshotType,DBSnapshotArn,Encrypted,KmsKeyId,Engine,EngineVersion]' \
  --output text)" || fail "snapshot lookup failed"

IFS=$'\t' read -r status database_identifier created_at snapshot_type snapshot_arn encrypted kms_key engine engine_version extra <<< "$snapshot_record"
[ -z "${extra:-}" ] || fail "snapshot lookup returned an unexpected shape"
[ "$status" = available ] || fail "snapshot is not available"
[ "$database_identifier" = "$expected_database" ] || fail "snapshot belongs to a different database"
[ "$snapshot_type" = manual ] || fail "snapshot is not a manual pre-deployment snapshot"
[ "$encrypted" = True ] || fail "snapshot is not encrypted"
[ "$kms_key" = "$expected_kms_key" ] || fail "snapshot uses a different KMS key"
[ "$engine" = postgres ] || fail "snapshot is not a PostgreSQL snapshot"
case "$engine_version" in "$expected_engine_major"|"$expected_engine_major".*) ;; *) fail "snapshot engine major version differs" ;; esac
case "$snapshot_arn" in arn:aws*:rds:"$expected_region":"$expected_account":snapshot:*) ;; *) fail "snapshot ARN belongs to a different account or region" ;; esac

# The single-quoted JMESPath expression deliberately protects AWS query backticks from Bash.
# shellcheck disable=SC2016
tag_record="$(aws rds list-tags-for-resource --resource-name "$snapshot_arn" \
  --query 'Tags[?Key==`noviis:deployment-sha` || Key==`noviis:change-ticket` || Key==`noviis:contract-consumed`].[Key,Value]' \
  --output text)" || fail "snapshot tag lookup failed"
tagged_sha="$(awk '$1 == "noviis:deployment-sha" { print $2 }' <<< "$tag_record")"
tagged_ticket="$(awk '$1 == "noviis:change-ticket" { print $2 }' <<< "$tag_record")"
consumed="$(awk '$1 == "noviis:contract-consumed" { print $2 }' <<< "$tag_record")"
[ "$tagged_sha" = "$deployment_sha" ] || fail "snapshot is not bound to this deployment SHA"
[ "$tagged_ticket" = "$change_ticket" ] || fail "snapshot is not bound to this change ticket"
[ -z "$consumed" ] || fail "snapshot was already consumed by a contract deployment"

created_epoch="$(date -u -d "$created_at" +%s 2>/dev/null)" || fail "snapshot creation time is invalid"
now_epoch="$(date -u +%s)"
[ "$created_epoch" -le "$now_epoch" ] || fail "snapshot was created in the future"
age_seconds=$((now_epoch - created_epoch))
[ "$age_seconds" -le "$maximum_age_seconds" ] || fail "snapshot is older than the permitted pre-deployment window"

printf 'snapshot_id=%s\ndatabase_identifier=%s\ndeployment_sha=%s\nchange_ticket=%s\naccount_id=%s\nregion=%s\nengine_version=%s\n' \
  "$snapshot_id" "$database_identifier" "$deployment_sha" "$change_ticket" "$actual_account" "$expected_region" "$engine_version"
