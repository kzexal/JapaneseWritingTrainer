package com.example.japanesewritingtrainer

/**
 * Which text field the learner wants to reproduce for a Vocabulary entry.
 * Japanese is never silently converted to kana; Reading is an explicit choice.
 */
enum class VocabularyWritingTarget {
    JAPANESE,
    READING
}

/** Resolved writing sequence. Repeated glyphs are intentionally preserved. */
data class VocabularyWritingSequence(
    val target: VocabularyWritingTarget,
    val sourceText: String,
    val characters: List<CharacterData>,
    val unsupportedSymbols: List<String>
) {
    val isWritable: Boolean
        get() = characters.isNotEmpty() && unsupportedSymbols.isEmpty()
}

/**
 * Maps a Vocabulary text field to the existing character datasets.
 *
 * Whitespace and punctuation are separators, not writing cards. Every other
 * Unicode code point must have an exact glyph match with valid stroke geometry.
 * No transliteration, Kanji substitution, or guessed geometry is performed.
 */
object VocabularyWritingMapper {

    fun resolve(
        vocabulary: VocabularyData,
        target: VocabularyWritingTarget,
        availableCharacters: List<CharacterData>
    ): VocabularyWritingSequence {
        val sourceText = when (target) {
            VocabularyWritingTarget.JAPANESE -> vocabulary.japanese
            VocabularyWritingTarget.READING -> vocabulary.reading
        }
        val byGlyph = availableCharacters
            .filter { it.strokes.isNotEmpty() && it.strokeCount == it.strokes.size }
            .associateBy { it.character }

        val resolved = mutableListOf<CharacterData>()
        val unsupported = linkedSetOf<String>()
        var offset = 0
        while (offset < sourceText.length) {
            val codePoint = sourceText.codePointAt(offset)
            val glyph = String(Character.toChars(codePoint))
            offset += Character.charCount(codePoint)

            if (glyph.all { it.isWhitespace() } || glyph.all(::isPunctuation)) {
                continue
            }

            val character = byGlyph[glyph]
            if (character == null) {
                unsupported += glyph
            } else {
                resolved += character
            }
        }

        return VocabularyWritingSequence(
            target = target,
            sourceText = sourceText,
            characters = resolved,
            unsupportedSymbols = unsupported.toList()
        )
    }

    private fun isPunctuation(character: Char): Boolean =
        when (Character.getType(character)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
}
