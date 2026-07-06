package com.speedcompass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TrackingService : Service() {
    override fun onCreate() {
        super.onCreate()
        TrackingRepository.initialize(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification())
                TrackingRepository.startLocationUpdates(recording = true)
            }
            ACTION_STOP -> {
                TrackingRepository.stopLocationUpdates(stopRecording = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Recording bike route")
            .setContentText("Speed Compass is logging GPS points.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Route tracking",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "route_tracking"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.speedcompass.START_TRACKING"
        private const val ACTION_STOP = "com.speedcompass.STOP_TRACKING"

        fun startIntent(context: Context): Intent =
            Intent(context, TrackingService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
    }
}
