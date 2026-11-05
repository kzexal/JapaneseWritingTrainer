package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Abstraction để session coordinator có thể kiểm thử độc lập với Room.
 */
interface CardProgressStore {
    suspend fun get(cardId: String): CardProgress?
    suspend fun getAll(): List<CardProgress>
    suspend fun save(progress: CardProgress)
    suspend fun saveAll(progress: List<CardProgress>)
    suspend fun delete(cardId: String)
    suspend fun clearAll()

    /** Mặc định cho fake store; Room override để ghi tiến độ và lịch sử nguyên tử. */
    suspend fun saveReview(progress: CardProgress, log: PracticeLog) {
        save(progress)
    }
}

/**
 * Repository là cổng truy cập tiến độ học.
 * Cập nhật tiến độ và thêm lịch sử review dùng chung một transaction Room.
 */
class CardProgressRepository internal constructor(
    private val dao: CardProgressDao,
    private val practiceLogDao: PracticeLogDao? = null,
    private val database: WritingTrainerDatabase? = null
) : CardProgressStore {

    constructor(context: Context) : this(
        WritingTrainerDatabase.getInstance(context.applicationContext)
    )

    private constructor(database: WritingTrainerDatabase) : this(
        dao = database.cardProgressDao(),
        practiceLogDao = database.practiceLogDao(),
        database = database
    )

    override suspend fun get(cardId: String): CardProgress? =
        dao.getByCardId(cardId)?.toDomain()

    override suspend fun getAll(): List<CardProgress> =
        dao.getAll().map(CardProgressEntity::toDomain)

    fun observeAll(): Flow<List<CardProgress>> =
        dao.observeAll().map { entities -> entities.map(CardProgressEntity::toDomain) }

    override suspend fun save(progress: CardProgress) {
        dao.upsert(progress.toEntity())
    }

    override suspend fun saveAll(progress: List<CardProgress>) {
        dao.upsertAll(progress.map(CardProgress::toEntity))
    }

    override suspend fun saveReview(progress: CardProgress, log: PracticeLog) {
        val historyDao = requireNotNull(practiceLogDao) {
            "PracticeLogDao is required to persist review history"
        }
        val write = suspend {
            dao.upsert(progress.toEntity())
            historyDao.insert(log.toEntity())
        }
        val roomDatabase = database
        if (roomDatabase != null) {
            roomDatabase.withTransaction { write() }
        } else {
            write()
        }
    }

    override suspend fun delete(cardId: String) {
        dao.deleteByCardId(cardId)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
