package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSessionPlannerTest {

    private val deck = WritingDeck(
        id = "japanese_hiragana",
        name = "Hiragana",
        description = "Luyện viết Hiragana",
        characterType = "hiragana",
        cardIds = listOf("a", "i", "u", "e")
    )

    private val cards = deck.cardIds.mapIndexed { index, id ->
        WritingCard(
            id = id,
            deckId = deck.id,
            characterId = id,
            character = listOf("あ", "い", "う", "え")[index],
            reading = listOf("a", "i", "u", "e")[index],
            group = "a",
            strokeCount = 1,
            order = index
        )
    }

    @Test
    fun placesDueReviewsBeforeNewCards() {
        val progress = mapOf(
            "i" to CardProgress(cardId = "i", status = CardStatus.REVIEW, dueAtEpochMillis = 1_000L)
        )

        val plan = LearningSessionPlanner.plan(deck, cards, progress, 1_000L, 2)

        assertEquals(listOf("i"), plan.reviewCards.map { it.card.id })
        assertEquals(listOf("a", "u"), plan.newCards.map { it.card.id })
        assertEquals(listOf("i", "a", "u"), plan.queue.map { it.card.id })
    }

    @Test
    fun onlyIncludesCardsThatAreDue() {
        val progress = mapOf(
            "a" to CardProgress(cardId = "a", status = CardStatus.REVIEW, dueAtEpochMillis = 2_000L),
            "i" to CardProgress(cardId = "i", status = CardStatus.LEARNING, dueAtEpochMillis = 500L)
        )

        val plan = LearningSessionPlanner.plan(deck, cards, progress, 1_000L, 0)

        assertEquals(listOf("i"), plan.reviewCards.map { it.card.id })
        assertTrue(plan.newCards.isEmpty())
        assertEquals(SessionCardKind.LEARNING, plan.reviewCards.single().kind)
    }

    @Test
    fun keepsLearningAndRelearningKindsDistinctInDueQueue() {
        val progress = mapOf(
            "a" to CardProgress(cardId = "a", status = CardStatus.LEARNING, dueAtEpochMillis = 500L),
            "i" to CardProgress(cardId = "i", status = CardStatus.RELEARNING, dueAtEpochMillis = 600L, lapses = 1),
            "u" to CardProgress(cardId = "u", status = CardStatus.REVIEW, dueAtEpochMillis = 700L)
        )

        val plan = LearningSessionPlanner.plan(deck, cards, progress, 1_000L, 0)

        assertEquals(
            listOf(SessionCardKind.LEARNING, SessionCardKind.RELEARNING, SessionCardKind.REVIEW),
            plan.reviewCards.map { it.kind }
        )
    }


    @Test
    fun ordersDueCardsByEarliestDueTimeThenDeckOrder() {
        val progress = mapOf(
            "a" to CardProgress(
                cardId = "a",
                status = CardStatus.LEARNING,
                dueAtEpochMillis = 900L
            ),
            "i" to CardProgress(
                cardId = "i",
                status = CardStatus.RELEARNING,
                dueAtEpochMillis = 700L
            ),
            "u" to CardProgress(
                cardId = "u",
                status = CardStatus.REVIEW,
                dueAtEpochMillis = 500L
            ),
            "e" to CardProgress(
                cardId = "e",
                status = CardStatus.REVIEW,
                dueAtEpochMillis = 700L
            )
        )

        val plan = LearningSessionPlanner.plan(
            deck = deck,
            cards = cards,
            progressByCardId = progress,
            nowEpochMillis = 1_000L,
            newCardLimit = 0
        )

        assertEquals(listOf("u", "i", "e", "a"), plan.reviewCards.map { it.card.id })
    }

    @Test
    fun respectsNewCardLimitAndReportsRemainingCards() {
        val plan = LearningSessionPlanner.plan(deck, cards, emptyMap(), 0L, 2)

        assertEquals(listOf("a", "i"), plan.newCards.map { it.card.id })
        assertEquals(2, plan.remainingNewCount)
        assertEquals(2, plan.totalCount)
    }

    @Test
    fun treatsCardsWithoutProgressAsNew() {
        val plan = LearningSessionPlanner.plan(deck, cards, emptyMap(), 0L, 10)

        assertEquals(4, plan.newCards.size)
        assertTrue(plan.newCards.all { it.progress.status == CardStatus.NEW })
    }

    @Test
    fun ignoresCardsThatDoNotBelongToDeck() {
        val otherDeckCard = cards.first().copy(id = "other", deckId = "japanese_katakana", characterId = "other")
        val plan = LearningSessionPlanner.plan(deck, cards + otherDeckCard, emptyMap(), 0L, 10)

        assertEquals(4, plan.newCards.size)
        assertFalse(plan.newCards.any { it.card.id == "other" })
    }

    @Test
    fun emptyDeckProducesEmptySession() {
        val emptyDeck = deck.copy(cardIds = emptyList())
        val plan = LearningSessionPlanner.plan(emptyDeck, cards, emptyMap(), 0L, 10)

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.totalCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeNewCardLimit() {
        LearningSessionPlanner.plan(deck, cards, emptyMap(), 0L, -1)
    }
}
