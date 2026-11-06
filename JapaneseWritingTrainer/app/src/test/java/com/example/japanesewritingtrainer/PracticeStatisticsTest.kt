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
    fun unratedFreePracticeDoesNotAffectRatingPercentages() {
        val logs = listOf(
            log("hiragana_a", ReviewRating.AGAIN, 1_000L, 100L),
            log("hiragana_a", ReviewRating.GOOD, 2_000L, 200L),
            PracticeLog(
                cardId = "hiragana_a",
                mode = PracticeMode.TRACE,
                rating = null,
                durationMs = 8_000L,
                timestampEpochMillis = 300L
            )
        )

        val stats = buildCharacterPracticeStats(logs).single()

        assertEquals(3, stats.totalAttempts)
        assertEquals(2, stats.ratedAttempts)
        assertEquals(0.5, stats.againRate, 0.0)
        assertEquals(0.5, stats.successRate, 0.0)
        assertEquals(11_000L, stats.totalDurationMs)
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

    @Test
    fun calculatesCurrentAndLongestPracticeStreaks() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 11, 6)
        val logs = listOf(
            logOn("hiragana_a", today.minusDays(2), zone),
            logOn("hiragana_i", today.minusDays(1), zone),
            logOn("hiragana_u", today, zone),
            logOn("hiragana_e", today.minusDays(4), zone)
        )
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()

        val streak = calculatePracticeStreak(logs, now, zone)

        assertEquals(3, streak.currentDays)
        assertEquals(3, streak.longestDays)
    }

    @Test
    fun currentStreakCanContinueFromYesterdayButExpiresAfterOneMissedDay() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 11, 6)
        val logs = listOf(
            logOn("hiragana_a", today.minusDays(1), zone),
            logOn("hiragana_i", today.minusDays(2), zone),
            logOn("hiragana_u", today.minusDays(4), zone)
        )
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()

        val streak = calculatePracticeStreak(logs, now, zone)

        assertEquals(2, streak.currentDays)
        assertEquals(2, streak.longestDays)
    }

    private fun logOn(
        cardId: String,
        date: java.time.LocalDate,
        zone: java.time.ZoneId
    ) = PracticeLog(
        cardId = cardId,
        mode = PracticeMode.FREE,
        rating = null,
        durationMs = 1_000L,
        timestampEpochMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )

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
