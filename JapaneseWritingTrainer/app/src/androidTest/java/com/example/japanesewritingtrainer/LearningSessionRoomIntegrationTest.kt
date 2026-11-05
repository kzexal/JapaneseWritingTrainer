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
class LearningSessionRoomIntegrationTest {

    private lateinit var database: WritingTrainerDatabase
    private lateinit var dao: CardProgressDao
    private lateinit var repository: CardProgressRepository
    private lateinit var practiceLogDao: PracticeLogDao

    private val now = 1_800_000_000_000L
    private val deck = WritingDeck(
        id = "hiragana",
        name = "Hiragana",
        description = "Luyện viết Hiragana",
        characterType = "hiragana",
        cardIds = listOf("a", "i")
    )
    private val cards = listOf(
        WritingCard("a", deck.id, "a", "あ", "a", "vowels", 3, 0),
        WritingCard("i", deck.id, "i", "い", "i", "vowels", 2, 1)
    )

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WritingTrainerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.cardProgressDao()
        practiceLogDao = database.practiceLogDao()
        repository = CardProgressRepository(dao, practiceLogDao, database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun schedulerSessionAndRoomPersistProgressAndHistoryAcrossSessions() = runBlocking {
        val coordinator = LearningSessionCoordinator(repository)

        val firstSession = coordinator.start(deck, cards, now, newCardLimit = 2)
        assertEquals("a", firstSession.current?.card?.id)

        val next = coordinator.rateCurrent(ReviewRating.GOOD, now + 4_000L)
        assertEquals("i", next.current?.card?.id)

        val persisted = dao.getByCardId("a")!!.toDomain()
        assertEquals(CardStatus.REVIEW, persisted.status)
        assertEquals(1, persisted.reviewCount)
        assertEquals(now + 4_000L, persisted.lastReviewedAtEpochMillis)
        assertTrue(persisted.dueAtEpochMillis!! > now)

        val logs = practiceLogDao.getByCardId("a").map(PracticeLogEntity::toDomain)
        assertEquals(1, logs.size)
        assertEquals(PracticeMode.RECALL, logs.single().mode)
        assertEquals(ReviewRating.GOOD, logs.single().rating)
        assertEquals(4_000L, logs.single().durationMs)

        // Tạo coordinator mới để bảo đảm dữ liệu được đọc lại từ Room,
        // không phụ thuộc state trong bộ nhớ của phiên trước.
        val resumedCoordinator = LearningSessionCoordinator(repository)
        val resumed = resumedCoordinator.start(
            deck = deck,
            cards = cards,
            nowEpochMillis = now,
            newCardLimit = 2
        )

        assertTrue(resumed.plan.reviewCards.isEmpty())
        assertEquals(listOf("i"), resumed.plan.newCards.map { it.card.id })
    }

    @Test
    fun learningCardBecomesDueAndCanGraduateInNextSession() = runBlocking {
        val coordinator = LearningSessionCoordinator(repository)
        coordinator.start(deck, cards, now, newCardLimit = 1)

        coordinator.rateCurrent(ReviewRating.AGAIN, now)

        val learning = dao.getByCardId("a")!!.toDomain()
        assertEquals(CardStatus.LEARNING, learning.status)
        assertEquals(now + 60_000L, learning.dueAtEpochMillis)

        val retryCoordinator = LearningSessionCoordinator(repository)
        val retry = retryCoordinator.start(
            deck = deck,
            cards = cards,
            nowEpochMillis = now + 60_000L,
            newCardLimit = 0
        )

        assertEquals("a", retry.current?.card?.id)
        assertEquals(SessionCardKind.LEARNING, retry.current?.kind)

        retryCoordinator.rateCurrent(ReviewRating.GOOD, now + 60_000L)

        val graduated = dao.getByCardId("a")!!.toDomain()
        assertEquals(CardStatus.REVIEW, graduated.status)
        assertEquals(2, graduated.reviewCount)
        assertEquals(1, graduated.correctCount)
        assertEquals(2, practiceLogDao.getByCardId("a").size)
        assertEquals(PracticeMode.REVIEW, practiceLogDao.getByCardId("a").first().toDomain().mode)
    }
}
