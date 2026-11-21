package com.example.japanesewritingtrainer

import android.content.Context
import org.json.JSONObject

/**
 * Joins workbook-generated Kanji metadata with the independent KanjiVG geometry asset.
 * This is static content only; it never reads or writes learner progress.
 */
data class KanjiDataset(
    val metadata: List<KanjiData>,
    val writingCharacters: List<CharacterData>
)

object KanjiStrokeRepository {

    /**
     * Reads and validates both Kanji assets once, returning metadata and writing geometry
     * from the same parsed dataset. Call from a background dispatcher.
     */
    fun loadDataset(
        context: Context,
        metadataAssetPath: String = "characters/kanji.json",
        strokesAssetPath: String = "characters/kanji_strokes.json"
    ): KanjiDataset {
        val metadataText = context.assets.open(metadataAssetPath).bufferedReader().use { it.readText() }
        val strokesText = context.assets.open(strokesAssetPath).bufferedReader().use { it.readText() }
        val metadata = KanjiRepository.parseDataset(metadataText)
        return KanjiDataset(
            metadata = metadata,
            writingCharacters = combine(metadata, strokesText)
        )
    }

    fun load(
        context: Context,
        metadataAssetPath: String = "characters/kanji.json",
        strokesAssetPath: String = "characters/kanji_strokes.json"
    ): List<CharacterData> = loadDataset(context, metadataAssetPath, strokesAssetPath).writingCharacters

    internal fun combine(metadata: List<KanjiData>, strokesText: String): List<CharacterData> {
        val root = JSONObject(strokesText)
        require(root.getInt("schemaVersion") == 1) { "Unsupported Kanji stroke dataset schema" }
        val records = root.getJSONArray("characters")
        require(records.length() > 0) { "Kanji stroke dataset must not be empty" }

        val geometryById = (0 until records.length()).associate { index ->
            val item = records.getJSONObject(index)
            val id = item.getString("id")
            require(id.isNotBlank()) { "Kanji stroke ID must not be blank" }
            id to item
        }
        require(geometryById.size == records.length()) { "Kanji stroke IDs must be unique" }
        require(geometryById.keys == metadata.map { it.id }.toSet()) {
            "Kanji metadata and stroke datasets must contain exactly the same IDs"
        }

        val result = metadata.map { item ->
            val geometry = geometryById.getValue(item.id)
            val glyph = geometry.getString("character")
            require(glyph == item.character) { "Kanji glyph mismatch for ${item.id}" }
            val viewBoxSize = geometry.getDouble("viewBoxSize").toFloat()
            val strokesJson = geometry.getJSONArray("strokes")
            val strokes = List(strokesJson.length()) { index ->
                val stroke = strokesJson.getJSONObject(index)
                StrokeData(stroke.getInt("order"), stroke.getString("svgPath"))
            }
            val count = geometry.getInt("strokeCount")
            require(count == item.strokeCount && count == strokes.size) {
                "Stroke count mismatch for ${item.character}: metadata=${item.strokeCount}, geometry=${strokes.size}"
            }
            require(strokes.map { it.order } == (1..count).toList()) {
                "Stroke order must be continuous for ${item.character}"
            }
            require(strokes.all { it.svgPath.isNotBlank() }) {
                "Empty SVG path for ${item.character}"
            }
            require(viewBoxSize.isFinite() && viewBoxSize > 0f) {
                "Invalid viewBoxSize for ${item.character}"
            }
            val reading = listOfNotNull(item.onyomi, item.kunyomi)
                .joinToString(" · ")
                .ifBlank { "Chưa có âm đọc" }
            CharacterData(
                id = item.id,
                character = item.character,
                type = "kanji",
                group = item.jlpt ?: "unclassified",
                reading = reading,
                strokeCount = count,
                viewBoxSize = viewBoxSize,
                strokes = strokes
            )
        }
        require(result.map { it.character }.distinct().size == result.size) {
            "Kanji glyphs must be unique"
        }
        return result
    }
}
