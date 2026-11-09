package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyWritingMapperTest {

    private fun character(glyph: String, type: String = "hiragana") = CharacterData(
        id = "${type}_${glyph}",
        character = glyph,
        type = type,
        group = "test",
        reading = glyph,
        strokeCount = 1,
        viewBoxSize = 109f,
        strokes = listOf(StrokeData(order = 1, svgPath = "M 0 0 L 10 10"))
    )

    private fun vocabulary(
        japanese: String = "海",
        reading: String = "うみ"
    ) = VocabularyData(
        id = "test",
        japanese = japanese,
        reading = reading,
        vietnamese = "test",
        example = null,
        exampleMeaning = null,
        group = null,
        radicals = null,
        mnemonics = null,
        jlpt = null,
        notes = null,
        tags = null
    )

    @Test
    fun japaneseTargetUsesJapaneseFieldWithoutKanaSubstitution() {
        val kanji = character("海", type = "kanji")
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "海", reading = "うみ"),
            VocabularyWritingTarget.JAPANESE,
            listOf(character("う"), character("み"), kanji)
        )

        assertEquals("海", sequence.sourceText)
        assertEquals(listOf("海"), sequence.characters.map { it.character })
        assertTrue(sequence.isWritable)
    }

    @Test
    fun readingTargetUsesReadingFieldOnlyWhenExplicitlySelected() {
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "海", reading = "うみ"),
            VocabularyWritingTarget.READING,
            listOf(character("う"), character("み"), character("海", "kanji"))
        )

        assertEquals("うみ", sequence.sourceText)
        assertEquals(listOf("う", "み"), sequence.characters.map { it.character })
        assertTrue(sequence.isWritable)
    }

    @Test
    fun preservesRepeatedCharactersAndOriginalOrder() {
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "ああい", reading = "ああい"),
            VocabularyWritingTarget.JAPANESE,
            listOf(character("あ"), character("い"))
        )

        assertEquals(listOf("あ", "あ", "い"), sequence.characters.map { it.character })
        assertTrue(sequence.isWritable)
    }

    @Test
    fun reportsUnsupportedKanjiInsteadOfSubstitutingKana() {
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "未知", reading = "みち"),
            VocabularyWritingTarget.JAPANESE,
            listOf(character("み"), character("ち"))
        )

        assertEquals(emptyList<CharacterData>(), sequence.characters)
        assertEquals(listOf("未", "知"), sequence.unsupportedSymbols)
        assertFalse(sequence.isWritable)
    }

    @Test
    fun ignoresWhitespaceAndPunctuationSeparators() {
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "あ・い", reading = "あ い"),
            VocabularyWritingTarget.JAPANESE,
            listOf(character("あ"), character("い"))
        )

        assertEquals(listOf("あ", "い"), sequence.characters.map { it.character })
        assertTrue(sequence.unsupportedSymbols.isEmpty())
        assertTrue(sequence.isWritable)
    }

    @Test
    fun rejectsCharactersWithoutValidStrokeGeometry() {
        val noGeometry = character("あ").copy(strokes = emptyList(), strokeCount = 0)
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "あ"),
            VocabularyWritingTarget.JAPANESE,
            listOf(noGeometry)
        )

        assertFalse(sequence.isWritable)
        assertEquals(listOf("あ"), sequence.unsupportedSymbols)
    }

    @Test
    fun reportsUnsupportedLongVowelMarkWhenGeometryIsMissing() {
        val sequence = VocabularyWritingMapper.resolve(
            vocabulary(japanese = "コーヒー"),
            VocabularyWritingTarget.JAPANESE,
            listOf(character("コ", "katakana"), character("ヒ", "katakana"))
        )

        assertEquals(listOf("ー", "フ"), sequence.unsupportedSymbols)
        assertFalse(sequence.isWritable)
    }
}
