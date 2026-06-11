package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTest {

    @Test
    fun compassLabelsAtSectorCenters() {
        assertEquals("N", Display.compassLabel(0.0))
        assertEquals("NNE", Display.compassLabel(22.5))
        assertEquals("E", Display.compassLabel(90.0))
        assertEquals("SSW", Display.compassLabel(202.5))
        assertEquals("SSW", Display.compassLabel(195.634)) // Berlin -> Munich
        assertEquals("NW", Display.compassLabel(309.522))
        assertEquals("NNW", Display.compassLabel(337.5))
    }

    @Test
    fun compassLabelWrapsBackToNorth() {
        // 348.75 / 22.5 = 15.5 -> roundToInt 16 -> mod 16 = 0 -> N
        assertEquals("N", Display.compassLabel(348.75))
        assertEquals("N", Display.compassLabel(359.9))
        assertEquals("NNW", Display.compassLabel(348.74))
    }

    @Test
    fun distanceUnderOneKmInMeters() {
        assertEquals("600 m", Display.formatDistance(600.4))
        assertEquals("999 m", Display.formatDistance(999.4))
        assertEquals("0 m", Display.formatDistance(0.0))
    }

    @Test
    fun distanceFromOneKmInKilometersOneDecimal() {
        assertEquals("1.0 km", Display.formatDistance(1000.0))
        assertEquals("504.3 km", Display.formatDistance(504337.9))
    }

    @Test
    fun arrowRotationIsBearingMinusHeadingNormalized() {
        assertEquals(0f, Display.arrowRotation(90.0, 90.0), 0.001f)
        assertEquals(20f, Display.arrowRotation(10.0, 350.0), 0.001f)   // wraps up
        assertEquals(340f, Display.arrowRotation(350.0, 10.0), 0.001f)  // wraps down
        assertEquals(270f, Display.arrowRotation(180.0, 270.0), 0.001f)
    }
}
