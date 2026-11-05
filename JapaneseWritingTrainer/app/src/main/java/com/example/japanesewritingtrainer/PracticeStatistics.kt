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

    val againRate: Double
        get() = if (totalAttempts == 0) 0.0 else againCount.toDouble() / totalAttempts

    val successRate: Double
        get() = if (totalAttempts == 0) 0.0 else successfulAttempts.toDouble() / totalAttempts

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
