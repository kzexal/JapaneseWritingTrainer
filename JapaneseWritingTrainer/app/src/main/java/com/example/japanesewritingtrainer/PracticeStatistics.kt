package com.example.japanesewritingtrainer

/** Thống kê tổng hợp từ lịch sử luyện viết của một ký tự. */
data class CharacterPracticeStats(
    val cardId: String,
    val totalAttempts: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val totalDurationMs: Long,
    val lastPracticedAtEpochMillis: Long
) {
    val successfulAttempts: Int
        get() = goodCount + easyCount

    /** Chỉ các lần có đánh giá mới tham gia tỷ lệ Again/Good/Easy. */
    val ratedAttempts: Int
        get() = againCount + hardCount + goodCount + easyCount

    val againRate: Double
        get() = if (ratedAttempts == 0) 0.0 else againCount.toDouble() / ratedAttempts

    val successRate: Double
        get() = if (ratedAttempts == 0) 0.0 else successfulAttempts.toDouble() / ratedAttempts

    val averageDurationMs: Long
        get() = if (totalAttempts == 0) 0L else totalDurationMs / totalAttempts
}

/** Tạo thống kê theo cardId; không tự suy diễn kết quả từ điểm nhận diện nét. */
fun buildCharacterPracticeStats(logs: List<PracticeLog>): List<CharacterPracticeStats> =
    logs.groupBy { it.cardId }
        .map { (cardId, cardLogs) ->
            CharacterPracticeStats(
                cardId = cardId,
                totalAttempts = cardLogs.size,
                againCount = cardLogs.count { it.rating == ReviewRating.AGAIN },
                hardCount = cardLogs.count { it.rating == ReviewRating.HARD },
                goodCount = cardLogs.count { it.rating == ReviewRating.GOOD },
                easyCount = cardLogs.count { it.rating == ReviewRating.EASY },
                totalDurationMs = cardLogs.sumOf { it.durationMs },
                lastPracticedAtEpochMillis = cardLogs.maxOf { it.timestampEpochMillis }
            )
        }
        .sortedByDescending { it.lastPracticedAtEpochMillis }

/** Thời lượng luyện viết tổng cộng, bỏ qua thời gian âm nếu dữ liệu cũ không hợp lệ. */
fun totalPracticeDurationMs(logs: List<PracticeLog>): Long =
    logs.sumOf { it.durationMs.coerceAtLeast(0L) }


/** Chuỗi ngày luyện hiện tại và chuỗi dài nhất, tính theo múi giờ thiết bị. */
data class PracticeStreakSummary(
    val currentDays: Int,
    val longestDays: Int
)

fun calculatePracticeStreak(
    logs: List<PracticeLog>,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
): PracticeStreakSummary {
    if (logs.isEmpty()) return PracticeStreakSummary(currentDays = 0, longestDays = 0)

    val activeDates = logs.map {
        java.time.Instant.ofEpochMilli(it.timestampEpochMillis)
            .atZone(zoneId)
            .toLocalDate()
    }.toSet()

    val today = java.time.Instant.ofEpochMilli(nowEpochMillis)
        .atZone(zoneId)
        .toLocalDate()
    var currentDate = when {
        today in activeDates -> today
        today.minusDays(1) in activeDates -> today.minusDays(1)
        else -> null
    }
    var currentStreak = 0
    while (currentDate != null && currentDate in activeDates) {
        currentStreak++
        currentDate = currentDate.minusDays(1)
    }

    val sortedDates = activeDates.sorted()
    var longestStreak = 1
    var runningStreak = 1
    for (index in 1 until sortedDates.size) {
        runningStreak = if (sortedDates[index] == sortedDates[index - 1].plusDays(1)) {
            runningStreak + 1
        } else {
            1
        }
        longestStreak = maxOf(longestStreak, runningStreak)
    }

    return PracticeStreakSummary(
        currentDays = currentStreak,
        longestDays = longestStreak
    )
}
