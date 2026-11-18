package com.example.japanesewritingtrainer

import java.time.Instant
import java.time.ZoneId

/** Immutable data rendered by the Android home-screen widget. */
data class PracticeStreakWidgetState(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val practicedToday: Boolean
)

fun buildPracticeStreakWidgetState(
    logs: List<PracticeLog>,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): PracticeStreakWidgetState {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    val practicedToday = logs.any { log ->
        Instant.ofEpochMilli(log.timestampEpochMillis).atZone(zoneId).toLocalDate() == today
    }
    val streak = calculatePracticeStreak(logs, nowEpochMillis, zoneId)
    return PracticeStreakWidgetState(
        currentStreakDays = streak.currentDays,
        longestStreakDays = streak.longestDays,
        practicedToday = practicedToday
    )
}
