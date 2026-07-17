#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
manifest = {
    line.strip()
    for line in (ROOT / "required-backend-metrics.txt").read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.startswith("#")
}
rules = (ROOT / "prometheus" / "noviis-alerts.yml").read_text(encoding="utf-8")
declared = set(re.findall(r'missing_metric", "([a-z0-9_]+)"', rules))

if manifest != declared:
    missing = sorted(manifest - declared)
    extra = sorted(declared - manifest)
    raise SystemExit(f"required metric manifest drift: missing={missing}, extra={extra}")

print(f"Required backend metric manifest verified ({len(manifest)} metrics)")
