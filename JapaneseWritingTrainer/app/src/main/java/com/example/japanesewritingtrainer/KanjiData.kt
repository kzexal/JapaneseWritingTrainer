package com.example.japanesewritingtrainer

import android.content.Context
import org.json.JSONObject

/**
 * Metadata-only Kanji record generated from vocabulary.xlsx.
 * Stroke geometry is intentionally separate and is not available in this dataset yet.
 */
data class KanjiData(
    val id: String,
    val character: String,
    val strokeCount: Int,
    val onyomi: String?,
    val kunyomi: String?,
    val meaning: String?,
    val radical: String?,
    val exampleWords: String?,
    val notes: String?,
    val jlpt: String?,
    val tags: String?
)

/** Loads and validates the generated Kanji metadata asset without touching learner state. */
object KanjiRepository {

    fun load(context: Context, assetPath: String = "characters/kanji.json"): List<KanjiData> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parseDataset(text)
    }

    internal fun parseDataset(text: String): List<KanjiData> {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Unsupported Kanji dataset schema" }
        val records = root.getJSONArray("records")
        require(records.length() > 0) { "Kanji dataset must not be empty" }

        val kanji = List(records.length()) { index ->
            val item = records.getJSONObject(index)
            val character = item.getString("character")
            val data = KanjiData(
                id = item.getString("id"),
                character = character,
                strokeCount = item.getInt("strokeCount"),
                onyomi = item.optionalText("onyomi"),
                kunyomi = item.optionalText("kunyomi"),
                meaning = item.optionalText("meaning"),
                radical = item.optionalText("radical"),
                exampleWords = item.optionalText("exampleWords"),
                notes = item.optionalText("notes"),
                jlpt = item.optionalText("jlpt"),
                tags = item.optionalText("tags")
            )
            require(data.id.isNotBlank()) { "Kanji ID must not be blank" }
            require(character.codePointCount(0, character.length) == 1) {
                "Kanji must contain exactly one Unicode code point: ${data.id}"
            }
            require(data.strokeCount > 0) { "Invalid stroke count for ${data.id}" }
            data
        }

        require(kanji.map { it.id }.distinct().size == kanji.size) {
            "Kanji IDs must be unique"
        }
        require(kanji.map { it.character }.distinct().size == kanji.size) {
            "Kanji characters must be unique"
        }
        return kanji
    }

    private fun JSONObject.optionalText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}


/** Pure search/filter operations for the future Kanji Library UI. */
object KanjiLibrary {

    fun filter(
        source: List<KanjiData>,
        query: String = "",
        jlpt: String? = null,
        radical: String? = null
    ): List<KanjiData> {
        val normalizedQuery = query.trim().lowercase()
        return source.filter { item ->
            (jlpt == null || item.jlpt.equals(jlpt, ignoreCase = true)) &&
                (radical == null || item.radical.equals(radical, ignoreCase = true)) &&
                (normalizedQuery.isEmpty() || searchableText(item).contains(normalizedQuery))
        }
    }

    fun availableJlptLevels(source: List<KanjiData>): List<String> =
        source.mapNotNull { it.jlpt?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

    fun availableRadicals(source: List<KanjiData>): List<String> =
        source.mapNotNull { it.radical?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

    private fun searchableText(item: KanjiData): String =
        listOfNotNull(
            item.character, item.onyomi, item.kunyomi, item.meaning,
            item.radical, item.exampleWords, item.notes, item.jlpt, item.tags
        ).joinToString(" ").lowercase()
}
