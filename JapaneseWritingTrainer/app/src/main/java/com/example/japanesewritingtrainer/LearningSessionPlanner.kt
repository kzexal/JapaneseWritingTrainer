package com.example.japanesewritingtrainer

enum class SessionCardKind {
    REVIEW,
    LEARNING,
    RELEARNING,
    NEW
}

data class LearningSessionCard(
    val card: WritingCard,
    val progress: CardProgress,
    val kind: SessionCardKind
)

/**
 * Một phiên học được tạo từ các Card đến hạn và giới hạn Card mới.
 * Các thẻ đến hạn được xếp trước New, giữ nguyên loại trạng thái để UI/log
 * có thể phân biệt Review, Learning và Relearning.
 */
data class LearningSessionPlan(
    val deckId: String,
    val reviewCards: List<LearningSessionCard>,
    val newCards: List<LearningSessionCard>,
    val remainingNewCount: Int
) {
    val queue: List<LearningSessionCard>
        get() = reviewCards + newCards

    val totalCount: Int
        get() = reviewCards.size + newCards.size

    val isEmpty: Boolean
        get() = totalCount == 0
}

object LearningSessionPlanner {

    fun plan(
        deck: WritingDeck,
        cards: List<WritingCard>,
        progressByCardId: Map<String, CardProgress>,
        nowEpochMillis: Long,
        newCardLimit: Int
    ): LearningSessionPlan {
        require(newCardLimit >= 0) { "newCardLimit must not be negative" }

        val cardsById = cards
            .filter { it.deckId == deck.id }
            .associateBy { it.id }

        // Duyệt theo cardIds để giữ nguyên thứ tự trong Deck.
        val deckCards = deck.cardIds.mapNotNull(cardsById::get)

        val reviewCards = deckCards
            .mapNotNull { card ->
                val progress = progressByCardId[card.id] ?: return@mapNotNull null
                if (!progress.isDue(nowEpochMillis)) return@mapNotNull null

                val kind = when (progress.status) {
                    CardStatus.LEARNING -> SessionCardKind.LEARNING
                    CardStatus.RELEARNING -> SessionCardKind.RELEARNING
                    CardStatus.REVIEW -> SessionCardKind.REVIEW
                    CardStatus.NEW -> error("New cards cannot be due")
                }
                LearningSessionCard(card = card, progress = progress, kind = kind)
            }
            .sortedWith(
                compareBy<LearningSessionCard> { it.progress.dueAtEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.card.order }
            )

        val allNewCards = deckCards.mapNotNull { card ->
            val progress = progressByCardId[card.id]
                ?: CardProgress(cardId = card.id)

            if (progress.status != CardStatus.NEW) return@mapNotNull null

            LearningSessionCard(
                card = card,
                progress = progress,
                kind = SessionCardKind.NEW
            )
        }

        return LearningSessionPlan(
            deckId = deck.id,
            reviewCards = reviewCards,
            newCards = allNewCards.take(newCardLimit),
            remainingNewCount = (allNewCards.size - newCardLimit).coerceAtLeast(0)
        )
    }
}
