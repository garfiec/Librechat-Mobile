package com.garfiec.librechat.core.data.gate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.garfiec.librechat.core.data.R

/**
 * Keeps the process unfrozen while the gate sign-in's Custom Tab covers the app: Android freezes cached
 * apps, and a frozen loopback listener can't answer the gate's form post before the authorization code
 * expires. A `shortService` on Android 14+: no extra permission, a few minutes at most.
 */
internal class GateSignInService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.gate_sign_in_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.gate_sign_in_title))
            .setContentText(getString(R.string.gate_sign_in_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private companion object {
        const val CHANNEL_ID = "gate_sign_in"
        const val NOTIFICATION_ID = 0x6A7E
    }
}
