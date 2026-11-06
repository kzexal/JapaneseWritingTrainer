package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LearningSessionEngineTest {

    private val deck = WritingDeck(
        id = "japanese_hiragana",
        name = "Hiragana",
        description = "Luyện viết Hiragana",
        characterType = "hiragana",
        cardIds = listOf("a", "i")
    )

    private val cards = listOf(
        WritingCard("a", deck.id, "a", "あ", "a", "a", 3, 0),
        WritingCard("i", deck.id, "i", "い", "i", "a", 2, 1)
    )

    private fun plan() = LearningSessionPlanner.plan(
        deck = deck,
        cards = cards,
        progressByCardId = emptyMap(),
        nowEpochMillis = 100L,
        newCardLimit = 2
    )

    @Test
    fun startsWithFirstCardAndReadyStatus() {
        val engine = LearningSessionEngine(plan())

        assertEquals(LearningSessionStatus.READY, engine.snapshot.status)
        assertEquals("a", engine.snapshot.current?.card?.id)

        val started = engine.start()
        assertEquals(LearningSessionStatus.IN_PROGRESS, started.status)
        assertEquals(0, started.completedCount)
    }

    @Test
    fun completingCardsAdvancesQueueAndCompletesSession() {
        val engine = LearningSessionEngine(plan())
        engine.start()

        val first = engine.completeCurrent(CardProgress(cardId = "a"))
        assertEquals("i", first.current?.card?.id)
        assertEquals(1, first.completedCount)
        assertEquals(0.5f, first.progressFraction)

        val last = engine.completeCurrent(CardProgress(cardId = "i"))
        assertEquals(LearningSessionStatus.COMPLETED, last.status)
        assertEquals(2, last.completedCount)
        assertEquals(1f, last.progressFraction)
        assertNull(last.current)
    }

    @Test
    fun emptyPlanIsCompletedImmediately() {
        val emptyPlan = LearningSessionPlan(
            deckId = deck.id,
            reviewCards = emptyList(),
            newCards = emptyList(),
            remainingNewCount = 0
        )
        val engine = LearningSessionEngine(emptyPlan)

        assertEquals(LearningSessionStatus.COMPLETED, engine.snapshot.status)
        assertNull(engine.start().current)
    }

    @Test
    fun cannotCompleteBeforeStarting() {
        val engine = LearningSessionEngine(plan())

        assertThrows(IllegalStateException::class.java) {
            engine.completeCurrent(CardProgress(cardId = "a"))
        }
    }

    @Test
    fun rejectsProgressForDifferentCard() {
        val engine = LearningSessionEngine(plan())
        engine.start()

        assertThrows(IllegalArgumentException::class.java) {
            engine.completeCurrent(CardProgress(cardId = "i"))
        }
        assertEquals("a", engine.snapshot.current?.card?.id)
    }

    @Test
    fun cannotCompleteCardAfterSessionFinished() {
        val engine = LearningSessionEngine(plan())
        engine.start()
        engine.completeCurrent(CardProgress(cardId = "a"))
        engine.completeCurrent(CardProgress(cardId = "i"))

        assertThrows(IllegalStateException::class.java) {
            engine.completeCurrent(CardProgress(cardId = "i"))
        }
    }

    @Test
    fun retryingDifficultCardsDoesNotMoveSessionProgressBackwards() {
        val singleCardDeck = deck.copy(cardIds = listOf("a"))
        val singleCardPlan = LearningSessionPlanner.plan(
            deck = singleCardDeck,
            cards = cards,
            progressByCardId = emptyMap(),
            nowEpochMillis = 100L,
            newCardLimit = 1
        )
        val engine = LearningSessionEngine(singleCardPlan)
        engine.start()

        repeat(2) {
            engine.completeCurrent(
                updatedProgress = CardProgress(cardId = "a", status = CardStatus.LEARNING),
                requeueCurrent = true
            )
        }
        val parked = engine.completeCurrent(
            updatedProgress = CardProgress(cardId = "a", status = CardStatus.LEARNING),
            markDifficult = true
        )

        assertEquals(1, parked.completedCount)
        assertEquals(1f, parked.progressFraction)

        val retried = engine.retryDifficultCards()

        assertEquals(1, retried.completedCount)
        assertEquals(1f, retried.progressFraction)
        assertEquals("a", retried.current?.card?.id)
    }

}
