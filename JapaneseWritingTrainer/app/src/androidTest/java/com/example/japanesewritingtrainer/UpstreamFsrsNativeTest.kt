package com.example.japanesewritingtrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** JNI smoke tests. Exact algorithm parity is asserted in Rust against upstream FSRS. */
@RunWith(AndroidJUnit4::class)
class UpstreamFsrsNativeTest {
    @Test
    fun nativeLibraryLoadsAndReturnsAllFourRatingsForNewCard() {
        val result = UpstreamFsrsNative.review(null, null, 0)
        val states = listOf(result.again, result.hard, result.good, result.easy)
        assertEquals(4, states.size)
        states.forEach {
            assertTrue(it.stability > 0.0)
            assertTrue(it.difficulty in 1.0..10.0)
            assertTrue(it.intervalDays >= 0.0)
        }
    }

    @Test
    fun nativeLibraryHandlesExistingMemoryState() {
        val result = UpstreamFsrsNative.review(7.0, 5.0, 7)
        assertTrue(result.good.stability > 0.0)
        assertTrue(result.good.difficulty in 1.0..10.0)
    }
}
