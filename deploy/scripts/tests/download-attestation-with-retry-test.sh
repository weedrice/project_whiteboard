#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
downloader="$project_root/deploy/scripts/download-attestation-with-retry.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
fake_bin="$fixture/bin"
mkdir -p "$fake_bin" "$fixture/output"
printf 'subject\n' > "$fixture/subject"

cat > "$fake_bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
attempt_file="${FAKE_GH_ATTEMPT_FILE:?}"
attempt=0
[ ! -f "$attempt_file" ] || attempt="$(cat "$attempt_file")"
attempt=$((attempt + 1))
printf '%s' "$attempt" > "$attempt_file"
if [ "$attempt" -le "${FAKE_GH_FAILURES:-0}" ]; then
  echo "temporary attestation API failure" >&2
  exit 1
fi
printf '{"bundle":"ok"}\n' > "sha256-test.jsonl"
EOF
chmod +x "$fake_bin/gh"

attempt_file="$fixture/attempt"
PATH="$fake_bin:$PATH" FAKE_GH_ATTEMPT_FILE="$attempt_file" FAKE_GH_FAILURES=2 \
  ATTESTATION_DOWNLOAD_BASE_DELAY_SECONDS=0 ATTESTATION_DOWNLOAD_MAX_ATTEMPTS=4 \
  "$downloader" "$fixture/subject" weedrice/project_whiteboard "$fixture/output"
[ "$(cat "$attempt_file")" -eq 3 ]
[ "$(find "$fixture/output" -maxdepth 1 -type f -name 'sha256*.jsonl' | wc -l)" -eq 1 ]

rm -f "$attempt_file" "$fixture/output"/*
if PATH="$fake_bin:$PATH" FAKE_GH_ATTEMPT_FILE="$attempt_file" FAKE_GH_FAILURES=5 \
  ATTESTATION_DOWNLOAD_BASE_DELAY_SECONDS=0 ATTESTATION_DOWNLOAD_MAX_ATTEMPTS=3 \
  "$downloader" "$fixture/subject" weedrice/project_whiteboard "$fixture/output"; then
  echo "Retry exhaustion must fail" >&2
  exit 1
fi
[ "$(cat "$attempt_file")" -eq 3 ]

echo "Attestation retry fixtures passed"
