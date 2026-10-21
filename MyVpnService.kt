package com.blackberry.loader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val CHANNEL_ID = "BlackBerryVpnChannel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
            builder.setSession("BlackBerry VPN")
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0) // Tüm trafiği yakala
            builder.addDnsServer("1.1.1.1")

            // ⚠️ ÖNEMLİ: Bu uygulamayı ve Telegram'ı VPN dışında tut (JSON'daki "allow" mantığı)
            builder.addDisallowedApplication(packageName) // Kendi uygulamamız (Loader) serbest
            builder.addDisallowedApplication("org.telegram.messenger") // Telegram serbest

            // JSON'da "deny" olan uygulamaları VPN'e sok (PUBG)
            // Builder zaten varsayılan olarak tüm uygulamaları VPN'e sokar, sadece disallowed olanları çıkarır.
            // Yani PUBG (com.tencent.ig) VPN'den geçecek ve engellenecek.

            vpnInterface = builder.establish()

            // Bildirim göster (VPN servisi çalışıyor göstergesi)
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BlackBerry VPN")
                .setContentText("Trafik filtreleniyor...")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()

            startForeground(1, notification)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}