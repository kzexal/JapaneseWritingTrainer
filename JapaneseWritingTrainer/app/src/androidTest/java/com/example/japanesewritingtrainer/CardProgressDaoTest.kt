package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardProgressDaoTest {

    private lateinit var database: WritingTrainerDatabase
    private lateinit var dao: CardProgressDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WritingTrainerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.cardProgressDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun savesAndReadsCardProgress() = runBlocking {
        val progress = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.REVIEW,
            dueAtEpochMillis = 123_456L,
            stabilityDays = 3.5,
            difficulty = 5.0,
            scheduledDays = 3.0,
            elapsedDays = 2.0,
            reps = 4,
            lapses = 1,
            reviewCount = 5,
            correctCount = 4,
            lastReviewedAtEpochMillis = 100_000L
        )

        dao.upsert(progress.toEntity())

        assertEquals(progress, dao.getByCardId(progress.cardId)?.toDomain())
    }

    @Test
    fun upsertReplacesExistingProgressForSameCard() = runBlocking {
        dao.upsert(CardProgress(cardId = "hiragana_a").toEntity())
        val updated = CardProgress(
            cardId = "hiragana_a",
            status = CardStatus.LEARNING,
            dueAtEpochMillis = 500L
        )

        dao.upsert(updated.toEntity())

        assertEquals(updated, dao.getByCardId("hiragana_a")?.toDomain())
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun deleteRemovesOnlyRequestedCard() = runBlocking {
        dao.upsertAll(
            listOf(
                CardProgress(cardId = "hiragana_a").toEntity(),
                CardProgress(cardId = "hiragana_i").toEntity()
            )
        )

        dao.deleteByCardId("hiragana_a")

        assertNull(dao.getByCardId("hiragana_a"))
        assertEquals(listOf("hiragana_i"), dao.getAll().map { it.cardId })
    }
}
