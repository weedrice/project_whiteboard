#!/usr/bin/env bash
set -euo pipefail

TEXTFILE_DIR="${TEXTFILE_DIR:-/var/lib/prometheus/node-exporter}"
[ "$#" -eq 3 ] || { echo "usage: $0 backend|frontend set|clear DEBT" >&2; exit 64; }
component="$1"
action="$2"
debt="$3"

case "$component" in backend|frontend) ;; *) echo "invalid component" >&2; exit 64 ;; esac
case "$action" in set|clear) ;; *) echo "invalid action" >&2; exit 64 ;; esac
case "$debt" in release_retention|incoming_release|status_diagnostic|seo_submission) ;; *) echo "invalid debt type" >&2; exit 64 ;; esac
[ "$EUID" -eq 0 ] || { echo "cleanup debt writer must run as root" >&2; exit 1; }

install -d -o root -g root -m 0755 "$TEXTFILE_DIR"
destination="$TEXTFILE_DIR/noviis_deployment_cleanup_${component}_${debt}.prom"
temporary="$(mktemp "$TEXTFILE_DIR/.noviis-cleanup.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
value=0
[ "$action" = set ] && value=1
printf '# HELP noviis_deployment_cleanup_debt Deployment follow-up debt requiring operator action.\n' > "$temporary"
printf '# TYPE noviis_deployment_cleanup_debt gauge\n' >> "$temporary"
printf 'noviis_deployment_cleanup_debt{component="%s",debt="%s"} %s\n' "$component" "$debt" "$value" >> "$temporary"
chmod 0644 "$temporary"
mv -Tf "$temporary" "$destination"
trap - EXIT
