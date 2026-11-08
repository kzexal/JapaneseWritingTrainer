#!/usr/bin/env python3
"""Generate Kanji metadata JSON from the repository's vocabulary.xlsx.

Run from the Android project directory:
    python tools/generate_kanji.py

The workbook is read-only input. Stroke geometry is intentionally not generated.
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

from openpyxl import load_workbook

PROJECT_ROOT = Path(__file__).resolve().parents[1]
WORKBOOK = PROJECT_ROOT.parent / "vocabulary.xlsx"
OUTPUT = PROJECT_ROOT / "app/src/main/assets/characters/kanji.json"
SHEET_NAME = "Kanji"
SCHEMA_VERSION = 1
EXPECTED_HEADERS = (
    "Kanji", "Onyomi", "Kunyomi", "Meaning", "Radical",
    "StrokeCount", "ExampleWords", "Notes", "JLPT", "Tags",
)
OPTIONAL_FIELDS = {
    "Onyomi": "onyomi",
    "Kunyomi": "kunyomi",
    "Meaning": "meaning",
    "Radical": "radical",
    "ExampleWords": "exampleWords",
    "Notes": "notes",
    "JLPT": "jlpt",
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


def stable_kanji_id(character: str) -> str:
    if len(character) != 1:
        raise ValueError(f"Expected one Unicode code point, got {character!r}")
    return f"kanji_{ord(character):04x}"


def normalize_stroke_count(value: Any, row_number: int) -> int:
    if isinstance(value, bool) or value is None:
        raise ValueError(f"{SHEET_NAME}!F{row_number}: StrokeCount is required")
    try:
        number = float(str(value).strip())
    except (TypeError, ValueError) as error:
        raise ValueError(
            f"{SHEET_NAME}!F{row_number}: StrokeCount must be numeric"
        ) from error
    if not math.isfinite(number) or not number.is_integer() or number <= 0:
        raise ValueError(
            f"{SHEET_NAME}!F{row_number}: StrokeCount must be a positive integer"
        )
    return int(number)


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
        seen: set[str] = set()
        for row_number, values in enumerate(sheet.iter_rows(min_row=2, values_only=True), 2):
            values = tuple(values[:len(EXPECTED_HEADERS)])
            if not any(normalize_text(value) is not None for value in values):
                continue

            character = normalize_text(values[0] if values else None)
            if character is None:
                raise ValueError(f"{SHEET_NAME}!A{row_number}: Kanji is required")
            if len(character) != 1:
                raise ValueError(
                    f"{SHEET_NAME}!A{row_number}: expected one Kanji code point, got {character!r}"
                )
            if character in seen:
                raise ValueError(f"{SHEET_NAME}!A{row_number}: duplicate Kanji {character!r}")
            seen.add(character)

            stroke_value = values[5] if len(values) > 5 else None
            record: dict[str, Any] = {
                "id": stable_kanji_id(character),
                "character": character,
                "strokeCount": normalize_stroke_count(stroke_value, row_number),
            }
            for header, field_name in OPTIONAL_FIELDS.items():
                index = EXPECTED_HEADERS.index(header)
                value = values[index] if index < len(values) else None
                record[field_name] = normalize_text(value)
            records.append(record)

        if not records:
            raise ValueError(f"{SHEET_NAME}: no Kanji records found")
        return records
    finally:
        workbook.close()


def build_dataset(records: list[dict[str, Any]]) -> dict[str, Any]:
    ids = [item["id"] for item in records]
    glyphs = [item["character"] for item in records]
    if len(ids) != len(set(ids)) or len(glyphs) != len(set(glyphs)):
        raise ValueError("Kanji IDs and characters must be unique")
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
    print(f"Kanji records: {len(records)}")
    print(f"Schema version: {SCHEMA_VERSION}")
    print("Stroke geometry: not included (metadata only)")


if __name__ == "__main__":
    main()
