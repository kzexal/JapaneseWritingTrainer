package com.example.japanesewritingtrainer

/**
 * Trạng thái tiến trình của một lượt Guided/Recall Writing.
 *
 * Chỉ tăng completedStrokeCount khi StrokeMatcher chấp nhận nét hiện tại.
 * Các lần thử không đạt vẫn được giữ trên canvas để người học có thể Undo
 * hoặc xem gợi ý; chúng không làm tiến trình chuyển sang nét tiếp theo.
 */
data class GuidedWritingProgress(
    val completedStrokeCount: Int = 0
) {
    fun canAcceptStroke(totalStrokeCount: Int): Boolean =
        totalStrokeCount > 0 && completedStrokeCount < totalStrokeCount

    fun nextStrokeOrder(totalStrokeCount: Int): Int? =
        if (canAcceptStroke(totalStrokeCount)) completedStrokeCount + 1 else null

    fun isComplete(totalStrokeCount: Int): Boolean =
        totalStrokeCount > 0 && completedStrokeCount >= totalStrokeCount

    fun completeStroke(totalStrokeCount: Int): GuidedWritingProgress {
        if (!canAcceptStroke(totalStrokeCount)) return this
        return copy(completedStrokeCount = completedStrokeCount + 1)
    }

    fun undo(): GuidedWritingProgress =
        copy(completedStrokeCount = (completedStrokeCount - 1).coerceAtLeast(0))

    fun reset(): GuidedWritingProgress = GuidedWritingProgress()
}
