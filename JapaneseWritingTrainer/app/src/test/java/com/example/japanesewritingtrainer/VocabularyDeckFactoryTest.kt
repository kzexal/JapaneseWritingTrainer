package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyDeckFactoryTest {

    private fun character(glyph: String, type: String = "hiragana", strokes: Int = 1) =
        CharacterData(
            id = "${type}_${glyph}",
            character = glyph,
            type = type,
            group = "test",
            reading = glyph,
            strokeCount = strokes,
            viewBoxSize = 109f,
            strokes = (1..strokes).map {
                StrokeData(order = it, svgPath = "M 0 0 L 10 10")
            }
        )

    private fun vocabulary(
        id: String = "vocab_3042",
        japanese: String = "あ",
        reading: String = "あ",
        group: String? = "01. Test"
    ) = VocabularyData(
        id = id,
        japanese = japanese,
        reading = reading,
        vietnamese = "test",
        example = null,
        exampleMeaning = null,
        group = group,
        radicals = null,
        mnemonics = null,
        jlpt = null,
        notes = null,
        tags = null
    )

    @Test
    fun createsWritingCardsOnlyForFullySupportedEntries() {
        val supported = vocabulary()
        val unsupported = vocabulary(
            id = "vocab_30b3_30fc_30d2_30fc",
            japanese = "コーヒー",
            reading = "こーひー"
        )
        val catalog = VocabularyDeckFactory.fromVocabulary(
            vocabulary = listOf(supported, unsupported),
            availableCharacters = listOf(
                character("あ"),
                character("コ", "katakana"),
                character("ヒ", "katakana")
            ),
            target = VocabularyWritingTarget.JAPANESE
        )

        assertEquals(listOf("vocab_japanese_3042"), catalog.deck.cardIds)
        assertEquals(1, catalog.cards.size)
        assertEquals(1, catalog.unsupportedEntries.size)
        assertEquals(listOf("ー"), catalog.unsupportedEntries.single().unsupportedSymbols)
    }

    @Test
    fun japaneseAndReadingTargetsHaveIndependentStableCardIds() {
        val item = vocabulary(
            id = "vocab_6d77",
            japanese = "海",
            reading = "うみ"
        )
        val characters = listOf(
            character("海", "kanji", 9),
            character("う"),
            character("み")
        )

        val japanese = VocabularyDeckFactory.fromVocabulary(
            listOf(item), characters, VocabularyWritingTarget.JAPANESE
        )
        val reading = VocabularyDeckFactory.fromVocabulary(
            listOf(item), characters, VocabularyWritingTarget.READING
        )

        assertEquals("vocab_japanese_6d77", japanese.cards.single().id)
        assertEquals("vocab_reading_6d77", reading.cards.single().id)
        assertFalse(japanese.cards.single().id == reading.cards.single().id)
        assertEquals(listOf("海"), japanese.sequencesByCardId.values.single().characters.map { it.character })
        assertEquals(listOf("う", "み"), reading.sequencesByCardId.values.single().characters.map { it.character })
    }

    @Test
    fun preservesRepeatedGlyphsAndCountsAllStrokes() {
        val item = vocabulary(
            id = "vocab_3042_3042",
            japanese = "ああ",
            reading = "ああ"
        )
        val catalog = VocabularyDeckFactory.fromVocabulary(
            listOf(item),
            listOf(character("あ", strokes = 2)),
            VocabularyWritingTarget.JAPANESE
        )

        assertEquals(listOf("あ", "あ"), catalog.sequencesByCardId.values.single().characters.map { it.character })
        assertEquals(4, catalog.cards.single().strokeCount)
    }

    @Test
    fun keepsOriginalVocabularyIdForContentLookupAndStableOrder() {
        val first = vocabulary(id = "vocab_3042", japanese = "あ")
        val second = vocabulary(id = "vocab_3044", japanese = "い")
        val catalog = VocabularyDeckFactory.fromVocabulary(
            listOf(first, second),
            listOf(character("あ"), character("い")),
            VocabularyWritingTarget.JAPANESE
        )

        assertEquals(listOf("vocab_3042", "vocab_3044"), catalog.cards.map { it.characterId })
        assertEquals(listOf(0, 1), catalog.cards.map { it.order })
        assertTrue(catalog.unsupportedEntries.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateVocabularyIds() {
        val item = vocabulary()
        VocabularyDeckFactory.fromVocabulary(
            listOf(item, item.copy(japanese = "い", id = item.id)),
            listOf(character("あ"), character("い")),
            VocabularyWritingTarget.JAPANESE
        )
    }
}
