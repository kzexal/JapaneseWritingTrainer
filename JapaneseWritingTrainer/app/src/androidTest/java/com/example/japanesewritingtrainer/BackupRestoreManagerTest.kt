package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
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
    fun invalidFsrsValuesDoNotReplaceExistingProgress() = runBlocking {
        val existing = CardProgress(
            cardId = "hiragana_u",
            status = CardStatus.REVIEW,
            stabilityDays = 8.0,
            difficulty = 4.0,
            scheduledDays = 8.0,
            elapsedDays = 2.0,
            reps = 4,
            reviewCount = 4,
            correctCount = 3
        )
        database.cardProgressDao().upsert(existing.toEntity())

        val validBackup = manager.createBackupJson()
        val invalidRoot = JSONObject(validBackup)
        invalidRoot.getJSONArray("cardProgress")
            .getJSONObject(0)
            .put("scheduledDays", -1.0)
        val invalidBackup = invalidRoot.toString()
        assertTrue(invalidBackup != validBackup)

        val failure = runCatching {
            manager.restoreFromJson(invalidBackup)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            existing,
            database.cardProgressDao().getByCardId("hiragana_u")?.toDomain()
        )
    }

    @Test
    fun duplicateCardIdsDoNotReplaceExistingData() = runBlocking {
        val existing = CardProgress(cardId = "hiragana_e", status = CardStatus.LEARNING)
        database.cardProgressDao().upsert(existing.toEntity())

        val backupRoot = JSONObject(manager.createBackupJson())
        val progressArray = backupRoot.getJSONArray("cardProgress")
        val duplicate = JSONObject(progressArray.getJSONObject(0).toString())
        progressArray.put(duplicate)

        val failure = runCatching {
            manager.restoreFromJson(backupRoot.toString())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            existing,
            database.cardProgressDao().getByCardId("hiragana_e")?.toDomain()
        )
    }

    @Test
    fun duplicatePracticeLogIdsDoNotReplaceExistingData() = runBlocking {
        val existing = CardProgress(cardId = "hiragana_o", status = CardStatus.REVIEW)
        database.cardProgressDao().upsert(existing.toEntity())

        val backupRoot = JSONObject(manager.createBackupJson())
        val duplicateLog = JSONObject()
            .put("id", 9L)
            .put("cardId", "hiragana_o")
            .put("mode", PracticeMode.REVIEW.name)
            .put("rating", ReviewRating.GOOD.name)
            .put("durationMs", 1200L)
            .put("timestampEpochMillis", 5000L)
        backupRoot.getJSONArray("practiceLogs")
            .put(JSONObject(duplicateLog.toString()))
            .put(JSONObject(duplicateLog.toString()))

        val failure = runCatching {
            manager.restoreFromJson(backupRoot.toString())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            existing,
            database.cardProgressDao().getByCardId("hiragana_o")?.toDomain()
        )
        assertEquals(0, database.practiceLogDao().count())
    }

    @Test
    fun failedDatabaseWriteRollsBackRestoreAndPreservesExistingData() = runBlocking {
        val existingProgress = CardProgress(
            cardId = "hiragana_old",
            status = CardStatus.REVIEW,
            reps = 3,
            reviewCount = 2,
            correctCount = 1
        )
        val existingLog = PracticeLog(
            id = 41L,
            cardId = "hiragana_old",
            mode = PracticeMode.REVIEW,
            rating = ReviewRating.GOOD,
            durationMs = 1500L,
            timestampEpochMillis = 9000L
        )
        database.cardProgressDao().upsert(existingProgress.toEntity())
        database.practiceLogDao().insert(existingLog.toEntity())

        val backup = JSONObject(manager.createBackupJson())
        val progress = backup.getJSONArray("cardProgress")
        progress.getJSONObject(0).put("cardId", "hiragana_new")
        val logs = backup.getJSONArray("practiceLogs")
        logs.put(
            JSONObject()
                .put("id", 42L)
                .put("cardId", "hiragana_new")
                .put("mode", PracticeMode.REVIEW.name)
                .put("rating", ReviewRating.GOOD.name)
                .put("durationMs", 2000L)
                .put("timestampEpochMillis", 10000L)
        )

        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_restore_log
            BEFORE INSERT ON practice_log
            BEGIN
                SELECT RAISE(ABORT, 'forced restore write failure');
            END
            """.trimIndent()
        )

        val failure = runCatching {
            manager.restoreFromJson(backup.toString())
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(
            existingProgress,
            database.cardProgressDao().getByCardId("hiragana_old")?.toDomain()
        )
        assertEquals(null, database.cardProgressDao().getByCardId("hiragana_new"))
        assertEquals(
            listOf(existingLog),
            database.practiceLogDao().getAll().map(PracticeLogEntity::toDomain)
        )
    }

    @Test
    fun malformedJsonDoesNotReplaceExistingData() = runBlocking {
        val existing = CardProgress(cardId = "hiragana_ka", status = CardStatus.LEARNING)
        database.cardProgressDao().upsert(existing.toEntity())

        val failure = runCatching {
            manager.restoreFromJson("""{"format":"japanese-writing-trainer","schemaVersion":1""")
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(
            existing,
            database.cardProgressDao().getByCardId("hiragana_ka")?.toDomain()
        )
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
