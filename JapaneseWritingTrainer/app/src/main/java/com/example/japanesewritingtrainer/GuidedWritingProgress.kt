package com.example.japanesewritingtrainer

/**
 * Trạng thái tiến trình của một lượt Guided Writing.
 *
 * Không đánh giá hình dạng nét viết trong MVP. Mỗi thao tác nhấc tay
 * được tính là hoàn thành một nét; việc kiểm tra hình dạng thuộc
 * StrokeMatcher (Level 2) và nằm ngoài phạm vi hiện tại.
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
