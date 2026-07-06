package com.speedcompass

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFormattingTest {
    @Test
    fun formatsMphAsTwoDigits() {
        assertEquals("10", twoDigitSpeed(4.4704f, SpeedUnit.Mph))
    }

    @Test
    fun formatsKmhAsTwoDigits() {
        assertEquals("36", twoDigitSpeed(10f, SpeedUnit.Kmh))
    }

    @Test
    fun clampsDisplayAtTwoDigits() {
        assertEquals("99", twoDigitSpeed(80f, SpeedUnit.Mph))
    }
}
