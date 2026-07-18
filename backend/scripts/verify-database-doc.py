#!/usr/bin/env python3
import argparse
from pathlib import Path
import re
import sys


MIGRATION_PATTERN = re.compile(r"^V([1-9][0-9]*)__[A-Za-z0-9][A-Za-z0-9_]*\.sql$")
CREATE_TABLE_PATTERN = re.compile(
    r"\bCREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    args = parser.parse_args()
    root = (args.root or Path(__file__).resolve().parents[2]).resolve()
    migration_dir = root / "backend/src/main/resources/db/migration"
    document_path = root / "backend/DATABASE.md"

    migrations: list[tuple[int, Path]] = []
    tables: set[str] = set()
    for path in migration_dir.glob("V*.sql"):
        match = MIGRATION_PATTERN.fullmatch(path.name)
        if not match:
            print(f"Invalid versioned migration filename: {path.name}", file=sys.stderr)
            return 1
        migrations.append((int(match.group(1)), path))
        tables.update(
            name.lower()
            for name in CREATE_TABLE_PATTERN.findall(path.read_text(encoding="utf-8"))
        )
    if not migrations:
        print("No Flyway migrations found", file=sys.stderr)
        return 1
    versions = [version for version, _ in migrations]
    if len(versions) != len(set(versions)):
        print("Duplicate Flyway migration versions found", file=sys.stderr)
        return 1

    _, latest = max(migrations, key=lambda item: item[0])
    document = document_path.read_text(encoding="utf-8")
    expected_range = f"`V1__baseline_schema.sql` - `{latest.name}`"
    expected_count = f"| 현재 테이블 수 | {len(tables)}개 |"
    failures = []
    if expected_range not in document:
        failures.append(f"migration range must end at {latest.name}")
    if expected_count not in document:
        failures.append(f"table count must be {len(tables)}")
    if failures:
        for failure in failures:
            print(f"DATABASE.md drift: {failure}", file=sys.stderr)
        return 1
    print(f"DATABASE.md matches {latest.name} and {len(tables)} created tables")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
