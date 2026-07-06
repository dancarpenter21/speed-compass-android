package com.speedcompass

import java.time.format.DateTimeFormatter

fun routeToGpx(points: List<RoutePoint>, routeName: String = "Speed Compass Route"): String {
    val trackPoints = points.joinToString(separator = "\n") { point ->
        buildString {
            append("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
            append('\n')
            point.altitudeMeters?.let { append("        <ele>").append(it).append("</ele>\n") }
            append("        <time>")
                .append(DateTimeFormatter.ISO_INSTANT.format(point.timestamp))
                .append("</time>\n")
            point.speedMetersPerSecond?.let {
                append("        <extensions><speed>")
                    .append(it)
                    .append("</speed></extensions>\n")
            }
            append("      </trkpt>")
        }
    }
    return """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<gpx version="1.1" creator="Speed Compass" xmlns="http://www.topografix.com/GPX/1/1">
        |  <metadata>
        |    <name>${routeName.xmlEscaped()}</name>
        |  </metadata>
        |  <trk>
        |    <name>${routeName.xmlEscaped()}</name>
        |    <trkseg>
        |$trackPoints
        |    </trkseg>
        |  </trk>
        |</gpx>
        |
    """.trimMargin()
}

private fun String.xmlEscaped(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
