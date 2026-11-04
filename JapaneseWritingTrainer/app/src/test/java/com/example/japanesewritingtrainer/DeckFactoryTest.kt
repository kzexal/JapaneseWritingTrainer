package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFactoryTest {

    private fun character(
        id: String,
        symbol: String,
        type: String = "hiragana",
        group: String = "a",
        reading: String = "a",
        strokeCount: Int = 1
    ) = CharacterData(
        id = id,
        character = symbol,
        type = type,
        group = group,
        reading = reading,
        strokeCount = strokeCount,
        viewBoxSize = 109f,
        strokes = emptyList()
    )

    @Test
    fun createsOneDeckForEachCharacterType() {
        val catalog = DeckFactory.fromCharacters(
            listOf(
                character("hiragana_a", "あ"),
                character("hiragana_i", "い"),
                character("katakana_a", "ア", type = "katakana")
            )
        )

        assertEquals(2, catalog.decks.size)
        assertEquals(
            listOf("japanese_hiragana", "japanese_katakana"),
            catalog.decks.map { it.id }
        )
    }

    @Test
    fun keepsCharacterGroupAsCardMetadata() {
        val catalog = DeckFactory.fromCharacters(
            listOf(
                character("hiragana_ka", "か", group = "ka"),
                character("hiragana_a", "あ", group = "a")
            )
        )

        val deck = catalog.decks.single()
        assertEquals(2, deck.cardCount)
        assertEquals(listOf("hiragana_a", "hiragana_ka"), deck.cardIds)
        assertEquals("a", catalog.cardsForDeck(deck.id)[0].group)
        assertEquals("ka", catalog.cardsForDeck(deck.id)[1].group)
    }

    @Test
    fun cardReferencesOriginalCharacterIdAndKeepsWritingMetadata() {
        val catalog = DeckFactory.fromCharacters(
            listOf(character("hiragana_a", "あ", reading = "a", strokeCount = 3))
        )

        val card = catalog.cards.single()
        assertEquals("hiragana_a", card.id)
        assertEquals("hiragana_a", card.characterId)
        assertEquals("あ", card.character)
        assertEquals("a", card.reading)
        assertEquals(3, card.strokeCount)
    }

    @Test
    fun emptyInputProducesEmptyCatalog() {
        val catalog = DeckFactory.fromCharacters(emptyList())

        assertTrue(catalog.decks.isEmpty())
        assertTrue(catalog.cards.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateCharacterIds() {
        DeckFactory.fromCharacters(
            listOf(
                character("same_id", "あ"),
                character("same_id", "い")
            )
        )
    }

    @Test
    fun newCardIsNotDueUntilSchedulerAssignsDueDate() {
        val progress = CardProgress(cardId = "hiragana_a")

        assertEquals(CardStatus.NEW, progress.status)
        assertFalse(progress.isDue(nowEpochMillis = 10_000L))
        assertNull(progress.dueAtEpochMillis)
    }

    @Test
    fun reviewCardIsDueAtOrAfterItsDueTime() {
        val dueProgress = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            dueAtEpochMillis = 10_000L
        )

        assertFalse(dueProgress.isDue(nowEpochMillis = 9_999L))
        assertTrue(dueProgress.isDue(nowEpochMillis = 10_000L))
    }

    @Test
    fun calculatesAccuracyFromReviewHistory() {
        val progress = CardProgress(
            cardId = "hiragana_a",
            reviewCount = 4,
            correctCount = 3
        )

        assertEquals(0.75, progress.accuracy, 0.0001)
    }
}
