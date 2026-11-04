package com.example.japanesewritingtrainer

enum class CardStatus {
    NEW,
    LEARNING,
    REVIEW
}

/** Một Deck là một bộ thẻ có thể học độc lập. */
data class WritingDeck(
    val id: String,
    val name: String,
    val description: String,
    val characterType: String,
    val cardIds: List<String>
) {
    val cardCount: Int
        get() = cardIds.size
}

/**
 * Nội dung tĩnh của một Card.
 * Card tham chiếu characterId để lấy SVG từ CharacterData, không sao chép SVG.
 */
data class WritingCard(
    val id: String,
    val deckId: String,
    val characterId: String,
    val character: String,
    val reading: String,
    val group: String,
    val strokeCount: Int,
    val order: Int
)

/**
 * Tiến độ của một Card, độc lập với nội dung Card.
 * Các trường interval/easeFactor là dữ liệu nền cho scheduler ở giai đoạn sau.
 */
data class CardProgress(
    val cardId: String,
    val status: CardStatus = CardStatus.NEW,
    val dueAtEpochMillis: Long? = null,
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val lastReviewedAtEpochMillis: Long? = null
) {
    init {
        require(cardId.isNotBlank()) { "cardId must not be blank" }
        require(intervalDays >= 0.0) { "intervalDays must not be negative" }
        require(easeFactor > 0.0) { "easeFactor must be positive" }
        require(repetitions >= 0) { "repetitions must not be negative" }
        require(lapses >= 0) { "lapses must not be negative" }
        require(reviewCount >= 0) { "reviewCount must not be negative" }
        require(correctCount in 0..reviewCount) {
            "correctCount must be between 0 and reviewCount"
        }
    }

    fun isDue(nowEpochMillis: Long): Boolean =
        status != CardStatus.NEW &&
            dueAtEpochMillis != null &&
            dueAtEpochMillis <= nowEpochMillis

    val accuracy: Double
        get() = if (reviewCount == 0) 0.0 else correctCount.toDouble() / reviewCount
}
