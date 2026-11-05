package com.example.japanesewritingtrainer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Một phiên luyện viết; rating có thể null nếu chế độ không yêu cầu tự đánh giá. */
@Entity(
    tableName = "practice_log",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["timestampEpochMillis"])
    ]
)
data class PracticeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardId: String,
    val mode: String,
    val rating: String?,
    val durationMs: Long,
    val timestampEpochMillis: Long
)

enum class PracticeMode {
    TRACE,
    GUIDED,
    RECALL,
    REVIEW,
    FREE
}

data class PracticeLog(
    val id: Long = 0,
    val cardId: String,
    val mode: PracticeMode,
    val rating: ReviewRating?,
    val durationMs: Long,
    val timestampEpochMillis: Long
) {
    init {
        require(cardId.isNotBlank()) { "cardId must not be blank" }
        require(durationMs >= 0) { "durationMs must not be negative" }
        require(timestampEpochMillis >= 0) { "timestampEpochMillis must not be negative" }
    }
}

fun PracticeLog.toEntity() = PracticeLogEntity(
    id = id,
    cardId = cardId,
    mode = mode.name,
    rating = rating?.name,
    durationMs = durationMs,
    timestampEpochMillis = timestampEpochMillis
)

fun PracticeLogEntity.toDomain() = PracticeLog(
    id = id,
    cardId = cardId,
    mode = PracticeMode.valueOf(mode),
    rating = rating?.let { ReviewRating.valueOf(it) },
    durationMs = durationMs,
    timestampEpochMillis = timestampEpochMillis
)

fun WritingMode.toPracticeMode(): PracticeMode = when (this) {
    WritingMode.TRACE -> PracticeMode.TRACE
    WritingMode.GUIDED -> PracticeMode.GUIDED
    WritingMode.RECALL -> PracticeMode.RECALL
}
