from __future__ import annotations

import hashlib
import html
import json
import re
import shutil
import sqlite3
import tempfile
import zipfile

from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = BASE_DIR / "anki_schema_inspection"

APKG_FILES = [
    "Japanese Grammar.apkg",
    "Japanese Kanji.apkg",
    "Japanese Vocabulary.apkg",
    "English Vocabulary.apkg",
]

CARD_TYPES = {0: "New", 1: "Learning", 2: "Review", 3: "Relearning"}
CARD_QUEUES = {
    -3: "Sched buried", -2: "User buried", -1: "Suspended",
    0: "New", 1: "Learning", 2: "Review", 3: "Day learning",
    4: "Preview",
}
REVLOG_TYPES = {0: "Learning", 1: "Review", 2: "Relearning", 3: "Cram", 4: "Rescheduling"}
REVLOG_RATINGS = {1: "Again", 2: "Hard", 3: "Good", 4: "Easy"}

SOUND_RE = re.compile(r"\[sound:([^\]]+)\]", re.IGNORECASE)
MEDIA_ATTR_RE = re.compile(
    r"""(?:src|href)\s*=\s*["']([^"']+)["']""",
    re.IGNORECASE,
)
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}
AUDIO_EXTENSIONS = {".mp3", ".mp4", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".3gp"}


def json_load(value, default=None):
    if value is None:
        return default
    if isinstance(value, bytes):
        try:
            value = value.decode("utf-8")
        except UnicodeDecodeError:
            return default
    try:
        return json.loads(value)
    except (json.JSONDecodeError, TypeError, UnicodeDecodeError):
        return default


def safe_int(value):
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def utc_from_seconds(value):
    try:
        return datetime.fromtimestamp(int(value), tz=timezone.utc).isoformat()
    except (ValueError, TypeError, OverflowError, OSError):
        return None


def utc_from_millis(value):
    try:
        return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc).isoformat()
    except (ValueError, TypeError, OverflowError, OSError):
        return None


def table_names(conn):
    return {
        row[0]
        for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        )
    }


def table_columns(conn, table):
    # table names are selected from sqlite_master, not user input.
    return [
        row[1]
        for row in conn.execute(f'PRAGMA table_info("{table}")')
    ]


def rows_as_dicts(conn, table, order_by=None):
    columns = table_columns(conn, table)
    if not columns:
        return []
    query = f'SELECT * FROM "{table}"'
    if order_by and order_by in columns:
        query += f' ORDER BY "{order_by}"'
    return [dict(row) for row in conn.execute(query)]


def blob_summary(value):
    if value is None:
        return None
    raw = bytes(value) if not isinstance(value, str) else value.encode("utf-8")
    return {
        "bytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "hex_prefix": raw[:24].hex(),
        "encoding": "opaque protobuf/blob; not decoded by this script",
    }


def extract_collection(apkg_path, temp_dir):
    with zipfile.ZipFile(apkg_path, "r") as archive:
        names = archive.namelist()
        name_set = set(names)
        db_path = Path(temp_dir) / "collection.sqlite"

        if "collection.anki21b" in name_set:
            try:
                import zstandard
            except ImportError as exc:
                raise RuntimeError(
                    "Missing zstandard. Install with: py -m pip install zstandard"
                ) from exc
            with zstandard.ZstdDecompressor().stream_reader(
                __import__("io").BytesIO(archive.read("collection.anki21b"))
            ) as reader, db_path.open("wb") as output:
                shutil.copyfileobj(reader, output)
            db_source = "collection.anki21b"
        elif "collection.anki21" in name_set:
            db_path.write_bytes(archive.read("collection.anki21"))
            db_source = "collection.anki21"
        elif "collection.anki2" in name_set:
            db_path.write_bytes(archive.read("collection.anki2"))
            db_source = "collection.anki2"
        else:
            raise RuntimeError("No supported collection database found in APKG.")

        media_manifest = {}
        manifest_status = "missing"
        if "media" in name_set:
            try:
                parsed = json.loads(archive.read("media").decode("utf-8"))
                if isinstance(parsed, dict):
                    media_manifest = {str(k): str(v) for k, v in parsed.items()}
                    manifest_status = "parsed"
                else:
                    manifest_status = "unexpected JSON type"
            except Exception as exc:
                manifest_status = f"parse error: {type(exc).__name__}"

        media_entries = []
        for archive_name in names:
            if archive_name in {"media", "meta", "collection.anki2", "collection.anki21", "collection.anki21b"}:
                continue
            info = archive.getinfo(archive_name)
            media_entries.append({
                "archive_key": archive_name,
                "bytes": info.file_size,
            })

        media_records = []
        for archive_key, filename in media_manifest.items():
            try:
                info = archive.getinfo(archive_key)
                size = info.file_size
                exists = True
            except KeyError:
                size = None
                exists = False
            media_records.append({
                "archive_key": archive_key,
                "filename": filename,
                "bytes": size,
                "exists_in_archive": exists,
                "extension": Path(filename).suffix.lower(),
            })

    return db_path, db_source, {
        "manifest_status": manifest_status,
        "manifest_entries": len(media_manifest),
        "records": media_records,
        "nonstandard_archive_entries": media_entries,
    }


def decode_legacy_models_and_decks(conn):
    cols = set(table_columns(conn, "col"))
    row = conn.execute("SELECT * FROM col LIMIT 1").fetchone()
    if row is None:
        return {}, {}
    data = dict(row)
    models = json_load(data.get("models"), {}) or {}
    decks = json_load(data.get("decks"), {}) or {}
    return models, decks


def analyze_note_types(conn, notes, tables):
    note_counts = Counter(str(n.get("mid")) for n in notes)
    result = []

    if {"notetypes", "fields", "templates"}.issubset(tables):
        notetypes = rows_as_dicts(conn, "notetypes", "name")
        fields = rows_as_dicts(conn, "fields", "ord")
        templates = rows_as_dicts(conn, "templates", "ord")

        fields_by_ntid = defaultdict(list)
        templates_by_ntid = defaultdict(list)

        for field in fields:
            fields_by_ntid[str(field.get("ntid"))].append({
                "ordinal": field.get("ord"),
                "name": field.get("name"),
                "config_blob": blob_summary(field.get("config")),
            })

        for template in templates:
            templates_by_ntid[str(template.get("ntid"))].append({
                "ordinal": template.get("ord"),
                "name": template.get("name"),
                "config_blob": blob_summary(template.get("config")),
            })

        for nt in notetypes:
            ntid = str(nt.get("id"))
            result.append({
                "id": ntid,
                "name": nt.get("name"),
                "note_count": note_counts.get(ntid, 0),
                "mtime_secs_utc": utc_from_seconds(nt.get("mtime_secs")),
                "fields": fields_by_ntid.get(ntid, []),
                "templates": templates_by_ntid.get(ntid, []),
                "config_blob": blob_summary(nt.get("config")),
            })
        return result, "normalized"

    legacy_models, _ = decode_legacy_models_and_decks(conn)
    for model_id, model in legacy_models.items():
        result.append({
            "id": str(model_id),
            "name": model.get("name"),
            "note_count": note_counts.get(str(model_id), 0),
            "fields": [
                {"ordinal": f.get("ord"), "name": f.get("name")}
                for f in model.get("flds", [])
            ],
            "templates": [
                {"ordinal": t.get("ord"), "name": t.get("name")}
                for t in model.get("tmpls", [])
            ],
            "legacy_model_type": model.get("type"),
        })
    return result, "legacy col.models"


def analyze_decks(conn, tables, cards):
    result = []
    if "decks" in tables:
        for deck in rows_as_dicts(conn, "decks", "name"):
            raw_name = str(deck.get("name") or "")
            # Schema 18 stores hierarchy separators as U+001F.
            display_name = raw_name.replace("\x1f", "::")
            deck_id = str(deck.get("id"))
            matching = [c for c in cards if str(c.get("did")) == deck_id]
            item = {
                "id": deck_id,
                "name": display_name,
                "raw_name_separator": "U+001F" if "\x1f" in raw_name else "none",
                "cards": len(matching),
                "common_blob": blob_summary(deck.get("common")),
                "kind_blob": blob_summary(deck.get("kind")),
            }
            result.append(item)
        source = "normalized"
    else:
        _, legacy_decks = decode_legacy_models_and_decks(conn)
        for deck_id, deck in legacy_decks.items():
            result.append({
                "id": str(deck_id),
                "name": deck.get("name"),
                "cards": sum(1 for c in cards if str(c.get("did")) == str(deck_id)),
                "legacy_deck": True,
            })
        source = "legacy col.decks"

    deck_configs = []
    if "deck_config" in tables:
        for conf in rows_as_dicts(conn, "deck_config", "name"):
            deck_configs.append({
                "id": str(conf.get("id")),
                "name": conf.get("name"),
                "mtime_secs_utc": utc_from_seconds(conf.get("mtime_secs")),
                "config_blob": blob_summary(conf.get("config")),
            })

    return result, source, deck_configs


def analyze_config(conn, tables):
    result = []
    if "config" not in tables:
        return result, "not present"
    for row in rows_as_dicts(conn, "config", "key"):
        value = row.get("val")
        decoded = json_load(value, None)
        if decoded is None and isinstance(value, bytes):
            try:
                decoded = value.decode("utf-8")
            except UnicodeDecodeError:
                decoded = {"opaque_blob": blob_summary(value)}
        result.append({
            "key": row.get("key"),
            "usn": row.get("usn"),
            "mtime_secs_utc": utc_from_seconds(row.get("mtime_secs")),
            "value": decoded,
        })
    return result, "normalized config key/value"


def analyze_cards(conn, tables, deck_lookup, note_type_lookup):
    columns = set(table_columns(conn, "cards"))
    selected = [
        c for c in (
            "id", "nid", "did", "ord", "type", "queue", "due",
            "odue", "odid", "ivl", "factor", "reps", "lapses",
            "left", "data", "mod", "flags"
        ) if c in columns
    ]
    rows = [dict(row) for row in conn.execute(
        "SELECT " + ", ".join(f'"{c}"' for c in selected) + " FROM cards"
    )]

    by_type = Counter()
    by_queue = Counter()
    by_deck = defaultdict(Counter)
    by_note_type = Counter()
    data_keys = Counter()
    possible_fsrs_cards = 0
    fsrs_samples = []

    for card in rows:
        t = safe_int(card.get("type"))
        q = safe_int(card.get("queue"))
        type_name = CARD_TYPES.get(t, f"Unknown ({t})")
        queue_name = CARD_QUEUES.get(q, f"Unknown ({q})")
        by_type[type_name] += 1
        by_queue[queue_name] += 1

        did = str(card.get("did"))
        deck_name = deck_lookup.get(did, f"Unknown deck ({did})")
        by_deck[deck_name][queue_name] += 1

        nid = str(card.get("nid"))
        nt_name = note_type_lookup.get(nid, "Unknown Note Type")
        by_note_type[nt_name] += 1

        data = json_load(card.get("data"), {})
        if isinstance(data, dict):
            for key in data:
                data_keys[str(key)] += 1
            if any(k in data for k in ("s", "d", "stability", "difficulty")):
                possible_fsrs_cards += 1
                if len(fsrs_samples) < 3:
                    fsrs_samples.append({
                        "card_id": card.get("id"),
                        "keys": sorted(data.keys()),
                        "values": data,
                    })

    return {
        "total": len(rows),
        "by_type": dict(by_type),
        "by_queue": dict(by_queue),
        "by_deck": {k: dict(v) for k, v in by_deck.items()},
        "by_note_type": dict(by_note_type),
        "cards_with_possible_fsrs_state": possible_fsrs_cards,
        "card_data_keys": dict(data_keys),
        "fsrs_state_samples": fsrs_samples,
    }, rows


def analyze_revlog(conn, tables):
    if "revlog" not in tables:
        return {"available": False, "reason": "revlog table missing"}
    rows = rows_as_dicts(conn, "revlog", "id")
    ratings = Counter()
    types = Counter()
    unique_cards = set()
    times = []
    timestamps = []

    for row in rows:
        ease = safe_int(row.get("ease"))
        rev_type = safe_int(row.get("type"))
        ratings[REVLOG_RATINGS.get(ease, f"Unknown ({ease})")] += 1
        types[REVLOG_TYPES.get(rev_type, f"Unknown ({rev_type})")] += 1
        unique_cards.add(row.get("cid"))
        if safe_int(row.get("time")) is not None:
            times.append(int(row["time"]))
        if safe_int(row.get("id")) is not None:
            timestamps.append(int(row["id"]))

    return {
        "available": True,
        "total": len(rows),
        "ratings": dict(ratings),
        "types": dict(types),
        "unique_cards_reviewed": len(unique_cards),
        "review_time_ms": {
            "min": min(times), "max": max(times),
            "average": round(sum(times) / len(times), 2),
        } if times else None,
        "first_review_utc": utc_from_millis(min(timestamps)) if timestamps else None,
        "last_review_utc": utc_from_millis(max(timestamps)) if timestamps else None,
    }


def analyze_media_and_references(media_info, notes, field_names_by_mid):
    filename_to_media = {
        r["filename"]: r for r in media_info["records"]
    }
    audio_refs = Counter()
    image_refs = Counter()
    refs_by_field = defaultdict(Counter)

    for note in notes:
        mid = str(note.get("mid"))
        names = field_names_by_mid.get(mid, [])
        values = str(note.get("flds") or "").split("\x1f")
        for idx, value in enumerate(values):
            field_name = names[idx] if idx < len(names) else f"field_{idx}"
            decoded = html.unescape(value or "")
            for match in SOUND_RE.findall(decoded):
                filename = match.strip()
                audio_refs[filename] += 1
                refs_by_field[field_name]["audio"] += 1
            for match in MEDIA_ATTR_RE.findall(decoded):
                filename = match.strip().split("?")[0].split("#")[0]
                ext = Path(filename).suffix.lower()
                if ext in IMAGE_EXTENSIONS:
                    image_refs[filename] += 1
                    refs_by_field[field_name]["image"] += 1
                elif ext in AUDIO_EXTENSIONS:
                    audio_refs[filename] += 1
                    refs_by_field[field_name]["audio"] += 1

    def summarize_refs(counter):
        items = []
        missing = []
        for filename, count in counter.most_common():
            media = filename_to_media.get(filename)
            item = {
                "filename": filename,
                "references": count,
                "present_in_manifest": media is not None,
                "present_in_archive": bool(media and media.get("exists_in_archive")),
                "bytes": media.get("bytes") if media else None,
            }
            items.append(item)
            if not item["present_in_archive"]:
                missing.append(filename)
        return {
            "unique_files_referenced": len(counter),
            "total_references": sum(counter.values()),
            "files": items[:100],
            "missing_or_unmapped_files": missing[:100],
        }

    ext_counts = Counter(r.get("extension") or "(no extension)" for r in media_info["records"])
    total_bytes = sum(r.get("bytes") or 0 for r in media_info["records"])

    return {
        "archive_media": {
            "manifest_status": media_info["manifest_status"],
            "manifest_entries": media_info["manifest_entries"],
            "total_bytes": total_bytes,
            "by_extension": dict(ext_counts),
            "audio_files": sum(1 for r in media_info["records"] if r.get("extension") in AUDIO_EXTENSIONS),
            "image_files": sum(1 for r in media_info["records"] if r.get("extension") in IMAGE_EXTENSIONS),
            "unmapped_archive_entries_sample": media_info["nonstandard_archive_entries"][:20],
        },
        "audio_references": summarize_refs(audio_refs),
        "image_references": summarize_refs(image_refs),
        "reference_counts_by_field": {k: dict(v) for k, v in refs_by_field.items()},
    }



def analyze_apkg(path):
    print(f"Analyzing {path.name} ...")

    with tempfile.TemporaryDirectory(prefix="anki_schema_") as temp_dir:
        db_path, db_source, media_info = extract_collection(path, temp_dir)
        conn = sqlite3.connect(db_path)

        def unicase_collation(left, right):
            left = (left or "").casefold()
            right = (right or "").casefold()
            return (left > right) - (left < right)

        conn.create_collation("unicase", unicase_collation)
        conn.row_factory = sqlite3.Row

        try:
            tables = table_names(conn)

            if "col" not in tables:
                raise RuntimeError("Collection table missing.")

            col_row = conn.execute("SELECT * FROM col LIMIT 1").fetchone()
            if col_row is None:
                raise RuntimeError("Collection table is empty.")

            col = dict(col_row)
            notes = rows_as_dicts(conn, "notes", "id") if "notes" in tables else []

            note_types, note_type_source = analyze_note_types(conn, notes, tables)

            field_names_by_mid = {}
            note_type_lookup = {}

            if {"notetypes", "fields"}.issubset(tables):
                nt_rows = rows_as_dicts(conn, "notetypes", "name")
                fields = rows_as_dicts(conn, "fields", "ord")
                fields_by_ntid = defaultdict(list)

                for field in fields:
                    fields_by_ntid[str(field.get("ntid"))].append(
                        field.get("name")
                    )

                for nt in nt_rows:
                    ntid = str(nt.get("id"))
                    note_type_lookup[ntid] = nt.get("name")
                    field_names_by_mid[ntid] = fields_by_ntid.get(ntid, [])

            else:
                legacy_models, _ = decode_legacy_models_and_decks(conn)

                for mid, model in legacy_models.items():
                    mid = str(mid)
                    note_type_lookup[mid] = model.get("name")
                    field_names_by_mid[mid] = [
                        f.get("name")
                        for f in sorted(
                            model.get("flds", []),
                            key=lambda x: x.get("ord", 0)
                        )
                    ]

            note_mid_by_id = {
                str(n.get("id")): str(n.get("mid"))
                for n in notes
            }

            decks, deck_source, deck_configs = analyze_decks(
                conn, tables, []
            )

            deck_lookup = {
                str(d["id"]): d["name"]
                for d in decks
            }

            cards_report, cards = analyze_cards(
                conn, tables, deck_lookup, note_type_lookup
            )

            deck_counts = Counter(
                str(c.get("did")) for c in cards
            )

            for deck in decks:
                deck["cards"] = deck_counts.get(
                    str(deck["id"]), 0
                )

            cards_report["by_note_type"] = dict(Counter(
                note_type_lookup.get(
                    note_mid_by_id.get(str(c.get("nid")), ""),
                    "Unknown Note Type"
                )
                for c in cards
            ))

            config_rows, config_source = analyze_config(conn, tables)
            revlog = analyze_revlog(conn, tables)

            media_report = analyze_media_and_references(
                media_info,
                notes,
                field_names_by_mid
            )

            note_counts = Counter(
                str(n.get("mid")) for n in notes
            )

            for nt in note_types:
                nt["note_count"] = note_counts.get(
                    str(nt.get("id")),
                    nt.get("note_count", 0)
                )

            table_info = {}

            for table in sorted(tables):
                table_info[table] = table_columns(conn, table)

            return {
                "file": path.name,
                "file_size_bytes": path.stat().st_size,
                "database_source": db_source,

                "collection": {
                    "schema_version": col.get("ver"),
                    "schema_modified_epoch_ms": col.get("scm"),
                    "created_utc": utc_from_seconds(col.get("crt")),
                    "modified_utc": utc_from_millis(col.get("mod")),
                    "version": col.get("ver"),
                },

                "totals": {
                    "notes": len(notes),
                    "cards": cards_report["total"],
                    "review_history": (
                        revlog.get("total")
                        if revlog.get("available")
                        else None
                    ),
                    "decks": len(decks),
                    "note_types": len(note_types),
                },

                "deck_source": deck_source,
                "decks": decks,
                "deck_configs": deck_configs,

                "note_type_source": note_type_source,
                "note_types": note_types,

                "cards": cards_report,
                "review_history": revlog,

                "collection_config": {
                    "source": config_source,
                    "entries": config_rows,
                    "legacy_col_conf_keys": sorted(
                        (json_load(col.get("conf"), {}) or {}).keys()
                    ) if isinstance(
                        json_load(col.get("conf"), {}), dict
                    ) else [],
                },

                "media": media_report,
                "database_tables": table_info,
            }

        finally:
            conn.close()



def write_summary(report):
    lines = [
        "ANKI SCHEMA + MEDIA INSPECTION",
        "=" * 72,
        "Read-only inspection. APKG files are not modified.",
        "Note field contents are not exported; only field names and media references are counted.",
        "",
    ]
    for item in report["reports"]:
        lines += [
            "=" * 72,
            item["file"],
            "-" * 72,
            f"Collection schema version: {item['collection']['schema_version']}",
            f"Notes: {item['totals']['notes']}",
            f"Cards: {item['totals']['cards']}",
            f"Decks: {item['totals']['decks']} ({item['deck_source']})",
            f"Note Types: {item['totals']['note_types']} ({item['note_type_source']})",
            f"Review history: {item['totals']['review_history']}",
            "",
            "DECKS",
        ]
        for deck in item["decks"]:
            lines.append(f"  {deck['name']} — {deck['cards']} cards (id={deck['id']})")
        lines += ["", "NOTE TYPES / FIELDS"]
        for nt in item["note_types"]:
            lines.append(f"  {nt.get('name')} — {nt.get('note_count')} notes")
            for field in nt.get("fields", []):
                lines.append(f"    {field.get('ordinal')}: {field.get('name')}")
            for template in nt.get("templates", []):
                lines.append(f"    Template {template.get('ordinal')}: {template.get('name')}")
        lines += ["", "CARD QUEUES"]
        for key, value in item["cards"]["by_queue"].items():
            lines.append(f"  {key}: {value}")
        lines += ["", "REVIEW RATINGS"]
        for key, value in item["review_history"].get("ratings", {}).items():
            lines.append(f"  {key}: {value}")
        lines += ["", "POSSIBLE FSRS STATE"]
        lines.append(f"  Cards with possible FSRS state: {item['cards']['cards_with_possible_fsrs_state']}")
        lines.append(f"  Card data keys: {item['cards']['card_data_keys']}")
        lines.append("  Note: field meanings/compatibility are not inferred by this script.")
        lines += ["", "DECK CONFIGURATION"]
        lines.append(f"  Deck config presets: {len(item['deck_configs'])}")
        for config in item["deck_configs"]:
            blob = config.get("config_blob") or {}
            lines.append(f"  {config.get('name')} (id={config.get('id')}): protobuf bytes={blob.get('bytes')}")
        lines.append("  Protobuf blobs are fingerprinted but not decoded; FSRS parameters need a compatible Anki protobuf schema.")
        lines += ["", "MEDIA / AUDIO"]
        media = item["media"]
        lines.append(f"  Manifest status: {media['archive_media']['manifest_status']}")
        lines.append(f"  Manifest files: {media['archive_media']['manifest_entries']}")
        lines.append(f"  Audio assets: {media['archive_media']['audio_files']}")
        lines.append(f"  Image assets: {media['archive_media']['image_files']}")
        lines.append(f"  Audio files referenced by notes: {media['audio_references']['unique_files_referenced']}")
        lines.append(f"  Total audio references: {media['audio_references']['total_references']}")
        lines.append(f"  Missing/unmapped audio: {len(media['audio_references']['missing_or_unmapped_files'])}")
        for audio in media["audio_references"]["files"][:15]:
            lines.append(f"    {audio['filename']} — refs={audio['references']}, present={audio['present_in_archive']}")
        lines.append(f"  Image files referenced by notes: {media['image_references']['unique_files_referenced']}")
        lines.append(f"  Media references by field: {media['reference_counts_by_field']}")
        lines += ["", "DATABASE TABLES"]
        for table, cols in item["database_tables"].items():
            lines.append(f"  {table}: {', '.join(cols)}")
        lines.append("")

    lines += ["=" * 72, "ERRORS"]
    for error in report["errors"]:
        lines.append(f"  {error['file']}: {error['error']}")
    lines += ["", "END OF REPORT"]
    (OUTPUT_DIR / "anki_schema_summary.txt").write_text("\n".join(lines), encoding="utf-8")


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    reports = []
    errors = []

    for filename in APKG_FILES:
        path = BASE_DIR / filename
        if not path.exists():
            errors.append({"file": filename, "error": "File not found"})
            print(f"[SKIP] {filename}: file not found")
            continue
        try:
            reports.append(analyze_apkg(path))
            print(f"[OK] {filename}")
        except Exception as exc:
            errors.append({"file": filename, "error": f"{type(exc).__name__}: {exc}"})
            print(f"[ERROR] {filename}: {exc}")

    output = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "reports": reports,
        "errors": errors,
        "notes": [
            "Anki schema v18 normalized tables are inspected when present.",
            "Opaque protobuf config blobs are fingerprinted, not decoded.",
            "Note contents are not exported; only media references are counted.",
            "Original APKG files are never modified.",
        ],
    }
    (OUTPUT_DIR / "anki_schema_inspection.json").write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    write_summary(output)

    print("\nInspection complete.")
    print(f"Successful APKGs: {len(reports)}")
    print(f"Errors: {len(errors)}")
    print(f"Output folder: {OUTPUT_DIR}")
    print("Files: anki_schema_inspection.json, anki_schema_summary.txt")
    print("Original APKG files were not modified.")


if __name__ == "__main__":
    main()
