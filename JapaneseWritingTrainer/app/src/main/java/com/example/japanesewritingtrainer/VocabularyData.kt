package com.example.japanesewritingtrainer

import android.content.Context
import org.json.JSONObject

/**
 * Workbook-backed Vocabulary metadata.
 *
 * This is content only: scheduling, review state and practice history remain
 * in the existing learner database and are not part of this model.
 */
data class VocabularyData(
    val id: String,
    val japanese: String,
    val reading: String,
    val vietnamese: String,
    val example: String?,
    val exampleMeaning: String?,
    val group: String?,
    val radicals: String?,
    val mnemonics: String?,
    val jlpt: String?,
    val notes: String?,
    val tags: String?
)

/** Loads and validates generated Vocabulary metadata without touching learner state. */
object VocabularyRepository {

    fun load(
        context: Context,
        assetPath: String = "characters/vocabulary.json"
    ): List<VocabularyData> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parseDataset(text)
    }

    internal fun parseDataset(text: String): List<VocabularyData> {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) {
            "Unsupported Vocabulary dataset schema"
        }
        val records = root.getJSONArray("records")
        require(records.length() > 0) { "Vocabulary dataset must not be empty" }

        val vocabulary = List(records.length()) { index ->
            val item = records.getJSONObject(index)
            val japanese = item.getString("japanese").trim()
            val data = VocabularyData(
                id = item.getString("id").trim(),
                japanese = japanese,
                reading = item.getString("reading").trim(),
                vietnamese = item.getString("vietnamese").trim(),
                example = item.optionalText("example"),
                exampleMeaning = item.optionalText("exampleMeaning"),
                group = item.optionalText("group"),
                radicals = item.optionalText("radicals"),
                mnemonics = item.optionalText("mnemonics"),
                jlpt = item.optionalText("jlpt"),
                notes = item.optionalText("notes"),
                tags = item.optionalText("tags")
            )

            require(data.id.isNotBlank()) { "Vocabulary ID must not be blank" }
            require(data.id == stableVocabularyId(japanese)) {
                "Vocabulary ID does not match Japanese text: ${data.id}"
            }
            require(japanese.isNotBlank()) { "Japanese text must not be blank" }
            require(data.reading.isNotBlank()) { "Reading must not be blank for ${data.id}" }
            require(data.vietnamese.isNotBlank()) {
                "Vietnamese meaning must not be blank for ${data.id}"
            }
            data
        }

        require(vocabulary.map { it.id }.distinct().size == vocabulary.size) {
            "Vocabulary IDs must be unique"
        }
        require(vocabulary.map { it.japanese }.distinct().size == vocabulary.size) {
            "Japanese entries must be unique"
        }
        return vocabulary
    }

    private fun stableVocabularyId(japanese: String): String {
        val codePoints = japanese.codePoints().toArray()
        return "vocab_" + codePoints.joinToString("_") { it.toString(16).padStart(4, '0') }
    }

    private fun JSONObject.optionalText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}

/** Pure search and group-filter operations for the future Vocabulary Library UI. */
object VocabularyLibrary {

    fun filter(
        source: List<VocabularyData>,
        query: String = "",
        group: String? = null
    ): List<VocabularyData> {
        val normalizedQuery = query.trim().lowercase()
        return source.filter { item ->
            (group == null || item.group.equals(group, ignoreCase = true)) &&
                (normalizedQuery.isEmpty() || searchableText(item).contains(normalizedQuery))
        }
    }

    fun availableGroups(source: List<VocabularyData>): List<String> =
        source.mapNotNull { it.group?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

    private fun searchableText(item: VocabularyData): String =
        listOfNotNull(
            item.japanese, item.reading, item.vietnamese, item.example,
            item.exampleMeaning, item.group, item.radicals, item.mnemonics,
            item.jlpt, item.notes, item.tags
        ).joinToString(" ").lowercase()
}
