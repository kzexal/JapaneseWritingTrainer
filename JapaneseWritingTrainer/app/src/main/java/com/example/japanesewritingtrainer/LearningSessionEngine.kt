package com.example.japanesewritingtrainer

/**
 * Trạng thái vòng đời của một phiên học.
 */
enum class LearningSessionStatus {
    READY,
    IN_PROGRESS,
    COMPLETED
}

/**
 * Snapshot bất biến để UI quan sát trạng thái phiên học.
 * currentIndex trỏ vào queue; bằng queue.size khi phiên đã hoàn thành.
 */
data class LearningSessionSnapshot(
    val plan: LearningSessionPlan,
    val currentIndex: Int,
    val status: LearningSessionStatus
) {
    val current: LearningSessionCard?
        get() = plan.queue.getOrOrNull(currentIndex)

    val completedCount: Int
        get() = currentIndex.coerceAtMost(plan.totalCount)

    val totalCount: Int
        get() = plan.totalCount

    val progressFraction: Float
        get() = if (totalCount == 0) 1f else completedCount.toFloat() / totalCount

    private fun <T> List<T>.getOrOrNull(index: Int): T? =
        if (index in indices) get(index) else null
}

/**
 * Điều phối queue của một phiên học.
 *
 * Engine không quyết định lịch ôn và không tự đánh giá nét viết.
 * Sau khi người học tự chọn Again/Hard/Good/Easy, tầng scheduler tạo
 * CardProgress mới; engine chỉ nhận tiến độ đã được xử lý và chuyển thẻ.
 */
class LearningSessionEngine(plan: LearningSessionPlan) {

    private var index = 0
    private var sessionStatus = when {
        plan.isEmpty -> LearningSessionStatus.COMPLETED
        else -> LearningSessionStatus.READY
    }

    val snapshot: LearningSessionSnapshot
        get() = LearningSessionSnapshot(
            plan = plan,
            currentIndex = index,
            status = sessionStatus
        )

    private val plan: LearningSessionPlan = plan

    /**
     * Bắt đầu phiên. Phiên rỗng luôn giữ trạng thái COMPLETED.
     */
    fun start(): LearningSessionSnapshot {
        if (sessionStatus == LearningSessionStatus.READY) {
            sessionStatus = LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }

    /**
     * Xác nhận card hiện tại đã được đánh giá và cập nhật tiến độ.
     * Progress phải thuộc đúng card đang hiển thị để tránh lưu nhầm thẻ.
     */
    fun completeCurrent(updatedProgress: CardProgress): LearningSessionSnapshot {
        check(sessionStatus == LearningSessionStatus.IN_PROGRESS) {
            "Session must be in progress before completing a card"
        }
        val currentCard = snapshot.current
            ?: error("There is no current card")

        require(updatedProgress.cardId == currentCard.card.id) {
            "Progress cardId does not match current card"
        }

        index += 1
        sessionStatus = if (index >= plan.totalCount) {
            LearningSessionStatus.COMPLETED
        } else {
            LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }
}
