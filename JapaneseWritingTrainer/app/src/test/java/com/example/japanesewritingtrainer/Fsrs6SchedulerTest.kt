package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fsrs6SchedulerTest {

    private val scheduler = Fsrs6Scheduler()
    private val now = 1_800_000_000_000L

    @Test
    fun forgettingCurveHasNinetyPercentRecallAtOneStability() {
        val recallability = scheduler.retrievability(
            elapsedDays = 10.0,
            stabilityDays = 10.0
        )

        assertEquals(0.9, recallability, 1e-10)
    }

    @Test
    fun newGoodCardStartsWithFsrsInitialMemoryState() {
        val updated = scheduler.review(
            CardProgress(cardId = "hiragana_a"),
            ReviewRating.GOOD,
            now
        )

        assertEquals(CardStatus.REVIEW, updated.status)
        assertEquals(2.3065, updated.stabilityDays!!, 1e-10)
        assertEquals(
            6.4133 - kotlin.math.exp(0.8334 * 2.0) + 1.0,
            updated.difficulty!!,
            1e-10
        )
        assertEquals(now + 2L * 86_400_000L, updated.dueAtEpochMillis)
        assertEquals(1, updated.reviewCount)
        assertEquals(1, updated.correctCount)
    }

    @Test
    fun newAgainCardEntersShortLearningStep() {
        val updated = scheduler.review(
            CardProgress(cardId = "hiragana_a"),
            ReviewRating.AGAIN,
            now
        )

        assertEquals(CardStatus.LEARNING, updated.status)
        assertEquals(0.212, updated.stabilityDays!!, 1e-10)
        assertEquals(now + 60_000L, updated.dueAtEpochMillis)
        assertEquals(0, updated.lapses)
        assertEquals(0, updated.correctCount)
    }

    @Test
    fun newHardCardGetsLearningRetryWhileEasyCardGraduates() {
        val hard = scheduler.review(
            CardProgress(cardId = "hard"),
            ReviewRating.HARD,
            now
        )
        val easy = scheduler.review(
            CardProgress(cardId = "easy"),
            ReviewRating.EASY,
            now
        )

        assertEquals(CardStatus.LEARNING, hard.status)
        assertEquals(now + 6 * 60_000L, hard.dueAtEpochMillis)
        assertEquals(CardStatus.REVIEW, easy.status)
        assertTrue(easy.dueAtEpochMillis!! > hard.dueAtEpochMillis!!)
    }

    @Test
    fun successfulInterdayReviewIncreasesStability() {
        val previous = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            stabilityDays = 3.0,
            difficulty = 5.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 3L * 86_400_000L,
            reps = 2,
            reviewCount = 2,
            correctCount = 2
        )

        val updated = scheduler.review(previous, ReviewRating.GOOD, now)

        assertTrue(updated.stabilityDays!! > 3.0)
        assertEquals(CardStatus.REVIEW, updated.status)
        assertEquals(3, updated.reviewCount)
        assertEquals(3, updated.correctCount)
    }

    @Test
    fun lapseReducesStabilityAndSchedulesRelearning() {
        val previous = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            stabilityDays = 10.0,
            difficulty = 6.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 10L * 86_400_000L,
            reps = 4,
            lapses = 1,
            reviewCount = 4,
            correctCount = 3
        )

        val updated = scheduler.review(previous, ReviewRating.AGAIN, now)

        assertTrue(updated.stabilityDays!! < 10.0)
        assertEquals(CardStatus.LEARNING, updated.status)
        assertEquals(now + 10 * 60_000L, updated.dueAtEpochMillis)
        assertEquals(2, updated.lapses)
        assertEquals(5, updated.reviewCount)
        assertEquals(3, updated.correctCount)
    }

    @Test
    fun sameDayGoodReviewNeverReducesStability() {
        val previous = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            stabilityDays = 0.2,
            difficulty = 7.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 60 * 60_000L
        )

        val updated = scheduler.review(previous, ReviewRating.GOOD, now)

        assertTrue(updated.stabilityDays!! >= 0.2)
    }

    @Test
    fun intervalRespectsDesiredRetentionAndMaximum() {
        val standard = Fsrs6Scheduler(desiredRetention = 0.9)
        val higherRetention = Fsrs6Scheduler(desiredRetention = 0.95)
        val capped = Fsrs6Scheduler(maximumIntervalDays = 30)

        assertTrue(higherRetention.intervalDays(10.0) < standard.intervalDays(10.0))
        assertEquals(30L, capped.intervalDays(10_000.0))
    }
}
