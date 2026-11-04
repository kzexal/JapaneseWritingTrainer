package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTransformerTest {

    @Test
    fun fitsSquareViewBoxIntoLandscapeCanvasWithoutDistortion() {
        val transformer = CoordinateTransformer.fit(
            sourceWidth = 100f,
            sourceHeight = 100f,
            canvasSize = Size(300f, 200f)
        )

        assertEquals(2f, transformer.scale, 0.001f)
        assertEquals(50f, transformer.offset.x, 0.001f)
        assertEquals(0f, transformer.offset.y, 0.001f)
        assertEquals(50f, transformer.contentBounds.left, 0.001f)
        assertEquals(250f, transformer.contentBounds.right, 0.001f)
    }

    @Test
    fun fitsPortraitViewBoxIntoLandscapeCanvas() {
        val transformer = CoordinateTransformer.fit(
            sourceWidth = 100f,
            sourceHeight = 200f,
            canvasSize = Size(300f, 200f)
        )

        assertEquals(1f, transformer.scale, 0.001f)
        assertEquals(100f, transformer.offset.x, 0.001f)
        assertEquals(0f, transformer.offset.y, 0.001f)
    }

    @Test
    fun convertsCoordinatesInBothDirections() {
        val transformer = CoordinateTransformer.fit(
            sourceWidth = 100f,
            sourceHeight = 100f,
            canvasSize = Size(300f, 200f)
        )
        val source = Offset(25f, 40f)

        val canvas = transformer.toCanvas(source)
        val restored = transformer.toSource(canvas)

        assertEquals(100f, canvas.x, 0.001f)
        assertEquals(80f, canvas.y, 0.001f)
        assertEquals(source.x, restored.x, 0.001f)
        assertEquals(source.y, restored.y, 0.001f)
    }

    @Test
    fun detectsWhetherPointIsInsideRenderedContent() {
        val transformer = CoordinateTransformer.fit(
            sourceWidth = 100f,
            sourceHeight = 100f,
            canvasSize = Size(300f, 200f)
        )

        assertTrue(transformer.contains(Offset(150f, 100f)))
        assertFalse(transformer.contains(Offset(10f, 100f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCanvasSize() {
        CoordinateTransformer.fit(100f, 100f, Size.Zero)
    }
}
