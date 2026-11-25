
from __future__ import annotations

import csv
import io
import json
import shutil
import sqlite3
import sys
import tempfile
import zipfile

from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


# ============================================================
# CONFIGURATION
# ============================================================

BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = BASE_DIR / "anki_analysis"

APKG_FILES = [
    "Japanese Grammar.apkg",
    "Japanese Kanji.apkg",
    "Japanese Vocabulary.apkg",
    "English Vocabulary.apkg",
]

# Anki card types
CARD_TYPES = {
    0: "New",
    1: "Learning",
    2: "Review",
    3: "Relearning",
}

# Anki queues
CARD_QUEUES = {
    -3: "Sched buried",
    -2: "User buried",
    -1: "Suspended",
    0: "New",
    1: "Learning",
    2: "Review",
    3: "Day learning",
    4: "Preview",
}

# Anki review log types
REVLOG_TYPES = {
    0: "Learning",
    1: "Review",
    2: "Relearning",
    3: "Cram",
}

REVLOG_RATINGS = {
    1: "Again",
    2: "Hard",
    3: "Good",
    4: "Easy",
}


# ============================================================
# HELPERS
# ============================================================

def json_load(value, default=None):
    if not value:
        return default

    try:
        return json.loads(value)
    except (json.JSONDecodeError, TypeError):
        return default


def safe_int(value):
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def get_table_names(conn):
    return {
        row[0]
        for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        )
    }


def get_columns(conn, table):
    try:
        return {
            row[1]
            for row in conn.execute(f'PRAGMA table_info("{table}")')
        }
    except sqlite3.Error:
        return set()


def count_by(rows, key):
    return dict(Counter(str(row[key]) for row in rows))


def utc_datetime(timestamp_ms):
    if timestamp_ms is None:
        return None

    try:
        return datetime.fromtimestamp(
            timestamp_ms / 1000,
            tz=timezone.utc
        ).isoformat()
    except (ValueError, OverflowError, OSError):
        return None


def find_interesting_config_keys(obj, path=""):
    """
    Find FSRS/scheduling-related configuration keys recursively.
    Does not export note contents.
    """
    keywords = (
        "fsrs",
        "retention",
        "stability",
        "difficulty",
        "scheduler",
        "algorithm",
        "interval",
        "lapse",
        "new.perday",
        "rev.perday",
    )

    found = {}

    if isinstance(obj, dict):
        for key, value in obj.items():
            current_path = f"{path}.{key}" if path else str(key)

            if any(word in str(key).lower() for word in keywords):
                found[current_path] = value

            found.update(
                find_interesting_config_keys(value, current_path)
            )

    elif isinstance(obj, list):
        for index, value in enumerate(obj):
            current_path = f"{path}[{index}]"
            found.update(
                find_interesting_config_keys(value, current_path)
            )

    return found


# ============================================================
# EXTRACT ANKI DATABASE
# ============================================================

def extract_collection(apkg_path, temp_dir):
    """
    Extract the newest available Anki collection database.

    Supports:
      - collection.anki21b (Zstandard compressed)
      - collection.anki21
      - collection.anki2

    The original APKG is never modified.
    """

    with zipfile.ZipFile(apkg_path, "r") as archive:
        names = set(archive.namelist())

        database_path = Path(temp_dir) / "collection.sqlite"

        if "collection.anki21b" in names:
            try:
                import zstandard
            except ImportError:
                raise RuntimeError(
                    "Missing zstandard. Run: py -m pip install zstandard"
                )

            compressed = archive.read("collection.anki21b")

            with zstandard.ZstdDecompressor().stream_reader(
                io.BytesIO(compressed)
            ) as reader:
                with database_path.open("wb") as output:
                    shutil.copyfileobj(reader, output)

            database_source = "collection.anki21b"

        elif "collection.anki21" in names:
            database_path.write_bytes(
                archive.read("collection.anki21")
            )
            database_source = "collection.anki21"

        elif "collection.anki2" in names:
            database_path.write_bytes(
                archive.read("collection.anki2")
            )
            database_source = "collection.anki2"

        else:
            raise RuntimeError(
                "No supported collection database found in APKG."
            )

        media_count = 0
        media_file_names = []

        if "media" in names:
            try:
                media_map = json.loads(archive.read("media"))
                media_file_names = list(media_map.values())
                media_count = len(media_map)
            except Exception:
                pass

    return database_path, database_source, media_count, media_file_names


# ============================================================
# ANALYZE NOTE TYPES
# ============================================================

def analyze_models(conn, notes):
    models = json_load(
        conn.execute("SELECT models FROM col").fetchone()[0],
        {}
    )

    note_counts = Counter(
        str(note["mid"]) for note in notes
    )

    result = []

    for model_id, model in models.items():
        fields = model.get("flds", [])

        result.append({
            "model_id": str(model_id),
            "name": model.get("name"),
            "type": model.get("type"),
            "sort_field": model.get("sortf"),
            "fields": [
                {
                    "ordinal": field.get("ord"),
                    "name": field.get("name"),
                    "sticky": field.get("sticky"),
                    "rtl": field.get("rtl"),
                }
                for field in fields
            ],
            "note_count": note_counts.get(str(model_id), 0),
            "templates": [
                template.get("name")
                for template in model.get("tmpls", [])
            ],
        })

    return result


# ============================================================
# ANALYZE CARDS
# ============================================================

def analyze_cards(conn, decks):
    columns = get_columns(conn, "cards")

    required = {"id", "nid", "did", "type", "queue"}
    missing = required - columns

    if missing:
        raise RuntimeError(
            f"Cards table is missing columns: {sorted(missing)}"
        )

    optional_columns = [
        "ivl",
        "factor",
        "reps",
        "lapses",
        "left",
        "due",
        "odue",
        "odid",
        "data",
        "mod",
        "flags",
    ]

    selected = ["id", "nid", "did", "type", "queue"]

    for column in optional_columns:
        if column in columns:
            selected.append(column)

    query = (
        "SELECT "
        + ", ".join(f'"{column}"' for column in selected)
        + " FROM cards"
    )

    rows = [
        dict(row)
        for row in conn.execute(query)
    ]

    type_counts = Counter()
    queue_counts = Counter()
    deck_stats = {}
    intervals = []
    repetitions = []
    lapses = []
    cards_with_memory_state = 0
    card_data_keys = Counter()
    memory_state_samples = []

    for card in rows:
        card_type = safe_int(card.get("type"))
        queue = safe_int(card.get("queue"))
        deck_id = str(card.get("did"))

        type_name = CARD_TYPES.get(
            card_type, f"Unknown ({card_type})"
        )
        queue_name = CARD_QUEUES.get(
            queue, f"Unknown ({queue})"
        )

        type_counts[type_name] += 1
        queue_counts[queue_name] += 1

        deck_name = decks.get(deck_id, f"Unknown deck ({deck_id})")

        if deck_name not in deck_stats:
            deck_stats[deck_name] = {
                "cards": 0,
                "types": Counter(),
                "queues": Counter(),
                "unique_notes": set(),
            }

        stats = deck_stats[deck_name]
        stats["cards"] += 1
        stats["types"][type_name] += 1
        stats["queues"][queue_name] += 1
        stats["unique_notes"].add(str(card["nid"]))

        for field in ("ivl", "reps", "lapses"):
            value = safe_int(card.get(field))

            if value is not None:
                if field == "ivl":
                    intervals.append(value)
                elif field == "reps":
                    repetitions.append(value)
                elif field == "lapses":
                    lapses.append(value)

        # Newer Anki versions may store FSRS memory state
        # in the card's data JSON.
        data = json_load(card.get("data"), {})

        if isinstance(data, dict):
            for key in data:
                card_data_keys[key] += 1

            if any(
                key.lower() in ("s", "d", "r", "stability", "difficulty")
                for key in data
            ):
                cards_with_memory_state += 1

                if len(memory_state_samples) < 5:
                    memory_state_samples.append({
                        "card_id": card["id"],
                        "keys": list(data.keys()),
                        "values": data,
                    })

    for stats in deck_stats.values():
        stats["unique_notes"] = len(stats["unique_notes"])
        stats["types"] = dict(stats["types"])
        stats["queues"] = dict(stats["queues"])

    def numeric_summary(values):
        if not values:
            return None

        return {
            "min": min(values),
            "max": max(values),
            "average": round(sum(values) / len(values), 2),
        }

    return {
        "total_cards": len(rows),
        "by_type": dict(type_counts),
        "by_queue": dict(queue_counts),
        "by_deck": deck_stats,
        "interval_days": numeric_summary(intervals),
        "repetitions": numeric_summary(repetitions),
        "lapses": numeric_summary(lapses),
        "cards_with_possible_memory_state": cards_with_memory_state,
        "card_data_keys": dict(card_data_keys),
        "memory_state_samples": memory_state_samples,
    }


# ============================================================
# ANALYZE REVIEW HISTORY
# ============================================================

def analyze_revlog(conn):
    tables = get_table_names(conn)

    if "revlog" not in tables:
        return {
            "available": False,
            "reason": "revlog table not found",
        }

    columns = get_columns(conn, "revlog")

    required = {"id", "cid", "ease", "type"}
    if not required.issubset(columns):
        return {
            "available": False,
            "reason": "Unexpected revlog schema",
            "columns": sorted(columns),
        }

    rows = [
        dict(row)
        for row in conn.execute(
            "SELECT * FROM revlog"
        )
    ]

    ratings = Counter()
    types = Counter()
    intervals = []
    review_times = []
    timestamps = []

    for row in rows:
        ease = safe_int(row.get("ease"))
        review_type = safe_int(row.get("type"))

        ratings[
            REVLOG_RATINGS.get(ease, f"Unknown ({ease})")
        ] += 1

        types[
            REVLOG_TYPES.get(
                review_type, f"Unknown ({review_type})"
            )
        ] += 1

        for field in ("ivl", "lastIvl"):
            value = safe_int(row.get(field))
            if value is not None:
                intervals.append(value)

        value = safe_int(row.get("time"))
        if value is not None:
            review_times.append(value)

        value = safe_int(row.get("id"))
        if value is not None:
            timestamps.append(value)

    result = {
        "available": True,
        "total_reviews": len(rows),
        "ratings": dict(ratings),
        "review_types": dict(types),
        "unique_cards_reviewed": len({
            row.get("cid") for row in rows
        }),
        "review_time_ms": (
            {
                "min": min(review_times),
                "max": max(review_times),
                "average": round(
                    sum(review_times) / len(review_times), 2
                ),
            }
            if review_times else None
        ),
        "first_review_utc": (
            utc_datetime(min(timestamps)) if timestamps else None
        ),
        "last_review_utc": (
            utc_datetime(max(timestamps)) if timestamps else None
        ),
    }

    return result


# ============================================================
# ANALYZE ONE APKG
# ============================================================

def analyze_apkg(apkg_path):
    print(f"\nAnalyzing: {apkg_path.name}")

    with tempfile.TemporaryDirectory(
        prefix="anki_analysis_"
    ) as temp_dir:

        db_path, db_source, media_count, media_names = (
            extract_collection(apkg_path, temp_dir)
        )

        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row

        try:
            tables = get_table_names(conn)

            if "col" not in tables:
                raise RuntimeError("Collection table not found.")

            col_columns = get_columns(conn, "col")

            if not {"models", "decks"}.issubset(col_columns):
                raise RuntimeError(
                    "Collection schema is missing models/decks."
                )

            col_row = conn.execute(
                "SELECT * FROM col LIMIT 1"
            ).fetchone()

            collection = dict(col_row)

            models_json = json_load(
                collection.get("models"), {}
            )

            decks_json = json_load(
                collection.get("decks"), {}
            )

            dconf_json = json_load(
                collection.get("dconf"), {}
            )

            conf_json = json_load(
                collection.get("conf"), {}
            )

            decks = {
                str(deck_id): deck.get("name", str(deck_id))
                for deck_id, deck in decks_json.items()
            }

            notes = [
                dict(row)
                for row in conn.execute(
                    "SELECT id, mid, tags FROM notes"
                )
            ]

            note_counts = Counter(
                str(note["mid"]) for note in notes
            )

            deck_card_counts = {}
            cards_report = analyze_cards(conn, decks)

            for deck_name, stats in cards_report["by_deck"].items():
                deck_card_counts[deck_name] = stats["cards"]

            models_report = analyze_models(conn, notes)
            revlog_report = analyze_revlog(conn)

            # Configuration keys that may reveal FSRS settings.
            scheduling_config = {
                "collection_config": find_interesting_config_keys(
                    conf_json
                ),
                "deck_options": find_interesting_config_keys(
                    dconf_json
                ),
                "deck_specific_config": {
                    decks.get(str(deck_id), str(deck_id)):
                        find_interesting_config_keys(deck)
                    for deck_id, deck in decks_json.items()
                },
            }

            # Keep only aggregate tag counts, not note contents.
            tag_counts = Counter()

            for note in notes:
                raw_tags = note.get("tags") or ""
                for tag in raw_tags.split():
                    tag_counts[tag] += 1

            report = {
                "file": apkg_path.name,
                "file_size_bytes": apkg_path.stat().st_size,
                "database_source": db_source,
                "collection": {
                    "creation_time_utc": utc_datetime(
                        collection.get("crt", 0) * 1000
                    ),
                    "last_modified_utc": utc_datetime(
                        collection.get("mod", 0) * 1000
                    ),
                    "schema_version": collection.get("scm"),
                    "collection_version": collection.get("ver"),
                },
                "totals": {
                    "notes": len(notes),
                    "cards": cards_report["total_cards"],
                    "review_history": (
                        revlog_report.get("total_reviews", 0)
                        if revlog_report.get("available")
                        else None
                    ),
                    "decks": len(decks_json),
                    "note_types": len(models_json),
                    "media_files": media_count,
                },
                "decks": [
                    {
                        "id": deck_id,
                        "name": deck_name,
                        "cards": deck_card_counts.get(deck_name, 0),
                    }
                    for deck_id, deck_name in decks.items()
                ],
                "note_types": models_report,
                "cards": cards_report,
                "review_history": revlog_report,
                "scheduling_config": scheduling_config,
                "tags": dict(tag_counts),
                "media": {
                    "count": media_count,
                    "filenames_sample": media_names[:20],
                },
                "database_tables": sorted(tables),
                "database_columns": {
                    table: sorted(get_columns(conn, table))
                    for table in ("col", "notes", "cards", "revlog")
                    if table in tables
                },
            }

            return report

        finally:
            conn.close()


# ============================================================
# GENERATE HUMAN-READABLE SUMMARY
# ============================================================

def write_summary(reports):
    lines = []

    lines.append("ANKI COLLECTION ANALYSIS")
    lines.append("=" * 65)
    lines.append("This report contains aggregate data only.")
    lines.append("Original APKG files were not modified.")
    lines.append("")

    for report in reports:
        totals = report["totals"]

        lines.append("=" * 65)
        lines.append(report["file"])
        lines.append("-" * 65)

        lines.append(f"Notes:         {totals['notes']}")
        lines.append(f"Cards:         {totals['cards']}")
        lines.append(f"Review history:{totals['review_history']}")
        lines.append(f"Decks:         {totals['decks']}")
        lines.append(f"Note Types:    {totals['note_types']}")
        lines.append(f"Media files:   {totals['media_files']}")
        lines.append("")

        lines.append("NOTE TYPES")

        for model in report["note_types"]:
            lines.append(
                f"\n  {model['name']} ({model['note_count']} notes)"
            )

            for field in model["fields"]:
                lines.append(
                    f"    {field['ordinal']}: {field['name']}"
                )

        lines.append("")
        lines.append("CARD TYPES")

        for name, count in report["cards"]["by_type"].items():
            lines.append(f"  {name}: {count}")

        lines.append("")
        lines.append("CARD QUEUES")

        for name, count in report["cards"]["by_queue"].items():
            lines.append(f"  {name}: {count}")

        lines.append("")
        lines.append("REVIEW RATINGS")

        history = report["review_history"]

        if history.get("available"):
            for name, count in history["ratings"].items():
                lines.append(f"  {name}: {count}")

            lines.append(
                f"  Unique cards reviewed: "
                f"{history['unique_cards_reviewed']}"
            )
            lines.append(
                f"  First review: {history['first_review_utc']}"
            )
            lines.append(
                f"  Last review: {history['last_review_utc']}"
            )
        else:
            lines.append("  Review history unavailable.")

        lines.append("")
        lines.append("POSSIBLE FSRS MEMORY DATA")

        lines.append(
            "  Cards with possible memory state: "
            f"{report['cards']['cards_with_possible_memory_state']}"
        )

        lines.append(
            "  Card data keys: "
            f"{report['cards']['card_data_keys']}"
        )

        lines.append("")
        lines.append("SCHEDULING CONFIGURATION")

        lines.append(
            json.dumps(
                report["scheduling_config"],
                ensure_ascii=False,
                indent=2
            )
        )

        lines.append("")

    lines.append("=" * 65)
    lines.append("END OF REPORT")

    output_path = OUTPUT_DIR / "anki_summary.txt"
    output_path.write_text(
        "\n".join(lines),
        encoding="utf-8"
    )

    return output_path


# ============================================================
# MAIN
# ============================================================

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    reports = []
    errors = []

    for filename in APKG_FILES:
        apkg_path = BASE_DIR / filename

        if not apkg_path.exists():
            errors.append({
                "file": filename,
                "error": "File not found",
            })
            print(f"[SKIP] Not found: {filename}")
            continue

        try:
            report = analyze_apkg(apkg_path)
            reports.append(report)
            print(f"[OK] {filename}")

        except Exception as exc:
            errors.append({
                "file": filename,
                "error": str(exc),
            })
            print(f"[ERROR] {filename}: {exc}")

    full_report = {
        "generated_at_utc": datetime.now(
            timezone.utc
        ).isoformat(),
        "reports": reports,
        "errors": errors,
    }

    json_path = OUTPUT_DIR / "anki_analysis.json"

    json_path.write_text(
        json.dumps(
            full_report,
            ensure_ascii=False,
            indent=2
        ),
        encoding="utf-8"
    )

    summary_path = write_summary(reports)

    print("\n" + "=" * 60)
    print("ANALYSIS FINISHED")
    print("=" * 60)
    print(f"Successful decks: {len(reports)}")
    print(f"Errors:           {len(errors)}")
    print(f"JSON report:      {json_path}")
    print(f"Summary:          {summary_path}")

    if errors:
        print("\nErrors:")
        for error in errors:
            print(f"  {error['file']}: {error['error']}")

    print("\nNo APKG file was modified.")


if __name__ == "__main__":
    main()
