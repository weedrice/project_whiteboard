#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
import sys


def parse_unit(path: Path) -> dict[str, dict[str, list[str]]]:
    sections: dict[str, dict[str, list[str]]] = {}
    section: str | None = None
    for number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith(("#", ";")):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            sections.setdefault(section, {})
            continue
        if section is None or "=" not in raw_line:
            raise ValueError(f"{path}:{number}: invalid unit line")
        key, value = raw_line.split("=", 1)
        key = key.strip()
        if not key:
            raise ValueError(f"{path}:{number}: empty directive")
        sections[section].setdefault(key, []).append(value.strip())
    return sections


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    args = parser.parse_args()
    root = (args.root or Path(__file__).resolve().parents[2]).resolve()
    manifest_path = root / "deploy/systemd/hardening-contract.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    failures: list[str] = []
    for unit in manifest.get("units", []):
        relative = unit["path"]
        path = root / relative
        if not path.is_file() or path.is_symlink():
            failures.append(f"{relative}: must be a regular non-symlink file")
            continue
        try:
            sections = parse_unit(path)
        except (OSError, UnicodeError, ValueError) as error:
            failures.append(str(error))
            continue
        section_name = unit["section"]
        directives = sections.get(section_name, {})
        for key, expected in unit.get("required", {}).items():
            actual = directives.get(key)
            if actual != expected:
                failures.append(f"{relative} [{section_name}] {key}: expected {expected!r}, received {actual!r}")
    if failures:
        for failure in failures:
            print(f"systemd hardening contract violation: {failure}", file=sys.stderr)
        return 1
    print("systemd hardening contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
