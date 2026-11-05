package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeLogEntityTest {

    @Test
    fun mapsRecallLogToRoomAndBack() {
        val log = PracticeLog(
            id = 12L,
            cardId = "hiragana_a",
            mode = PracticeMode.RECALL,
            rating = ReviewRating.GOOD,
            durationMs = 8_500L,
            timestampEpochMillis = 1_800_000_000_000L
        )

        assertEquals(log, log.toEntity().toDomain())
    }

    @Test
    fun mapsOptionalRatingAsNull() {
        val entity = PracticeLogEntity(
            cardId = "hiragana_i",
            mode = PracticeMode.FREE.name,
            rating = null,
            durationMs = 2_000L,
            timestampEpochMillis = 1_800_000_000_000L
        )

        val log = entity.toDomain()

        assertEquals(PracticeMode.FREE, log.mode)
        assertEquals(null, log.rating)
    }
}
