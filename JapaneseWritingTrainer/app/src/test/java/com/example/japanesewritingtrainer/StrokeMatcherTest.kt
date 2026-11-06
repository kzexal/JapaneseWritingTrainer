package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMatcherTest {

    private val reference = ParsedStroke(
        order = 1,
        points = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(30f, 10f),
            StrokePoint(50f, 10f),
            StrokePoint(70f, 10f),
            StrokePoint(90f, 10f)
        ),
        startPoint = StrokePoint(10f, 10f),
        endPoint = StrokePoint(90f, 10f),
        length = 80f,
        bounds = Rect(10f, 10f, 90f, 10f)
    )

    private fun userStroke(
        points: List<StrokePoint>,
        length: Float = 80f
    ): AnalyzedUserStroke {
        return AnalyzedUserStroke(
            order = 1,
            points = points,
            startPoint = points.first(),
            endPoint = points.last(),
            length = length,
            bounds = Rect(
                points.minOf { it.x },
                points.minOf { it.y },
                points.maxOf { it.x },
                points.maxOf { it.y }
            ),
            durationMillis = 500L
        )
    }

    @Test
    fun acceptsStrokeThatMatchesReference() {
        val result = StrokeMatcher.match(
            reference,
            userStroke(reference.points),
            viewBoxSize = 109f
        )

        assertTrue(result.accepted)
        assertTrue(result.directionCorrect)
        assertTrue(result.lengthWithinTolerance)
        assertEquals(100f, result.score, 0.01f)
        assertEquals(1f, result.lengthRatio, 0.001f)
    }

    @Test
    fun rejectsStrokeWrittenInReverseDirection() {
        val result = StrokeMatcher.match(
            reference,
            userStroke(reference.points.reversed()),
            viewBoxSize = 109f
        )

        assertFalse(result.accepted)
        assertFalse(result.directionCorrect)
        assertTrue(result.directionReversed)
        assertTrue(result.startDistance > 0f)
        assertTrue(result.endDistance > 0f)
        assertTrue("Nét ngược chiều không được có điểm cao", result.score <= 49f)
    }

    @Test
    fun penalizesStrokeWithIncorrectPosition() {
        val shifted = reference.points.map { StrokePoint(it.x, it.y + 40f) }
        val result = StrokeMatcher.match(
            reference,
            userStroke(shifted),
            viewBoxSize = 109f
        )

        assertFalse(result.accepted)
        assertFalse(result.directionReversed)
        assertTrue(result.averagePathDistance > 0f)
        assertTrue(result.score < 100f)
    }


    @Test
    fun acceptsModerateUniformScaleVariation() {
        val scaled = reference.points.map {
            StrokePoint(
                x = 50f + (it.x - 50f) * 1.2f,
                y = 10f + (it.y - 10f) * 1.2f
            )
        }
        val result = StrokeMatcher.match(
            reference,
            userStroke(scaled, length = 96f),
            viewBoxSize = 109f
        )

        assertTrue("Biến thiên kích thước vừa phải nên được chấp nhận", result.accepted)
        assertTrue(result.lengthWithinTolerance)
        assertEquals(1.2f, result.lengthRatio, 0.001f)
    }

    @Test
    fun acceptsSmallNaturalCurvatureVariation() {
        val gentlyCurved = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(30f, 13f),
            StrokePoint(50f, 15f),
            StrokePoint(70f, 13f),
            StrokePoint(90f, 10f)
        )
        val result = StrokeMatcher.match(
            reference,
            userStroke(gentlyCurved, length = 80.647f),
            viewBoxSize = 109f
        )

        assertTrue("Độ cong nhẹ do thao tác tay không nên bị từ chối", result.accepted)
        assertTrue(result.directionCorrect)
        assertTrue(result.lengthWithinTolerance)
    }

    @Test
    fun acceptsSmallStartAndEndPointOffset() {
        val slightlyShifted = reference.points.map {
            StrokePoint(it.x + 18f, it.y + 18f)
        }
        val result = StrokeMatcher.match(
            reference,
            userStroke(slightlyShifted),
            viewBoxSize = 109f
        )

        assertTrue("Sai lệch nhỏ ở hai đầu không nên làm nét bị từ chối", result.directionCorrect)
        assertTrue(result.accepted)
    }

    @Test
    fun penalizesIncorrectStrokeLength() {
        val shorter = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(25f, 10f),
            StrokePoint(40f, 10f),
            StrokePoint(55f, 10f)
        )
        val result = StrokeMatcher.match(
            reference,
            userStroke(shorter, length = 45f),
            viewBoxSize = 109f
        )

        assertTrue(result.lengthRatio < 1f)
        assertFalse(result.lengthWithinTolerance)
        assertFalse(result.accepted)
        assertTrue(result.score <= 59f)
    }

    @Test
    fun rejectsHalfLengthStrokeEvenWhenItsVisibleShapeIsSimilar() {
        val halfLength = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(20f, 10f),
            StrokePoint(30f, 10f),
            StrokePoint(40f, 10f),
            StrokePoint(50f, 10f)
        )
        val result = StrokeMatcher.match(
            reference,
            userStroke(halfLength, length = 40f),
            viewBoxSize = 109f
        )

        assertEquals(0.5f, result.lengthRatio, 0.001f)
        assertFalse(result.lengthWithinTolerance)
        assertFalse(result.accepted)
        assertTrue("Nét chỉ viết một nửa không được đạt điểm cao", result.score <= 59f)
    }

    @Test
    fun rejectsVeryShortDotInsteadOfTreatingItAsCompleteStroke() {
        val dot = listOf(
            StrokePoint(10f, 10f),
            StrokePoint(10.5f, 10f)
        )
        val result = StrokeMatcher.match(
            reference,
            userStroke(dot, length = 0.5f),
            viewBoxSize = 109f
        )

        assertFalse(result.lengthWithinTolerance)
        assertFalse(result.accepted)
        assertTrue(result.score <= 59f)
    }

    @Test
    fun resamplesPolylineToRequestedNumberOfPoints() {
        val sampled = StrokeMatcher.resample(reference.points, 16)

        assertEquals(16, sampled.size)
        assertEquals(reference.startPoint.x, sampled.first().x, 0.001f)
        assertEquals(reference.endPoint.x, sampled.last().x, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidViewBoxSize() {
        StrokeMatcher.match(
            reference,
            userStroke(reference.points),
            viewBoxSize = 0f
        )
    }
}
