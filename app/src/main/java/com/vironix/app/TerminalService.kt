package com.vironix.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * TerminalService
 *
 * A minimal foreground service so Android doesn't kill the shell process
 * when the app is backgrounded (e.g. user switches apps mid-download).
 * This is the same pattern Termux uses: a persistent low-priority
 * notification keeps the process alive.
 *
 * This is currently a skeleton — TerminalActivity runs the shell directly
 * for simplicity in v1. Wire this up later if you want long-running
 * background sessions (e.g. a server started with `apk add nginx`).
 */
class TerminalService : Service() {

    private val channelId = "vironix_running"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Vironix session", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Vironix")
            .setContentText("Linux session running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
