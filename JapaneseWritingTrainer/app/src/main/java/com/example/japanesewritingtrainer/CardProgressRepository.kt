package com.example.japanesewritingtrainer

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository là cổng truy cập tiến độ học.
 * Các lớp UI/session không truy vấn Room trực tiếp.
 */
class CardProgressRepository(context: Context) {

    private val dao = WritingTrainerDatabase
        .getInstance(context.applicationContext)
        .cardProgressDao()

    suspend fun get(cardId: String): CardProgress? =
        dao.getByCardId(cardId)?.toDomain()

    suspend fun getAll(): List<CardProgress> =
        dao.getAll().map(CardProgressEntity::toDomain)

    fun observeAll(): Flow<List<CardProgress>> =
        dao.observeAll().map { entities -> entities.map(CardProgressEntity::toDomain) }

    suspend fun save(progress: CardProgress) {
        dao.upsert(progress.toEntity())
    }

    suspend fun saveAll(progress: List<CardProgress>) {
        dao.upsertAll(progress.map(CardProgress::toEntity))
    }

    suspend fun delete(cardId: String) {
        dao.deleteByCardId(cardId)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }
}
