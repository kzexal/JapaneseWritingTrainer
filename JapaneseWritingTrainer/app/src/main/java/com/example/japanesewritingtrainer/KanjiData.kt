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

    /** Builds lightweight CharacterData summaries off the main thread for home/review/statistics. */
    fun toCharacterSummaries(metadata: List<KanjiData>): List<CharacterData> =
        metadata.map { item ->
            CharacterData(
                id = item.id,
                character = item.character,
                type = "kanji",
                group = item.jlpt ?: "unclassified",
                reading = listOfNotNull(item.onyomi, item.kunyomi)
                    .joinToString(" · ").ifBlank { "Chưa có âm đọc" },
                strokeCount = item.strokeCount,
                viewBoxSize = 109f,
                strokes = emptyList()
            )
        }

    private fun JSONObject.optionalText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}


/** One Kangxi radical group; variants sharing the same index are shown together. */
data class KanjiRadicalGroup(
    val number: Int,
    val label: String,
    val count: Int
)

/** Search/filter operations for the Kanji Library UI. */
object KanjiLibrary {

    data class Index internal constructor(
        val records: List<KanjiData>,
        internal val searchableTextById: Map<String, String>,
        internal val radicalNumberById: Map<String, Int?>
    )

    /** Precompute normalized search text and radical IDs once when the deck is loaded. */
    fun createIndex(source: List<KanjiData>): Index = Index(
        records = source,
        searchableTextById = source.associate { it.id to searchableText(it) },
        radicalNumberById = source.associate { it.id to radicalNumber(it.radical) }
    )

    fun filter(
        source: List<KanjiData>,
        query: String = "",
        jlpt: String? = null,
        radical: String? = null
    ): List<KanjiData> = filter(createIndex(source), query, jlpt, radical)

    fun filter(
        index: Index,
        query: String = "",
        jlpt: String? = null,
        radical: String? = null
    ): List<KanjiData> {
        val normalizedQuery = query.trim().lowercase()
        val radicalNumber = radical?.let(::radicalNumber)
        return index.records.filter { item ->
            (jlpt == null || item.jlpt.equals(jlpt, ignoreCase = true)) &&
                (radicalNumber == null || index.radicalNumberById[item.id] == radicalNumber) &&
                (normalizedQuery.isEmpty() ||
                    index.searchableTextById[item.id].orEmpty().contains(normalizedQuery))
        }
    }

    fun availableJlptLevels(source: List<KanjiData>): List<String> =
        source.mapNotNull { it.jlpt?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

    fun availableRadicalGroups(source: List<KanjiData>): List<KanjiRadicalGroup> =
        availableRadicalGroups(createIndex(source))

    fun availableRadicalGroups(index: Index): List<KanjiRadicalGroup> =
        index.records.mapNotNull { item ->
            val number = index.radicalNumberById[item.id] ?: return@mapNotNull null
            val glyph = item.radical?.substringBeforeLast("(")?.trim()
                ?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            number to glyph
        }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (number, glyphs) ->
                KanjiRadicalGroup(
                    number = number,
                    label = "${glyphs.distinct().joinToString("・")} ($number)",
                    count = glyphs.size
                )
            }

    fun availableRadicals(source: List<KanjiData>): List<String> =
        availableRadicalGroups(source).map { it.label }

    private val radicalNumberRegex = Regex("""\((\d+)\)\s*$""")

    private fun radicalNumber(value: String?): Int? =
        radicalNumberRegex.find(value.orEmpty())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun searchableText(item: KanjiData): String =
        listOfNotNull(
            item.character, item.onyomi, item.kunyomi, item.meaning,
            item.radical, item.exampleWords, item.notes, item.jlpt, item.tags
        ).joinToString(" ").lowercase()
}
