package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fsrs6SchedulerEdgeCasesTest {
    private val now = 1_800_000_000_000L

    private fun scheduler() = Fsrs6Scheduler(calculateStates = { _, _, _, _ ->
        FsrsReviewResult(
            again = FsrsReviewState(0.212, 8.0, 1.0),
            hard = FsrsReviewState(0.8, 7.0, 4.0),
            good = FsrsReviewState(2.0, 6.0, 2.0),
            easy = FsrsReviewState(8.0, 3.0, 10.0)
        )
    })

    @Test
    fun learningAgainKeepsCardInLearningWithoutCountingLapse() {
        val learning = CardProgress(
            cardId = "hiragana_ka",
            status = CardStatus.LEARNING,
            stabilityDays = 0.212,
            difficulty = 8.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 60_000L,
            reps = 1,
            reviewCount = 1
        )

        val result = scheduler().review(learning, ReviewRating.AGAIN, now)

        assertEquals(CardStatus.LEARNING, result.status)
        assertEquals(now + 60_000L, result.dueAtEpochMillis)
        assertEquals(0, result.lapses)
        assertEquals(2, result.reps)
        assertEquals(2, result.reviewCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun goodAndEasyGraduateLearningCardsToReview() {
        listOf(ReviewRating.GOOD, ReviewRating.EASY).forEach { rating ->
            val learning = CardProgress(
                cardId = "hiragana_" + rating.name.lowercase(),
                status = CardStatus.LEARNING,
                stabilityDays = 0.5,
                difficulty = 6.0,
                dueAtEpochMillis = now,
                lastReviewedAtEpochMillis = now - 6 * 60_000L
            )

            val result = scheduler().review(learning, rating, now)

            assertEquals(CardStatus.REVIEW, result.status)
            assertTrue(result.dueAtEpochMillis!! > now)
        }
    }

    @Test
    fun reviewWithClockEarlierThanLastReviewDoesNotProduceNegativeElapsedTime() {
        var receivedElapsedDays = -1
        val scheduler = Fsrs6Scheduler(calculateStates = { _, _, elapsed, _ ->
            receivedElapsedDays = elapsed
            FsrsReviewResult(
                again = FsrsReviewState(0.2, 8.0, 1.0),
                hard = FsrsReviewState(0.3, 7.0, 1.0),
                good = FsrsReviewState(1.0, 6.0, 2.0),
                easy = FsrsReviewState(2.0, 4.0, 3.0)
            )
        })
        val previous = CardProgress(
            cardId = "hiragana_ta",
            status = CardStatus.REVIEW,
            stabilityDays = 4.0,
            difficulty = 5.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now + 1_000L
        )

        val result = scheduler.review(previous, ReviewRating.GOOD, now)

        assertEquals(0, receivedElapsedDays)
        assertEquals(0.0, result.elapsedDays, 0.0)
        assertTrue(result.dueAtEpochMillis!! > now)
        assertTrue(result.stabilityDays!! > 0.0)
    }

    @Test
    fun successfulReviewUpdatesAllReviewCountersConsistently() {
        val previous = CardProgress(
            cardId = "hiragana_na",
            status = CardStatus.REVIEW,
            stabilityDays = 2.0,
            difficulty = 5.0,
            dueAtEpochMillis = now,
            lastReviewedAtEpochMillis = now - 2 * 86_400_000L,
            reps = 3,
            lapses = 1,
            reviewCount = 3,
            correctCount = 2
        )

        val result = scheduler().review(previous, ReviewRating.HARD, now)

        assertEquals(4, result.reps)
        assertEquals(4, result.reviewCount)
        assertEquals(3, result.correctCount)
        assertEquals(1, result.lapses)
        assertEquals(now, result.lastReviewedAtEpochMillis)
    }
}
