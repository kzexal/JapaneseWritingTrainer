package com.example.japanesewritingtrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SvgStrokeParserTest {

    @Test
    fun parsesStartEndLengthAndBounds() {
        val parsed = SvgStrokeParser.parse(
            StrokeData(
                order = 1,
                svgPath = "M0,0 C0,10 10,10 10,0"
            )
        )

        assertEquals(1, parsed.order)
        assertEquals(0f, parsed.startPoint.x, 0.01f)
        assertEquals(0f, parsed.startPoint.y, 0.01f)
        assertEquals(10f, parsed.endPoint.x, 0.01f)
        assertEquals(0f, parsed.endPoint.y, 0.01f)

        assertTrue(parsed.length > 10f)
        assertTrue(parsed.bounds.left <= 0f)
        assertTrue(parsed.bounds.right >= 10f)
        assertTrue(parsed.bounds.bottom > 0f)
    }

    @Test
    fun samplesPointsAtRequestedResolution() {
        val parsed = SvgStrokeParser.parse(
            StrokeData(order = 2, svgPath = "M0,0 L20,0"),
            sampleCount = 8
        )

        assertEquals(9, parsed.points.size)
        assertEquals(0f, parsed.points.first().x, 0.01f)
        assertEquals(20f, parsed.points.last().x, 0.01f)
        assertEquals(10f, parsed.points[4].x, 0.01f)
    }

    @Test
    fun parseAllReturnsStrokesInOrder() {
        val parsed = SvgStrokeParser.parseAll(
            listOf(
                StrokeData(order = 2, svgPath = "M10,0 L20,0"),
                StrokeData(order = 1, svgPath = "M0,0 L10,0")
            ),
            sampleCount = 4
        )

        assertEquals(listOf(1, 2), parsed.map { it.order })
        assertEquals(0f, parsed.first().startPoint.x, 0.01f)
        assertEquals(10f, parsed.last().startPoint.x, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSampleCount() {
        SvgStrokeParser.parse(
            StrokeData(order = 1, svgPath = "M0,0 L10,10"),
            sampleCount = 0
        )
    }
}
