package com.speedcompass

import kotlin.math.roundToInt

private const val MPS_TO_MPH = 2.2369363f
private const val MPS_TO_KMH = 3.6f

fun speedValue(metersPerSecond: Float, unit: SpeedUnit): Int {
    val converted = when (unit) {
        SpeedUnit.Mph -> metersPerSecond * MPS_TO_MPH
        SpeedUnit.Kmh -> metersPerSecond * MPS_TO_KMH
    }
    return converted.coerceAtLeast(0f).roundToInt()
}

fun twoDigitSpeed(metersPerSecond: Float, unit: SpeedUnit): String =
    speedValue(metersPerSecond, unit).coerceIn(0, 99).toString().padStart(2, '0')
