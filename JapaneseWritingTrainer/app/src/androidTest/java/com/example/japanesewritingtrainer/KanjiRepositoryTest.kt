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
    fun librarySearch/FindsCharacterReadingAndMeaning() {
        val data = KanjiRepository.load(ApplicationProvider.getApplicationContext())
        assertEquals("一", KanjiLibrary.filter(data, query = "one").single().character)
        assertEquals("校", KanjiLibrary.filter(data, query = "コウ").single().character)
        assertEquals("電", KanjiLibrary.filter(data, query = "電").single().character)
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
