package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreManagerTest {

    private lateinit var database: WritingTrainerDatabase
    private lateinit var manager: BackupRestoreManager

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WritingTrainerDatabase::class.java
        ).allowMainThreadQueries().build()
        manager = BackupRestoreManager(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun backupAndRestorePreserveProgressAndPracticeHistory() = runBlocking {
        val progress = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            dueAtEpochMillis = 500_000L,
            stabilityDays = 4.5,
            difficulty = 5.2,
            scheduledDays = 4.0,
            elapsedDays = 3.0,
            reps = 6,
            lapses = 1,
            reviewCount = 5,
            correctCount = 4,
            lastReviewedAtEpochMillis = 100_000L
        )
        val log = PracticeLog(
            id = 12L,
            cardId = "hiragana_a",
            mode = PracticeMode.REVIEW,
            rating = ReviewRating.GOOD,
            durationMs = 8_000L,
            timestampEpochMillis = 100_000L
        )

        database.cardProgressDao().upsert(progress.toEntity())
        database.practiceLogDao().insert(log.toEntity())

        val backup = manager.createBackupJson()

        database.cardProgressDao().deleteAll()
        database.practiceLogDao().deleteAll()
        manager.restoreFromJson(backup)

        assertEquals(progress, database.cardProgressDao().getByCardId("hiragana_a")?.toDomain())
        assertEquals(listOf(log), database.practiceLogDao().getAll().map(PracticeLogEntity::toDomain))
    }

    @Test
    fun unsupportedBackupDoesNotDeleteExistingProgress() = runBlocking {
        val progress = CardProgress(cardId = "hiragana_i", status = CardStatus.LEARNING)
        database.cardProgressDao().upsert(progress.toEntity())

        val failure = runCatching {
            manager.restoreFromJson(
                """{"format":"japanese-writing-trainer","schemaVersion":999,"cardProgress":[],"practiceLogs":[]}"""
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(progress, database.cardProgressDao().getByCardId("hiragana_i")?.toDomain())
    }
}
