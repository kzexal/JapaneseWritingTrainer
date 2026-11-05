package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fsrs6SchedulerTest {

    private val now = 1_800_000_000_000L

    private fun result(
        again: FsrsReviewState = FsrsReviewState(0.212, 8.0, 1.0),
        hard: FsrsReviewState = FsrsReviewState(0.8, 7.0, 4.0),
        good: FsrsReviewState = FsrsReviewState(2.3065, 6.4, 2.0),
        easy: FsrsReviewState = FsrsReviewState(8.2956, 3.0, 10.0)
    ) = FsrsReviewResult(again, hard, good, easy)

    @Test
    fun newGoodCardUsesUpstreamGoodState() {
        var receivedStability: Double? = -1.0
        var receivedDifficulty: Double? = -1.0
        var receivedElapsedDays = -1
        var receivedRetention = -1.0
        val scheduler = Fsrs6Scheduler(calculateStates = { stability, difficulty, elapsed, retention ->
            receivedStability = stability
            receivedDifficulty = difficulty
            receivedElapsedDays = elapsed
            receivedRetention = retention
            result()
        })

        val updated = scheduler.review(CardProgress(cardId = "hiragana_a"), ReviewRating.GOOD, now)

        assertNull(receivedStability)
        assertNull(receivedDifficulty)
        assertEquals(0, receivedElapsedDays)
        assertEquals(0.9, receivedRetention, 0.0)
        assertEquals(CardStatus.REVIEW, updated.status)
        assertEquals(2.3065, updated.stabilityDays!!, 0.0)
        assertEquals(6.4, updated.difficulty!!, 0.0)
        assertEquals(now + 2L * 86_400_000L, updated.dueAtEpochMillis)
        assertEquals(1, updated.reviewCount)
        assertEquals(1, updated.correctCount)
    }

    @Test
    fun newAgainCardEntersShortLearningStepAndUsesAgainMemoryState() {
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, _, _ -> result() })

        val updated = scheduler.review(CardProgress(cardId = "hiragana_a"), ReviewRating.AGAIN, now)

        assertEquals(CardStatus.LEARNING, updated.status)
        assertEquals(0.212, updated.stabilityDays!!, 0.0)
        assertEquals(8.0, updated.difficulty!!, 0.0)
        assertEquals(now + 60_000L, updated.dueAtEpochMillis)
        assertEquals(0, updated.lapses)
        assertEquals(0, updated.correctCount)
    }

    @Test
    fun newHardCardGetsLearningRetryWhileEasyCardGraduates() {
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, _, _ -> result() })

        val hard = scheduler.review(CardProgress(cardId = "hard"), ReviewRating.HARD, now)
        val easy = scheduler.review(CardProgress(cardId = "easy"), ReviewRating.EASY, now)

        assertEquals(CardStatus.LEARNING, hard.status)
        assertEquals(now + 6 * 60_000L, hard.dueAtEpochMillis)
        assertEquals(CardStatus.REVIEW, easy.status)
        assertEquals(8.2956, easy.stabilityDays!!, 0.0)
        assertEquals(now + 10L * 86_400_000L, easy.dueAtEpochMillis)
    }

    @Test
    fun successfulInterdayReviewPassesElapsedWholeDaysToUpstream() {
        var receivedElapsedDays = -1
        var receivedStability: Double? = null
        val scheduler = Fsrs6Scheduler(calculateStates = { stability, _, elapsed, _ ->
            receivedStability = stability
            receivedElapsedDays = elapsed
            result()
        })
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

        assertEquals(3.0, receivedStability!!, 0.0)
        assertEquals(3, receivedElapsedDays)
        assertEquals(2.3065, updated.stabilityDays!!, 0.0)
        assertEquals(CardStatus.REVIEW, updated.status)
        assertEquals(3, updated.reviewCount)
        assertEquals(3, updated.correctCount)
    }

    @Test
    fun lapseUsesUpstreamAgainStateAndSchedulesRelearning() {
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, _, _ ->
            result(again = FsrsReviewState(0.5, 8.0, 1.0))
        })
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
        assertEquals(CardStatus.RELEARNING, updated.status)
        assertEquals(now + 10 * 60_000L, updated.dueAtEpochMillis)
        assertEquals(2, updated.lapses)
        assertEquals(5, updated.reviewCount)
        assertEquals(3, updated.correctCount)
    }

    @Test
    fun repeatedAgainDuringLearningDoesNotRecalculateFsrsMemory() {
        var calculationCount = 0
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, _, _ ->
            calculationCount++
            result()
        })
        val first = scheduler.review(CardProgress(cardId = "new"), ReviewRating.AGAIN, now)
        val second = scheduler.review(first, ReviewRating.AGAIN, now + 60_000L)

        assertEquals(1, calculationCount)
        assertEquals(CardStatus.LEARNING, second.status)
        assertEquals(first.stabilityDays, second.stabilityDays)
        assertEquals(first.difficulty, second.difficulty)
        assertEquals(0, second.lapses)
    }

    @Test
    fun sameDayReviewUsesZeroElapsedWholeDays() {
        var receivedElapsedDays = -1
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, elapsed, _ ->
            receivedElapsedDays = elapsed
            result(good = FsrsReviewState(0.3, 7.0, 1.0))
        })
        val previous = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            stabilityDays = 0.2,
            difficulty = 7.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 60 * 60_000L
        )

        val updated = scheduler.review(previous, ReviewRating.GOOD, now)

        assertEquals(0, receivedElapsedDays)
        assertEquals(0.3, updated.stabilityDays!!, 0.0)
    }

    @Test
    fun desiredRetentionIsForwardedAndMaximumIntervalIsApplied() {
        var receivedRetention = -1.0
        val scheduler = Fsrs6Scheduler(
            desiredRetention = 0.95,
            maximumIntervalDays = 30,
            calculateStates = { _, _, _, retention ->
                receivedRetention = retention
                result(good = FsrsReviewState(10.0, 5.0, 10_000.0))
            }
        )

        val updated = scheduler.review(CardProgress(cardId = "hiragana_a"), ReviewRating.GOOD, now)

        assertEquals(0.95, receivedRetention, 0.0)
        assertEquals(30.0, updated.scheduledDays, 0.0)
        assertEquals(now + 30L * 86_400_000L, updated.dueAtEpochMillis)
    }
}
