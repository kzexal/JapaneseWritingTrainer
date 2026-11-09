#!/usr/bin/env python3
"""Import stroke geometry for the Kanji listed in vocabulary.xlsx from KanjiVG.

Download and extract the KanjiVG release archive, then run from the Android
project directory:

    python tools/import_kanjivg_strokes.py --source-dir tools/vendor/kanjivg/kanji

The source archive is not modified. The workbook remains the source of truth
for the requested Kanji list and expected stroke counts.
"""
from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from generate_kanji import parse_workbook

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = PROJECT_ROOT / "tools/vendor/kanjivg/kanji"
DEFAULT_OUTPUT = PROJECT_ROOT / "app/src/main/assets/characters/kanji_strokes.json"
SCHEMA_VERSION = 1
KANJIVG_ATTRIBUTION = (
    "Stroke geometry derived from KanjiVG, copyright Ulrich Apel and "
    "contributors; licensed under Creative Commons Attribution-ShareAlike 3.0. "
    "https://kanjivg.tagaini.net/"
)
KANJIVG_LICENSE = "CC BY-SA 3.0"


def source_filename(character: str) -> str:
    if len(character) != 1:
        raise ValueError(f"Expected one Unicode code point, got {character!r}")
    return f"{ord(character):05x}.svg"


def parse_svg_strokes(svg_path: Path) -> tuple[float, list[str]]:
    """Read only ordered stroke paths from the KanjiVG StrokePaths group."""
    try:
        root = ET.parse(svg_path).getroot()
    except (ET.ParseError, OSError) as error:
        raise ValueError(f"Cannot parse KanjiVG SVG {svg_path}: {error}") from error

    view_box = root.attrib.get("viewBox", "").replace(",", " ").split()
    if len(view_box) != 4:
        raise ValueError(f"{svg_path}: expected a four-value SVG viewBox")
    try:
        width = float(view_box[2])
        height = float(view_box[3])
    except ValueError as error:
        raise ValueError(f"{svg_path}: invalid SVG viewBox {view_box!r}") from error
    if width <= 0 or height <= 0 or abs(width - height) > 0.001:
        raise ValueError(f"{svg_path}: expected a positive square viewBox")

    stroke_group = next(
        (
            element
            for element in root.iter()
            if element.tag.endswith("g")
            and element.attrib.get("id", "").startswith("kvg:StrokePaths_")
        ),
        None,
    )
    if stroke_group is None:
        raise ValueError(f"{svg_path}: missing KanjiVG StrokePaths group")

    strokes: list[str] = []
    path_ids: list[str] = []
    for element in stroke_group.iter():
        if element.tag.endswith("path"):
            path_data = element.attrib.get("d", "").strip()
            if not path_data:
                raise ValueError(f"{svg_path}: found a stroke without path data")
            strokes.append(path_data)
            path_ids.append(element.attrib.get("id", ""))

    if not strokes:
        raise ValueError(f"{svg_path}: no stroke paths found")

    # KanjiVG encodes stroke indices in IDs. If present, ensure the document
    # order agrees with those indices instead of silently reordering geometry.
    numbered_ids = [
        int(match.group(1))
        for path_id in path_ids
        if (match := re.search(r"-s(\d+)$", path_id))
    ]
    if numbered_ids and numbered_ids != list(range(1, len(strokes) + 1)):
        raise ValueError(
            f"{svg_path}: stroke path IDs are not continuous/in document order: "
            f"{numbered_ids}"
        )

    return width, strokes


def build_stroke_dataset(
    metadata_records: list[dict[str, Any]],
    source_dir: Path,
) -> dict[str, Any]:
    if not source_dir.is_dir():
        raise FileNotFoundError(f"KanjiVG source directory not found: {source_dir}")

    characters: list[dict[str, Any]] = []
    for record in metadata_records:
        character = record["character"]
        svg_path = source_dir / source_filename(character)
        if not svg_path.is_file():
            raise FileNotFoundError(
                f"KanjiVG SVG missing for {character} "
                f"(U+{ord(character):04X}): {svg_path}"
            )

        view_box_size, path_data = parse_svg_strokes(svg_path)
        expected_count = record["strokeCount"]
        if len(path_data) != expected_count:
            raise ValueError(
                f"{character} (U+{ord(character):04X}): workbook expects "
                f"{expected_count} strokes, KanjiVG contains {len(path_data)}. "
                "Review the character variant/count before importing."
            )

        characters.append({
            "id": record["id"],
            "character": character,
            "viewBoxSize": view_box_size,
            "strokeCount": len(path_data),
            "strokes": [
                {"order": index, "svgPath": path}
                for index, path in enumerate(path_data, start=1)
            ],
        })

    return {
        "schemaVersion": SCHEMA_VERSION,
        "source": {
            "name": "KanjiVG",
            "license": KANJIVG_LICENSE,
            "attribution": KANJIVG_ATTRIBUTION,
        },
        "characters": characters,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=DEFAULT_SOURCE_DIR,
        help="Directory containing KanjiVG per-character SVG files",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Output JSON path (default: app/src/main/assets/characters/kanji_strokes.json)",
    )
    args = parser.parse_args()

    metadata = parse_workbook()
    dataset = build_stroke_dataset(metadata, args.source_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Validated and generated: {args.output}")
    print(f"Kanji with stroke geometry: {len(dataset['characters'])}")
    print(f"Source: KanjiVG ({KANJIVG_LICENSE})")
    print("Workbook: read-only; no learner data or metadata was changed")


if __name__ == "__main__":
    main()
