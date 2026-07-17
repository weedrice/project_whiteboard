#!/usr/bin/env python3

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "backend" / "src" / "main" / "java"
MANIFEST = ROOT / "deploy" / "monitoring" / "scheduled-jobs.txt"
RULES = ROOT / "deploy" / "monitoring" / "prometheus" / "noviis-alerts.yml"


def source_jobs() -> set[str]:
    jobs: set[str] = set()
    pattern = re.compile(
        r"@Scheduled\s*\([^)]*\)\s*(?:@[A-Za-z0-9_.]+(?:\([^)]*\))?\s*)*"
        r"(?:public|protected|private)?\s*[A-Za-z0-9_<>, ?\[\].]+\s+(\w+)\s*\(",
        re.MULTILINE,
    )
    for path in JAVA_ROOT.rglob("*.java"):
        if "agent" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        class_match = re.search(r"\bclass\s+(\w+)", text)
        if not class_match:
            continue
        for match in pattern.finditer(text):
            jobs.add(f"{class_match.group(1)}.{match.group(1)}")
    return jobs


def manifest_jobs() -> dict[str, tuple[str, str]]:
    jobs: dict[str, tuple[str, str]] = {}
    for raw_line in MANIFEST.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        job, cadence, maximum_age = line.split("|", 2)
        if job in jobs:
            raise ValueError(f"duplicate scheduled job in manifest: {job}")
        if cadence not in {"frequent", "hourly", "daily"} or not maximum_age.isdigit():
            raise ValueError(f"invalid manifest entry: {line}")
        jobs[job] = (cadence, maximum_age)
    return jobs


def main() -> int:
    actual = source_jobs()
    expected = manifest_jobs()
    if actual != set(expected):
        print(f"scheduled job manifest drift: missing={sorted(actual - set(expected))}, stale={sorted(set(expected) - actual)}", file=sys.stderr)
        return 1

    rules = RULES.read_text(encoding="utf-8")
    for job, (cadence, maximum_age) in expected.items():
        escaped = re.escape(job)
        block = re.compile(
            rf"expr:\s*vector\({re.escape(maximum_age)}\).*?labels:\s*\{{[^}}]*cadence:\s*{cadence},[^}}]*scheduled_job:\s*{escaped},",
            re.DOTALL,
        )
        if not block.search(rules):
            print(f"scheduled job is missing matching freshness rule: {job}", file=sys.stderr)
            return 1
    print(f"Scheduled job manifest verified ({len(expected)} non-Agent jobs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
