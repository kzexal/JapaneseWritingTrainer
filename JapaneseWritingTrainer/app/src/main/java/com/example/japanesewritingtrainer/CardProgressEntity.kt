package com.example.japanesewritingtrainer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_progress")
data class CardProgressEntity(
    @PrimaryKey
    val cardId: String,
    val status: String = CardStatus.NEW.name,
    val dueAtEpochMillis: Long? = null,
    val stabilityDays: Double? = null,
    val difficulty: Double? = null,
    val scheduledDays: Double = 0.0,
    val elapsedDays: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val lastReviewedAtEpochMillis: Long? = null
)

fun CardProgress.toEntity() = CardProgressEntity(
    cardId = cardId,
    status = status.name,
    dueAtEpochMillis = dueAtEpochMillis,
    stabilityDays = stabilityDays,
    difficulty = difficulty,
    scheduledDays = scheduledDays,
    elapsedDays = elapsedDays,
    reps = reps,
    lapses = lapses,
    reviewCount = reviewCount,
    correctCount = correctCount,
    lastReviewedAtEpochMillis = lastReviewedAtEpochMillis
)

fun CardProgressEntity.toDomain() = CardProgress(
    cardId = cardId,
    status = CardStatus.valueOf(status),
    dueAtEpochMillis = dueAtEpochMillis,
    stabilityDays = stabilityDays,
    difficulty = difficulty,
    scheduledDays = scheduledDays,
    elapsedDays = elapsedDays,
    reps = reps,
    lapses = lapses,
    reviewCount = reviewCount,
    correctCount = correctCount,
    lastReviewedAtEpochMillis = lastReviewedAtEpochMillis
)
