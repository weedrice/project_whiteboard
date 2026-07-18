#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
import re
import sys


SCOPED_BACKEND_PANELS = {
    "Backend up",
    "JVM heap %",
    "Process CPU",
    "GC pause rate",
    "HTTP 5xx ratio",
    "HTTP p95 latency",
    "HikariCP saturation",
    "Backend process restarts",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--promql-output", type=Path)
    args = parser.parse_args()
    root = (args.root or Path(__file__).resolve().parents[2]).resolve()
    dashboard_path = root / "deploy/monitoring/grafana/dashboards/noviis-overview.json"
    try:
        dashboard = json.loads(dashboard_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        print(f"Grafana dashboard validation failed: {error}", file=sys.stderr)
        return 1

    failures: list[str] = []
    panels = dashboard.get("panels")
    if not isinstance(panels, list) or not panels:
        failures.append("panels must be a non-empty array")
        panels = []
    seen_ids: set[int] = set()
    rules: list[dict[str, str]] = []
    for panel_index, panel in enumerate(panels):
        panel_id = panel.get("id")
        title = panel.get("title")
        if not isinstance(panel_id, int) or panel_id <= 0:
            failures.append(f"panel {panel_index} has an invalid id")
        elif panel_id in seen_ids:
            failures.append(f"duplicate panel id: {panel_id}")
        else:
            seen_ids.add(panel_id)
        if not isinstance(title, str) or not title.strip():
            failures.append(f"panel {panel_id!r} has no title")
            title = f"panel-{panel_index}"
        targets = panel.get("targets")
        if not isinstance(targets, list) or not targets:
            failures.append(f"panel {title} has no targets")
            continue
        seen_refs: set[str] = set()
        for target_index, target in enumerate(targets):
            ref_id = target.get("refId")
            expression = target.get("expr")
            if not isinstance(ref_id, str) or not re.fullmatch(r"[A-Z][A-Z0-9]*", ref_id):
                failures.append(f"panel {title} target {target_index} has an invalid refId")
            elif ref_id in seen_refs:
                failures.append(f"panel {title} has duplicate refId {ref_id}")
            else:
                seen_refs.add(ref_id)
            if not isinstance(expression, str) or not expression.strip():
                failures.append(f"panel {title} target {ref_id!r} has no PromQL expression")
                continue
            if title in SCOPED_BACKEND_PANELS and 'job="noviis-backend"' not in expression:
                failures.append(f"panel {title} is not scoped to job=noviis-backend")
            rules.append({"record": f"noviis_dashboard_expr_{len(rules) + 1}", "expr": expression})

    if failures:
        for failure in failures:
            print(f"Grafana dashboard validation failed: {failure}", file=sys.stderr)
        return 1
    if args.promql_output:
        args.promql_output.write_text(
            json.dumps({"groups": [{"name": "noviis-grafana-dashboard", "rules": rules}]}, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    print(f"Grafana dashboard contract verified ({len(panels)} panels, {len(rules)} expressions)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
