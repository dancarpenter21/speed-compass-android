package com.speedcompass

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GpxWriterTest {
    @Test
    fun writesTrackPointsAndEscapesName() {
        val gpx = routeToGpx(
            points = listOf(
                RoutePoint(
                    timestamp = Instant.parse("2026-07-06T16:00:00Z"),
                    latitude = 40.0,
                    longitude = -73.0,
                    altitudeMeters = 12.5,
                    speedMetersPerSecond = 4.2f,
                    bearingDegrees = 90f,
                ),
            ),
            routeName = "Bike & Test",
        )

        assertTrue(gpx.contains("""<name>Bike &amp; Test</name>"""))
        assertTrue(gpx.contains("""<trkpt lat="40.0" lon="-73.0">"""))
        assertTrue(gpx.contains("<ele>12.5</ele>"))
        assertTrue(gpx.contains("<time>2026-07-06T16:00:00Z</time>"))
        assertTrue(gpx.contains("<speed>4.2</speed>"))
    }
}
