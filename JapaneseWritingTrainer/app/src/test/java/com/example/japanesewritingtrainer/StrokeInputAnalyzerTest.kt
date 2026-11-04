package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class StrokeInputAnalyzerTest {

    private val transformer = CoordinateTransformer.fit(
        sourceWidth = 100f,
        sourceHeight = 100f,
        canvasSize = Size(200f, 200f)
    )

    @Test
    fun normalizesCanvasPointsToSvgCoordinates() {
        val result = StrokeInputAnalyzer.analyze(
            order = 1,
            rawPoints = listOf(
                CanvasStrokePoint(20f, 40f, 1_000L),
                CanvasStrokePoint(60f, 40f, 1_100L),
                CanvasStrokePoint(100f, 40f, 1_200L)
            ),
            transformer = transformer
        )

        assertNotNull(result)
        result!!
        assertEquals(1, result.order)
        assertEquals(3, result.points.size)
        assertEquals(10f, result.startPoint.x, 0.001f)
        assertEquals(20f, result.startPoint.y, 0.001f)
        assertEquals(50f, result.endPoint.x, 0.001f)
        assertEquals(20f, result.endPoint.y, 0.001f)
        assertEquals(40f, result.length, 0.001f)
        assertEquals(10f, result.bounds.left, 0.001f)
        assertEquals(50f, result.bounds.right, 0.001f)
        assertEquals(200L, result.durationMillis)
    }

    @Test
    fun removesPointsThatAreTooCloseButKeepsStrokeEndpoint() {
        val result = StrokeInputAnalyzer.analyze(
            order = 2,
            rawPoints = listOf(
                CanvasStrokePoint(20f, 40f, 0L),
                CanvasStrokePoint(21f, 40f, 20L),
                CanvasStrokePoint(60f, 40f, 100L)
            ),
            transformer = transformer,
            minDistancePx = 5f
        )

        assertNotNull(result)
        result!!
        assertEquals(2, result.points.size)
        assertEquals(10f, result.startPoint.x, 0.001f)
        assertEquals(30f, result.endPoint.x, 0.001f)
        assertEquals(20f, result.length, 0.001f)
        assertEquals(100L, result.durationMillis)
    }

    @Test
    fun rejectsGestureWithPointOutsideRenderedSvgArea() {
        val result = StrokeInputAnalyzer.analyze(
            order = 1,
            rawPoints = listOf(
                CanvasStrokePoint(-1f, 40f, 0L),
                CanvasStrokePoint(60f, 40f, 100L)
            ),
            transformer = transformer
        )

        assertNull(result)
    }

    @Test
    fun rejectsGestureWithNoMovement() {
        val result = StrokeInputAnalyzer.analyze(
            order = 1,
            rawPoints = listOf(
                CanvasStrokePoint(40f, 40f, 0L),
                CanvasStrokePoint(40f, 40f, 100L)
            ),
            transformer = transformer
        )

        assertNull(result)
    }

    @Test
    fun returnsNullWhenThereAreNotEnoughRawPoints() {
        val result = StrokeInputAnalyzer.analyze(
            order = 1,
            rawPoints = listOf(CanvasStrokePoint(40f, 40f, 0L)),
            transformer = transformer
        )

        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroStrokeOrder() {
        StrokeInputAnalyzer.analyze(
            order = 0,
            rawPoints = listOf(
                CanvasStrokePoint(20f, 40f, 0L),
                CanvasStrokePoint(60f, 40f, 100L)
            ),
            transformer = transformer
        )
    }
}
