package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardProgressEntity::class, PracticeLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WritingTrainerDatabase : RoomDatabase() {

    abstract fun cardProgressDao(): CardProgressDao
    abstract fun practiceLogDao(): PracticeLogDao

    companion object {
        /**
         * Migration giữ nguyên dữ liệu tiến độ phiên bản 1 và tạo bảng lịch sử.
         * Không dùng fallbackToDestructiveMigration để tránh mất tiến độ người học.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS practice_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        cardId TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        rating TEXT,
                        durationMs INTEGER NOT NULL,
                        timestampEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_practice_log_cardId ON practice_log(cardId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_practice_log_timestampEpochMillis ON practice_log(timestampEpochMillis)"
                )
            }
        }

        /**
         * Lapses chỉ tăng khi một thẻ Review bị Again. Vì vậy LEARNING có lapses
         * lớn hơn 0 là các thẻ đang học lại sau một lần quên.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE card_progress SET status = 'RELEARNING' WHERE status = 'LEARNING' AND lapses > 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: WritingTrainerDatabase? = null

        fun getInstance(context: Context): WritingTrainerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WritingTrainerDatabase::class.java,
                    "japanese_writing_trainer.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
