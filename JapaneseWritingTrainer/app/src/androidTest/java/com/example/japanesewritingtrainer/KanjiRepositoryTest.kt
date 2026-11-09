package com.example.japanesewritingtrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanjiRepositoryTest {
    @Test
    fun generatedAssetContainsUniqueValidKanjiMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kanji = KanjiRepository.load(context)
        assertEquals(86, kanji.size)
        assertEquals(kanji.size, kanji.map { it.id }.distinct().size)
        assertEquals(kanji.size, kanji.map { it.character }.distinct().size)
        assertTrue(kanji.all { it.id.startsWith("kanji_") })
        assertTrue(kanji.all { it.strokeCount > 0 })
        assertTrue(kanji.any { it.character == "一" && it.id == "kanji_4e00" })
        listOf("曜", "校", "電", "駅").forEach { glyph ->
            assertTrue(kanji.any { it.character == glyph && it.kunyomi == null })
        }
    }

    @Test
    fun parserPreservesMetadataAndNullReadings() {
        val parsed = KanjiRepository.parseDataset(
            """
            {"schemaVersion":1,"records":[{
              "id":"kanji_4e00","character":"一","strokeCount":1,
              "onyomi":"イチ","kunyomi":null,"meaning":"One","radical":"一",
              "exampleWords":null,"notes":null,"jlpt":"N5","tags":"number"
            }]}
            """.trimIndent()
        )
        assertEquals(1, parsed.size)
        assertEquals("イチ", parsed.single().onyomi)
        assertNull(parsed.single().kunyomi)
        assertEquals("One", parsed.single().meaning)
    }

    @Test
    fun generatedKanjiStrokeAssetMatchesMetadataAndCanBeConvertedForGuidedWriting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = KanjiRepository.load(context)
        val characters = KanjiStrokeRepository.load(context)
        assertEquals(86, characters.size)
        assertEquals(metadata.map { it.id }.toSet(), characters.map { it.id }.toSet())
        assertTrue(characters.all { it.type == "kanji" })
        assertTrue(characters.all { it.strokeCount == it.strokes.size })
        assertTrue(characters.all { it.strokes.map(StrokeData::order) == (1..it.strokeCount).toList() })
        assertTrue(characters.any { it.character == "一" && it.strokes.size == 1 })
        assertTrue(characters.any { it.character == "電" && it.strokes.size == 13 })
    }

    @Test
    fun kanjiCharactersProduceAnIndependentLearningDeck() {
        val characters = KanjiStrokeRepository.load(ApplicationProvider.getApplicationContext())
        val catalog = DeckFactory.fromCharacters(characters)
        val deck = catalog.decks.single { it.characterType == "kanji" }
        val cards = catalog.cardsForDeck(deck.id)

        assertEquals("japanese_kanji", deck.id)
        assertEquals(86, deck.cardCount)
        assertEquals(86, cards.size)
        assertTrue(cards.all { it.id.startsWith("kanji_") && it.deckId == deck.id })

        val plan = LearningSessionPlanner.plan(
            deck = deck,
            cards = cards,
            progressByCardId = emptyMap(),
            nowEpochMillis = 1_000L,
            newCardLimit = 10
        )
        assertEquals(10, plan.newCards.size)
        assertEquals(76, plan.remainingNewCount)
        assertTrue(plan.newCards.all { it.kind == SessionCardKind.NEW })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateKanjiIds() {
        KanjiRepository.parseDataset(
            """
            {"schemaVersion":1,"records":[
              {"id":"same","character":"一","strokeCount":1},
              {"id":"same","character":"二","strokeCount":2}
            ]}
            """.trimIndent()
        )
    }

    @Test
    fun librarySearchFindsCharacterReadingAndMeaning() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        assertTrue(KanjiLibrary.filter(data, query = "one").any { it.character == "一" })
        assertTrue(KanjiLibrary.filter(data, query = "コウ").any { it.character == "校" })
        assertTrue(KanjiLibrary.filter(data, query = "電").any { it.character == "電" })
    }

    @Test
    fun libraryFiltersByJlptAndRadical() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        assertEquals(86, KanjiLibrary.filter(data, jlpt = "N5").size)
        assertTrue(KanjiLibrary.filter(data, radical = "一 (1)").any { it.character == "一" })
        assertTrue(KanjiLibrary.availableJlptLevels(data).contains("N5"))
        assertTrue(KanjiLibrary.availableRadicals(data).isNotEmpty())
    }

}
