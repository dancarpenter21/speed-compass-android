package com.speedcompass

import java.time.Instant

enum class SpeedUnit(val label: String) {
    Mph("mph"),
    Kmh("km/h"),
}

data class RoutePoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

data class DashboardState(
    val speedMetersPerSecond: Float = 0f,
    val gpsBearingDegrees: Float? = null,
    val sensorHeadingDegrees: Float? = null,
    val displayHeadingDegrees: Float = 0f,
    val isRecording: Boolean = false,
    val pointCount: Int = 0,
    val selectedUnit: SpeedUnit = SpeedUnit.Mph,
    val hasLocation: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val errorMessage: String? = null,
)
