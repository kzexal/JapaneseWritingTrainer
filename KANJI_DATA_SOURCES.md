# Kanji dataset sources and attribution

The Kanji dataset contains 1,031 characters through JLPT N2 (86 existing N5 records plus 945 additions from N4–N2).

## Metadata and JLPT classification

- **OpenJLPT**: https://github.com/evanclan/OpenJLPT
- Provides the N4, N3 and N2 kanji classification, readings, meanings, radical numbers, grade/frequency metadata and example-word lists.
- License: **CC BY-SA 4.0**.
- OpenJLPT derives JLPT levels from Jonathan Waller's community-standard lists. JLPT does not publish official post-2010 kanji lists; levels are unofficial approximations.
- Upstream sources include KANJIDIC2 (EDRDG) and Jonathan Waller's JLPT resources. See OpenJLPT's NOTICE.md for full upstream attribution.

## Stroke geometry

- **KanjiVG**, by Ulrich Apel and contributors: https://kanjivg.tagaini.net/
- The paths are distributed via **kanji-db-data**: https://github.com/uball/kanji-db-data
- KanjiVG license: **CC BY-SA 3.0**; kanji-db-data dataset license: **CC BY-SA 4.0**.
- The stroke paths are ordered and validated against each character's stored stroke count.

## Dataset maintenance

- Existing N5 records and IDs are retained to protect learner progress.
- Stroke counts for six new entries (謎, 餌, 僅, 餅, 遜, 遡) follow the actual KanjiVG path count because it differs from the OpenJLPT count.
- Metadata and geometry are kept in separate assets: `app/src/main/assets/characters/kanji.json` and `kanji_strokes.json`.
