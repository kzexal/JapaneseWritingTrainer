#!/usr/bin/env python3
"""Generate Vocabulary metadata JSON from the repository's vocabulary.xlsx.

Run from the Android project directory:
    py -3.13 tools/generate_vocabulary.py

The workbook is read-only input. Learner progress is stored separately and is
never generated or modified by this tool.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from openpyxl import load_workbook

PROJECT_ROOT = Path(__file__).resolve().parents[1]
WORKBOOK = PROJECT_ROOT.parent / "vocabulary.xlsx"
OUTPUT = PROJECT_ROOT / "app/src/main/assets/characters/vocabulary.json"
SHEET_NAME = "Vocabulary"
SCHEMA_VERSION = 1
EXPECTED_HEADERS = (
    "Japanese", "Reading", "Vietnamese", "Example", "ExampleMeaning",
    "Nhóm bộ thủ chính", "Radicals", "Mnemonics", "JLPT", "Notes", "Tags",
)
FIELD_NAMES = {
    "Reading": "reading",
    "Vietnamese": "vietnamese",
    "Example": "example",
    "ExampleMeaning": "exampleMeaning",
    "Nhóm bộ thủ chính": "group",
    "Radicals": "radicals",
    "Mnemonics": "mnemonics",
    "JLPT": "jlpt",
    "Notes": "notes",
    "Tags": "tags",
}
NULL_TEXT_VALUES = {"none", "null"}


def normalize_text(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    if not normalized or normalized.casefold() in NULL_TEXT_VALUES:
        return None
    return normalized


def stable_vocabulary_id(japanese: str) -> str:
    """Build an ID from every Unicode code point, independent of Excel row order."""
    normalized = normalize_text(japanese)
    if normalized is None:
        raise ValueError("Japanese text is required for a stable vocabulary ID")
    codepoints = "_".join(f"{ord(character):04x}" for character in normalized)
    return f"vocab_{codepoints}"


def parse_workbook(workbook_path: Path = WORKBOOK) -> list[dict[str, Any]]:
    if not workbook_path.is_file():
        raise FileNotFoundError(f"Workbook not found: {workbook_path}")

    workbook = load_workbook(workbook_path, read_only=True, data_only=True)
    try:
        if SHEET_NAME not in workbook.sheetnames:
            raise ValueError(f"Workbook is missing sheet {SHEET_NAME!r}")
        sheet = workbook[SHEET_NAME]
        header_row = next(sheet.iter_rows(min_row=1, max_row=1, values_only=True), ())
        headers = tuple(normalize_text(value) for value in header_row)
        if headers != EXPECTED_HEADERS:
            raise ValueError(
                f"{SHEET_NAME}!1: unexpected headers; expected {EXPECTED_HEADERS}, got {headers}"
            )

        records: list[dict[str, Any]] = []
        seen_japanese: set[str] = set()
        seen_ids: set[str] = set()
        for row_number, values in enumerate(sheet.iter_rows(min_row=2, values_only=True), 2):
            values = tuple(values[:len(EXPECTED_HEADERS)])
            if not any(normalize_text(value) is not None for value in values):
                continue

            japanese = normalize_text(values[0] if values else None)
            if japanese is None:
                raise ValueError(f"{SHEET_NAME}!A{row_number}: Japanese is required")
            if japanese in seen_japanese:
                raise ValueError(
                    f"{SHEET_NAME}!A{row_number}: duplicate Japanese entry {japanese!r}"
                )
            seen_japanese.add(japanese)

            reading = normalize_text(values[1] if len(values) > 1 else None)
            vietnamese = normalize_text(values[2] if len(values) > 2 else None)
            if reading is None:
                raise ValueError(f"{SHEET_NAME}!B{row_number}: Reading is required")
            if vietnamese is None:
                raise ValueError(f"{SHEET_NAME}!C{row_number}: Vietnamese is required")

            record: dict[str, Any] = {
                "id": stable_vocabulary_id(japanese),
                "japanese": japanese,
            }
            if record["id"] in seen_ids:
                raise ValueError(f"{SHEET_NAME}!A{row_number}: generated ID collision")
            seen_ids.add(record["id"])

            for header, field_name in FIELD_NAMES.items():
                index = EXPECTED_HEADERS.index(header)
                value = values[index] if index < len(values) else None
                record[field_name] = normalize_text(value)
            record["reading"] = reading
            record["vietnamese"] = vietnamese
            records.append(record)

        if not records:
            raise ValueError(f"{SHEET_NAME}: no Vocabulary records found")
        return records
    finally:
        workbook.close()


def build_dataset(records: list[dict[str, Any]]) -> dict[str, Any]:
    ids = [item["id"] for item in records]
    japanese = [item["japanese"] for item in records]
    if len(ids) != len(set(ids)) or len(japanese) != len(set(japanese)):
        raise ValueError("Vocabulary IDs and Japanese entries must be unique")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "source": {"workbook": "vocabulary.xlsx", "sheet": SHEET_NAME},
        "records": records,
    }


def main() -> None:
    records = parse_workbook()
    dataset = build_dataset(records)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Validated and generated: {OUTPUT}")
    print(f"Vocabulary records: {len(records)}")
    print(f"Schema version: {SCHEMA_VERSION}")
    print("Learner progress: unchanged (stored separately)")


if __name__ == "__main__":
    main()
