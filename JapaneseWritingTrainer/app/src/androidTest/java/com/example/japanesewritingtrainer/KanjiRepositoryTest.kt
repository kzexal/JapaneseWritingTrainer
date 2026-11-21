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
    private companion object {
        const val EXPECTED_KANJI_COUNT = 1031
        const val EXPECTED_N5_KANJI_COUNT = 86
    }

    @Test
    fun generatedAssetContainsUniqueValidKanjiMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kanji = KanjiRepository.load(context)
        assertEquals(EXPECTED_KANJI_COUNT, kanji.size)
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
        assertEquals(EXPECTED_KANJI_COUNT, characters.size)
        assertEquals(metadata.map { it.id }.toSet(), characters.map { it.id }.toSet())
        assertTrue(characters.all { it.type == "kanji" })
        assertTrue(characters.all { it.strokeCount == it.strokes.size })
        assertTrue(characters.all { it.strokes.map(StrokeData::order) == (1..it.strokeCount).toList() })
        assertTrue(characters.any { it.character == "一" && it.strokes.size == 1 })
        assertTrue(characters.any { it.character == "電" && it.strokes.size == 13 })
    }

    @Test
    fun loadsOnlyTheRequestedKanjiGeometryFromTheStrokeAsset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sea = KanjiRepository.load(context).single { it.character == "海" }
        val character = KanjiStrokeRepository.loadCharacter(context, sea.id, sea)

        assertEquals(sea.id, character.id)
        assertEquals("海", character.character)
        assertEquals(sea.strokeCount, character.strokes.size)
        assertEquals((1..sea.strokeCount).toList(), character.strokes.map { it.order })
    }

    @Test
    fun everyKanjiGeometryShardCanLoadARepresentativeCharacter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = KanjiRepository.load(context)
        val representatives = metadata.groupBy {
            (it.id.removePrefix("kanji_").toLong(16) and 0xF).toInt()
        }.values.map { it.first() }

        assertEquals(16, representatives.size)
        representatives.forEach { record ->
            val character = KanjiStrokeRepository.loadCharacter(context, record.id, record)
            assertEquals(record.character, character.character)
            assertEquals(record.strokeCount, character.strokes.size)
        }
    }

    @Test
    fun kanjiCharactersProduceAnIndependentLearningDeck() {
        val characters = KanjiStrokeRepository.load(ApplicationProvider.getApplicationContext())
        val catalog = DeckFactory.fromCharacters(characters)
        val deck = catalog.decks.single { it.characterType == "kanji" }
        val cards = catalog.cardsForDeck(deck.id)

        assertEquals("japanese_kanji", deck.id)
        assertEquals(EXPECTED_KANJI_COUNT, deck.cardCount)
        assertEquals(EXPECTED_KANJI_COUNT, cards.size)
        assertTrue(cards.all { it.id.startsWith("kanji_") && it.deckId == deck.id })

        val plan = LearningSessionPlanner.plan(
            deck = deck,
            cards = cards,
            progressByCardId = emptyMap(),
            nowEpochMillis = 1_000L,
            newCardLimit = 10
        )
        assertEquals(10, plan.newCards.size)
        assertEquals(EXPECTED_KANJI_COUNT - 10, plan.remainingNewCount)
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


    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsGlyphMismatchWithMetadata() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e00",
                character = "一",
                strokeCount = 1,
                onyomi = null,
                kunyomi = null,
                meaning = null,
                radical = null,
                exampleWords = null,
                notes = null,
                jlpt = "N5",
                tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e00","character":"二","strokeCount":1,
              "viewBoxSize":109,"strokes":[{"order":1,"svgPath":"M 0 0 L 1 1"}]
            }]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsStrokeCountMismatchWithMetadata() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e00",
                character = "一",
                strokeCount = 2,
                onyomi = null,
                kunyomi = null,
                meaning = null,
                radical = null,
                exampleWords = null,
                notes = null,
                jlpt = "N5",
                tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e00","character":"一","strokeCount":1,
              "viewBoxSize":109,"strokes":[{"order":1,"svgPath":"M 0 0 L 1 1"}]
            }]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsMissingOrUnexpectedMetadataIds() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e00",
                character = "一",
                strokeCount = 1,
                onyomi = null,
                kunyomi = null,
                meaning = null,
                radical = null,
                exampleWords = null,
                notes = null,
                jlpt = "N5",
                tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e8c","character":"二","strokeCount":1,
              "viewBoxSize":109,"strokes":[{"order":1,"svgPath":"M 0 0 L 1 1"}]
            }]}
            """.trimIndent()
        )
    }


    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsNonContinuousStrokeOrder() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e8c", character = "二", strokeCount = 2,
                onyomi = null, kunyomi = null, meaning = null, radical = null,
                exampleWords = null, notes = null, jlpt = "N5", tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e8c","character":"二","strokeCount":2,
              "viewBoxSize":109,"strokes":[
                {"order":1,"svgPath":"M 0 0 L 1 1"},
                {"order":3,"svgPath":"M 1 1 L 2 2"}
              ]
            }]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsEmptyStrokePath() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e00", character = "一", strokeCount = 1,
                onyomi = null, kunyomi = null, meaning = null, radical = null,
                exampleWords = null, notes = null, jlpt = "N5", tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e00","character":"一","strokeCount":1,
              "viewBoxSize":109,"strokes":[{"order":1,"svgPath":"  "}]
            }]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun strokeAssetRejectsInvalidViewBoxSize() {
        val metadata = listOf(
            KanjiData(
                id = "kanji_4e00", character = "一", strokeCount = 1,
                onyomi = null, kunyomi = null, meaning = null, radical = null,
                exampleWords = null, notes = null, jlpt = "N5", tags = null
            )
        )
        KanjiStrokeRepository.combine(
            metadata,
            """
            {"schemaVersion":1,"characters":[{
              "id":"kanji_4e00","character":"一","strokeCount":1,
              "viewBoxSize":0,"strokes":[{"order":1,"svgPath":"M 0 0 L 1 1"}]
            }]}
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
    fun indexedLibrarySearchAndFiltersMatchSourceBasedQueries() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        val index = KanjiLibrary.createIndex(data)

        assertEquals(
            KanjiLibrary.filter(data, query = "one", radical = "一 (1)").map { it.id },
            KanjiLibrary.filter(index, query = "one", radical = "一 (1)").map { it.id }
        )
        assertEquals(
            KanjiLibrary.availableRadicalGroups(data),
            KanjiLibrary.availableRadicalGroups(index)
        )
    }

    @Test
    fun libraryFiltersByJlptAndRadical() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        assertEquals(EXPECTED_N5_KANJI_COUNT, KanjiLibrary.filter(data, jlpt = "N5").size)
        assertTrue(KanjiLibrary.filter(data, radical = "一 (1)").any { it.character == "一" })
        assertTrue(KanjiLibrary.availableJlptLevels(data).contains("N5"))
        assertTrue(KanjiLibrary.availableRadicals(data).isNotEmpty())
    }

    @Test
    fun radicalGroupsAreMergedByKangxiIndexAndSortedNumerically() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        val groups = KanjiLibrary.availableRadicalGroups(data)
        assertEquals(groups.map { it.number }.sorted(), groups.map { it.number })
        val river = groups.single { it.number == 47 }
        assertEquals("川・巛 (47)", river.label)
        assertEquals(2, river.count)
        assertEquals(setOf("川", "州"), KanjiLibrary.filter(data, radical = "川 (47)")
            .map { it.character }.toSet())
    }

    @Test
    fun seaKanjiExistsAndBelongsToWaterRadical() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        val sea = data.single { it.character == "海" }
        assertEquals("水 (85)", sea.radical)
        assertTrue(KanjiLibrary.filter(data, radical = "水 (85)").any { it.character == "海" })
    }

}
