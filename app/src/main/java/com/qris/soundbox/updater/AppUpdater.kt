package com.qris.soundbox.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.qris.soundbox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class AppUpdater(private val context: Context, private val owner: String, private val repo: String) {

    private val TAG = "AppUpdater"
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GithubApiService::class.java)
    
    private var downloadId: Long = -1

    suspend fun checkForUpdates(): GithubRelease? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getLatestRelease(owner, repo)
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val currentVersion = BuildConfig.VERSION_NAME
                        val latestVersion = release.tagName.replace("v", "")
                        
                        Log.d(TAG, "Current: $currentVersion, Latest: $latestVersion")
                        // Simple version check (assumes semantic versioning like 1.0.0 or 1.0)
                        if (latestVersion != currentVersion && latestVersion > currentVersion) {
                            return@withContext release
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}")
            }
            null
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String, fileName: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Downloading Voice-Notf Update")
            .setDescription("Mengunduh versi terbaru...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
        
        Toast.makeText(context, "Mulai mengunduh update...", Toast.LENGTH_SHORT).show()

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(fileName)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!file.exists()) {
            Log.e(TAG, "APK file not found for installation")
            return
        }

        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent: ${e.message}")
            Toast.makeText(context, "Gagal membuka installer", Toast.LENGTH_SHORT).show()
        }
    }
}
