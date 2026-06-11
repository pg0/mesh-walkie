package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenSetTest {

    @Test
    fun firstSightingIsNewSecondIsNot() {
        val seen = SeenSet(capacity = 500)
        assertTrue(seen.checkAndAdd("a:1"))
        assertFalse(seen.checkAndAdd("a:1"))
        assertTrue(seen.checkAndAdd("a:2"))
    }

    @Test
    fun evictsOldestAtCapacity() {
        val seen = SeenSet(capacity = 3)
        seen.checkAndAdd("k1"); seen.checkAndAdd("k2"); seen.checkAndAdd("k3")
        seen.checkAndAdd("k4")               // evicts k1
        assertEquals(3, seen.size)
        assertTrue(seen.checkAndAdd("k1"))   // k1 forgotten -> counts as new again
        assertFalse(seen.checkAndAdd("k4"))  // k4 still remembered
    }

    @Test
    fun defaultCapacityIs500() {
        val seen = SeenSet()
        repeat(500) { assertTrue(seen.checkAndAdd("k$it")) }
        assertEquals(500, seen.size)
        seen.checkAndAdd("k500")
        assertEquals(500, seen.size)         // bounded memory
        assertTrue(seen.checkAndAdd("k0"))   // oldest evicted
    }
}
