package com.example.japanesewritingtrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterRepositoryTest {

    @Test
    fun hiraganaAssetContainsSeventyOneValidCharacters() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val characters = CharacterRepository.load(
            context,
            "characters/hiragana.json"
        )

        assertEquals(71, characters.size)
        assertEquals(71, characters.map { it.id }.distinct().size)
        assertEquals(71, characters.map { it.character }.distinct().size)
        assertTrue(characters.all { it.type == "hiragana" })
        assertTrue(characters.all { it.id.startsWith("hiragana_") })
        assertTrue(characters.any { it.character == "が" && it.id == "hiragana_304c" })
        assertTrue(characters.any { it.character == "ぱ" && it.id == "hiragana_3071" })
        characters.forEach { character ->
            assertEquals(character.strokeCount, character.strokes.size)
            assertEquals(
                (1..character.strokeCount).toList(),
                character.strokes.map { it.order }
            )
            assertTrue(character.strokes.all { it.svgPath.isNotBlank() })
        }
    }

    @Test
    fun katakanaAssetContainsSeventyOneValidCharactersWithSeparateIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val hiragana = CharacterRepository.load(context, "characters/hiragana.json")
        val katakana = CharacterRepository.load(context, "characters/katakana.json")

        assertEquals(71, katakana.size)
        assertEquals(71, katakana.map { it.id }.distinct().size)
        assertEquals(71, katakana.map { it.character }.distinct().size)
        assertTrue(katakana.any { it.character == "ガ" && it.id == "katakana_30ac" })
        assertTrue(katakana.any { it.character == "パ" && it.id == "katakana_30d1" })
        assertTrue(katakana.all { it.type == "katakana" })
        assertTrue(katakana.all { it.id.startsWith("katakana_") })
        assertTrue(
            (hiragana + katakana).map { it.id }.distinct().size ==
                hiragana.size + katakana.size
        )
        katakana.forEach { character ->
            assertEquals(character.strokeCount, character.strokes.size)
            assertEquals(
                (1..character.strokeCount).toList(),
                character.strokes.map { it.order }
            )
            assertTrue(character.strokes.all { it.svgPath.isNotBlank() })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiscontinuousStrokeOrder() {
        CharacterRepository.parseDataset(
            """
            [
              {
                "id": "hiragana_a",
                "character": "あ",
                "type": "hiragana",
                "group": "a",
                "reading": "a",
                "strokeCount": 2,
                "viewBoxSize": 109,
                "strokes": [
                  {"order": 1, "svgPath": "M 0 0"},
                  {"order": 3, "svgPath": "M 1 1"}
                ]
              }
            ]
            """.trimIndent()
        )
    }
}
