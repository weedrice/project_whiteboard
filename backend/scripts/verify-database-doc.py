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
DOCUMENT_TABLE_PATTERN = re.compile(r"^\|\s*`([a-z][a-z0-9_]*)`\s*\|", re.MULTILINE)


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
    table_list_match = re.search(
        r"^## 테이블 목록\s*$([\s\S]*?)^## 주요 제약과 인덱스\s*$",
        document,
        re.MULTILINE,
    )
    failures = []
    if expected_range not in document:
        failures.append(f"migration range must end at {latest.name}")
    if expected_count not in document:
        failures.append(f"table count must be {len(tables)}")
    if table_list_match is None:
        failures.append("table list section could not be parsed")
    else:
        documented_tables = {
            name.lower() for name in DOCUMENT_TABLE_PATTERN.findall(table_list_match.group(1))
        }
        missing_tables = sorted(tables - documented_tables)
        unknown_tables = sorted(documented_tables - tables)
        if missing_tables:
            failures.append(
                "table list is missing migration-created tables: " + ", ".join(missing_tables)
            )
        if unknown_tables:
            failures.append(
                "table list contains tables not created by migrations: " + ", ".join(unknown_tables)
            )
    if failures:
        for failure in failures:
            print(f"DATABASE.md drift: {failure}", file=sys.stderr)
        return 1
    print(
        f"DATABASE.md matches {latest.name} and documents all {len(tables)} created tables"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
