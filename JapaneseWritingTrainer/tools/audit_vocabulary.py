#!/usr/bin/env python3
"""Read-only inventory of the Vocabulary sheet in vocabulary.xlsx.

Run from the Android project directory:
    py -3.13 tools/audit_vocabulary.py
    py -3.13 tools/audit_vocabulary.py --sample-rows 10

This tool never modifies the workbook or application assets.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from openpyxl import load_workbook

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WORKBOOK = PROJECT_ROOT.parent / "vocabulary.xlsx"
DEFAULT_SHEET = "Vocabulary"


def normalize(value: Any) -> str | None:
    if value is None:
        return None
    result = " ".join(str(value).split())
    return result or None


def display_value(value: Any, limit: int = 100) -> str:
    text = normalize(value)
    if text is None:
        return "<empty>"
    return text if len(text) <= limit else text[: limit - 1] + "…"


def audit_sheet(workbook_path: Path, sheet_name: str, sample_rows: int) -> None:
    if not workbook_path.is_file():
        raise FileNotFoundError(f"Workbook not found: {workbook_path}")

    workbook = load_workbook(workbook_path, read_only=True, data_only=True)
    try:
        print(f"Workbook: {workbook_path}")
        print(f"Sheets: {', '.join(workbook.sheetnames)}")
        if sheet_name not in workbook.sheetnames:
            raise ValueError(
                f"Sheet {sheet_name!r} not found. Available sheets: {workbook.sheetnames}"
            )

        sheet = workbook[sheet_name]
        rows = sheet.iter_rows(values_only=True)
        header_values = next(rows, ())
        headers = [normalize(value) or "" for value in header_values]
        if not any(headers):
            raise ValueError(f"{sheet_name!r} has no header row")
        if len(headers) != len(set(headers)):
            raise ValueError(f"{sheet_name!r} contains duplicate column headers")

        column_values: list[list[str]] = [[] for _ in headers]
        samples: list[tuple[int, tuple[Any, ...]]] = []
        duplicate_rows: dict[str, dict[str, list[int]]] = {
            name: defaultdict(list) for name in ("Reading", "Example", "ExampleMeaning")
        }
        group_counts: Counter[str] = Counter()
        non_empty_rows = 0

        for row_number, row in enumerate(rows, start=2):
            values = tuple(row[: len(headers)])
            if not any(normalize(value) is not None for value in values):
                continue
            non_empty_rows += 1
            if len(samples) < sample_rows:
                samples.append((row_number, values))

            row_by_header = {
                header: values[index] if index < len(values) else None
                for index, header in enumerate(headers)
            }
            for index, header in enumerate(headers):
                value = normalize(values[index] if index < len(values) else None)
                if value is not None:
                    column_values[index].append(value)

            for header, occurrences in duplicate_rows.items():
                value = normalize(row_by_header.get(header))
                if value is not None:
                    occurrences[value].append(row_number)

            group = normalize(row_by_header.get("Nhóm bộ thủ chính"))
            if group is not None:
                group_counts[group] += 1

        print(f"\nSheet: {sheet_name}")
        print(f"Header columns: {len(headers)}")
        print(f"Non-empty data rows: {non_empty_rows}")
        print("\nColumn inventory:")
        for index, header in enumerate(headers, start=1):
            values = column_values[index - 1]
            counts = Counter(value.casefold() for value in values)
            duplicate_excess = sum(count - 1 for count in counts.values() if count > 1)
            print(
                f"  {index:>2}. {header}: populated={len(values)}, "
                f"distinct={len(counts)}, duplicate_values={duplicate_excess}"
            )

        print("\nRepeated values (row numbers included):")
        for header, occurrences in duplicate_rows.items():
            repeated = [
                (value, row_numbers)
                for value, row_numbers in occurrences.items()
                if len(row_numbers) > 1
            ]
            print(f"  {header}:")
            if not repeated:
                print("    none")
            for value, row_numbers in sorted(repeated, key=lambda item: item[1][0]):
                print(f"    {display_value(value)} -> rows {', '.join(map(str, row_numbers))}")

        print("\nMain radical group counts:")
        if group_counts:
            for group, count in group_counts.most_common():
                print(f"  {group}: {count}")
        else:
            print("  no values")

        print("\nSample rows:")
        for row_number, values in samples:
            cells = []
            for index, header in enumerate(headers):
                value = values[index] if index < len(values) else None
                cells.append(f"{header}={display_value(value)}")
            print(f"  Row {row_number}: " + " | ".join(cells))
    finally:
        workbook.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", type=Path, default=DEFAULT_WORKBOOK)
    parser.add_argument("--sheet", default=DEFAULT_SHEET)
    parser.add_argument("--sample-rows", type=int, default=5)
    args = parser.parse_args()
    if args.sample_rows < 0:
        parser.error("--sample-rows must be zero or greater")
    audit_sheet(args.workbook, args.sheet, args.sample_rows)


if __name__ == "__main__":
    main()
