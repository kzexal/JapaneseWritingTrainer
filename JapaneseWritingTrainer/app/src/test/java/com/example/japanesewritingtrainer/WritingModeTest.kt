package com.example.japanesewritingtrainer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingModeTest {

    @Test
    fun traceDoesNotRequireStrokeValidation() {
        assertFalse(WritingMode.TRACE.requiresStrokeValidation)
        assertFalse(WritingMode.TRACE.showsStrokeGuidance)
    }

    @Test
    fun freePracticeDoesNotRequireStrokeValidationOrGuidance() {
        assertFalse(WritingMode.FREE.requiresStrokeValidation)
        assertFalse(WritingMode.FREE.showsStrokeGuidance)
        assertEquals(PracticeMode.FREE, WritingMode.FREE.toPracticeMode())
    }

    @Test
    fun guidedRequiresValidationAndShowsGuidance() {
        assertTrue(WritingMode.GUIDED.requiresStrokeValidation)
        assertTrue(WritingMode.GUIDED.showsStrokeGuidance)
    }

    @Test
    fun recallRequiresValidationWithoutShowingGuidance() {
        assertTrue(WritingMode.RECALL.requiresStrokeValidation)
        assertFalse(WritingMode.RECALL.showsStrokeGuidance)
    }
}
