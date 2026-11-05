package com.example.japanesewritingtrainer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PracticeLogDao {
    @Insert
    suspend fun insert(log: PracticeLogEntity): Long

    @Insert
    suspend fun insertAll(logs: List<PracticeLogEntity>)

    @Query("SELECT * FROM practice_log ORDER BY timestampEpochMillis DESC, id DESC")
    suspend fun getAll(): List<PracticeLogEntity>

    @Query("SELECT * FROM practice_log WHERE cardId = :cardId ORDER BY timestampEpochMillis DESC, id DESC")
    suspend fun getByCardId(cardId: String): List<PracticeLogEntity>

    @Query("SELECT COUNT(*) FROM practice_log")
    suspend fun count(): Int

    @Query("DELETE FROM practice_log")
    suspend fun deleteAll()
}
