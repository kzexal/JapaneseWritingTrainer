import importlib.util
import tempfile
import unittest
from pathlib import Path

from openpyxl import Workbook

MODULE_PATH = Path(__file__).resolve().parents[1] / "generate_kanji.py"
SPEC = importlib.util.spec_from_file_location("generate_kanji", MODULE_PATH)
kanji_generator = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(kanji_generator)


HEADERS = list(kanji_generator.EXPECTED_HEADERS)


def make_workbook(path: Path, rows: list[list[object]]) -> None:
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "Kanji"
    sheet.append(HEADERS)
    for row in rows:
        sheet.append(row)
    workbook.save(path)


class GenerateKanjiTests(unittest.TestCase):
    def test_normalizes_none_text_to_null_and_keeps_real_values(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            make_workbook(path, [[
                "曜", "ヨウ", "None", "weekday", "日", 18,
                "曜日", None, "N5", "time",
            ]])

            records = kanji_generator.parse_workbook(path)

        self.assertEqual(1, len(records))
        self.assertEqual("kanji_66dc", records[0]["id"])
        self.assertIsNone(records[0]["kunyomi"])
        self.assertIsNone(records[0]["notes"])
        self.assertEqual("ヨウ", records[0]["onyomi"])
        self.assertEqual("weekday", records[0]["meaning"])
        self.assertEqual(18, records[0]["strokeCount"])

    def test_null_spellings_are_normalized_case_insensitively(self):
        self.assertIsNone(kanji_generator.normalize_text(" None "))
        self.assertIsNone(kanji_generator.normalize_text("NULL"))
        self.assertIsNone(kanji_generator.normalize_text(""))
        self.assertIsNone(kanji_generator.normalize_text(None))
        self.assertEqual("なし", kanji_generator.normalize_text(" なし "))

    def test_stable_id_uses_unicode_code_point(self):
        self.assertEqual("kanji_66dc", kanji_generator.stable_kanji_id("曜"))
        self.assertEqual("kanji_6821", kanji_generator.stable_kanji_id("校"))

    def test_rejects_duplicate_kanji(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            row = ["校", "コウ", "None", "school", "木", 10, None, None, "N5", None]
            make_workbook(path, [row, row])

            with self.assertRaisesRegex(ValueError, "duplicate Kanji"):
                kanji_generator.parse_workbook(path)

    def test_rejects_invalid_stroke_count_with_cell_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            row = ["校", "コウ", None, "school", "木", 2.5, None, None, "N5", None]
            make_workbook(path, [row])

            with self.assertRaisesRegex(ValueError, r"Kanji!F2"):
                kanji_generator.parse_workbook(path)

    def test_builds_versioned_metadata_document_without_geometry(self):
        records = [{
            "id": "kanji_6821",
            "character": "校",
            "strokeCount": 10,
            "onyomi": "コウ",
            "kunyomi": None,
            "meaning": "school",
            "radical": "木",
            "exampleWords": None,
            "notes": None,
            "jlpt": "N5",
            "tags": None,
        }]

        dataset = kanji_generator.build_dataset(records)

        self.assertEqual(1, dataset["schemaVersion"])
        self.assertEqual("Kanji", dataset["source"]["sheet"])
        self.assertEqual(records, dataset["records"])
        self.assertNotIn("strokes", dataset["records"][0])
        self.assertNotIn("svgPath", dataset["records"][0])


if __name__ == "__main__":
    unittest.main()
