#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/scripts/verify-release-provenance.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

fake_bin="$fixture/bin"
trusted_root="$fixture/trusted-root.jsonl"
release="$fixture/release"
mkdir -p "$fake_bin" "$release"
printf '{}\n' > "$trusted_root"

cat > "$fake_bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\0' "$@" >> "$GH_ARGUMENT_LOG"
if [ -f "$GH_FAIL_MARKER" ]; then exit 1; fi
EOF
chmod +x "$fake_bin/gh"

expected_sha=0123456789abcdef0123456789abcdef01234567
printf 'jar\n' > "$release/app.jar"
printf 'commit_sha=%s\nrun_id=100\nrun_number=20\nrun_attempt=1\napi_contract_revision=test-v1\n' \
  "$expected_sha" > "$release/RELEASE_METADATA"
printf '{}\n' > "$release/sbom.spdx.json"
printf '{}\n' > "$release/PROVENANCE_BUNDLE.jsonl"
(cd "$release" && {
  sha256sum app.jar RELEASE_METADATA sbom.spdx.json > SHA256SUMS
  printf 'release_type=backend\ncommit_sha=%s\n' "$expected_sha" > RELEASE_ENVELOPE
  sha256sum app.jar RELEASE_METADATA sbom.spdx.json SHA256SUMS >> RELEASE_ENVELOPE
})

invoke() {
  ALLOW_NON_ROOT_TEST=true \
  TRUSTED_ROOT_FILE="$trusted_root" \
  GH_ARGUMENT_LOG="$fixture/gh-arguments" \
  GH_FAIL_MARKER="$fixture/gh-fail" \
  PATH="$fake_bin:$PATH" \
  bash "$script" "$release" "$expected_sha" backend
}

invoke
test "$(tr '\0' '\n' < "$fixture/gh-arguments" | grep -c '^--source-digest$')" -eq 2
test "$(tr '\0' '\n' < "$fixture/gh-arguments" | grep -c '^refs/heads/main$')" -eq 2
test "$(tr '\0' '\n' < "$fixture/gh-arguments" | grep -Fxc "$release/RELEASE_ENVELOPE")" -eq 1
test "$(tr '\0' '\n' < "$fixture/gh-arguments" | grep -Fxc "$release/app.jar")" -eq 1

touch "$release/unexpected"
if invoke; then
  echo "Expected unexpected release content to fail" >&2
  exit 1
fi
rm "$release/unexpected"

printf 'tampered\n' >> "$release/app.jar"
if invoke; then
  echo "Expected a checksum mismatch to fail" >&2
  exit 1
fi
printf 'jar\n' > "$release/app.jar"
(cd "$release" && {
  sha256sum app.jar RELEASE_METADATA sbom.spdx.json > SHA256SUMS
  printf 'release_type=backend\ncommit_sha=%s\n' "$expected_sha" > RELEASE_ENVELOPE
  sha256sum app.jar RELEASE_METADATA sbom.spdx.json SHA256SUMS >> RELEASE_ENVELOPE
})

printf 'tampered\n' >> "$release/sbom.spdx.json"
if invoke; then
  echo "Expected a replaced SBOM to fail the signed release envelope" >&2
  exit 1
fi
printf '{}\n' > "$release/sbom.spdx.json"
(cd "$release" && sha256sum sbom.spdx.json app.jar RELEASE_METADATA > SHA256SUMS)
if invoke; then
  echo "Expected a regenerated local checksum without a new signed envelope to fail" >&2
  exit 1
fi
(cd "$release" && {
  printf 'release_type=backend\ncommit_sha=%s\n' "$expected_sha" > RELEASE_ENVELOPE
  sha256sum app.jar RELEASE_METADATA sbom.spdx.json SHA256SUMS >> RELEASE_ENVELOPE
})

touch "$fixture/gh-fail"
if invoke; then
  echo "Expected an invalid attestation to fail" >&2
  exit 1
fi

grep -Fq 'subject-path: backend/release/RELEASE_ENVELOPE' "$project_root/.github/workflows/ci.yml"
grep -Fq 'subject-path: backend/release/app.jar' "$project_root/.github/workflows/ci.yml"
grep -Fq 'subject-path: frontend/RELEASE_ENVELOPE' "$project_root/.github/workflows/ci.yml"
grep -Fq 'subject-path: frontend/frontend-release.tar.gz' "$project_root/.github/workflows/ci.yml"
grep -Fq 'release-artifact/RELEASE_ENVELOPE' "$project_root/.github/workflows/deploy-backend.yml"
grep -Fq 'release-artifact/RELEASE_ENVELOPE' "$project_root/.github/workflows/deploy-frontend.yml"
grep -Eq '^ +source: .*release-artifact/RELEASE_ENVELOPE' "$project_root/.github/workflows/deploy-backend.yml"
grep -Eq '^ +source: .*release-artifact/RELEASE_ENVELOPE' "$project_root/.github/workflows/deploy-frontend.yml"

echo "Release provenance fixtures passed"
