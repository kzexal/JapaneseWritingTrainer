import importlib.util
import tempfile
import unittest
from pathlib import Path

from openpyxl import Workbook

MODULE_PATH = Path(__file__).resolve().parents[1] / "generate_vocabulary.py"
SPEC = importlib.util.spec_from_file_location("generate_vocabulary", MODULE_PATH)
vocabulary_generator = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(vocabulary_generator)


HEADERS = list(vocabulary_generator.EXPECTED_HEADERS)


def make_workbook(path: Path, rows: list[list[object]]) -> None:
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "Vocabulary"
    sheet.append(HEADERS)
    for row in rows:
        sheet.append(row)
    workbook.save(path)


class GenerateVocabularyTests(unittest.TestCase):
    def test_stable_id_uses_all_unicode_codepoints(self):
        self.assertEqual("vocab_6625", vocabulary_generator.stable_vocabulary_id("春"))
        self.assertEqual(
            "vocab_6625_308b",
            vocabulary_generator.stable_vocabulary_id("春る"),
        )
        self.assertEqual(
            vocabulary_generator.stable_vocabulary_id("春"),
            vocabulary_generator.stable_vocabulary_id("春"),
        )

    def test_keeps_homophones_and_repeated_examples_as_separate_cards(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            make_workbook(path, [
                ["春", "はる", "Mùa xuân", "春が好きです。", "Tôi thích mùa xuân。",
                 "10. Nhóm Nhật", "三 + 人 + 日", "mnemonic one", None, None, None],
                ["貼る", "はる", "Dán", "切手を貼ります。", "Dán tem。",
                 "12. Nhóm Khác", "貝 + 占", "mnemonic two", None, None, None],
                ["聞く", "きく", "Nghe / Hỏi", "音楽を聞きます。", "Nghe nhạc。",
                 "07. Nhóm 言", "門 + 耳", "mnemonic three", None, None, None],
                ["音楽", "おんがく", "Âm nhạc", "音楽を聞きます。", "Nghe nhạc。",
                 "02. Nhóm 木", "音 + 楽", "mnemonic four", None, None, None],
            ])

            records = vocabulary_generator.parse_workbook(path)

        self.assertEqual(4, len(records))
        self.assertEqual(4, len({record["id"] for record in records}))
        self.assertEqual("春", records[0]["japanese"])
        self.assertEqual("貼る", records[1]["japanese"])
        self.assertEqual(records[2]["example"], records[3]["example"])
        self.assertEqual(records[2]["exampleMeaning"], records[3]["exampleMeaning"])

    def test_normalizes_empty_and_null_text_to_none(self):
        self.assertIsNone(vocabulary_generator.normalize_text(None))
        self.assertIsNone(vocabulary_generator.normalize_text("  "))
        self.assertIsNone(vocabulary_generator.normalize_text(" None "))
        self.assertIsNone(vocabulary_generator.normalize_text("NULL"))
        self.assertEqual("N5", vocabulary_generator.normalize_text(" N5 "))

    def test_rejects_duplicate_japanese_with_excel_row(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            row = ["海", "うみ", "Biển", None, None, None, None, None, None, None, None]
            make_workbook(path, [row, row])

            with self.assertRaisesRegex(ValueError, r"Vocabulary!A3: duplicate Japanese"):
                vocabulary_generator.parse_workbook(path)

    def test_requires_reading_and_vietnamese(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "vocabulary.xlsx"
            make_workbook(path, [
                ["海", None, "Biển", None, None, None, None, None, None, None, None]
            ])

            with self.assertRaisesRegex(ValueError, r"Vocabulary!B2: Reading is required"):
                vocabulary_generator.parse_workbook(path)

    def test_builds_versioned_metadata_dataset(self):
        records = [{
            "id": "vocab_6d77",
            "japanese": "海",
            "reading": "うみ",
            "vietnamese": "Biển",
            "example": None,
            "exampleMeaning": None,
            "group": "01. Nhóm 氵",
            "radicals": None,
            "mnemonics": None,
            "jlpt": None,
            "notes": None,
            "tags": None,
        }]

        dataset = vocabulary_generator.build_dataset(records)

        self.assertEqual(1, dataset["schemaVersion"])
        self.assertEqual("Vocabulary", dataset["source"]["sheet"])
        self.assertEqual(records, dataset["records"])


if __name__ == "__main__":
    unittest.main()
