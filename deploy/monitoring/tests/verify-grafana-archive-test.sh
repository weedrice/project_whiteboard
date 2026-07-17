#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$project_root/deploy/monitoring/verify-grafana-archive.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

archive="$fixture/grafana.tar.gz"
cat > "$fixture/uname" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  -s) printf 'Linux\n' ;;
  -m) printf 'x86_64\n' ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$fixture/uname"
printf 'trusted archive bytes\n' > "$archive"
checksum="$(sha256sum "$archive" | awk '{print $1}')"

UNAME_BIN="$fixture/uname" bash "$script" "$archive" "$checksum" linux-amd64

printf 'tampered\n' >> "$archive"
if UNAME_BIN="$fixture/uname" bash "$script" "$archive" "$checksum" linux-amd64; then
  echo "Expected a tampered Grafana archive to be rejected" >&2
  exit 1
fi

if UNAME_BIN="$fixture/uname" bash "$script" "$archive" "$(sha256sum "$archive" | awk '{print $1}')" linux-arm64; then
  echo "Expected an archive for another architecture to be rejected" >&2
  exit 1
fi

echo "Grafana archive verification fixtures passed"
