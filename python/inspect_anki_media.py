from __future__ import annotations

import hashlib
import io
import json
import re
import sqlite3
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

APKG_FILES = [
    "Japanese Grammar.apkg",
    "Japanese Kanji.apkg",
    "Japanese Vocabulary.apkg",
    "English Vocabulary.apkg",
]
SOUND_RE = re.compile(r"\[sound:([^\]]+)\]", re.IGNORECASE)
MEDIA_ATTR_RE = re.compile(r'''(?:src|href)\s*=\s*["']([^"']+)["']''', re.IGNORECASE)
TTS_RE = re.compile(r"\{\{\s*tts\s+([^:}]+):([^}]+)\}\}", re.IGNORECASE)
AUDIO_EXT = {".mp3", ".mp4", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".3gp", ".wma"}
IMAGE_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".avif"}


def read_varint(data: bytes, pos: int):
    value = shift = 0
    while pos < len(data):
        b = data[pos]
        pos += 1
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, pos
        shift += 7
        if shift >= 70:
            raise ValueError("protobuf varint too long")
    raise ValueError("truncated protobuf varint")


def protobuf_fields(data: bytes):
    pos = 0
    while pos < len(data):
        tag, pos = read_varint(data, pos)
        number, wire = tag >> 3, tag & 7
        if number == 0:
            raise ValueError("invalid protobuf field number 0")
        if wire == 0:
            value, pos = read_varint(data, pos)
        elif wire == 1:
            if pos + 8 > len(data): raise ValueError("truncated fixed64")
            value, pos = data[pos:pos+8], pos + 8
        elif wire == 2:
            length, pos = read_varint(data, pos)
            if pos + length > len(data): raise ValueError("truncated length-delimited field")
            value, pos = data[pos:pos+length], pos + length
        elif wire == 5:
            if pos + 4 > len(data): raise ValueError("truncated fixed32")
            value, pos = data[pos:pos+4], pos + 4
        else:
            raise ValueError(f"unsupported protobuf wire type {wire}")
        yield number, wire, value


def decode_media_entries(raw: bytes):
    entries = []
    for number, wire, payload in protobuf_fields(raw):
        if number != 1 or wire != 2:
            continue
        name, size, sha1 = "", None, ""
        for field_no, field_wire, value in protobuf_fields(payload):
            if field_no == 1 and field_wire == 2:
                name = value.decode("utf-8")
            elif field_no == 2 and field_wire == 0:
                size = value
            elif field_no == 3 and field_wire == 2:
                sha1 = value.hex()
        entries.append({"index": len(entries), "filename": name, "declared_size": size, "declared_sha1": sha1})
    return entries


def zstd_decompress(data: bytes) -> bytes:
    try:
        import zstandard
    except ImportError as exc:
        raise RuntimeError("Thiếu zstandard. Chạy: py -m pip install zstandard") from exc
    # Anki's frames may omit the decompressed content-size field. The one-shot
    # decompress() API requires that size unless max_output_size is supplied;
    # stream_reader handles such frames without needing it.
    with zstandard.ZstdDecompressor().stream_reader(io.BytesIO(data)) as reader:
        chunks = []
        while True:
            chunk = reader.read(1024 * 1024)
            if not chunk:
                break
            chunks.append(chunk)
    return b"".join(chunks)


def load_media(archive: zipfile.ZipFile, names: set[str]):
    if "media" not in names:
        return {"format": "missing", "status": "media entry missing", "records": [], "archive_numeric_entries": []}
    raw = archive.read("media")
    # Modern APKG: media table and each numbered payload are separately zstd-framed.
    try:
        decoded = zstd_decompress(raw)
        entries = decode_media_entries(decoded)
        modern = True
        fmt = "modern protobuf + zstd"
    except Exception as modern_error:
        # Legacy APKG uses a UTF-8 JSON object mapping numeric names to filenames.
        try:
            mapping = json.loads(raw.decode("utf-8"))
            if not isinstance(mapping, dict): raise ValueError("legacy media JSON is not an object")
            entries = [{"index": int(k), "filename": str(v), "declared_size": None, "declared_sha1": ""} for k, v in mapping.items()]
            entries.sort(key=lambda x: x["index"])
            modern = False
            fmt = "legacy JSON"
        except Exception as legacy_error:
            return {"format": "unknown", "status": f"cannot decode media manifest (modern: {modern_error}; legacy: {legacy_error})", "records": [], "archive_numeric_entries": []}

    records = []
    for entry in entries:
        idx = str(entry["index"])
        if idx not in names:
            records.append({**entry, "archive_entry": idx, "exists": False, "actual_size": None, "actual_sha1": None, "size_matches": None, "sha1_matches": None, "decode_error": "numeric archive entry missing"})
            continue
        payload = archive.read(idx)
        decode_error = None
        try:
            content = zstd_decompress(payload) if modern else payload
        except Exception as exc:
            content = b""
            decode_error = f"{type(exc).__name__}: {exc}"
        actual_sha1 = hashlib.sha1(content).hexdigest() if not decode_error else None
        actual_size = len(content) if not decode_error else None
        records.append({
            **entry, "archive_entry": idx, "exists": True,
            "actual_size": actual_size, "actual_sha1": actual_sha1,
            "size_matches": (actual_size == entry["declared_size"]) if entry["declared_size"] is not None and actual_size is not None else None,
            "sha1_matches": (actual_sha1.lower() == entry["declared_sha1"].lower()) if entry["declared_sha1"] and actual_sha1 else None,
            "decode_error": decode_error,
        })
    numeric = sorted((n for n in names if n.isdigit()), key=int)
    used = {str(e["index"]) for e in entries}
    return {"format": fmt, "status": "parsed", "records": records, "archive_numeric_entries": numeric, "unmapped_numeric_entries": [n for n in numeric if n not in used]}


def sqlite_from_apkg(archive: zipfile.ZipFile, names: set[str], folder: Path):
    if "collection.anki21b" in names:
        db = zstd_decompress(archive.read("collection.anki21b"))
    elif "collection.anki21" in names:
        db = archive.read("collection.anki21")
    elif "collection.anki2" in names:
        db = archive.read("collection.anki2")
    else:
        raise RuntimeError("Không tìm thấy collection database")
    path = folder / "collection.sqlite"
    path.write_bytes(db)
    return path


def get_columns(conn, table):
    return [r[1] for r in conn.execute(f'PRAGMA table_info("{table}")')]


def get_field_names(conn):
    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    by_mid = {}
    if {"notetypes", "fields"}.issubset(tables):
        nt = {str(r["id"]): r["name"] for r in conn.execute("SELECT id,name FROM notetypes")}
        for row in conn.execute("SELECT ntid,ord,name FROM fields ORDER BY ord"):
            by_mid.setdefault(str(row["ntid"]), []).append(row["name"])
        return by_mid, nt, tables
    if "col" in tables:
        row = conn.execute("SELECT * FROM col LIMIT 1").fetchone()
        if row:
            data = dict(row)
            try: models = json.loads(data.get("models") or "{}")
            except Exception: models = {}
            for mid, model in models.items():
                by_mid[str(mid)] = [f.get("name", "") for f in sorted(model.get("flds", []), key=lambda x: x.get("ord", 0))]
            return by_mid, {str(k): v.get("name", "") for k, v in models.items()}, tables
    return by_mid, {}, tables


def analyze_one(path: Path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        media = load_media(archive, names)
        with tempfile.TemporaryDirectory(prefix="anki_media_audit_") as tmp:
            conn = sqlite3.connect(sqlite_from_apkg(archive, names, Path(tmp)))
            conn.row_factory = sqlite3.Row
            try:
                field_names, note_types, tables = get_field_names(conn)
                refs_audio, refs_image = Counter(), Counter()
                refs_field = defaultdict(Counter)
                sound_tags_by_note = Counter()
                tts_templates = []
                if "notes" in tables:
                    for note in conn.execute("SELECT mid,flds FROM notes"):
                        mid = str(note["mid"])
                        values = str(note["flds"] or "").split("\x1f")
                        names_for_note = field_names.get(mid, [])
                        for i, raw in enumerate(values):
                            field = names_for_note[i] if i < len(names_for_note) else f"field_{i}"
                            text = raw or ""
                            for filename in SOUND_RE.findall(text):
                                refs_audio[filename.strip()] += 1
                                refs_field[field]["audio"] += 1
                                sound_tags_by_note[mid] += 1
                            for filename in MEDIA_ATTR_RE.findall(text):
                                filename = filename.split("?")[0].split("#")[0].strip()
                                ext = Path(filename).suffix.lower()
                                if ext in AUDIO_EXT:
                                    refs_audio[filename] += 1; refs_field[field]["audio"] += 1
                                elif ext in IMAGE_EXT:
                                    refs_image[filename] += 1; refs_field[field]["image"] += 1
                template_config_errors = []
                template_formats_inspected = False
                if "templates" in tables:
                    cols = set(get_columns(conn, "templates"))
                    # Legacy collections store qfmt/afmt as columns. Schema v18
                    # stores both HTML templates inside templates.config protobuf.
                    select = [c for c in ("ntid", "name", "qfmt", "afmt", "config") if c in cols]
                    if "ntid" in select:
                        query = "SELECT " + ",".join(f'"{c}"' for c in select) + " FROM templates"
                        for row in conn.execute(query):
                            row_keys = set(row.keys())
                            formats = {}
                            if "qfmt" in row_keys:
                                formats["qfmt"] = row["qfmt"] or ""
                            if "afmt" in row_keys:
                                formats["afmt"] = row["afmt"] or ""
                            if "config" in row_keys and row["config"]:
                                try:
                                    # Official Anki CardTemplateConfig:
                                    # q_format = field 1, a_format = field 2.
                                    for field_no, wire, value in protobuf_fields(bytes(row["config"])):
                                        if field_no == 1 and wire == 2:
                                            formats["qfmt"] = value.decode("utf-8")
                                        elif field_no == 2 and wire == 2:
                                            formats["afmt"] = value.decode("utf-8")
                                except Exception as exc:
                                    template_config_errors.append({
                                        "notetype": note_types.get(str(row["ntid"]), str(row["ntid"])),
                                        "template": row["name"] if "name" in row_keys else "(unknown)",
                                        "error": f"{type(exc).__name__}: {exc}",
                                    })
                            if formats:
                                template_formats_inspected = True
                            ntid = str(row["ntid"])
                            template_name = row["name"] if "name" in row_keys else "(unnamed template)"
                            for side, template_html in formats.items():
                                for match in TTS_RE.finditer(template_html):
                                    tts_templates.append({
                                        "notetype": note_types.get(ntid, ntid),
                                        "template": template_name,
                                        "side": side,
                                        "language_voice": match.group(1).strip(),
                                        "field": match.group(2).strip(),
                                    })
                by_filename = {r["filename"]: r for r in media.get("records", [])}
                def summarize(counter):
                    out = []
                    for filename, count in counter.most_common():
                        rec = by_filename.get(filename)
                        out.append({"filename": filename, "references": count, "manifest_entry_found": rec is not None, "file_decoded": bool(rec and rec.get("exists") and not rec.get("decode_error")), "archive_index": rec.get("archive_entry") if rec else None})
                    return {"unique_files": len(counter), "total_references": sum(counter.values()), "items": out, "missing_from_manifest": [x["filename"] for x in out if not x["manifest_entry_found"]], "not_decoded": [x["filename"] for x in out if x["manifest_entry_found"] and not x["file_decoded"]]}
                audio_records = [r for r in media.get("records", []) if Path(r.get("filename", "")).suffix.lower() in AUDIO_EXT]
                image_records = [r for r in media.get("records", []) if Path(r.get("filename", "")).suffix.lower() in IMAGE_EXT]
                checks = {
                    "media_files": len(media.get("records", [])),
                    "audio_assets_by_extension": len(audio_records),
                    "image_assets_by_extension": len(image_records),
                    "decoded_assets": sum(1 for r in media.get("records", []) if r.get("exists") and not r.get("decode_error")),
                    "size_mismatches": [r["filename"] for r in media.get("records", []) if r.get("size_matches") is False],
                    "sha1_mismatches": [r["filename"] for r in media.get("records", []) if r.get("sha1_matches") is False],
                    "decode_failures": [{"filename": r["filename"], "error": r.get("decode_error")} for r in media.get("records", []) if r.get("decode_error")],
                }
                return {
                    "file": path.name,
                    "media_format": media.get("format"),
                    "manifest_status": media.get("status"),
                    "media_checks": checks,
                    "audio_references": summarize(refs_audio),
                    "image_references": summarize(refs_image),
                    "reference_counts_by_field": {k: dict(v) for k, v in refs_field.items()},
                    "awesome_tts_template_tags": tts_templates,
                    "template_formats_inspected": template_formats_inspected,
                    "template_config_parse_errors": template_config_errors,
                    "unmapped_numeric_entries": media.get("unmapped_numeric_entries", []),
                    "media_manifest_records": media.get("records", []),
                }
            finally:
                conn.close()


def main():
    base = Path(__file__).resolve().parent
    output_dir = base / "anki_media_audit"
    output_dir.mkdir(exist_ok=True)
    reports, errors = [], []
    for name in APKG_FILES:
        path = base / name
        if not path.exists():
            errors.append({"file": name, "error": "File not found"})
            print(f"[SKIP] {name}: not found")
            continue
        try:
            reports.append(analyze_one(path))
            print(f"[OK] {name}")
        except Exception as exc:
            errors.append({"file": name, "error": f"{type(exc).__name__}: {exc}"})
            print(f"[ERROR] {name}: {exc}")
    result = {"reports": reports, "errors": errors, "notes": ["Read-only audit; APKG files are not modified.", "Modern MediaEntries are decoded using the official Anki protobuf field layout.", "Modern numbered media payloads are independently zstd-decompressed and checked against declared size/SHA1.", "Audio references and AwesomeTTS template tags are counted separately."]}
    (output_dir / "anki_media_audit.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["ANKI MEDIA / AUDIO AUDIT", "=" * 72, "Read-only. No APKG content is modified.", ""]
    for r in reports:
        lines += ["=" * 72, r["file"], f"Format: {r['media_format']}", f"Manifest: {r['manifest_status']}", f"Media files: {r['media_checks']['media_files']}", f"Audio assets: {r['media_checks']['audio_assets_by_extension']}", f"Image assets: {r['media_checks']['image_assets_by_extension']}", f"Decoded assets: {r['media_checks']['decoded_assets']}", f"Audio references: {r['audio_references']['total_references']} across {r['audio_references']['unique_files']} files", f"Audio refs missing from manifest: {len(r['audio_references']['missing_from_manifest'])}", f"Audio assets not decoded: {len(r['audio_references']['not_decoded'])}", f"Size mismatches: {len(r['media_checks']['size_mismatches'])}", f"SHA1 mismatches: {len(r['media_checks']['sha1_mismatches'])}", f"Template formats inspected: {r.get('template_formats_inspected', False)}", f"Template config parse errors: {len(r.get('template_config_parse_errors', []))}", f"AwesomeTTS template tags: {len(r['awesome_tts_template_tags'])}", "Audio references by field:"]
        for field, counts in sorted(r["reference_counts_by_field"].items()):
            if counts.get("audio"): lines.append(f"  {field}: {counts['audio']}")
        if r["awesome_tts_template_tags"]:
            lines.append("AwesomeTTS template tags:")
            for tag in r["awesome_tts_template_tags"]: lines.append(f"  {tag['notetype']} / {tag['template']} / {tag['side']}: tts {tag['language_voice']} -> {tag['field']}")
        lines.append("")
    lines += ["=" * 72, "ERRORS"] + [f"{e['file']}: {e['error']}" for e in errors]
    (output_dir / "anki_media_audit_summary.txt").write_text("\n".join(lines), encoding="utf-8")
    print(f"\nDone. Reports: {output_dir}")


if __name__ == "__main__":
    main()
