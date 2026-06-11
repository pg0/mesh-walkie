package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingFilterTest {

    @Test
    fun firstSampleIsReturnedAsIs() {
        assertEquals(123f, HeadingFilter(alpha = 0.5f).update(123f), 0.001f)
    }

    @Test
    fun smoothsTowardNewValue() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(0f)
        assertEquals(5f, f.update(10f), 0.001f)     // 0 + 0.5 * 10
        assertEquals(7.5f, f.update(10f), 0.001f)   // 5 + 0.5 * 5
    }

    @Test
    fun wrapsAcrossNorthUpward() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(350f)
        // shortest path 350 -> 10 is +20 degrees, NOT -340
        assertEquals(0f, f.update(10f), 0.001f)     // 350 + 0.5 * 20 = 360 -> 0
    }

    @Test
    fun wrapsAcrossNorthDownward() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(10f)
        assertEquals(0f, f.update(350f), 0.001f)    // 10 + 0.5 * (-20) = 0
    }

    @Test
    fun outputAlwaysInZeroTo360() {
        val f = HeadingFilter(alpha = 1.0f)
        f.update(359f)
        val v = f.update(2f)
        assertEquals(2f, v, 0.001f)
    }
}
