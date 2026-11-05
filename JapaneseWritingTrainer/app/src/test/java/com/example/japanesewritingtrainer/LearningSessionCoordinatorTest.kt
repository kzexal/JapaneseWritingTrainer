package com.example.japanesewritingtrainer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningSessionCoordinatorTest {

    private val now = 1_800_000_000_000L
    private val deck = WritingDeck(
        id = "hiragana",
        name = "Hiragana",
        description = "Hiragana writing practice",
        characterType = "hiragana",
        cardIds = listOf("a", "i")
    )
    private val cards = listOf(
        WritingCard("a", deck.id, "a", "あ", "a", "vowels", 3, 0),
        WritingCard("i", deck.id, "i", "い", "i", "vowels", 2, 1)
    )

    @Test
    fun ratingPersistsProgressBeforeAdvancingToNextCard() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = LearningSessionCoordinator(store)

        val started = coordinator.start(deck, cards, now, newCardLimit = 2)
        assertEquals("a", started.current?.card?.id)

        val next = coordinator.rateCurrent(ReviewRating.GOOD, now)

        val saved = store.get("a")
        assertNotNull(saved)
        assertEquals(CardStatus.REVIEW, saved?.status)
        assertEquals(1, saved?.reviewCount)
        assertEquals("i", next.current?.card?.id)
        assertEquals(1, next.completedCount)
    }

    @Test
    fun aNewSessionUsesPersistedDueDatesAndDoesNotRepeatFutureReviews() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = LearningSessionCoordinator(store)

        coordinator.start(deck, cards, now, newCardLimit = 2)
        coordinator.rateCurrent(ReviewRating.GOOD, now)

        val nextSession = coordinator.start(
            deck = deck,
            cards = cards,
            nowEpochMillis = now,
            newCardLimit = 2
        )

        assertEquals(listOf("i"), nextSession.plan.newCards.map { it.card.id })
        assertTrue(nextSession.plan.reviewCards.isEmpty())
        assertEquals(0, nextSession.plan.remainingNewCount)
    }

    @Test
    fun deckProgressSummaryCountsCardsSavedAcrossSessions() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = LearningSessionCoordinator(store)

        coordinator.start(deck, cards, now, newCardLimit = 2)
        coordinator.rateCurrent(ReviewRating.GOOD, now)

        val summary = coordinator.getDeckProgressSummary(deck, cards)

        assertEquals(2, summary.totalCards)
        assertEquals(1, summary.learnedCards)
        assertEquals(1, summary.reviewCards)
        assertEquals(0, summary.learningCards)
        assertEquals(1, summary.newCards)
    }

    @Test
    fun deckProgressSummaryTreatsCardsWithoutSavedRowsAsNew() = runBlocking {
        val coordinator = LearningSessionCoordinator(InMemoryProgressStore())

        val summary = coordinator.getDeckProgressSummary(deck, cards)

        assertEquals(2, summary.totalCards)
        assertEquals(0, summary.learnedCards)
        assertEquals(2, summary.newCards)
    }

    @Test
    fun persistenceFailureDoesNotAdvanceTheSession() = runBlocking {
        val store = InMemoryProgressStore().apply { failWrites = true }
        val coordinator = LearningSessionCoordinator(store)
        coordinator.start(deck, cards, now, newCardLimit = 2)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.rateCurrent(ReviewRating.GOOD, now) }
        }

        val snapshot = coordinator.snapshot()
        assertEquals("a", snapshot?.current?.card?.id)
        assertEquals(0, snapshot?.completedCount)
        assertEquals(LearningSessionStatus.IN_PROGRESS, snapshot?.status)
    }

    @Test
    fun cannotRateAfterSessionHasCompleted() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = LearningSessionCoordinator(store)
        coordinator.start(deck, cards, now, newCardLimit = 1)

        coordinator.rateCurrent(ReviewRating.GOOD, now)

        assertTrue(coordinator.snapshot()!!.status == LearningSessionStatus.COMPLETED)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.rateCurrent(ReviewRating.GOOD, now) }
        }
        assertEquals(1, store.getAll().size)
    }

    private class InMemoryProgressStore : CardProgressStore {
        private val values = mutableMapOf<String, CardProgress>()
        var failWrites: Boolean = false

        override suspend fun get(cardId: String): CardProgress? = values[cardId]

        override suspend fun getAll(): List<CardProgress> = values.values.toList()

        override suspend fun save(progress: CardProgress) {
            check(!failWrites) { "Simulated persistence failure" }
            values[progress.cardId] = progress
        }

        override suspend fun saveAll(progress: List<CardProgress>) {
            check(!failWrites) { "Simulated persistence failure" }
            progress.forEach { values[it.cardId] = it }
        }

        override suspend fun delete(cardId: String) {
            values.remove(cardId)
        }

        override suspend fun clearAll() {
            values.clear()
        }
    }
}
