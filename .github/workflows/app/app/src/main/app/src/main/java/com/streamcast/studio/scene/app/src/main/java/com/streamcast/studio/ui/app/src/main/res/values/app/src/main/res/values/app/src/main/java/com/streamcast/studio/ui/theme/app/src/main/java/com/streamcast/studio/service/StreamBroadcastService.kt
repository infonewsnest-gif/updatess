package com.streamcast.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StreamBroadcastService : Service() {

    companion object {
        const val CHANNEL_ID = "StreamBroadcastChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAM -> {
                val platform = intent.getStringExtra("EXTRA_PLATFORM") ?: "Platform"
                val caption = intent.getStringExtra("EXTRA_CAPTION") ?: ""
                val notification = createNotification("$platform লাইভ সম্প্রচার চলছে: $caption")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_STOP_STREAM -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Stream Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NewsNestBD Live Studio")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
