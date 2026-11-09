import tempfile
import unittest
from pathlib import Path

from import_kanjivg_strokes import build_stroke_dataset, parse_svg_strokes, source_filename


SVG_TEMPLATE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 109 109">
  <g id="kvg:StrokePaths_04e00">
    <path id="kvg:04e00-s1" d="M10,10 L20,20"/>
    <path id="kvg:04e00-s2" d="M30,30 L40,40"/>
  </g>
  <g id="kvg:StrokeNumbers_04e00">
    <text>1</text><text>2</text>
  </g>
</svg>
"""


class ImportKanjiVGStrokesTests(unittest.TestCase):
    def test_source_filename_uses_padded_unicode_codepoint(self):
        self.assertEqual("04e00.svg", source_filename("一"))
        self.assertEqual("066dc.svg", source_filename("曜"))

    def test_extracts_paths_in_stroke_order_and_ignores_numbers_group(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "04e00.svg"
            path.write_text(SVG_TEMPLATE, encoding="utf-8")

            view_box_size, strokes = parse_svg_strokes(path)

        self.assertEqual(109.0, view_box_size)
        self.assertEqual(["M10,10 L20,20", "M30,30 L40,40"], strokes)

    def test_builds_separate_geometry_dataset_with_attribution(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            (source_dir / "04e00.svg").write_text(SVG_TEMPLATE, encoding="utf-8")
            metadata = [{
                "id": "kanji_4e00",
                "character": "一",
                "strokeCount": 2,
            }]

            dataset = build_stroke_dataset(metadata, source_dir)

        self.assertEqual(1, dataset["schemaVersion"])
        self.assertEqual("CC BY-SA 3.0", dataset["source"]["license"])
        self.assertIn("KanjiVG", dataset["source"]["attribution"])
        self.assertEqual("kanji_4e00", dataset["characters"][0]["id"])
        self.assertEqual(2, dataset["characters"][0]["strokeCount"])
        self.assertEqual(
            [1, 2],
            [stroke["order"] for stroke in dataset["characters"][0]["strokes"]],
        )

    def test_rejects_stroke_count_mismatch_instead_of_importing_bad_geometry(self):
        with tempfile.TemporaryDirectory() as directory:
            source_dir = Path(directory)
            (source_dir / "04e00.svg").write_text(SVG_TEMPLATE, encoding="utf-8")
            metadata = [{
                "id": "kanji_4e00",
                "character": "一",
                "strokeCount": 1,
            }]

            with self.assertRaisesRegex(ValueError, "workbook expects 1 strokes"):
                build_stroke_dataset(metadata, source_dir)

    def test_rejects_missing_source_file(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(FileNotFoundError, "KanjiVG SVG missing"):
                build_stroke_dataset(
                    [{"id": "kanji_4e00", "character": "一", "strokeCount": 1}],
                    Path(directory),
                )


if __name__ == "__main__":
    unittest.main()
