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
    fun hiraganaAssetContainsFortySixValidCharacters() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val characters = CharacterRepository.load(
            context,
            "characters/hiragana.json"
        )

        assertEquals(46, characters.size)
        assertEquals(46, characters.map { it.id }.distinct().size)
        assertEquals(46, characters.map { it.character }.distinct().size)
        characters.forEach { character ->
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
