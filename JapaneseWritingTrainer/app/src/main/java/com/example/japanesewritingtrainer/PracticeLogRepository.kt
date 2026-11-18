package com.example.japanesewritingtrainer

import android.content.Context

/** Repository đọc lịch sử luyện viết; ghi log đi cùng cập nhật tiến độ trong transaction. */
class PracticeLogRepository internal constructor(
    private val dao: PracticeLogDao,
    private val widgetContext: Context? = null
) {
    constructor(context: Context) : this(
        WritingTrainerDatabase.getInstance(context.applicationContext).practiceLogDao(),
        context.applicationContext
    )

    suspend fun getAll(): List<PracticeLog> =
        dao.getAll().map(PracticeLogEntity::toDomain)

    suspend fun getByCardId(cardId: String): List<PracticeLog> =
        dao.getByCardId(cardId).map(PracticeLogEntity::toDomain)

    suspend fun count(): Int = dao.count()

    suspend fun countDistinctNewCardsSince(sinceEpochMillis: Long): Int =
        dao.countDistinctNewCardsSince(sinceEpochMillis)

    suspend fun countDistinctNewCardsSince(
        sinceEpochMillis: Long,
        cardIds: List<String>
    ): Int = if (cardIds.isEmpty()) 0
        else dao.countDistinctNewCardsSince(sinceEpochMillis, cardIds)

    suspend fun add(log: PracticeLog): Long {
        val insertedId = dao.insert(log.toEntity())
        widgetContext?.let(PracticeStreakWidgetProvider::requestUpdate)
        return insertedId
    }

    suspend fun clearAll() = dao.deleteAll()
}
