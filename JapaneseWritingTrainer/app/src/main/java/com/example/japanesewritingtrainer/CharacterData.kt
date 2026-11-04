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
        val array = JSONArray(text)

        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val strokesJson = o.getJSONArray("strokes")
            val strokes = List(strokesJson.length()) { j ->
                val s = strokesJson.getJSONObject(j)
                StrokeData(order = s.getInt("order"), svgPath = s.getString("svgPath"))
            }.sortedBy { it.order }

            CharacterData(
                id = o.getString("id"),
                character = o.getString("character"),
                type = o.getString("type"),
                group = o.getString("group"),
                reading = o.getString("reading"),
                strokeCount = o.getInt("strokeCount"),
                viewBoxSize = o.optDouble("viewBoxSize", 109.0).toFloat(),
                strokes = strokes
            )
        }
    }
}