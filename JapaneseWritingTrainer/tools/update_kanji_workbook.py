#!/usr/bin/env python3
"""Append the repository Kanji dataset to vocabulary.xlsx without changing other sheets.

Run from the repository root:
    python JapaneseWritingTrainer/tools/update_kanji_workbook.py
Or choose paths explicitly:
    python JapaneseWritingTrainer/tools/update_kanji_workbook.py --workbook vocabulary.xlsx --output vocabulary_updated.xlsx
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from openpyxl import load_workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_WORKBOOK = REPO_ROOT / "vocabulary.xlsx"
DEFAULT_METADATA = REPO_ROOT / "JapaneseWritingTrainer/app/src/main/assets/characters/kanji.json"
HEADERS = (
    "Kanji", "Onyomi", "Kunyomi", "Meaning", "Radical",
    "StrokeCount", "ExampleWords", "Notes", "JLPT", "Tags",
)
SOURCE_ROWS = (
    ("Kanji metadata", "OpenJLPT", "CC BY-SA 4.0", "https://github.com/evanclan/OpenJLPT",
     "N4/N3/N2 level assignments and readings/meanings; derived from KANJIDIC2 and Jonathan Waller's community lists."),
    ("JLPT level note", "OpenJLPT / Jonathan Waller", "Unofficial community classification",
     "https://www.tanos.co.uk/jlpt/", "JLPT does not publish official post-2010 kanji lists; treat levels as study-list approximations."),
    ("Stroke order", "KanjiVG / Ulrich Apel and contributors", "CC BY-SA 3.0",
     "https://kanjivg.tagaini.net/", "Stroke paths are derived from KanjiVG and distributed via kanji-db-data."),
    ("Stroke data distribution", "kanji-db-data", "CC BY-SA 4.0",
     "https://github.com/uball/kanji-db-data", "Inline SVG stroke paths from kits/anki-joyo-kanji.tsv."),
)

def clean(value: Any) -> Any:
    return None if value is None else value

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", type=Path, default=DEFAULT_WORKBOOK)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    parser.add_argument("--output", type=Path, default=None,
                        help="Output workbook. Defaults to <workbook_stem>_updated.xlsx.")
    args = parser.parse_args()

    if not args.workbook.is_file():
        raise FileNotFoundError(f"Workbook not found: {args.workbook}")
    if not args.metadata.is_file():
        raise FileNotFoundError(f"Kanji metadata not found: {args.metadata}")

    dataset = json.loads(args.metadata.read_text(encoding="utf-8"))
    records = dataset.get("records", [])
    if not records:
        raise ValueError("Kanji metadata contains no records")

    workbook = load_workbook(args.workbook)
    if "Kanji" not in workbook.sheetnames:
        raise ValueError("Workbook is missing the Kanji sheet")
    sheet = workbook["Kanji"]

    actual_headers = tuple(sheet.cell(1, col).value for col in range(1, len(HEADERS) + 1))
    if actual_headers != HEADERS:
        raise ValueError(f"Unexpected Kanji headers: {actual_headers!r}")

    existing = {
        str(sheet.cell(row, 1).value).strip()
        for row in range(2, sheet.max_row + 1)
        if sheet.cell(row, 1).value is not None
    }
    added = 0
    for item in records:
        character = str(item["character"])
        if character in existing:
            continue
        values = (
            character, clean(item.get("onyomi")), clean(item.get("kunyomi")),
            clean(item.get("meaning")), clean(item.get("radical")),
            int(item["strokeCount"]), clean(item.get("exampleWords")),
            clean(item.get("notes")), clean(item.get("jlpt")), clean(item.get("tags")),
        )
        sheet.append(values)
        existing.add(character)
        added += 1

    if "Sources" in workbook.sheetnames:
        sources = workbook["Sources"]
        sources.delete_rows(1, sources.max_row)
    else:
        sources = workbook.create_sheet("Sources")
    source_headers = ("Data", "Provider", "License / status", "URL", "Notes")
    sources.append(source_headers)
    for row in SOURCE_ROWS:
        sources.append(row)
    for cell in sources[1]:
        cell.font = Font(bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor="6667AB")
        cell.alignment = Alignment(vertical="center", wrap_text=True)
    widths = (24, 34, 34, 48, 100)
    for index, width in enumerate(widths, start=1):
        sources.column_dimensions[get_column_letter(index)].width = width
    sources.freeze_panes = "A2"
    sources.auto_filter.ref = sources.dimensions
    for row in sources.iter_rows(min_row=2):
        for cell in row:
            cell.alignment = Alignment(vertical="top", wrap_text=True)

    output = args.output or args.workbook.with_name(f"{args.workbook.stem}_updated{args.workbook.suffix}")
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output)
    print(f"Kanji dataset records: {len(records)}")
    print(f"New rows appended: {added}")
    print(f"Existing Kanji rows preserved: {len(existing) - added}")
    print(f"Other workbook sheets preserved: {', '.join(name for name in workbook.sheetnames if name not in ('Kanji', 'Sources'))}")
    print(f"Updated workbook: {output}")

if __name__ == "__main__":
    main()
