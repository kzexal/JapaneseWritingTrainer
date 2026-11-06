#!/usr/bin/env python3
"""Generate and validate the 46 basic Hiragana stroke dataset from KanjiVG.

Run from any working directory:
    python tools/generate_hiragana.py

KanjiVG is copyright Ulrich Apel and licensed under CC BY-SA 3.0.
The generated dataset is a derivative dataset and must retain attribution
and comply with the same ShareAlike license.
"""

import json
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji"
OUTPUT = ROOT / "app/src/main/assets/characters/hiragana.json"

# (characters, readings, group)
ROWS = [
    ("あいうえお", ["a", "i", "u", "e", "o"], "a"),
    ("かきくけこ", ["ka", "ki", "ku", "ke", "ko"], "ka"),
    ("さしすせそ", ["sa", "shi", "su", "se", "so"], "sa"),
    ("たちつてと", ["ta", "chi", "tsu", "te", "to"], "ta"),
    ("なにぬねの", ["na", "ni", "nu", "ne", "no"], "na"),
    ("はひふへほ", ["ha", "hi", "fu", "he", "ho"], "ha"),
    ("まみむめも", ["ma", "mi", "mu", "me", "mo"], "ma"),
    ("やゆよ", ["ya", "yu", "yo"], "ya"),
    ("らりるれろ", ["ra", "ri", "ru", "re", "ro"], "ra"),
    ("わをん", ["wa", "wo", "n"], "wa"),
]


def expected_characters():
    return {
        (character, reading, group)
        for characters, readings, group in ROWS
        for character, reading in zip(characters, readings)
    }


def svg_codepoint_filename(character: str) -> str:
    return f"{ord(character):05x}.svg"


def extract_paths(svg_bytes: bytes) -> list[str]:
    root = ET.fromstring(svg_bytes)
    stroke_group = next(
        (
            element
            for element in root.iter()
            if element.attrib.get("id", "").startswith("kvg:StrokePaths_")
        ),
        None,
    )
    if stroke_group is None:
        raise ValueError("Không tìm thấy group kvg:StrokePaths trong SVG")

    paths = [
        element.attrib["d"].strip()
        for element in stroke_group.iter()
        if element.tag.rsplit("}", 1)[-1] == "path"
        and element.attrib.get("d", "").strip()
    ]
    if not paths:
        raise ValueError("SVG không có path nét chữ")
    return paths


def download_svg(character: str) -> bytes:
    filename = svg_codepoint_filename(character)
    url = f"{BASE_URL}/{filename}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "JapaneseWritingTrainer-dataset-generator"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def validate_dataset(dataset: list[dict]) -> None:
    expected = expected_characters()
    actual = {
        (item.get("character"), item.get("reading"), item.get("group"))
        for item in dataset
    }
    if len(dataset) != 46:
        raise ValueError(f"Expected 46 characters, got {len(dataset)}")
    if actual != expected:
        missing = expected - actual
        unexpected = actual - expected
        raise ValueError(
            f"Dataset character set mismatch. Missing={missing}; unexpected={unexpected}"
        )

    ids = [item.get("id") for item in dataset]
    if len(ids) != len(set(ids)):
        raise ValueError("Character IDs must be unique")

    for item in dataset:
        character = item["character"]
        strokes = item.get("strokes")
        if not item.get("id") or not item.get("reading") or not item.get("group"):
            raise ValueError(f"Missing metadata for {character}")
        if item.get("type") != "hiragana":
            raise ValueError(f"Invalid type for {character}")
        if not isinstance(item.get("viewBoxSize"), (int, float)) or item["viewBoxSize"] <= 0:
            raise ValueError(f"Invalid viewBoxSize for {character}")
        if not isinstance(strokes, list) or not strokes:
            raise ValueError(f"No stroke data for {character}")
        if item.get("strokeCount") != len(strokes):
            raise ValueError(f"strokeCount mismatch for {character}")

        for expected_order, stroke in enumerate(strokes, start=1):
            if stroke.get("order") != expected_order:
                raise ValueError(
                    f"Stroke order for {character} must be continuous from 1"
                )
            path = stroke.get("svgPath", "")
            if not isinstance(path, str) or not path.strip():
                raise ValueError(f"Empty SVG path in {character}, stroke {expected_order}")
            if "M" not in path.upper():
                raise ValueError(f"SVG path has no move command in {character}")


def main():
    dataset = []
    for characters, readings, group in ROWS:
        for character, reading in zip(characters, readings):
            print(f"Downloading {character} ({reading})...")
            paths = extract_paths(download_svg(character))
            dataset.append(
                {
                    "id": f"hiragana_{reading}",
                    "character": character,
                    "type": "hiragana",
                    "group": group,
                    "reading": reading,
                    "strokeCount": len(paths),
                    "viewBoxSize": 109.0,
                    "strokes": [
                        {"order": index + 1, "svgPath": path}
                        for index, path in enumerate(paths)
                    ],
                }
            )

    validate_dataset(dataset)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"\nValidated and generated: {OUTPUT}")
    print(f"Characters: {len(dataset)}")


if __name__ == "__main__":
    main()
