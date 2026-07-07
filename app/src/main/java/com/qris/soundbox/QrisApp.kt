package com.qris.soundbox

import android.app.Application
import com.qris.soundbox.data.AppDatabase
import com.qris.soundbox.data.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.google.firebase.FirebaseApp
import android.util.Log

class QrisApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        
        try {
            FirebaseApp.initializeApp(this)
            Log.d("QrisApp", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("QrisApp", "Failed to initialize Firebase", e)
        }
        
        // Populate default rules if they are missing
        CoroutineScope(Dispatchers.IO).launch {
            val ruleDao = database.ruleDao()
            val existingRules = ruleDao.getAllRulesList()
            val existingPackages = existingRules.map { it.packageName }.toSet()
            
            val defaults = listOf(
                Rule(
                    appName = "GoBiz",
                    packageName = "com.gojek.gobiz",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Pembayaran QRIS masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Shopee Partner",
                    packageName = "com.shopee.partner",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Shopee Pay sebesar {amount} rupiah berhasil dari {name}"
                ),
                Rule(
                    appName = "BCA Merchant",
                    packageName = "com.bca.merchant",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "BCA masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "DANA",
                    packageName = "id.dana",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Dana masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "OVO Merchant",
                    packageName = "xin.mian.ovo",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "OVO sebesar {amount} rupiah diterima dari {name}"
                ),
                Rule(
                    appName = "myBCA",
                    packageName = "id.co.bca.mybca",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "B C A masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "BCA Mobile",
                    packageName = "com.bca",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "B C A masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Livin by Mandiri",
                    packageName = "id.co.bankmandiri.livin.in",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Mandiri masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "BRImo",
                    packageName = "id.co.bri.brimo",
                    regexAmount = """(?i)(?:Rp\.?|sebesar|nominal)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "B R I masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "BNI Mobile",
                    packageName = "src.bni.amws",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "B N I masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "BSI Mobile",
                    packageName = "id.co.bsimobile.pisa",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "B S I masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "SeaBank",
                    packageName = "com.seabank.id",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "SeaBank masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Bank Jago",
                    packageName = "com.jago.jagoapp",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Jago masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Allo Bank",
                    packageName = "com.allobank.alloapp",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Allo Bank masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "blu by BCA",
                    packageName = "id.co.bcadigital.blu",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "blu masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Neobank",
                    packageName = "com.bankneo.neobank",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Neo masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "OVO",
                    packageName = "com.ovo.id",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Ovo masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "Shopee",
                    packageName = "com.shopee.id",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Shopee Pay masuk sebesar {amount} rupiah dari {name}"
                ),
                Rule(
                    appName = "GoPay",
                    packageName = "com.gojek.app",
                    regexAmount = """(?i)(?:Rp\.?|sebesar)\s*([\d\.,]+)""",
                    regexPayer = """(?i)dari\s+([A-Za-z0-9\s.]{3,30})""",
                    speakTemplate = "Gopay masuk sebesar {amount} rupiah dari {name}"
                )
            )
            
            defaults.forEach { rule ->
                if (!existingPackages.contains(rule.packageName)) {
                    ruleDao.insertRule(rule)
                }
            }
        }
    }
}
