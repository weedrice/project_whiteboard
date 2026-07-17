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
printf 'commit_sha=%s\n' "$expected_sha" > "$release/RELEASE_METADATA"
printf '{}\n' > "$release/sbom.spdx.json"
printf '{}\n' > "$release/PROVENANCE_BUNDLE.jsonl"
(cd "$release" && sha256sum app.jar RELEASE_METADATA sbom.spdx.json > SHA256SUMS)

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
(cd "$release" && sha256sum app.jar RELEASE_METADATA sbom.spdx.json > SHA256SUMS)

touch "$fixture/gh-fail"
if invoke; then
  echo "Expected an invalid attestation to fail" >&2
  exit 1
fi

echo "Release provenance fixtures passed"
