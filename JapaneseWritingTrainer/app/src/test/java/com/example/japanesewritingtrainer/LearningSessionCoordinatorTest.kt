package com.example.japanesewritingtrainer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val coordinator = createCoordinator(store)

        val started = coordinator.start(deck, cards, now, newCardLimit = 2)
        assertEquals("a", started.current?.card?.id)

        val next = coordinator.rateCurrent(ReviewRating.GOOD, now + 5_000L)

        val saved = store.get("a")
        assertNotNull(saved)
        assertEquals(CardStatus.REVIEW, saved?.status)
        assertEquals(1, saved?.reviewCount)
        assertEquals("i", next.current?.card?.id)
        assertEquals(1, next.completedCount)
    }

    @Test
    fun ratingCreatesRecallLogWithElapsedWritingTime() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = createCoordinator(store)
        coordinator.start(deck, cards, now, newCardLimit = 2)

        coordinator.rateCurrent(ReviewRating.GOOD, now + 12_500L)

        val log = store.logs.single()
        assertEquals("a", log.cardId)
        assertEquals(PracticeMode.RECALL, log.mode)
        assertEquals(ReviewRating.GOOD, log.rating)
        assertEquals(12_500L, log.durationMs)
        assertEquals(now + 12_500L, log.timestampEpochMillis)
    }

    @Test
    fun aNewSessionUsesPersistedDueDatesAndDoesNotRepeatFutureReviews() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = createCoordinator(store)

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
        val coordinator = createCoordinator(store)

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
        val coordinator = createCoordinator(InMemoryProgressStore())

        val summary = coordinator.getDeckProgressSummary(deck, cards)

        assertEquals(2, summary.totalCards)
        assertEquals(0, summary.learnedCards)
        assertEquals(2, summary.newCards)
    }

    @Test
    fun persistenceFailureDoesNotAdvanceTheSession() = runBlocking {
        val store = InMemoryProgressStore().apply { failWrites = true }
        val coordinator = createCoordinator(store)
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
        val coordinator = createCoordinator(store)
        coordinator.start(deck, cards, now, newCardLimit = 1)

        coordinator.rateCurrent(ReviewRating.GOOD, now)

        assertTrue(coordinator.snapshot()!!.status == LearningSessionStatus.COMPLETED)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.rateCurrent(ReviewRating.GOOD, now) }
        }
        assertEquals(1, store.getAll().size)
    }

    @Test
    fun thirdAgainInOneSessionMovesCardToDifficultQueue() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = createCoordinator(store)
        val singleCardDeck = deck.copy(cardIds = listOf("a"))
        val singleCard = cards.filter { it.id == "a" }

        coordinator.start(singleCardDeck, singleCard, now, newCardLimit = 1)
        coordinator.rateCurrent(ReviewRating.AGAIN, now + 1_000L)
        coordinator.rateCurrent(ReviewRating.AGAIN, now + 2_000L)

        val difficult = coordinator.rateCurrent(ReviewRating.AGAIN, now + 3_000L)

        assertNull(difficult.current)
        assertEquals(LearningSessionStatus.COMPLETED, difficult.status)
        assertEquals(listOf("a"), difficult.difficultCards.map { it.card.id })
        assertEquals(3, store.logs.size)
    }

    @Test
    fun difficultCardCanBeExplicitlyRetriedAndAgainCounterResets() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = createCoordinator(store)
        val singleCardDeck = deck.copy(cardIds = listOf("a"))
        val singleCard = cards.filter { it.id == "a" }

        coordinator.start(singleCardDeck, singleCard, now, newCardLimit = 1)
        repeat(3) { index ->
            coordinator.rateCurrent(ReviewRating.AGAIN, now + (index + 1) * 1_000L)
        }

        val retry = coordinator.retryDifficultCards()
        assertEquals("a", retry.current?.card?.id)
        assertTrue(retry.difficultCards.isEmpty())
        assertEquals(LearningSessionStatus.IN_PROGRESS, retry.status)

        val afterRetryAgain = coordinator.rateCurrent(ReviewRating.AGAIN, now + 4_000L)
        assertEquals("a", afterRetryAgain.current?.card?.id)
        assertTrue(afterRetryAgain.difficultCards.isEmpty())
        assertEquals(LearningSessionStatus.IN_PROGRESS, afterRetryAgain.status)
    }

    @Test
    fun nonAgainRatingResetsConsecutiveAgainCount() = runBlocking {
        val store = InMemoryProgressStore()
        val coordinator = createCoordinator(store)
        val singleCardDeck = deck.copy(cardIds = listOf("a"))
        val singleCard = cards.filter { it.id == "a" }

        coordinator.start(singleCardDeck, singleCard, now, newCardLimit = 1)
        coordinator.rateCurrent(ReviewRating.AGAIN, now + 1_000L)
        coordinator.rateCurrent(ReviewRating.HARD, now + 2_000L)

        // Hard completes this card; a new session resets all session-local counters.
        val nextSession = coordinator.start(singleCardDeck, singleCard, now + 3_000L, newCardLimit = 1)
        assertEquals(LearningSessionStatus.COMPLETED, nextSession.status)
        assertTrue(nextSession.difficultCards.isEmpty())
    }

    /**
     * Coordinator unit tests verify orchestration and persistence, not JNI.
     * FSRS-rs itself is covered by native bridge tests and Android instrumentation
     * tests; using a deterministic scheduler keeps these JVM tests platform-neutral.
     */
    private fun createCoordinator(store: CardProgressStore) =
        LearningSessionCoordinator(store, TestCardReviewScheduler)

    private object TestCardReviewScheduler : CardReviewScheduler {
        override fun review(
            progress: CardProgress,
            rating: ReviewRating,
            nowEpochMillis: Long
        ): CardProgress {
            val dueAt = nowEpochMillis + MILLIS_PER_DAY
            return progress.copy(
                status = CardStatus.REVIEW,
                dueAtEpochMillis = dueAt,
                stabilityDays = 1.0,
                difficulty = 5.0,
                scheduledDays = 1.0,
                elapsedDays = progress.lastReviewedAtEpochMillis?.let {
                    (nowEpochMillis - it).coerceAtLeast(0L).toDouble() / MILLIS_PER_DAY
                } ?: 0.0,
                reps = progress.reps + 1,
                reviewCount = progress.reviewCount + 1,
                correctCount = progress.correctCount +
                    if (rating == ReviewRating.AGAIN) 0 else 1,
                lastReviewedAtEpochMillis = nowEpochMillis
            )
        }

        private const val MILLIS_PER_DAY = 86_400_000L
    }

    private class InMemoryProgressStore : CardProgressStore {
        private val values = mutableMapOf<String, CardProgress>()
        val logs = mutableListOf<PracticeLog>()
        var failWrites: Boolean = false

        override suspend fun get(cardId: String): CardProgress? = values[cardId]

        override suspend fun getAll(): List<CardProgress> = values.values.toList()

        override suspend fun save(progress: CardProgress) {
            check(!failWrites) { "Simulated persistence failure" }
            values[progress.cardId] = progress
        }

        override suspend fun saveReview(progress: CardProgress, log: PracticeLog) {
            check(!failWrites) { "Simulated persistence failure" }
            values[progress.cardId] = progress
            logs += log
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
            logs.clear()
        }
    }
}
