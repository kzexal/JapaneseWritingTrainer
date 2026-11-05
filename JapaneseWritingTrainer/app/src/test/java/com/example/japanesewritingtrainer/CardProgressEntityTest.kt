package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Test

class CardProgressEntityTest {

    @Test
    fun mapsDomainProgressToEntityAndBackWithoutLosingFields() {
        val original = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            dueAtEpochMillis = 1_735_000_000_000L,
            stabilityDays = 4.5,
            difficulty = 6.2,
            scheduledDays = 4.0,
            elapsedDays = 3.0,
            reps = 7,
            lapses = 2,
            reviewCount = 9,
            correctCount = 7,
            lastReviewedAtEpochMillis = 1_734_000_000_000L
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original, restored)
    }

    @Test
    fun mapsNewProgressWithNullSchedulingFields() {
        val original = CardProgress(cardId = "hiragana_i")

        val entity = original.toEntity()
        val restored = entity.toDomain()

        assertEquals(CardStatus.NEW.name, entity.status)
        assertEquals(null, entity.dueAtEpochMillis)
        assertEquals(null, entity.stabilityDays)
        assertEquals(null, entity.difficulty)
        assertEquals(original, restored)
    }
}
