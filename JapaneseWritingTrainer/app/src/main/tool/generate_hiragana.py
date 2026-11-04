#!/usr/bin/env python3
"""
Generate assets/characters/hiragana.json from KanjiVG SVG files.

Usage:
    python tools/generate_hiragana.py

The script downloads the 46 basic Hiragana SVG files from KanjiVG,
extracts stroke paths in order, and writes the JSON dataset expected by
CharacterRepository.kt.

License/attribution:
KanjiVG is released under Creative Commons Attribution-ShareAlike 3.0.
Include attribution and the license notice if you distribute the app.
"""

import json
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

BASE_URL = "https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji"
OUTPUT = Path("app/src/main/assets/characters/hiragana.json")

# (character, romaji, group)
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

def svg_codepoint_filename(character: str) -> str:
    # KanjiVG names files using 5-digit lowercase hexadecimal code points.
    return f"{ord(character):05x}.svg"

def extract_paths(svg_bytes: bytes) -> list[str]:
    root = ET.fromstring(svg_bytes)

    # Locate the StrokePaths group; ignore the separate StrokeNumbers group.
    stroke_group = None
    for element in root.iter():
        element_id = element.attrib.get("id", "")
        if element_id.startswith("kvg:StrokePaths_"):
            stroke_group = element
            break

    if stroke_group is None:
        raise ValueError("Không tìm thấy group kvg:StrokePaths trong SVG")

    paths = []
    for element in stroke_group.iter():
        if element.tag.rsplit("}", 1)[-1] == "path":
            d = element.attrib.get("d")
            if d:
                paths.append(d)

    if not paths:
        raise ValueError("SVG không có path nét chữ")
    return paths

def download_svg(character: str) -> bytes:
    filename = svg_codepoint_filename(character)
    url = f"{BASE_URL}/{filename}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "JapaneseWritingTrainer-dataset-generator"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()

def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    dataset = []

    for characters, readings, group in ROWS:
        for character, reading in zip(characters, readings):
            print(f"Downloading {character} ({reading})...")
            svg_bytes = download_svg(character)
            paths = extract_paths(svg_bytes)

            dataset.append({
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
            })

    if len(dataset) != 46:
        raise RuntimeError(f"Expected 46 characters, got {len(dataset)}")

    OUTPUT.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )
    print(f"\nDone: {OUTPUT.resolve()}")
    print(f"Characters: {len(dataset)}")

if __name__ == "__main__":
    main()
