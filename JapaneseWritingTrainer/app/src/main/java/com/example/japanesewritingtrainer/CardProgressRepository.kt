package com.example.japanesewritingtrainer

import android.content.Context
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
}
/**
 * Repository là cổng truy cập tiến độ học.
 * Các lớp UI/session không truy vấn Room trực tiếp.
 */
class CardProgressRepository(context: Context) : CardProgressStore {

    private val dao = WritingTrainerDatabase
        .getInstance(context.applicationContext)
        .cardProgressDao()

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

    override suspend fun delete(cardId: String) {
        dao.deleteByCardId(cardId)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
