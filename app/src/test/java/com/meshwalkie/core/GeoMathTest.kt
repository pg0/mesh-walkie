package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {

    @Test
    fun distanceBerlinToMunichIs504km() {
        // Berlin 52.52,13.405 -> Munich 48.137,11.575 = 504337.9 m
        val d = GeoMath.distanceMeters(52.52, 13.405, 48.137, 11.575)
        assertEquals(504337.9, d, 500.0)
    }

    @Test
    fun distanceOneDegreeLongitudeAtEquator() {
        // 2*pi*R/360 = 111194.93 m
        val d = GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111194.9, d, 1.0)
    }

    @Test
    fun distanceSamePointIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(52.52, 13.405, 52.52, 13.405), 0.001)
    }

    @Test
    fun bearingDueEastIs90() {
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.001)
    }

    @Test
    fun bearingDueNorthIs0() {
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.001)
    }

    @Test
    fun bearingBerlinToMunichIsSouthSouthWest() {
        // precomputed: 195.634 degrees
        assertEquals(195.634, GeoMath.bearingDegrees(52.52, 13.405, 48.137, 11.575), 0.01)
    }

    @Test
    fun bearingMunichToBerlinIsNorthNorthEast() {
        // precomputed: 14.224 degrees (NOT the 180-degree reverse - great circle)
        assertEquals(14.224, GeoMath.bearingDegrees(48.137, 11.575, 52.52, 13.405), 0.01)
    }

    @Test
    fun bearingIsAlwaysInZeroTo360() {
        // northwest-ish target: precomputed 309.522 degrees
        val b = GeoMath.bearingDegrees(52.52, 13.405, 52.62, 13.205)
        assertEquals(309.522, b, 0.01)
    }
}
