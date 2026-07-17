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
readme = (ROOT / "README.md").read_text(encoding="utf-8")
readme_match = re.search(
    r"<!-- required-backend-metrics:start -->\s*```text\s*(.*?)\s*```\s*<!-- required-backend-metrics:end -->",
    readme,
    re.DOTALL,
)
if readme_match is None:
    raise SystemExit("required metric README contract block is missing")
documented = {line.strip() for line in readme_match.group(1).splitlines() if line.strip()}

if manifest != declared:
    missing = sorted(manifest - declared)
    extra = sorted(declared - manifest)
    raise SystemExit(f"required metric manifest drift: missing={missing}, extra={extra}")
if manifest != documented:
    missing = sorted(manifest - documented)
    extra = sorted(documented - manifest)
    raise SystemExit(f"required metric README drift: missing={missing}, extra={extra}")

print(f"Required backend metric manifest verified ({len(manifest)} metrics)")
