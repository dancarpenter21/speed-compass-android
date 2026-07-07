package com.speedcompass

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant

object TrackingRepository {
    private const val PREFS = "speed_compass"
    private const val UNIT = "unit"
    private const val ROUTE_FILE = "latest_route.tsv"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val points = mutableListOf<RoutePoint>()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
        points += readRoute()
        val unit = runCatching { SpeedUnit.valueOf(prefs.getString(UNIT, SpeedUnit.Mph.name)!!) }
            .getOrDefault(SpeedUnit.Mph)
        _state.update { it.copy(selectedUnit = unit, pointCount = points.size) }
    }

    fun setUnit(unit: SpeedUnit) {
        prefs.edit().putString(UNIT, unit.name).apply()
        _state.update { it.copy(selectedUnit = unit) }
    }

    fun updateSensorHeading(headingDegrees: Float?) {
        _state.update { current ->
            val target = selectHeading(current.gpsBearingDegrees, headingDegrees, current.speedMetersPerSecond)
            current.copy(
                sensorHeadingDegrees = headingDegrees,
                displayHeadingDegrees = target?.let { smoothHeading(current.displayHeadingDegrees, it) }
                    ?: current.displayHeadingDegrees,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(recording: Boolean) {
        if (locationCallback != null) {
            if (recording) {
                points.clear()
                routeFile().delete()
            }
            _state.update { it.copy(isRecording = it.isRecording || recording, pointCount = points.size) }
            return
        }
        if (recording) {
            points.clear()
            routeFile().delete()
        }
        _state.update { it.copy(isRecording = recording, pointCount = points.size, errorMessage = null) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::handleLocation)
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, appContext.mainLooper)
            .addOnFailureListener { error ->
                _state.update { it.copy(errorMessage = error.localizedMessage ?: "Location unavailable") }
            }
    }

    fun stopLocationUpdates(stopRecording: Boolean) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        if (stopRecording) _state.update { it.copy(isRecording = false) }
    }

    fun snapshotPoints(): List<RoutePoint> = points.toList()

    private fun handleLocation(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else 0f
        val bearing = if (location.hasBearing()) location.bearing else null
        if (_state.value.isRecording) {
            val point = RoutePoint(
                timestamp = Instant.ofEpochMilli(location.time.takeIf { it > 0L } ?: System.currentTimeMillis()),
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                bearingDegrees = bearing,
            )
            points += point
            appendRoutePoint(point)
        }
        _state.update { current ->
            val target = selectHeading(bearing, current.sensorHeadingDegrees, speed)
            current.copy(
                speedMetersPerSecond = speed,
                gpsBearingDegrees = bearing,
                displayHeadingDegrees = target?.let { smoothHeading(current.displayHeadingDegrees, it) }
                    ?: current.displayHeadingDegrees,
                hasLocation = true,
                lastLatitude = location.latitude,
                lastLongitude = location.longitude,
                pointCount = points.size,
                errorMessage = null,
            )
        }
    }

    private fun routeFile(): File = File(appContext.filesDir, ROUTE_FILE)

    private fun appendRoutePoint(point: RoutePoint) {
        routeFile().appendText(
            listOf(
                point.timestamp.toEpochMilli(),
                point.latitude,
                point.longitude,
                point.altitudeMeters ?: "",
                point.speedMetersPerSecond ?: "",
                point.bearingDegrees ?: "",
            ).joinToString("\t") + "\n",
        )
    }

    private fun readRoute(): List<RoutePoint> {
        val file = routeFile()
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 6) return@mapNotNull null
            runCatching {
                RoutePoint(
                    timestamp = Instant.ofEpochMilli(parts[0].toLong()),
                    latitude = parts[1].toDouble(),
                    longitude = parts[2].toDouble(),
                    altitudeMeters = parts[3].takeIf { it.isNotBlank() }?.toDouble(),
                    speedMetersPerSecond = parts[4].takeIf { it.isNotBlank() }?.toFloat(),
                    bearingDegrees = parts[5].takeIf { it.isNotBlank() }?.toFloat(),
                )
            }.getOrNull()
        }
    }
}
