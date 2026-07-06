package com.speedcompass

import kotlin.math.abs

private val ordinalDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun normalizeDegrees(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

fun ordinalDirection(degrees: Float): String {
    val sector = ((normalizeDegrees(degrees) + 22.5f) / 45f).toInt() % 8
    return ordinalDirections[sector]
}

fun selectHeading(gpsBearing: Float?, sensorHeading: Float?, speedMetersPerSecond: Float): Float? =
    if (gpsBearing != null && speedMetersPerSecond >= 0.8f) gpsBearing else sensorHeading ?: gpsBearing

fun smoothHeading(current: Float, target: Float, factor: Float = 0.18f): Float {
    val normalizedCurrent = normalizeDegrees(current)
    val normalizedTarget = normalizeDegrees(target)
    var delta = normalizedTarget - normalizedCurrent
    if (abs(delta) > 180f) delta -= 360f * kotlin.math.sign(delta)
    return normalizeDegrees(normalizedCurrent + delta * factor.coerceIn(0f, 1f))
}
