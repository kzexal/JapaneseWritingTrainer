package com.example.japanesewritingtrainer

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

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
    val status: LearningSessionStatus,
    val queue: List<LearningSessionCard> = plan.queue,
    val difficultCards: List<LearningSessionCard> = emptyList()
) {
    val current: LearningSessionCard?
        get() = queue.firstOrNull()

    /** Số thẻ đã xử lý trong phiên, bao gồm cả thẻ được tạm xếp vào nhóm khó. */
    val completedCount: Int
        get() = currentIndex.coerceAtMost(plan.totalCount)

    val totalCount: Int
        get() = plan.totalCount

    val progressFraction: Float
        get() = if (totalCount == 0) 1f else completedCount.toFloat() / totalCount
}

/**
 * Điều phối queue của một phiên học.
 * Thẻ Again được đưa xuống cuối queue; thẻ đạt ngưỡng Again được tách vào nhóm khó.
 */
class LearningSessionEngine(private val plan: LearningSessionPlan) {
    private val queue = plan.queue.toMutableList()
    private val difficultCards = mutableListOf<LearningSessionCard>()
    private val completedCardIds = linkedSetOf<String>()

    private var sessionStatus = if (plan.isEmpty) {
        LearningSessionStatus.COMPLETED
    } else {
        LearningSessionStatus.READY
    }

    val snapshot: LearningSessionSnapshot
        get() = LearningSessionSnapshot(
            plan = plan,
            currentIndex = completedCardIds.size + difficultCards.size,
            status = sessionStatus,
            queue = queue.toList(),
            difficultCards = difficultCards.toList()
        )

    fun start(): LearningSessionSnapshot {
        if (sessionStatus == LearningSessionStatus.READY) {
            sessionStatus = LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }

    fun completeCurrent(
        updatedProgress: CardProgress,
        requeueCurrent: Boolean = false,
        markDifficult: Boolean = false
    ): LearningSessionSnapshot {
        check(sessionStatus == LearningSessionStatus.IN_PROGRESS) {
            "Session must be in progress before completing a card"
        }
        val currentCard = queue.firstOrNull() ?: error("There is no current card")
        require(updatedProgress.cardId == currentCard.card.id) {
            "Progress cardId does not match current card"
        }
        require(!(requeueCurrent && markDifficult)) {
            "A card cannot be requeued and marked difficult at the same time"
        }

        queue.removeAt(0)
        val updatedCard = currentCard.copy(
            progress = updatedProgress,
            kind = when (updatedProgress.status) {
                CardStatus.NEW -> SessionCardKind.NEW
                CardStatus.LEARNING -> SessionCardKind.LEARNING
                CardStatus.RELEARNING -> SessionCardKind.RELEARNING
                CardStatus.REVIEW -> SessionCardKind.REVIEW
            }
        )
        when {
            requeueCurrent -> queue.add(updatedCard)
            markDifficult -> difficultCards.add(updatedCard)
            else -> completedCardIds.add(currentCard.card.id)
        }

        sessionStatus = if (queue.isEmpty()) {
            LearningSessionStatus.COMPLETED
        } else {
            LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }

    /** Đưa toàn bộ thẻ khó trở lại cuối queue khi người học chủ động chọn luyện lại. */
    fun retryDifficultCards(): LearningSessionSnapshot {
        check(sessionStatus == LearningSessionStatus.COMPLETED) {
            "Difficult cards can only be retried after the active queue is completed"
        }
        if (difficultCards.isNotEmpty()) {
            queue.addAll(difficultCards)
            difficultCards.clear()
            sessionStatus = LearningSessionStatus.IN_PROGRESS
        }
        return snapshot
    }
}

/**
 * Kết nối session engine với Room repository.
 * Mỗi lần đánh giá lưu tiến độ + practice log trong cùng transaction.
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
    private var currentCardStartedAtEpochMillis: Long? = null
    private val againCountsByCardId = mutableMapOf<String, Int>()
    private val operationInProgress = AtomicBoolean(false)

    suspend fun start(
        deck: WritingDeck,
        cards: List<WritingCard>,
        nowEpochMillis: Long,
        newCardLimit: Int
    ): LearningSessionSnapshot {
        check(operationInProgress.compareAndSet(false, true)) {
            "Another learning-session operation is already in progress"
        }
        try {
            val progress = progressRepository.getAll().associateBy { it.cardId }
            val plan = LearningSessionPlanner.plan(
            deck = deck,
            cards = cards,
            progressByCardId = progress,
            nowEpochMillis = nowEpochMillis,
            newCardLimit = newCardLimit
        )
            againCountsByCardId.clear()
            return LearningSessionEngine(plan).also {
                engine = it
                currentCardStartedAtEpochMillis = nowEpochMillis
            }.start()
        } finally {
            operationInProgress.set(false)
        }
    }

    /**
     * Ghi nhận rating, thời gian luyện và lịch sử; chỉ thay đổi queue sau khi
     * transaction lưu thành công. Again được thử lại tối đa hai lần tự động;
     * lần Again thứ ba chuyển thẻ vào nhóm khó của phiên.
     *
     * A duplicate/concurrent rating while persistence is in flight is ignored;
     * this prevents a double tap from creating duplicate FSRS updates and logs.
     */
    suspend fun rateCurrent(
        rating: ReviewRating,
        nowEpochMillis: Long
    ): LearningSessionSnapshot {
        val activeEngine = engine ?: error("Start a session before rating a card")
        if (!operationInProgress.compareAndSet(false, true)) {
            return activeEngine.snapshot
        }
        try {
            val current = activeEngine.snapshot.current
                ?: error("There is no current card")

        val updated = scheduler.review(current.progress, rating, nowEpochMillis)
        require(updated.cardId == current.card.id) {
            "Scheduler returned progress for a different card"
        }

        val nextAgainCount = if (rating == ReviewRating.AGAIN) {
            (againCountsByCardId[current.card.id] ?: 0) + 1
        } else {
            0
        }
        val shouldRequeue = rating == ReviewRating.AGAIN &&
            nextAgainCount < DIFFICULT_AGAIN_THRESHOLD
        val shouldMarkDifficult = rating == ReviewRating.AGAIN &&
            nextAgainCount >= DIFFICULT_AGAIN_THRESHOLD

        val log = PracticeLog(
            cardId = current.card.id,
            mode = if (current.kind != SessionCardKind.NEW) {
                PracticeMode.REVIEW
            } else {
                PracticeMode.RECALL
            },
            rating = rating,
            durationMs = (nowEpochMillis - (currentCardStartedAtEpochMillis ?: nowEpochMillis))
                .coerceAtLeast(0L),
            timestampEpochMillis = nowEpochMillis
        )

        progressRepository.saveReview(updated, log)
        if (nextAgainCount == 0) {
            againCountsByCardId.remove(current.card.id)
        } else {
            againCountsByCardId[current.card.id] = nextAgainCount
        }

        val next = activeEngine.completeCurrent(
            updatedProgress = updated,
            requeueCurrent = shouldRequeue,
            markDifficult = shouldMarkDifficult
        )
        if (next.current != null) {
            currentCardStartedAtEpochMillis = nowEpochMillis
        }
            return next
        } finally {
            operationInProgress.set(false)
        }
    }

    /** Người học chủ động luyện lại nhóm thẻ khó; bộ đếm Again được đặt lại. */
    fun retryDifficultCards(nowEpochMillis: Long = System.currentTimeMillis()): LearningSessionSnapshot {
        check(!operationInProgress.get()) {
            "Cannot retry difficult cards while another session operation is in progress"
        }
        val activeEngine = engine ?: error("Start a session before retrying difficult cards")
        activeEngine.snapshot.difficultCards.forEach {
            againCountsByCardId.remove(it.card.id)
        }
        val next = activeEngine.retryDifficultCards()
        currentCardStartedAtEpochMillis = nowEpochMillis
        return next
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
            progressById[it.id]?.status in setOf(CardStatus.LEARNING, CardStatus.RELEARNING)
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

    companion object {
        const val DIFFICULT_AGAIN_THRESHOLD = 3
    }
}
