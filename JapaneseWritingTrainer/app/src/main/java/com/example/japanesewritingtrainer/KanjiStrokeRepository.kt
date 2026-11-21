package com.example.japanesewritingtrainer

import android.content.Context
import android.util.JsonReader
import org.json.JSONObject
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Joins workbook-generated Kanji metadata with the independent KanjiVG geometry asset.
 * This is static content only; it never reads or writes learner progress.
 */
data class KanjiDataset(
    val metadata: List<KanjiData>,
    val writingCharacters: List<CharacterData>
)

object KanjiStrokeRepository {


    private const val CACHE_CAPACITY = 64
    private val characterCache = object : LinkedHashMap<String, CharacterData>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CharacterData>?): Boolean =
            size > CACHE_CAPACITY
    }

    /** Loads only the lightweight metadata used by the Kanji library. */
    fun loadMetadata(
        context: Context,
        metadataAssetPath: String = "characters/kanji.json"
    ): List<KanjiData> = KanjiRepository.load(context, metadataAssetPath)

    /** Loads writing geometry only when a learning session actually needs it. */
    fun loadWritingCharacters(
        context: Context,
        metadata: List<KanjiData>,
        strokesAssetPath: String = "characters/kanji_strokes.json"
    ): List<CharacterData> {
        val strokesText = context.assets.open(strokesAssetPath).bufferedReader().use { it.readText() }
        return combine(metadata, strokesText)
    }

    /**
     * Streams the stroke asset and materializes only the requested Kanji.
     * Other records are skipped without creating StrokeData objects.
     */
    fun loadCharacter(
        context: Context,
        id: String,
        metadata: KanjiData,
        shardsAssetDirectory: String = "characters/kanji_strokes_shards"
    ): CharacterData {
        synchronized(characterCache) {
            characterCache[id]?.let { return it }
        }

        val suffix = id.removePrefix("kanji_")
        require(suffix.matches(Regex("[0-9a-f]{4,6}"))) { "Invalid Kanji ID: $id" }
        val shardKey = (suffix.toLong(16) and 0xF).toString(16)
        val shardPath = "$shardsAssetDirectory/$shardKey.json"
        val result = context.assets.open(shardPath).use { input ->
            JsonReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                findCharacter(reader, id, metadata)
                    ?: error("Kanji stroke geometry not found for $id")
            }
        }

        synchronized(characterCache) {
            characterCache[id] = result
        }
        return result
    }

    private fun findCharacter(
        reader: JsonReader,
        requestedId: String,
        metadata: KanjiData
    ): CharacterData? {
        var schemaVersion: Int? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "characters" -> {
                    require(schemaVersion == 1) { "Unsupported Kanji stroke dataset schema" }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val candidate = readCharacterRecord(reader, requestedId, metadata)
                        if (candidate != null) return candidate
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(schemaVersion == 1) { "Unsupported Kanji stroke dataset schema" }
        return null
    }

    private fun readCharacterRecord(
        reader: JsonReader,
        requestedId: String,
        metadata: KanjiData
    ): CharacterData? {
        reader.beginObject()
        require(reader.hasNext() && reader.nextName() == "id") {
            "Kanji stroke records must start with an ID"
        }
        val recordId = reader.nextString()
        if (recordId != requestedId) {
            while (reader.hasNext()) {
                reader.nextName()
                reader.skipValue()
            }
            reader.endObject()
            return null
        }

        var glyph: String? = null
        var viewBoxSize: Float? = null
        var strokeCount: Int? = null
        var strokes: List<StrokeData>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "character" -> glyph = reader.nextString()
                "viewBoxSize" -> viewBoxSize = reader.nextDouble().toFloat()
                "strokeCount" -> strokeCount = reader.nextInt()
                "strokes" -> {
                    val parsed = mutableListOf<StrokeData>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var order: Int? = null
                        var path: String? = null
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "order" -> order = reader.nextInt()
                                "svgPath" -> path = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        parsed += StrokeData(
                            order = requireNotNull(order) { "Missing stroke order for $requestedId" },
                            svgPath = requireNotNull(path) { "Missing SVG path for $requestedId" }
                        )
                    }
                    reader.endArray()
                    strokes = parsed
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val resolvedGlyph = requireNotNull(glyph) { "Missing Kanji glyph for $requestedId" }
        val resolvedViewBox = requireNotNull(viewBoxSize) { "Missing viewBoxSize for $requestedId" }
        val resolvedCount = requireNotNull(strokeCount) { "Missing stroke count for $requestedId" }
        val resolvedStrokes = requireNotNull(strokes) { "Missing strokes for $requestedId" }
        require(resolvedGlyph == metadata.character) { "Kanji glyph mismatch for $requestedId" }
        require(resolvedCount == metadata.strokeCount && resolvedCount == resolvedStrokes.size) {
            "Stroke count mismatch for ${metadata.character}"
        }
        require(resolvedStrokes.map { it.order } == (1..resolvedCount).toList()) {
            "Stroke order must be continuous for ${metadata.character}"
        }
        require(resolvedStrokes.all { it.svgPath.isNotBlank() }) {
            "Empty SVG path for ${metadata.character}"
        }
        require(resolvedViewBox.isFinite() && resolvedViewBox > 0f) {
            "Invalid viewBoxSize for ${metadata.character}"
        }
        val reading = listOfNotNull(metadata.onyomi, metadata.kunyomi)
            .joinToString(" · ")
            .ifBlank { "Chưa có âm đọc" }
        return CharacterData(
            id = metadata.id,
            character = metadata.character,
            type = "kanji",
            group = metadata.jlpt ?: "unclassified",
            reading = reading,
            strokeCount = resolvedCount,
            viewBoxSize = resolvedViewBox,
            strokes = resolvedStrokes
        )
    }

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
