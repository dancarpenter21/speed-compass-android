package com.speedcompass

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Surface as AndroidSurface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        TrackingRepository.initialize(this)
        sensorManager = getSystemService(SensorManager::class.java)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        setContent {
            SpeedCompassApp(
                hasLocationPermission = ::hasLocationPermission,
                requestPermissions = ::requestablePermissions,
                onPermissionReady = { TrackingRepository.startLocationUpdates(recording = false) },
                onStartTracking = {
                    ContextCompat.startForegroundService(this, TrackingService.startIntent(this))
                },
                onStopTracking = {
                    startService(TrackingService.stopIntent(this))
                    if (hasLocationPermission()) TrackingRepository.startLocationUpdates(recording = false)
                },
                onOpenMaps = ::openMaps,
                onExportRoute = ::shareLatestRoute,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (hasLocationPermission()) TrackingRepository.startLocationUpdates(recording = false)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (!TrackingRepository.state.value.isRecording) {
            TrackingRepository.stopLocationUpdates(stopRecording = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        ) {
            return
        }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val adjustedMatrix = FloatArray(9)
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: AndroidSurface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        when (rotation) {
            AndroidSurface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                adjustedMatrix,
            )
            AndroidSurface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                adjustedMatrix,
            )
            AndroidSurface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                adjustedMatrix,
            )
            else -> rotationMatrix.copyInto(adjustedMatrix)
        }
        SensorManager.getOrientation(adjustedMatrix, orientation)
        TrackingRepository.updateSensorHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestablePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun openMaps(state: DashboardState) {
        val uri = if (state.lastLatitude != null && state.lastLongitude != null) {
            val latitude = String.format(Locale.US, "%.6f", state.lastLatitude)
            val longitude = String.format(Locale.US, "%.6f", state.lastLongitude)
            Uri.parse("geo:$latitude,$longitude?z=16")
        } else {
            Uri.parse("geo:0,0")
        }
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(GOOGLE_MAPS_PACKAGE)
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(googleMapsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(fallbackIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "No maps app available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareLatestRoute() {
        val points = TrackingRepository.snapshotPoints()
        if (points.isEmpty()) return
        val exportDir = File(cacheDir, "exports").also { it.mkdirs() }
        val file = File(exportDir, "speed-compass-route.gpx")
        file.writeText(routeToGpx(points))
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, "Export GPX route"))
    }

    private companion object {
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}

@Composable
private fun SpeedCompassApp(
    hasLocationPermission: () -> Boolean,
    requestPermissions: () -> Array<String>,
    onPermissionReady: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenMaps: (DashboardState) -> Unit,
    onExportRoute: () -> Unit,
) {
    val state by TrackingRepository.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            onPermissionReady()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) onPermissionReady() else permissionLauncher.launch(requestPermissions())
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF050505),
        ) {
            Dashboard(
                state = state,
                onToggleUnit = {
                    TrackingRepository.setUnit(
                        if (state.selectedUnit == SpeedUnit.Mph) SpeedUnit.Kmh else SpeedUnit.Mph,
                    )
                },
                onStartTracking = {
                    if (hasLocationPermission()) onStartTracking() else permissionLauncher.launch(requestPermissions())
                },
                onStopTracking = onStopTracking,
                onOpenMaps = { onOpenMaps(state) },
                onExportRoute = onExportRoute,
            )
        }
    }
}

@Composable
private fun Dashboard(
    state: DashboardState,
    onToggleUnit: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenMaps: () -> Unit,
    onExportRoute: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(24.dp),
    ) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedPanel(state, onToggleUnit, Modifier.weight(1f).fillMaxHeight())
                CompassPanel(state, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpeedPanel(state, onToggleUnit, Modifier.weight(1f).fillMaxWidth())
                CompassPanel(state, Modifier.weight(1f).fillMaxWidth().offset(y = (-76).dp))
            }
        }

        Controls(
            state = state,
            onStartTracking = onStartTracking,
            onStopTracking = onStopTracking,
            onOpenMaps = onOpenMaps,
            onExportRoute = onExportRoute,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = if (isLandscape) 0.dp else (-28).dp),
        )
    }
}

@Composable
private fun SpeedPanel(state: DashboardState, onToggleUnit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = twoDigitSpeed(state.speedMetersPerSecond, state.selectedUnit),
            color = Color(0xFFF2F2F2),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 168.sp,
            lineHeight = 168.sp,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onToggleUnit,
            colors = darkButtonColors(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                state.selectedUnit.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFE0E0E0), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CompassPanel(state: DashboardState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CompassRing(
            headingDegrees = state.displayHeadingDegrees,
            modifier = Modifier.size(280.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = ordinalDirection(state.displayHeadingDegrees),
                color = Color(0xFFF2F2F2),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 54.sp,
            )
            Text(
                text = "${normalizeDegrees(state.displayHeadingDegrees).toInt().toString().padStart(3, '0')}°",
                color = Color(0xFFBDBDBD),
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun CompassRing(headingDegrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Color(0xFFE8E8E8),
            radius = radius - 8.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        for (degree in 0 until 360 step 5) {
            val corrected = Math.toRadians((degree - headingDegrees - 90f).toDouble())
            val isMajor = degree % 45 == 0
            val outer = radius - 8.dp.toPx()
            val inner = outer - if (isMajor) 18.dp.toPx() else 8.dp.toPx()
            val start = Offset(
                center.x + cos(corrected).toFloat() * inner,
                center.y + sin(corrected).toFloat() * inner,
            )
            val end = Offset(
                center.x + cos(corrected).toFloat() * outer,
                center.y + sin(corrected).toFloat() * outer,
            )
            drawLine(
                color = if (isMajor) Color(0xFFF2F2F2) else Color(0xFF7A7A7A),
                start = start,
                end = end,
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // The dial rotates beneath the fixed heading index, so this marker stays
        // attached to zero degrees and always identifies north.
        val northAngle = Math.toRadians((-headingDegrees - 90f).toDouble())
        val northDirection = Offset(
            cos(northAngle).toFloat(),
            sin(northAngle).toFloat(),
        )
        val northTangent = Offset(-northDirection.y, northDirection.x)
        fun northArrowPoint(distance: Float, sideways: Float = 0f) = Offset(
            x = center.x + northDirection.x * distance + northTangent.x * sideways,
            y = center.y + northDirection.y * distance + northTangent.y * sideways,
        )

        val arrowTip = radius - 10.dp.toPx()
        val arrowShoulder = radius - 34.dp.toPx()
        val arrowTail = radius - 57.dp.toPx()
        val arrowPath = Path().apply {
            moveTo(northArrowPoint(arrowTip).x, northArrowPoint(arrowTip).y)
            lineTo(northArrowPoint(arrowShoulder, 11.dp.toPx()).x, northArrowPoint(arrowShoulder, 11.dp.toPx()).y)
            lineTo(northArrowPoint(arrowShoulder, 3.5.dp.toPx()).x, northArrowPoint(arrowShoulder, 3.5.dp.toPx()).y)
            lineTo(northArrowPoint(arrowTail, 3.5.dp.toPx()).x, northArrowPoint(arrowTail, 3.5.dp.toPx()).y)
            lineTo(northArrowPoint(arrowTail, -3.5.dp.toPx()).x, northArrowPoint(arrowTail, -3.5.dp.toPx()).y)
            lineTo(northArrowPoint(arrowShoulder, -3.5.dp.toPx()).x, northArrowPoint(arrowShoulder, -3.5.dp.toPx()).y)
            lineTo(northArrowPoint(arrowShoulder, -11.dp.toPx()).x, northArrowPoint(arrowShoulder, -11.dp.toPx()).y)
            close()
        }
        drawPath(
            path = arrowPath,
            color = Color(0xFF4D080C),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = arrowPath,
            color = Color(0xFFFF3344),
        )
        drawLine(
            color = Color(0xFFFFA0A8),
            start = northArrowPoint(arrowTail + 4.dp.toPx()),
            end = northArrowPoint(arrowTip - 8.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        drawLine(
            color = Color(0xFFF2F2F2),
            start = Offset(center.x, center.y - radius + 2.dp.toPx()),
            end = Offset(center.x, center.y - radius + 34.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun Controls(
    state: DashboardState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenMaps: () -> Unit,
    onExportRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = if (state.isRecording) onStopTracking else onStartTracking,
            colors = darkButtonColors(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(if (state.isRecording) "Stop" else "Start", fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onOpenMaps,
            colors = darkButtonColors(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text("Maps", fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onExportRoute,
            enabled = !state.isRecording && state.pointCount > 0,
            colors = darkButtonColors(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text("Export GPX (${state.pointCount})", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun darkButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF161616),
    contentColor = Color(0xFFF2F2F2),
    disabledContainerColor = Color(0xFF101010),
    disabledContentColor = Color(0xFF696969),
)
