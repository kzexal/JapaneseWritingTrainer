package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeLogDaoTest {

    private lateinit var database: WritingTrainerDatabase
    private lateinit var dao: PracticeLogDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WritingTrainerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.practiceLogDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertsAndReturnsLogsNewestFirst() = runBlocking {
        dao.insert(
            PracticeLog(
                cardId = "hiragana_a",
                mode = PracticeMode.RECALL,
                rating = ReviewRating.GOOD,
                durationMs = 3_000L,
                timestampEpochMillis = 100L
            ).toEntity()
        )
        dao.insert(
            PracticeLog(
                cardId = "hiragana_a",
                mode = PracticeMode.REVIEW,
                rating = ReviewRating.EASY,
                durationMs = 2_000L,
                timestampEpochMillis = 200L
            ).toEntity()
        )

        val logs = dao.getByCardId("hiragana_a").map(PracticeLogEntity::toDomain)

        assertEquals(2, logs.size)
        assertEquals(200L, logs.first().timestampEpochMillis)
        assertEquals(ReviewRating.EASY, logs.first().rating)
        assertEquals(100L, logs.last().timestampEpochMillis)
        assertEquals(2, dao.count())
    }

    @Test
    fun countsDistinctRatedNewRecallCardsSinceDayStart() = runBlocking {
        val dayStart = 1_000L
        val logs = listOf(
            PracticeLog("hiragana_a", PracticeMode.RECALL, ReviewRating.AGAIN, 500L, 1_000L),
            PracticeLog("hiragana_a", PracticeMode.REVIEW, ReviewRating.GOOD, 500L, 2_000L),
            PracticeLog("hiragana_b", PracticeMode.RECALL, ReviewRating.GOOD, 500L, 2_000L),
            PracticeLog("hiragana_c", PracticeMode.RECALL, null, 500L, 2_000L),
            PracticeLog("hiragana_d", PracticeMode.RECALL, ReviewRating.EASY, 500L, 999L)
        )
        dao.insertAll(logs.map(PracticeLog::toEntity))

        assertEquals(2, dao.countDistinctNewCardsSince(dayStart))
    }

    @Test
    fun deleteAllRemovesHistory() = runBlocking {
        dao.insert(
            PracticeLog(
                cardId = "hiragana_a",
                mode = PracticeMode.RECALL,
                rating = ReviewRating.AGAIN,
                durationMs = 1_000L,
                timestampEpochMillis = 100L
            ).toEntity()
        )

        dao.deleteAll()

        assertEquals(0, dao.count())
    }
}
