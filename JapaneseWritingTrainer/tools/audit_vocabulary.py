#!/usr/bin/env python3
"""Read-only inventory of the Vocabulary sheet in vocabulary.xlsx.

Run from the Android project directory:
    python tools/audit_vocabulary.py
    python tools/audit_vocabulary.py --sample-rows 10

This tool never modifies the workbook or application assets.
"""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
from typing import Any

from openpyxl import load_workbook

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WORKBOOK = PROJECT_ROOT.parent / "vocabulary.xlsx"
DEFAULT_SHEET = "Vocabulary"


def display_value(value: Any, limit: int = 100) -> str:
    if value is None:
        return "<empty>"
    text = " ".join(str(value).split())
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
        headers = [display_value(value) for value in header_values]
        if not any(value != "<empty>" for value in headers):
            raise ValueError(f"{sheet_name!r} has no header row")

        column_values: list[list[Any]] = [[] for _ in headers]
        non_empty_rows = 0
        samples: list[tuple[int, tuple[Any, ...]]] = []
        for row_number, row in enumerate(rows, start=2):
            values = tuple(row[: len(headers)])
            if not any(value is not None and str(value).strip() for value in values):
                continue
            non_empty_rows += 1
            if len(samples) < sample_rows:
                samples.append((row_number, values))
            for index in range(len(headers)):
                value = values[index] if index < len(values) else None
                if value is not None and str(value).strip():
                    column_values[index].append(value)

        print(f"\nSheet: {sheet_name}")
        print(f"Header columns: {len(headers)}")
        print(f"Non-empty data rows: {non_empty_rows}")
        print("\nColumn inventory:")
        for index, header in enumerate(headers, start=1):
            values = column_values[index - 1]
            normalized = [str(value).strip().casefold() for value in values]
            duplicates = sum(count - 1 for count in Counter(normalized).values() if count > 1)
            print(
                f"  {index:>2}. {header}: populated={len(values)}, "
                f"distinct={len(set(normalized))}, duplicate_values={duplicates}"
            )

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
    parser.add_argument(
        "--workbook",
        type=Path,
        default=DEFAULT_WORKBOOK,
        help=f"Workbook path (default: {DEFAULT_WORKBOOK})",
    )
    parser.add_argument("--sheet", default=DEFAULT_SHEET)
    parser.add_argument("--sample-rows", type=int, default=5)
    args = parser.parse_args()
    if args.sample_rows < 0:
        parser.error("--sample-rows must be zero or greater")
    audit_sheet(args.workbook, args.sheet, args.sample_rows)


if __name__ == "__main__":
    main()
