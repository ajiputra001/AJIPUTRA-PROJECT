package com.qris.soundbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qris.soundbox.R

class AudioSyncWorker : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice-Notf Aktif")
            .setContentText("Aplikasi berjalan di latar belakang untuk memantau pembayaran.")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        // ID must not be 0
        startForeground(101, notification)
        
        // Memastikan sistem selalu mencoba menghidupkan kembali service ini jika dimatikan paksa
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Sistem QRIS",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Menjaga aplikasi tetap hidup di latar belakang"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kita bisa me-restart service ini saat dihancurkan jika ingin lebih ekstrem,
        // tapi START_STICKY biasanya sudah cukup.
    }

    companion object {
        const val CHANNEL_ID = "KeepAliveServiceChannel"
        
        fun start(context: Context) {
            val intent = Intent(context, AudioSyncWorker::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
