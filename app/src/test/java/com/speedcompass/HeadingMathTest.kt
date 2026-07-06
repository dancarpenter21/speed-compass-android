package com.speedcompass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingMathTest {
    @Test
    fun mapsHeadingToOrdinalDirection() {
        assertEquals("N", ordinalDirection(0f))
        assertEquals("NE", ordinalDirection(44f))
        assertEquals("E", ordinalDirection(91f))
        assertEquals("NW", ordinalDirection(320f))
    }

    @Test
    fun selectsGpsBearingWhenMoving() {
        assertEquals(180f, selectHeading(gpsBearing = 180f, sensorHeading = 90f, speedMetersPerSecond = 2f))
    }

    @Test
    fun selectsSensorHeadingWhenNearlyStill() {
        assertEquals(90f, selectHeading(gpsBearing = 180f, sensorHeading = 90f, speedMetersPerSecond = 0.2f))
    }

    @Test
    fun smoothsAcrossZeroBoundary() {
        val smoothed = smoothHeading(current = 350f, target = 10f, factor = 0.5f)
        assertTrue(smoothed < 5f || smoothed > 350f)
    }
}
