package com.qris.soundbox

import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import com.qris.soundbox.data.AppDatabase
import com.qris.soundbox.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class AudioPlaybackService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private val TAG = "QrisNotificationListener"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val NOTIFICATION_ID = 9911
    private val CHANNEL_ID = "QrisScannerChannel"

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        Log.d(TAG, "Service Created, initializing TTS")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener Connected, starting Foreground Service")
        startForegroundServiceNotification()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener Disconnected, requesting rebind")
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                requestRebind(android.content.ComponentName(this, AudioPlaybackService::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting rebind: ${e.message}")
        }
    }

    private fun startForegroundServiceNotification() {
        val channelName = "Pemindai Transaksi QRIS"
        val channelDescription = "Menjaga sistem pemindai suara pembayaran tetap aktif di latar belakang"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDescription
            }
            val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice-Notf")
            .setContentText("Pemindai Suara Pembayaran QRIS Aktif")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)

        val notification = builder.build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Indonesian language is not supported or missing data")
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsReady = true
            Log.d(TAG, "TTS Initialized successfully")
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        
        val fullContentText = if (bigText.length > text.length) bigText else text
        val rawMessage = "Title: $title | Text: $fullContentText"

        Log.d(TAG, "Notification received from: $packageName, Raw: $rawMessage")

        serviceScope.launch {
            val rules = db.ruleDao().getRulesForPackage(packageName)
            if (rules.isEmpty()) {
                // Smart Detection Logic
                val lowerText = fullContentText.lowercase()
                val isPaymentKeyword = lowerText.contains("transfer") || 
                                       lowerText.contains("pembayaran") || 
                                       lowerText.contains("masuk") || 
                                       lowerText.contains("berhasil") || 
                                       lowerText.contains("terima") ||
                                       lowerText.contains("bayar") ||
                                       isTargetApp(packageName)

                if (isPaymentKeyword) {
                    val smartAmountPattern = Pattern.compile("(?i)rp\\s*([\\d\\.,]+)")
                    val smartMatcher = smartAmountPattern.matcher(fullContentText)
                    
                    if (smartMatcher.find()) {
                        val rawAmount = smartMatcher.group(1) ?: "0"
                        val parsedAmount = parseAmount(rawAmount)
                        
                        val payerName = "Pelanggan (Auto)"
                        val speakText = "Pembayaran masuk sebesar ${formatNominalToSpeech(parsedAmount)} rupiah"
                        
                        speakOut(speakText)

                        val tx = saveTransaction(
                            appName = getAppNameFromPackage(packageName),
                            packageName = packageName,
                            amount = parsedAmount,
                            payerName = payerName,
                            rawText = rawMessage,
                            isParsed = true
                        )

                        val prefs = getSharedPreferences("qris_prefs", android.content.Context.MODE_PRIVATE)
                        val webhookUrl = prefs.getString("webhook_url", "") ?: ""
                        if (webhookUrl.isNotEmpty()) {
                            sendWebhook(webhookUrl, tx)
                        }
                        Log.d(TAG, "Smart Detection matched: $packageName, Amount: $parsedAmount")
                    } else if (isTargetApp(packageName)) {
                        saveTransaction(
                            appName = getAppNameFromPackage(packageName),
                            packageName = packageName,
                            amount = 0.0,
                            payerName = "Tidak Teridentifikasi",
                            rawText = rawMessage,
                            isParsed = false
                        )
                    }
                }
                return@launch
            }

            for (rule in rules) {
                try {
                    val amountPattern = Pattern.compile(rule.regexAmount)
                    val amountMatcher = amountPattern.matcher(fullContentText)
                    
                    if (amountMatcher.find()) {
                        val rawAmount = amountMatcher.group(1) ?: "0"
                        val parsedAmount = parseAmount(rawAmount)

                        var payerName = "Pelanggan"
                        if (rule.regexPayer.isNotEmpty()) {
                            val payerPattern = Pattern.compile(rule.regexPayer)
                            val payerMatcher = payerPattern.matcher(fullContentText)
                            if (payerMatcher.find()) {
                                payerName = payerMatcher.group(1)?.trim() ?: "Pelanggan"
                            }
                        }

                        val speakText = rule.speakTemplate
                            .replace("{amount}", formatNominalToSpeech(parsedAmount))
                            .replace("{name}", payerName)

                        speakOut(speakText)

                        val tx = saveTransaction(
                            appName = rule.appName,
                            packageName = packageName,
                            amount = parsedAmount,
                            payerName = payerName,
                            rawText = rawMessage,
                            isParsed = true
                        )

                        val prefs = getSharedPreferences("qris_prefs", android.content.Context.MODE_PRIVATE)
                        val webhookUrl = prefs.getString("webhook_url", "") ?: ""
                        if (webhookUrl.isNotEmpty()) {
                            sendWebhook(webhookUrl, tx)
                        }

                        Log.d(TAG, "Matched rule: ${rule.appName}, Amount: $parsedAmount, Payer: $payerName")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error matching rule ${rule.appName}: ${e.message}")
                }
            }
        }
    }

    private fun isTargetApp(packageName: String): Boolean {
        val targets = listOf("com.gojek.gobiz", "com.shopee.partner", "com.bca.merchant", "id.dana.bisnis", "xin.mian.ovo")
        return targets.any { packageName.contains(it) }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                packageName.contains("gobiz") -> "GoBiz"
                packageName.contains("shopee") -> "Shopee Partner"
                packageName.contains("bca") -> "BCA Merchant"
                packageName.contains("dana") -> "DANA Bisnis"
                packageName.contains("ovo") -> "OVO Merchant"
                else -> packageName
            }
        }
    }

    private fun parseAmount(rawAmount: String): Double {
        var cleaned = rawAmount.replace("Rp", "", ignoreCase = true)
            .replace(" ", "")
            .replace("\u00A0", "")
        
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".")
        } else if (cleaned.contains(",")) {
            val parts = cleaned.split(",")
            if (parts.size == 2 && parts[1].length == 3) {
                cleaned = cleaned.replace(",", "")
            } else {
                cleaned = cleaned.replace(",", ".")
            }
        } else if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            if (parts.size == 2 && parts[1].length == 3) {
                cleaned = cleaned.replace(".", "")
            } else {
                cleaned = cleaned.replace(".", "")
            }
        }
        
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun formatNominalToSpeech(amount: Double): String {
        val longVal = amount.toLong()
        return if (longVal.toDouble() == amount) {
            longVal.toString()
        } else {
            amount.toString()
        }
    }

    private fun speakOut(text: String) {
        if (isTtsReady && tts != null) {
            val prefs = getSharedPreferences("qris_prefs", android.content.Context.MODE_PRIVATE)
            val pitch = prefs.getFloat("voice_pitch", 1.0f)
            val speed = prefs.getFloat("voice_speed", 1.0f)
            val isBeepEnabled = prefs.getBoolean("is_beep_enabled", true)
            
            tts?.setPitch(pitch)
            tts?.setSpeechRate(speed)

            if (isBeepEnabled) {
                try {
                    val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                    Thread.sleep(250)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "QrisNotificationSpeak")
        } else {
            Log.e(TAG, "TTS not ready or null")
        }
    }

    private suspend fun saveTransaction(
        appName: String,
        packageName: String,
        amount: Double,
        payerName: String,
        rawText: String,
        isParsed: Boolean
    ): Transaction {
        val tx = Transaction(
            appName = appName,
            packageName = packageName,
            amount = amount,
            payerName = payerName,
            rawText = rawText,
            isParsedSuccessfully = isParsed
        )
        db.transactionDao().insertTransaction(tx)
        
        val intent = Intent("com.qris.soundbox.TRANSACTION_RECEIVED")
        sendBroadcast(intent)
        return tx
    }

    private fun sendWebhook(urlStr: String, transaction: Transaction) {
        if (urlStr.isEmpty()) return
        serviceScope.launch {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                val jsonParam = org.json.JSONObject().apply {
                    put("id", transaction.id)
                    put("timestamp", transaction.timestamp)
                    put("appName", transaction.appName)
                    put("packageName", transaction.packageName)
                    put("amount", transaction.amount)
                    put("payerName", transaction.payerName)
                    put("rawText", transaction.rawText)
                    put("isParsed", transaction.isParsedSuccessfully)
                }

                conn.outputStream.use { os ->
                    val input = jsonParam.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "Webhook response: $responseCode")
            } catch (e: Exception) {
                Log.e(TAG, "Webhook failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}
