package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeStatisticsTest {

    @Test
    fun groupsAttemptsByCharacterAndCountsRatings() {
        val logs = listOf(
            log("hiragana_a", ReviewRating.AGAIN, 1_000L, 100L),
            log("hiragana_i", ReviewRating.GOOD, 2_000L, 200L),
            log("hiragana_a", ReviewRating.HARD, 3_000L, 300L),
            log("hiragana_a", ReviewRating.EASY, 5_000L, 400L)
        )

        val stats = buildCharacterPracticeStats(logs).associateBy { it.cardId }
        val a = stats.getValue("hiragana_a")
        val i = stats.getValue("hiragana_i")

        assertEquals(3, a.totalAttempts)
        assertEquals(1, a.againCount)
        assertEquals(1, a.hardCount)
        assertEquals(1, a.easyCount)
        assertEquals(1, a.successfulAttempts)
        assertEquals(9_000L, a.totalDurationMs)
        assertEquals(3_000L, a.averageDurationMs)
        assertEquals(400L, a.lastPracticedAtEpochMillis)

        assertEquals(1, i.totalAttempts)
        assertEquals(1, i.goodCount)
        assertEquals(1.0, i.successRate, 0.0)
    }

    @Test
    fun sortsCharactersByMostRecentPractice() {
        val logs = listOf(
            log("hiragana_a", ReviewRating.GOOD, 1_000L, 100L),
            log("hiragana_i", ReviewRating.HARD, 1_000L, 300L),
            log("hiragana_u", ReviewRating.AGAIN, 1_000L, 200L)
        )

        assertEquals(
            listOf("hiragana_i", "hiragana_u", "hiragana_a"),
            buildCharacterPracticeStats(logs).map { it.cardId }
        )
    }

    @Test
    fun emptyHistoryProducesNoStatistics() {
        assertEquals(emptyList<CharacterPracticeStats>(), buildCharacterPracticeStats(emptyList()))
        assertEquals(0L, totalPracticeDurationMs(emptyList()))
    }

    private fun log(
        cardId: String,
        rating: ReviewRating,
        durationMs: Long,
        timestamp: Long
    ) = PracticeLog(
        cardId = cardId,
        mode = PracticeMode.REVIEW,
        rating = rating,
        durationMs = durationMs,
        timestampEpochMillis = timestamp
    )
}
