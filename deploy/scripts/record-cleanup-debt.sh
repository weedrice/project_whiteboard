#!/usr/bin/env bash
set -euo pipefail

TEXTFILE_DIR="${TEXTFILE_DIR:-/var/lib/prometheus/node-exporter}"
[ "$#" -eq 3 ] || { echo "usage: $0 backend|frontend set|clear|reconcile DEBT" >&2; exit 64; }
component="$1"
action="$2"
debt="$3"

case "$component" in backend|frontend) ;; *) echo "invalid component" >&2; exit 64 ;; esac
case "$action" in set|clear|reconcile) ;; *) echo "invalid action" >&2; exit 64 ;; esac
case "$debt" in release_retention|incoming_release|status_diagnostic|seo_submission) ;; *) echo "invalid debt type" >&2; exit 64 ;; esac
test_mode="${NOVIIS_CLEANUP_TEST_MODE:-false}"
if [ "$EUID" -ne 0 ] && [ "$test_mode" != true ]; then
  echo "cleanup debt writer must run as root" >&2
  exit 1
fi
[ "$action" != reconcile ] || [ "$debt" = incoming_release ] || {
  echo "reconcile is supported only for incoming_release" >&2
  exit 64
}

if [ "$test_mode" = true ]; then
  install -d -m 0755 "$TEXTFILE_DIR"
else
  install -d -o root -g root -m 0755 "$TEXTFILE_DIR"
fi
destination="$TEXTFILE_DIR/noviis_deployment_cleanup_${component}_${debt}.prom"
temporary="$(mktemp "$TEXTFILE_DIR/.noviis-cleanup.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
value=0
[ "$action" = set ] && value=1
orphan_count=0
oldest_age_seconds=0
if [ "$action" = reconcile ]; then
  if [ "$test_mode" = true ]; then
    incoming_root="${NOVIIS_CLEANUP_TEST_INCOMING_ROOT:?test incoming root is required}"
    install -d -m 0755 "$incoming_root"
  else
    case "$component" in
      backend) incoming_root="/opt/app/backend/incoming" ;;
      frontend) incoming_root="/var/www/incoming/frontend" ;;
    esac
    install -d -o root -g root -m 0755 "$incoming_root"
  fi
  incoming_root_real="$(realpath "$incoming_root")"
  if [ "$test_mode" != true ]; then
    case "$component:$incoming_root_real" in
      backend:/opt/app/backend/incoming|frontend:/var/www/incoming/frontend) ;;
      *) echo "incoming root escaped its approved path" >&2; exit 1 ;;
    esac
  fi
  now_epoch="$(date +%s)"
  while IFS= read -r -d '' orphan; do
    orphan_real="$(realpath -m -- "$orphan")"
    case "$orphan_real" in "$incoming_root_real"/*) ;; *) echo "orphan escaped incoming root" >&2; exit 1 ;; esac
    orphan_count=$((orphan_count + 1))
    modified_epoch="$(stat -c %Y "$orphan_real")"
    age=$((now_epoch - modified_epoch))
    [ "$age" -ge 0 ] || age=0
    [ "$age" -le "$oldest_age_seconds" ] || oldest_age_seconds="$age"
  done < <(find "$incoming_root_real" -mindepth 1 -maxdepth 1 -print0)
  [ "$orphan_count" -eq 0 ] || value=1
fi
printf '# HELP noviis_deployment_cleanup_debt Deployment follow-up debt requiring operator action.\n' > "$temporary"
printf '# TYPE noviis_deployment_cleanup_debt gauge\n' >> "$temporary"
printf 'noviis_deployment_cleanup_debt{component="%s",debt="%s"} %s\n' "$component" "$debt" "$value" >> "$temporary"
printf '# HELP noviis_deployment_cleanup_writer_success_timestamp_seconds Last successful cleanup debt metric write.\n' >> "$temporary"
printf '# TYPE noviis_deployment_cleanup_writer_success_timestamp_seconds gauge\n' >> "$temporary"
printf 'noviis_deployment_cleanup_writer_success_timestamp_seconds{component="%s",debt="%s"} %s\n' "$component" "$debt" "$(date +%s)" >> "$temporary"
if [ "$action" = reconcile ]; then
  printf '# HELP noviis_deployment_incoming_orphans Incoming release directories awaiting cleanup.\n' >> "$temporary"
  printf '# TYPE noviis_deployment_incoming_orphans gauge\n' >> "$temporary"
  printf 'noviis_deployment_incoming_orphans{component="%s"} %s\n' "$component" "$orphan_count" >> "$temporary"
  printf '# HELP noviis_deployment_incoming_orphan_oldest_age_seconds Age of the oldest incoming release directory.\n' >> "$temporary"
  printf '# TYPE noviis_deployment_incoming_orphan_oldest_age_seconds gauge\n' >> "$temporary"
  printf 'noviis_deployment_incoming_orphan_oldest_age_seconds{component="%s"} %s\n' "$component" "$oldest_age_seconds" >> "$temporary"
fi
chmod 0644 "$temporary"
mv -Tf "$temporary" "$destination"
trap - EXIT
[ "$action" != reconcile ] || [ "$orphan_count" -eq 0 ] || exit 2
