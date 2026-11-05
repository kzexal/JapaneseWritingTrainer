package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @Test
    fun migrationFromVersionOnePreservesProgressAndCreatesPracticeLog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v1-test.db"
        context.deleteDatabase(databaseName)

        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            """
            CREATE TABLE IF NOT EXISTS card_progress (
                cardId TEXT NOT NULL,
                status TEXT NOT NULL,
                dueAtEpochMillis INTEGER,
                stabilityDays REAL,
                difficulty REAL,
                scheduledDays REAL NOT NULL,
                elapsedDays REAL NOT NULL,
                reps INTEGER NOT NULL,
                lapses INTEGER NOT NULL,
                reviewCount INTEGER NOT NULL,
                correctCount INTEGER NOT NULL,
                lastReviewedAtEpochMillis INTEGER,
                PRIMARY KEY(cardId)
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO card_progress (
                cardId, status, dueAtEpochMillis, stabilityDays, difficulty,
                scheduledDays, elapsedDays, reps, lapses, reviewCount,
                correctCount, lastReviewedAtEpochMillis
            ) VALUES ('hiragana_a', 'REVIEW', 123456, 3.0, 5.0, 2.0, 1.0, 2, 0, 2, 1, 100000)
            """.trimIndent()
        )
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, WritingTrainerDatabase::class.java, databaseName)
            .addMigrations(WritingTrainerDatabase.MIGRATION_1_2, WritingTrainerDatabase.MIGRATION_2_3)
            .build()

        try {
            val progress = migrated.cardProgressDao().getByCardId("hiragana_a")
            assertEquals(CardStatus.REVIEW.name, progress?.status)
            assertEquals(0, migrated.practiceLogDao().count())
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersionTwoConvertsLapsedLearningCardsToRelearning() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v2-test.db"
        context.deleteDatabase(databaseName)

        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            """
            CREATE TABLE card_progress (
                cardId TEXT NOT NULL,
                status TEXT NOT NULL,
                dueAtEpochMillis INTEGER,
                stabilityDays REAL,
                difficulty REAL,
                scheduledDays REAL NOT NULL,
                elapsedDays REAL NOT NULL,
                reps INTEGER NOT NULL,
                lapses INTEGER NOT NULL,
                reviewCount INTEGER NOT NULL,
                correctCount INTEGER NOT NULL,
                lastReviewedAtEpochMillis INTEGER,
                PRIMARY KEY(cardId)
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            CREATE TABLE practice_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                cardId TEXT NOT NULL,
                mode TEXT NOT NULL,
                rating TEXT,
                durationMs INTEGER NOT NULL,
                timestampEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacy.execSQL(
            """
            CREATE INDEX index_practice_log_cardId ON practice_log(cardId)
            """.trimIndent()
        )
        legacy.execSQL(
            """
            CREATE INDEX index_practice_log_timestampEpochMillis ON practice_log(timestampEpochMillis)
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO card_progress (cardId, status, scheduledDays, elapsedDays, reps, lapses, reviewCount, correctCount)
            VALUES
                ('first_learning', 'LEARNING', 0.01, 0.0, 2, 0, 2, 0),
                ('lapsed_learning', 'LEARNING', 0.01, 0.0, 5, 1, 5, 2),
                ('review_card', 'REVIEW', 3.0, 2.0, 3, 0, 3, 3)
            """.trimIndent()
        )
        legacy.version = 2
        legacy.close()

        val migrated = Room.databaseBuilder(context, WritingTrainerDatabase::class.java, databaseName)
            .addMigrations(WritingTrainerDatabase.MIGRATION_2_3)
            .build()

        try {
            assertEquals(
                CardStatus.LEARNING.name,
                migrated.cardProgressDao().getByCardId("first_learning")?.status
            )
            assertEquals(
                CardStatus.RELEARNING.name,
                migrated.cardProgressDao().getByCardId("lapsed_learning")?.status
            )
            assertEquals(
                CardStatus.REVIEW.name,
                migrated.cardProgressDao().getByCardId("review_card")?.status
            )
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
}
