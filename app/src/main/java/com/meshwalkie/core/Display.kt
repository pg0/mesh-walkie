package com.meshwalkie.core

import java.util.Locale
import kotlin.math.roundToInt

/** Pure presentation math for the peer rows. No Android imports. */
object Display {
    private val COMPASS_16 = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /** 16-point compass label: idx = round(deg / 22.5) mod 16. */
    fun compassLabel(bearingDeg: Double): String {
        val idx = (bearingDeg / 22.5).roundToInt().mod(16)
        return COMPASS_16[idx]
    }

    /** <1 km in meters, >=1 km in km with one decimal. */
    fun formatDistance(meters: Double): String =
        if (meters.roundToInt() < 1000) "${meters.roundToInt()} m"
        else String.format(Locale.US, "%.1f km", meters / 1000.0)

    /** On-screen arrow rotation = bearingToPeer - myHeading, normalized [0, 360). */
    fun arrowRotation(bearingToPeerDeg: Double, myHeadingDeg: Double): Float {
        val r = (bearingToPeerDeg - myHeadingDeg) % 360.0
        return ((r + 360.0) % 360.0).toFloat()
    }
}
