package com.example.japanesewritingtrainer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardProgressDao {

    @Query("SELECT * FROM card_progress WHERE cardId = :cardId LIMIT 1")
    suspend fun getByCardId(cardId: String): CardProgressEntity?

    @Query("SELECT * FROM card_progress ORDER BY cardId")
    suspend fun getAll(): List<CardProgressEntity>

    @Query("SELECT * FROM card_progress ORDER BY cardId")
    fun observeAll(): Flow<List<CardProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: CardProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progress: List<CardProgressEntity>)

    @Query("DELETE FROM card_progress WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: String)

    @Query("DELETE FROM card_progress")
    suspend fun deleteAll()
}
