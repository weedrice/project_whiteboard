#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/backend/scripts/verify-contract-deployment-evidence.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
fake_bin="$fixture/bin"
mkdir -p "$fake_bin"

cat > "$fake_bin/aws" <<'EOF'
#!/usr/bin/env bash
case "${1:-} ${2:-}" in
  "sts get-caller-identity") printf '%s\n' "${ACTUAL_ACCOUNT:-123456789012}" ;;
  "rds describe-db-snapshots")
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "${SNAPSHOT_STATUS:-available}" \
      "${SNAPSHOT_DATABASE:-noviis-prod}" \
      "${SNAPSHOT_CREATED_AT:-2026-07-17T00:00:00Z}" \
      "${SNAPSHOT_TYPE:-manual}" \
      "${SNAPSHOT_ARN:-arn:aws:rds:ap-northeast-2:123456789012:snapshot:noviis-pre-contract}" \
      "${SNAPSHOT_ENCRYPTED:-True}" \
      "${SNAPSHOT_KMS_KEY:-arn:aws:kms:ap-northeast-2:123456789012:key/test-key}" \
      "${SNAPSHOT_ENGINE:-postgres}" \
      "${SNAPSHOT_ENGINE_VERSION:-16.4}"
    ;;
  "rds list-tags-for-resource")
    printf 'noviis:deployment-sha\t%s\n' "${TAGGED_SHA:-0123456789abcdef0123456789abcdef01234567}"
    printf 'noviis:change-ticket\t%s\n' "${TAGGED_TICKET:-CHG-1234}"
    [ -z "${CONSUMED_SHA:-}" ] || printf 'noviis:contract-consumed\t%s\n' "$CONSUMED_SHA"
    ;;
  *) exit 1 ;;
esac
EOF

cat > "$fake_bin/date" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = -u ] && [ "${2:-}" = -d ]; then
  printf '%s\n' "${SNAPSHOT_CREATED_EPOCH:-1000}"
else
  printf '%s\n' "${NOW_EPOCH:-2000}"
fi
EOF
chmod +x "$fake_bin"/*

sha=0123456789abcdef0123456789abcdef01234567
run_verify() {
  PATH="$fake_bin:$PATH" \
    AWS_EXPECTED_ACCOUNT_ID=123456789012 \
    AWS_REGION=ap-northeast-2 \
    RDS_SNAPSHOT_KMS_KEY_ARN=arn:aws:kms:ap-northeast-2:123456789012:key/test-key \
    RDS_ENGINE_MAJOR_VERSION=16 \
    bash "$script" noviis-pre-contract noviis-prod "$sha" CHG-1234
}

run_verify >/dev/null

for failure in \
  'SNAPSHOT_STATUS=creating' \
  'SNAPSHOT_DATABASE=other-db' \
  'ACTUAL_ACCOUNT=999999999999' \
  'SNAPSHOT_ARN=arn:aws:rds:us-east-1:123456789012:snapshot:wrong-region' \
  'SNAPSHOT_ENCRYPTED=False' \
  'SNAPSHOT_KMS_KEY=arn:aws:kms:ap-northeast-2:123456789012:key/wrong' \
  'SNAPSHOT_ENGINE_VERSION=15.9' \
  'TAGGED_SHA=ffffffffffffffffffffffffffffffffffffffff' \
  'TAGGED_TICKET=CHG-9999' \
  'CONSUMED_SHA=0123456789abcdef0123456789abcdef01234567'; do
  variable="${failure%%=*}"
  printf -v "$variable" '%s' "${failure#*=}"
  export "${variable?}"
  if run_verify; then
    echo "Expected contract evidence mismatch to fail: $failure" >&2
    exit 1
  fi
  unset "$variable"
done

if SNAPSHOT_CREATED_EPOCH=1 NOW_EPOCH=100000 MAX_SNAPSHOT_AGE_SECONDS=3600 run_verify; then
  echo "Expected a stale snapshot to fail" >&2
  exit 1
fi

echo "Contract deployment evidence fixtures passed"
