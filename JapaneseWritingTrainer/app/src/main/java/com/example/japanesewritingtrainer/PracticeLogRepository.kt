package com.example.japanesewritingtrainer

import android.content.Context

/** Repository đọc lịch sử luyện viết; ghi log đi cùng cập nhật tiến độ trong transaction. */
class PracticeLogRepository internal constructor(
    private val dao: PracticeLogDao
) {
    constructor(context: Context) : this(
        WritingTrainerDatabase.getInstance(context.applicationContext).practiceLogDao()
    )

    suspend fun getAll(): List<PracticeLog> =
        dao.getAll().map(PracticeLogEntity::toDomain)

    suspend fun getByCardId(cardId: String): List<PracticeLog> =
        dao.getByCardId(cardId).map(PracticeLogEntity::toDomain)

    suspend fun count(): Int = dao.count()

    suspend fun countDistinctNewCardsSince(sinceEpochMillis: Long): Int =
        dao.countDistinctNewCardsSince(sinceEpochMillis)

    suspend fun add(log: PracticeLog): Long = dao.insert(log.toEntity())

    suspend fun clearAll() = dao.deleteAll()
}
