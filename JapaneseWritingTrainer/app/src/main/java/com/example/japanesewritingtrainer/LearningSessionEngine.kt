package com.example.japanesewritingtrainer

import android.content.Context

/**
 * Đánh giá chủ quan sau khi người học đã viết và xem đáp án.
 * Không lấy StrokeMatchResult.score để tự suy ra rating.
 */
enum class ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

/** Hợp đồng scheduler; Fsrs6Scheduler là implementation mặc định của ứng dụng. */
interface CardReviewScheduler {
    fun review(
        progress: CardProgress,
        rating: ReviewRating,
        nowEpochMillis: Long
    ): CardProgress
}

/** Trạng thái vòng đời của một phiên học. */
enum class LearningSessionStatus {
    READY,
    IN_PROGRESS,
    COMPLETED
}

/** Snapshot bất biến để UI quan sát trạng thái phiên học. */
data class LearningSessionSnapshot(
    val plan: LearningSessionPlan,
    val currentIndex: Int,
    val status: LearningSessionStatus
) {
    val current: LearningSessionCard?
        get() = plan.queue.getOrNull(currentIndex)

    val completedCount: Int
        get() = currentIndex.coerceAtMost(plan.totalCount)

    val totalCount: Int
        get() = plan.totalCount

    val progressFraction: Float
        get() = if (totalCount == 0) 1f else completedCount.toFloat() / totalCount
}

/**
 * Điều phối queue của một phiên học.
 * Engine không quyết định lịch ôn và không tự đánh giá nét viết.
 */
class LearningSessionEngine(private val plan: LearningSessionPlan) {
    private var index = 0
    private var sessionStatus = if (plan.isEmpty) {
        LearningSessionStatus.COMPLETED
    } else {
        LearningSessionStatus.READY
    }

    val snapshot: LearningSessionSnapshot
        get() = LearningSessionSnapshot(plan, index, sessionStatus)

    fun start(): LearningSessionSnapshot {
        if (sessionStatus == LearningSessionStatus.READY) {
            sessionStatus = LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }

    fun completeCurrent(updatedProgress: CardProgress): LearningSessionSnapshot {
        check(sessionStatus == LearningSessionStatus.IN_PROGRESS) {
            "Session must be in progress before completing a card"
        }
        val currentCard = snapshot.current ?: error("There is no current card")
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

/**
 * Kết nối session engine với Room repository.
 * Việc tạo phiên và chấm rating đều lưu tiến độ trước khi trả kết quả cho UI.
 */
class LearningSessionCoordinator internal constructor(
    private val progressRepository: CardProgressStore,
    private val scheduler: CardReviewScheduler = Fsrs6Scheduler()
) {
    constructor(
        context: Context,
        scheduler: CardReviewScheduler = Fsrs6Scheduler()
    ) : this(CardProgressRepository(context.applicationContext), scheduler)
    private var engine: LearningSessionEngine? = null

    suspend fun start(
        deck: WritingDeck,
        cards: List<WritingCard>,
        nowEpochMillis: Long,
        newCardLimit: Int
    ): LearningSessionSnapshot {
        val progress = progressRepository.getAll().associateBy { it.cardId }
        val plan = LearningSessionPlanner.plan(
            deck = deck,
            cards = cards,
            progressByCardId = progress,
            nowEpochMillis = nowEpochMillis,
            newCardLimit = newCardLimit
        )
        return LearningSessionEngine(plan).also { engine = it }.start()
    }

    /**
     * Ghi nhận rating cho card hiện tại, lưu Room, rồi chuyển queue.
     * Scheduler là nguồn duy nhất quyết định status và due date mới.
     */
    suspend fun rateCurrent(
        rating: ReviewRating,
        nowEpochMillis: Long
    ): LearningSessionSnapshot {
        val activeEngine = engine ?: error("Start a session before rating a card")
        val current = activeEngine.snapshot.current
            ?: error("There is no current card")

        val updated = scheduler.review(current.progress, rating, nowEpochMillis)
        require(updated.cardId == current.card.id) {
            "Scheduler returned progress for a different card"
        }
        progressRepository.save(updated)
        return activeEngine.completeCurrent(updated)
    }

    /**
     * Đọc tiến độ đã lưu của riêng deck, không phụ thuộc queue của phiên hiện tại.
     * Card chưa có bản ghi progress được tính là NEW.
     */
    suspend fun getDeckProgressSummary(
        deck: WritingDeck,
        cards: List<WritingCard>
    ): DeckProgressSummary {
        val deckCardIds = cards
            .filter { it.deckId == deck.id }
            .map { it.id }
            .toSet()
        val progressById = progressRepository.getAll()
            .filter { it.cardId in deckCardIds }
            .associateBy { it.cardId }

        val deckCards = cards.filter { it.id in deckCardIds }
        val learned = deckCards.count { card ->
            (progressById[card.id]?.status ?: CardStatus.NEW) != CardStatus.NEW
        }
        val learning = deckCards.count {
            progressById[it.id]?.status == CardStatus.LEARNING
        }
        val review = deckCards.count {
            progressById[it.id]?.status == CardStatus.REVIEW
        }

        return DeckProgressSummary(
            totalCards = deckCards.size,
            learnedCards = learned,
            learningCards = learning,
            reviewCards = review,
            newCards = deckCards.size - learned
        )
    }

    fun snapshot(): LearningSessionSnapshot? = engine?.snapshot
}
