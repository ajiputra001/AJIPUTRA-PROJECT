package com.qris.soundbox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qris.soundbox.service.AudioSyncWorker

class StartupInit : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot completed. Starting KeepAliveService.")
            
            // Start our foreground service to keep the app alive
            try {
                AudioSyncWorker.start(context)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Gagal memulai service: ${e.message}")
            }
        }
    }
}
