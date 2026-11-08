package com.example.japanesewritingtrainer

import android.content.Context
import org.json.JSONArray

/** Một nét của chữ: thứ tự và đường SVG (hệ tọa độ viewBox của dataset). */
data class StrokeData(
    val order: Int,
    val svgPath: String
)

/** Dữ liệu tĩnh của một ký tự (không thay đổi theo người học). */
data class CharacterData(
    val id: String,
    val character: String,
    val type: String,
    val group: String,
    val reading: String,
    val strokeCount: Int,
    val viewBoxSize: Float,
    val strokes: List<StrokeData>
)

/** Đọc dataset ký tự từ thư mục assets. */
object CharacterRepository {

    fun load(context: Context, assetPath: String): List<CharacterData> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parseDataset(text)
    }

    internal fun parseDataset(text: String): List<CharacterData> {
        val array = JSONArray(text)
        require(array.length() > 0) { "Character dataset must not be empty" }

        val characters = List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val strokesJson = item.getJSONArray("strokes")
            val strokes = List(strokesJson.length()) { j ->
                val stroke = strokesJson.getJSONObject(j)
                StrokeData(
                    order = stroke.getInt("order"),
                    svgPath = stroke.getString("svgPath")
                )
            }

            val character = CharacterData(
                id = item.getString("id"),
                character = item.getString("character"),
                type = item.getString("type"),
                group = item.getString("group"),
                reading = item.getString("reading"),
                strokeCount = item.getInt("strokeCount"),
                viewBoxSize = item.optDouble("viewBoxSize", 109.0).toFloat(),
                strokes = strokes
            )

            require(character.id.isNotBlank()) { "Character ID must not be blank" }
            require(character.character.isNotBlank()) { "Character glyph must not be blank" }
            require(character.reading.isNotBlank()) { "Character reading must not be blank" }
            require(character.group.isNotBlank()) { "Character group must not be blank" }
            require(character.viewBoxSize.isFinite() && character.viewBoxSize > 0f) {
                "Invalid viewBoxSize for ${character.id}"
            }
            require(character.strokeCount == strokes.size && strokes.isNotEmpty()) {
                "strokeCount does not match stroke data for ${character.id}"
            }
            require(strokes.map { it.order } == (1..strokes.size).toList()) {
                "Stroke order must be continuous from 1 for ${character.id}"
            }
            require(strokes.all { it.svgPath.isNotBlank() }) {
                "Empty SVG path for ${character.id}"
            }
            character
        }

        require(characters.map { it.id }.distinct().size == characters.size) {
            "Character IDs must be unique"
        }
        require(characters.map { it.character }.distinct().size == characters.size) {
            "Character glyphs must be unique"
        }
        return characters
    }
}


/** Tên hiển thị thống nhất cho từng bộ ký tự. */
fun characterTypeLabel(type: String): String = when (type.trim().lowercase()) {
    "hiragana" -> "Hiragana"
    "katakana" -> "Katakana"
    "kanji" -> "Kanji"
    else -> type.replaceFirstChar { it.uppercase() }
}
