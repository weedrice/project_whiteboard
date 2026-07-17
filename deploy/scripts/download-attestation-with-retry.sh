#!/usr/bin/env bash
set -euo pipefail

[ "$#" -eq 3 ] || { echo "usage: $0 SUBJECT REPOSITORY OUTPUT_DIRECTORY" >&2; exit 64; }
subject="$1"
repository="$2"
output_directory="$3"
max_attempts="${ATTESTATION_DOWNLOAD_MAX_ATTEMPTS:-5}"
base_delay_seconds="${ATTESTATION_DOWNLOAD_BASE_DELAY_SECONDS:-1}"

[[ "$max_attempts" =~ ^[1-9][0-9]*$ ]] || { echo "invalid attestation attempt limit" >&2; exit 64; }
[[ "$base_delay_seconds" =~ ^[0-9]+$ ]] || { echo "invalid attestation retry delay" >&2; exit 64; }
case "$repository" in */*) ;; *) echo "invalid repository" >&2; exit 64 ;; esac
[ -f "$subject" ] || { echo "attestation subject is missing" >&2; exit 1; }

subject="$(realpath "$subject")"
mkdir -p "$output_directory"
output_directory="$(realpath "$output_directory")"

for ((attempt=1; attempt<=max_attempts; attempt++)); do
  find "$output_directory" -mindepth 1 -maxdepth 1 -type f -delete
  if (cd "$output_directory" && gh attestation download "$subject" --repo "$repository"); then
    echo "Attestation bundle downloaded on attempt $attempt"
    exit 0
  fi
  if [ "$attempt" -eq "$max_attempts" ]; then
    echo "Attestation bundle download exhausted $max_attempts attempts" >&2
    exit 1
  fi
  delay=$((base_delay_seconds * (1 << (attempt - 1))))
  [ "$delay" -le 16 ] || delay=16
  if [ "$base_delay_seconds" -gt 0 ]; then
    delay=$((delay + RANDOM % 3))
    sleep "$delay"
  fi
done
