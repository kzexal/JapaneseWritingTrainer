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
class VocabularyRepositoryTest {

    @Test
    fun generatedAssetContainsUniqueValidVocabularyRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vocabulary = VocabularyRepository.load(context)

        assertEquals(198, vocabulary.size)
        assertEquals(vocabulary.size, vocabulary.map { it.id }.distinct().size)
        assertEquals(vocabulary.size, vocabulary.map { it.japanese }.distinct().size)
        assertTrue(vocabulary.all { it.id.startsWith("vocab_") })
        assertTrue(vocabulary.all { it.reading.isNotBlank() && it.vietnamese.isNotBlank() })
        assertTrue(vocabulary.any { it.japanese == "海" && it.id == "vocab_6d77" })
        assertTrue(vocabulary.any { it.group == "15. Nhóm Không Kanji" })
    }

    @Test
    fun homophonesRemainSeparateVocabularyCards() {
        val vocabulary = VocabularyRepository.load(ApplicationProvider.getApplicationContext())
        val spring = vocabulary.single { it.japanese == "春" }
        val paste = vocabulary.single { it.japanese == "貼る" }

        assertEquals("はる", spring.reading)
        assertEquals("はる", paste.reading)
        assertTrue(spring.id != paste.id)
    }

    @Test
    fun repeatedExamplesArePreservedAcrossDifferentWords() {
        val vocabulary = VocabularyRepository.load(ApplicationProvider.getApplicationContext())
        val music = vocabulary.single { it.japanese == "音楽" }
        val listen = vocabulary.single { it.japanese == "聞く" }

        assertEquals("音楽を聞きます。", music.example)
        assertEquals(music.example, listen.example)
        assertEquals("Nghe nhạc.", music.exampleMeaning)
        assertEquals(music.exampleMeaning, listen.exampleMeaning)
    }

    @Test
    fun emptyOptionalMetadataIsParsedAsNull() {
        val vocabulary = VocabularyRepository.load(ApplicationProvider.getApplicationContext())
        assertTrue(vocabulary.all { it.jlpt == null && it.notes == null && it.tags == null })
    }

    @Test
    fun parserPreservesAllVocabularyFields() {
        val parsed = VocabularyRepository.parseDataset(
            """
            {"schemaVersion":1,"records":[{
              "id":"vocab_6d77","japanese":"海","reading":"うみ","vietnamese":"Biển",
              "example":"夏に海へ行きます。","exampleMeaning":"Mùa hè tôi đi biển.",
              "group":"01. Nhóm 氵","radicals":"氵(Thủy)","mnemonics":"Ghi nhớ",
              "jlpt":null,"notes":null,"tags":null
            }]}
            """.trimIndent()
        )

        assertEquals(1, parsed.size)
        assertEquals("海", parsed.single().japanese)
        assertEquals("うみ", parsed.single().reading)
        assertEquals("Biển", parsed.single().vietnamese)
        assertEquals("01. Nhóm 氵", parsed.single().group)
        assertNull(parsed.single().jlpt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateVocabularyIds() {
        VocabularyRepository.parseDataset(
            """
            {"schemaVersion":1,"records":[
              {"id":"vocab_6d77","japanese":"海","reading":"うみ","vietnamese":"Biển"},
              {"id":"vocab_6d77","japanese":"川","reading":"かわ","vietnamese":"Sông"}
            ]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIdThatDoesNotMatchJapaneseText() {
        VocabularyRepository.parseDataset(
            """
            {"schemaVersion":1,"records":[{
              "id":"vocab_5ddd","japanese":"海","reading":"うみ","vietnamese":"Biển"
            }]}
            """.trimIndent()
        )
    }

    @Test
    fun librarySearchFindsJapaneseReadingAndVietnameseMeaning() {
        val vocabulary = VocabularyRepository.load(ApplicationProvider.getApplicationContext())

        assertTrue(VocabularyLibrary.filter(vocabulary, query = "海").any { it.japanese == "海" })
        assertTrue(VocabularyLibrary.filter(vocabulary, query = "うみ").any { it.japanese == "海" })
        assertTrue(VocabularyLibrary.filter(vocabulary, query = "biển").any { it.japanese == "海" })
    }

    @Test
    fun libraryFiltersByMainRadicalGroup() {
        val vocabulary = VocabularyRepository.load(ApplicationProvider.getApplicationContext())
        val waterGroup = VocabularyLibrary.filter(vocabulary, group = "01. Nhóm 氵")

        assertTrue(waterGroup.isNotEmpty())
        assertTrue(waterGroup.all { it.group == "01. Nhóm 氵" })
        assertTrue(VocabularyLibrary.availableGroups(vocabulary).size == 15)
        assertTrue(VocabularyLibrary.availableGroups(vocabulary).contains("15. Nhóm Không Kanji"))
    }
}
