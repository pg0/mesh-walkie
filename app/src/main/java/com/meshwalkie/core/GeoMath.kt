package com.meshwalkie.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure spherical geo math. No Android imports - JVM testable. */
object GeoMath {
    private const val EARTH_RADIUS_M = 6371000.0

    /** Haversine distance in meters between two WGS84 points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing from point 1 to point 2, degrees in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val theta = atan2(
            sin(dLambda) * cos(phi2),
            cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        )
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }
}
