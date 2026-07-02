package com.qris.soundbox

import android.app.Application
import com.qris.soundbox.data.AppDatabase
import com.qris.soundbox.data.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QrisApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Populate default rules if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            val ruleDao = database.ruleDao()
            if (ruleDao.getRuleCount() == 0) {
                val defaults = listOf(
                    Rule(
                        appName = "GoBiz",
                        packageName = "com.gojek.gobiz",
                        regexAmount = """Rp\s*([0-9.,]+)""",
                        regexPayer = """dari\s+([A-Za-z0-9\s]{3,30})""",
                        speakTemplate = "Pembayaran QRIS masuk sebesar {amount} rupiah dari {name}"
                    ),
                    Rule(
                        appName = "Shopee Partner",
                        packageName = "com.shopee.partner",
                        regexAmount = """Rp\s*([0-9.,]+)""",
                        regexPayer = """dari\s+([A-Za-z0-9\s]{3,30})""",
                        speakTemplate = "ShopeePay sebesar {amount} rupiah berhasil dari {name}"
                    ),
                    Rule(
                        appName = "BCA Merchant",
                        packageName = "com.bca.merchant",
                        regexAmount = """Rp\s*([0-9.,]+)""",
                        regexPayer = """dari\s+([A-Za-z0-9\s]{3,30})""",
                        speakTemplate = "BCA masuk sebesar {amount} rupiah dari {name}"
                    ),
                    Rule(
                        appName = "DANA",
                        packageName = "id.dana",
                        regexAmount = """Rp\s*([0-9.,]+)""",
                        regexPayer = """dari\s+(.+?)\s+berhasil""",
                        speakTemplate = "Dana masuk sebesar {amount} rupiah dari {name}"
                    ),
                    Rule(
                        appName = "OVO Merchant",
                        packageName = "xin.mian.ovo",
                        regexAmount = """Rp\s*([0-9.,]+)""",
                        regexPayer = """dari\s+([A-Za-z0-9\s]{3,30})""",
                        speakTemplate = "OVO sebesar {amount} rupiah diterima dari {name}"
                    )
                )
                defaults.forEach { ruleDao.insertRule(it) }
            }
        }
    }
}
