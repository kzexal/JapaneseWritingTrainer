package com.example.japanesewritingtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedWritingProgressTest {

    @Test
    fun startsAtFirstStroke() {
        val progress = GuidedWritingProgress()

        assertEquals(1, progress.nextStrokeOrder(totalStrokeCount = 3))
        assertEquals(0, progress.completedStrokeCount)
        assertFalse(progress.isComplete(totalStrokeCount = 3))
    }

    @Test
    fun advancesOneStrokeAtATime() {
        val progress = GuidedWritingProgress()
            .completeStroke(totalStrokeCount = 3)
            .completeStroke(totalStrokeCount = 3)

        assertEquals(2, progress.completedStrokeCount)
        assertEquals(3, progress.nextStrokeOrder(totalStrokeCount = 3))
        assertTrue(progress.canAcceptStroke(totalStrokeCount = 3))
    }

    @Test
    fun doesNotAdvancePastTotalStrokeCount() {
        val progress = GuidedWritingProgress()
            .completeStroke(totalStrokeCount = 1)
            .completeStroke(totalStrokeCount = 1)

        assertEquals(1, progress.completedStrokeCount)
        assertNull(progress.nextStrokeOrder(totalStrokeCount = 1))
        assertTrue(progress.isComplete(totalStrokeCount = 1))
        assertFalse(progress.canAcceptStroke(totalStrokeCount = 1))
    }

    @Test
    fun undoMovesBackOneStroke() {
        val progress = GuidedWritingProgress(completedStrokeCount = 2).undo()

        assertEquals(1, progress.completedStrokeCount)
        assertEquals(2, progress.nextStrokeOrder(totalStrokeCount = 3))
    }

    @Test
    fun undoNeverGoesBelowZero() {
        val progress = GuidedWritingProgress().undo()

        assertEquals(0, progress.completedStrokeCount)
    }

    @Test
    fun resetReturnsToFirstStroke() {
        val progress = GuidedWritingProgress(completedStrokeCount = 3).reset()

        assertEquals(0, progress.completedStrokeCount)
        assertEquals(1, progress.nextStrokeOrder(totalStrokeCount = 3))
    }
}
