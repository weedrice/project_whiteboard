#!/usr/bin/env bash
set -euo pipefail

archive="${1:?Grafana archive path is required}"
expected_sha256="${2:?Grafana archive SHA-256 is required}"
expected_arch="${3:?Grafana archive architecture is required}"
UNAME_BIN="${UNAME_BIN:-uname}"

fail() {
  echo "Grafana archive verification failed: $*" >&2
  exit 1
}

kernel_name="$($UNAME_BIN -s)"
machine_name="$($UNAME_BIN -m)"
case "$kernel_name-$machine_name" in
  Linux-x86_64) actual_arch=linux-amd64 ;;
  Linux-aarch64|Linux-arm64) actual_arch=linux-arm64 ;;
  *) fail "unsupported runner architecture: $kernel_name-$machine_name" ;;
esac

[ "$actual_arch" = "$expected_arch" ] \
  || fail "runner architecture $actual_arch does not match pinned archive $expected_arch"
[[ "$expected_sha256" =~ ^[0-9a-f]{64}$ ]] || fail "expected SHA-256 is invalid"
[ -f "$archive" ] && [ ! -L "$archive" ] || fail "archive must be a regular non-symlink file"

actual_sha256="$(sha256sum "$archive" | awk '{print $1}')"
[ "$actual_sha256" = "$expected_sha256" ] || fail "archive checksum mismatch"

echo "Grafana archive checksum verified for $expected_arch"
