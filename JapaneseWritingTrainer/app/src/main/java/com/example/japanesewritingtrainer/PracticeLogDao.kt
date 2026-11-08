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

    /** Số thẻ mới khác nhau đã được đánh giá trong ngày; các lần Again lặp lại không tính thêm. */
    @Query(
        "SELECT COUNT(DISTINCT cardId) FROM practice_log " +
            "WHERE mode = 'RECALL' AND rating IS NOT NULL AND timestampEpochMillis >= :sinceEpochMillis"
    )
    suspend fun countDistinctNewCardsSince(sinceEpochMillis: Long): Int

    /** Count distinct newly rated cards only from the selected deck. */
    @Query(
        "SELECT COUNT(DISTINCT cardId) FROM practice_log " +
            "WHERE mode = 'RECALL' AND rating IS NOT NULL " +
            "AND timestampEpochMillis >= :sinceEpochMillis AND cardId IN (:cardIds)"
    )
    suspend fun countDistinctNewCardsSince(
        sinceEpochMillis: Long,
        cardIds: List<String>
    ): Int

    @Query("DELETE FROM practice_log")
    suspend fun deleteAll()
}
