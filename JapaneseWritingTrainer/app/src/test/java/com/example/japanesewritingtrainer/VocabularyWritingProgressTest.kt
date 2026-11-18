package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyWritingProgressTest {

    @Test
    fun exposesFirstGlyphAndAdvancesOneGlyphAtATime() {
        val sequence = sequenceOf("海", "う", "み")
        var progress = VocabularyWritingProgress(sequence)

        assertEquals("海", progress.currentCharacter?.character)
        assertEquals(0, progress.completedCharacterCount)
        assertFalse(progress.isComplete)

        progress = progress.completeCurrentCharacter()
        assertEquals("う", progress.currentCharacter?.character)
        assertEquals(1, progress.completedCharacterCount)

        progress = progress.completeCurrentCharacter()
        assertEquals("み", progress.currentCharacter?.character)
        assertEquals(2, progress.completedCharacterCount)

        progress = progress.completeCurrentCharacter()
        assertTrue(progress.isComplete)
        assertEquals(3, progress.completedCharacterCount)
        assertNull(progress.currentCharacter)
    }

    @Test
    fun preservesRepeatedGlyphsAsSeparateSteps() {
        val sequence = sequenceOf("あ", "あ")
        var progress = VocabularyWritingProgress(sequence)

        assertEquals("あ", progress.currentCharacter?.character)
        progress = progress.completeCurrentCharacter()
        assertFalse(progress.isComplete)
        assertEquals("あ", progress.currentCharacter?.character)

        progress = progress.completeCurrentCharacter()
        assertTrue(progress.isComplete)
    }

    @Test
    fun completingAnAlreadyCompletedSequenceDoesNotAdvancePastEnd() {
        val progress = VocabularyWritingProgress(sequenceOf("海"))
            .completeCurrentCharacter()

        assertSame(progress, progress.completeCurrentCharacter())
        assertEquals(1, progress.completedCharacterCount)
    }

    @Test
    fun resetReturnsToFirstGlyph() {
        val progress = VocabularyWritingProgress(sequenceOf("海", "う", "み"))
            .completeCurrentCharacter()
            .completeCurrentCharacter()

        val reset = progress.reset()

        assertEquals(0, reset.completedCharacterCount)
        assertEquals("海", reset.currentCharacter?.character)
        assertFalse(reset.isComplete)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSequence() {
        val unsupported = VocabularyWritingSequence(
            target = VocabularyWritingTarget.JAPANESE,
            sourceText = "コー",
            characters = listOf(glyph("コ")),
            unsupportedSymbols = listOf("ー")
        )

        VocabularyWritingProgress(unsupported)
    }

    private fun sequenceOf(vararg glyphs: String): VocabularyWritingSequence {
        val characters = glyphs.mapIndexed { index, value -> glyph(value, index) }
        return VocabularyWritingSequence(
            target = VocabularyWritingTarget.JAPANESE,
            sourceText = glyphs.joinToString(""),
            characters = characters,
            unsupportedSymbols = emptyList()
        )
    }

    private fun glyph(value: String, index: Int = 0) = CharacterData(
        id = "glyph_${index}_${value}",
        character = value,
        type = "test",
        group = "test",
        reading = value,
        strokeCount = 1,
        viewBoxSize = 109f,
        strokes = listOf(StrokeData(order = 1, svgPath = "M 0 0 L 1 1"))
    )
}
