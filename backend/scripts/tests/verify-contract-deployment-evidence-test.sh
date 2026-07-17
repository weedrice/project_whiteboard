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
printf '%s\t%s\t%s\t%s\n' \
  "${SNAPSHOT_STATUS:-available}" \
  "${SNAPSHOT_DATABASE:-noviis-prod}" \
  "${SNAPSHOT_CREATED_AT:-2026-07-17T00:00:00Z}" \
  "${SNAPSHOT_TYPE:-manual}"
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
PATH="$fake_bin:$PATH" bash "$script" noviis-pre-contract noviis-prod "$sha" >/dev/null

if SNAPSHOT_STATUS=creating PATH="$fake_bin:$PATH" bash "$script" noviis-pre-contract noviis-prod "$sha"; then
  echo "Expected a non-available snapshot to fail" >&2
  exit 1
fi

if SNAPSHOT_DATABASE=other-db PATH="$fake_bin:$PATH" bash "$script" noviis-pre-contract noviis-prod "$sha"; then
  echo "Expected a snapshot for another database to fail" >&2
  exit 1
fi

if SNAPSHOT_CREATED_EPOCH=1 NOW_EPOCH=100000 MAX_SNAPSHOT_AGE_SECONDS=3600 \
  PATH="$fake_bin:$PATH" bash "$script" noviis-pre-contract noviis-prod "$sha"; then
  echo "Expected a stale snapshot to fail" >&2
  exit 1
fi

echo "Contract deployment evidence fixtures passed"
