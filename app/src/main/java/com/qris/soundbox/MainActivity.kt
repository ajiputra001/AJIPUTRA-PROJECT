package com.qris.soundbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qris.soundbox.data.AppDatabase
import com.qris.soundbox.data.Rule
import com.qris.soundbox.data.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Theme Colors
val DarkBg = Color(0xFF0F0C20)
val CardBg = Color(0xFF1B1738)
val AccentPurple = Color(0xFF8B5CF6)
val AccentPink = Color(0xFFEC4899)
val AccentGreen = Color(0xFF10B981)
val TextGray = Color(0xFF9CA3AF)
val TextWhite = Color(0xFFF9FAFB)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start Foreground Service to keep app alive
        try {
            com.qris.soundbox.service.KeepAliveService.start(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start KeepAliveService: ${e.message}")
        }
        
        tts = TextToSpeech(this, this)

        val db = AppDatabase.getDatabase(this)
        val factory = QrisViewModelFactory(db)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBg,
                    surface = CardBg,
                    primary = AccentPurple,
                    secondary = AccentPink,
                    onBackground = TextWhite,
                    onSurface = TextWhite
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    QrisAppUI(factory = factory, onSpeak = ::speakTestText, ttsReady = isTtsReady)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsReady = true
        }
    }

    private fun speakTestText(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MainActivitySpeakTest")
        } else {
            Toast.makeText(this, "TTS belum siap atau bahasa Indonesia tidak didukung", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

// Helpers
fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return formatter.format(amount).replace(",00", "").replace("Rp", "Rp ")
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat != null) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (pkgName == cn.packageName) {
                    return true
                }
            }
        }
    }
    return false
}

fun reconnectNotificationService(context: Context) {
    try {
        val pm = context.packageManager
        val cn = android.content.ComponentName(context, NotificationListener::class.java)
        pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Deprecated JSON checking, we use AppUpdater now inline in Compose

// ViewModel implementation
class QrisViewModel(private val db: AppDatabase) : ViewModel() {
    val transactionsFlow = db.transactionDao().getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rulesFlow = db.ruleDao().getAllRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val startOfDay: Long
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

    val todaySalesFlow = db.transactionDao().getTodayTotalSales(startOfDay)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addRule(rule: Rule) {
        viewModelScope.launch {
            db.ruleDao().insertRule(rule)
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            db.ruleDao().deleteRule(rule)
        }
    }

    fun toggleRule(rule: Rule) {
        viewModelScope.launch {
            db.ruleDao().insertRule(rule.copy(isActive = !rule.isActive))
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            db.transactionDao().deleteAllTransactions()
        }
    }
}

class QrisViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QrisViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QrisViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// UI Composables
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrisAppUI(factory: QrisViewModelFactory, onSpeak: (String) -> Unit, ttsReady: Boolean) {
    val viewModel: QrisViewModel = viewModel(factory = factory)
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    
    // Auto-update permission state
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    
    // In-App Update states
    data class UpdateInfo(val hasUpdate: Boolean, val versionName: String, val changelog: String, val apkUrl: String)
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var appUpdater by remember { mutableStateOf<com.qris.soundbox.updater.AppUpdater?>(null) }
    
    LaunchedEffect(Unit) {
        val updater = com.qris.soundbox.updater.AppUpdater(context, "ajiputra001", "Voice-Notf")
        appUpdater = updater
        val release = updater.checkForUpdates()
        if (release != null) {
            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            if (apkAsset != null) {
                updateInfo = UpdateInfo(true, release.tagName.replace("v", ""), release.body, apkAsset.downloadUrl)
            }
        }
    }
    
    if (updateInfo != null && updateInfo?.hasUpdate == true) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Pembaruan Tersedia") },
            text = {
                Column {
                    Text("Versi baru aplikasi Voice-Notf (v${updateInfo?.versionName}) telah dirilis.", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Perubahan:", color = TextGray, fontSize = 12.sp)
                    Text(updateInfo?.changelog ?: "", fontSize = 13.sp, color = TextWhite)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appUpdater?.downloadAndInstallUpdate(updateInfo?.apkUrl ?: "", "VoiceNotf_update.apk")
                        updateInfo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Perbarui Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("Nanti Saja")
                }
            }
        )
    }
    
    // Refresh permission status when activity resumes
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = isNotificationServiceEnabled(context)
                if (isPermissionGranted) {
                    reconnectNotificationService(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "App Logo",
                            tint = AccentPurple,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice-Notf",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardBg,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple("Dashboard", Icons.Default.Dashboard, 0),
                    Triple("Aturan", Icons.Default.Rule, 1),
                    Triple("Sandbox", Icons.Default.PlayArrow, 2),
                    Triple("Pengaturan", Icons.Default.Settings, 3),
                    Triple("Tentang", Icons.Default.Info, 4)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentPurple,
                            selectedTextColor = AccentPurple,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = Color(0x338B5CF6)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel, isPermissionGranted, context)
                1 -> RulesScreen(viewModel)
                2 -> SandboxScreen(viewModel, onSpeak)
                3 -> SettingsScreen(viewModel, isPermissionGranted, context)
                4 -> AboutScreen()
            }
        }
    }
}

@Composable
fun TransactionChart(transactions: List<Transaction>) {
    val successfulTx = transactions.filter { it.isParsedSuccessfully }.take(7).reversed()
    if (successfulTx.size < 2) return

    Card(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tren Transaksi Terakhir", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextGray)
            Spacer(modifier = Modifier.height(12.dp))
            
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val maxAmount = successfulTx.maxOfOrNull { it.amount } ?: 1.0
                val minAmount = successfulTx.minOfOrNull { it.amount } ?: 0.0
                val amountRange = if (maxAmount == minAmount) 1.0 else (maxAmount - minAmount)
                
                val points = successfulTx.mapIndexed { idx, tx ->
                    val x = idx * (width / (successfulTx.size - 1))
                    val y = height - ((tx.amount - minAmount) / amountRange * (height - 30.dp.toPx())).toFloat() - 15.dp.toPx()
                    androidx.compose.ui.geometry.Offset(x, y.toFloat())
                }
                
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = AccentPurple,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                
                val fillPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0].x, height)
                    for (i in 0 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                    lineTo(points.last().x, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(AccentPurple.copy(alpha = 0.3f), Color.Transparent),
                        startY = points.minOf { it.y },
                        endY = height
                    )
                )
                
                points.forEach { point ->
                    drawCircle(
                        color = AccentPink,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: QrisViewModel, isPermissionGranted: Boolean, context: Context) {
    val transactions by viewModel.transactionsFlow.collectAsState()
    val todaySales by viewModel.todaySalesFlow.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Alert
        if (!isPermissionGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.Red,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Izin Akses Notifikasi Mati",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Aplikasi tidak bisa membaca pembayaran QRIS. Ketuk di sini untuk menyalakannya.",
                                fontSize = 12.sp,
                                color = Color(0xFFFCA5A5)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Khusus Android 13+: Jika setelan tidak bisa dibuka (Restricted Settings), buka Info Aplikasi ini di Pengaturan HP -> tekan 3 titik di pojok kanan atas -> pilih 'Izinkan Pengaturan Terbatas' (Allow Restricted Settings).",
                                fontSize = 10.sp,
                                color = Color(0xFFFFD1D1),
                                lineHeight = 14.sp
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Go to Settings", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Today Earnings Card
        item {
            val totalEarnings = formatCurrency(todaySales)
            val successfulTxCount = transactions.filter { it.isParsedSuccessfully && it.timestamp >= getStartOfDay() }.size

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(AccentPurple, AccentPink)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text("Total QRIS Masuk Hari Ini", color = Color(0xCCF9FAFB), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        totalEarnings,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Trending",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "$successfulTxCount Pembayaran Sukses",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (transactions.filter { it.isParsedSuccessfully }.size >= 2) {
            item {
                TransactionChart(transactions)
            }
        }

        // Section Title
        item {
            Text(
                "Riwayat Transaksi",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextWhite,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Transactions List
        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = "Empty",
                            tint = TextGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Belum ada transaksi hari ini", color = TextGray)
                    }
                }
            }
        } else {
            items(transactions) { tx ->
                TransactionRow(tx)
            }
        }
    }
}

fun getStartOfDay(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Composable
fun TransactionRow(tx: Transaction) {
    val appColor = when {
        tx.appName.contains("gobiz", ignoreCase = true) -> Color(0xFF00AA13) // GoJek Green
        tx.appName.contains("shopee", ignoreCase = true) -> Color(0xFFEE4D2D) // Shopee Orange
        tx.appName.contains("bca", ignoreCase = true) -> Color(0xFF00569F) // BCA Blue
        tx.appName.contains("dana", ignoreCase = true) -> Color(0xFF108EE9) // Dana Blue
        tx.appName.contains("ovo", ignoreCase = true) -> Color(0xFF4C2A86) // OVO Purple
        else -> AccentPurple
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App indicator badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(appColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (tx.isParsedSuccessfully) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = tx.appName,
                    tint = if (tx.isParsedSuccessfully) AccentGreen else Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tx.appName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        formatTime(tx.timestamp),
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (tx.isParsedSuccessfully) "Dari: ${tx.payerName}" else "Gagal memproses teks notifikasi",
                    color = if (tx.isParsedSuccessfully) TextWhite else Color.Red.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!tx.isParsedSuccessfully) {
                    Text(
                        text = tx.rawText,
                        color = TextGray,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (tx.isParsedSuccessfully) {
                Text(
                    formatCurrency(tx.amount),
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: QrisViewModel) {
    val rules by viewModel.rulesFlow.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var regexAmount by remember { mutableStateOf("") }
    var regexPayer by remember { mutableStateOf("") }
    var speakTemplate by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Aturan")
            }
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Aturan Deteksi Notifikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextWhite
                )
                Text(
                    "Aplikasi menggunakan Regex berikut untuk mengekstrak nominal dan nama dari notifikasi. Anda dapat menonaktifkan atau menambahkan aturan baru.",
                    fontSize = 13.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (rules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada aturan deteksi", color = TextGray)
                    }
                }
            } else {
                items(rules) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Aturan Baru") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = appName,
                            onValueChange = { appName = it },
                            label = { Text("Nama Aplikasi (e.g. OVO)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Nama Paket (e.g. id.ovo.merchant)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regexAmount,
                            onValueChange = { regexAmount = it },
                            label = { Text("Regex Nominal (e.g. Rp\\s*([0-9.,]+))") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regexPayer,
                            onValueChange = { regexPayer = it },
                            label = { Text("Regex Pengirim (e.g. dari\\s+([A-Za-z\\s]+))") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = speakTemplate,
                            onValueChange = { speakTemplate = it },
                            label = { Text("Template Suara (e.g. Uang masuk {amount} dari {name})") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text("Template Bahasa Cepat:", fontSize = 11.sp, color = TextGray)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SuggestionChip(
                                onClick = { speakTemplate = "Pembayaran masuk sebesar {amount} rupiah dari {name}" },
                                label = { Text("Indo", fontSize = 10.sp) }
                            )
                            SuggestionChip(
                                onClick = { speakTemplate = "Matur nuwun, arto mlebet {amount} rupiah saking {name}" },
                                label = { Text("Jawa", fontSize = 10.sp) }
                            )
                            SuggestionChip(
                                onClick = { speakTemplate = "Hatur nuhun, artos lebet {amount} rupiah ti {name}" },
                                label = { Text("Sunda", fontSize = 10.sp) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (appName.isNotEmpty() && packageName.isNotEmpty() && regexAmount.isNotEmpty() && speakTemplate.isNotEmpty()) {
                                viewModel.addRule(
                                    Rule(
                                        appName = appName,
                                        packageName = packageName,
                                        regexAmount = regexAmount,
                                        regexPayer = regexPayer,
                                        speakTemplate = speakTemplate
                                    )
                                )
                                showAddDialog = false
                                // Clear inputs
                                appName = ""
                                packageName = ""
                                regexAmount = ""
                                regexPayer = ""
                                speakTemplate = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun RuleCard(rule: Rule, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                    Text(rule.packageName, fontSize = 11.sp, color = TextGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentPurple,
                            checkedTrackColor = AccentPurple.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF2E2856))
            Spacer(modifier = Modifier.height(12.dp))

            Text("Regex Nominal:", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Bold)
            Text(rule.regexAmount, fontSize = 12.sp, color = TextWhite)
            
            if (rule.regexPayer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Regex Pengirim:", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Bold)
                Text(rule.regexPayer, fontSize = 12.sp, color = TextWhite)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Template Suara:", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Bold)
            Text(rule.speakTemplate, fontSize = 12.sp, color = AccentPink, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SandboxScreen(viewModel: QrisViewModel, onSpeak: (String) -> Unit) {
    val rules by viewModel.rulesFlow.collectAsState()
    
    var selectedPackage by remember { mutableStateOf("") }
    var mockTitle by remember { mutableStateOf("QRIS Berhasil") }
    var mockText by remember { mutableStateOf("Pembayaran sebesar Rp 50.000 dari John Doe berhasil diterima") }

    var testResultText by remember { mutableStateOf("") }
    var testResultColor by remember { mutableStateOf(TextWhite) }

    LaunchedEffect(rules) {
        if (rules.isNotEmpty() && selectedPackage.isEmpty()) {
            selectedPackage = rules.first().packageName
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Sandbox Simulator",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextWhite
            )
            Text(
                "Gunakan halaman ini untuk memvalidasi pencocokan Regex dan preview suara TTS (Text-to-Speech).",
                fontSize = 13.sp,
                color = TextGray
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pilih Aplikasi Target:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    // Simple radio group for packages in rules
                    rules.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPackage = rule.packageName }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedPackage == rule.packageName,
                                onClick = { selectedPackage = rule.packageName },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentPurple)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rule.appName + " (${rule.packageName})", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = mockTitle,
                        onValueChange = { mockTitle = it },
                        label = { Text("Judul Notifikasi") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = mockText,
                        onValueChange = { mockText = it },
                        label = { Text("Teks Notifikasi") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            val activeRule = rules.find { it.packageName == selectedPackage }
                            if (activeRule == null) {
                                testResultText = "Error: Tidak ada aturan untuk paket aplikasi ini"
                                testResultColor = Color.Red
                                return@Button
                            }

                            try {
                                val amountPattern = Pattern.compile(activeRule.regexAmount)
                                val amountMatcher = amountPattern.matcher(mockText)

                                if (amountMatcher.find()) {
                                    val rawAmount = amountMatcher.group(1) ?: "0"
                                    val parsedAmount = cleanAmount(rawAmount)

                                    var payerName = "Pelanggan"
                                    if (activeRule.regexPayer.isNotEmpty()) {
                                        val payerPattern = Pattern.compile(activeRule.regexPayer)
                                        val payerMatcher = payerPattern.matcher(mockText)
                                        if (payerMatcher.find()) {
                                            payerName = payerMatcher.group(1)?.trim() ?: "Pelanggan"
                                        }
                                    }

                                    // Replace templates
                                    val speakResult = activeRule.speakTemplate
                                        .replace("{amount}", parsedAmount.toLong().toString())
                                        .replace("{name}", payerName)

                                    testResultText = """
                                        Pencocokan Sukses!
                                        Aplikasi: ${activeRule.appName}
                                        Nominal: ${formatCurrency(parsedAmount)}
                                        Pengirim: $payerName
                                        Hasil Suara: "$speakResult"
                                    """.trimIndent()
                                    testResultColor = AccentGreen

                                    onSpeak(speakResult)
                                } else {
                                    testResultText = "Gagal mencocokkan nominal. Cek kembali Regex Nominal Anda."
                                    testResultColor = Color.Red
                                }
                            } catch (e: Exception) {
                                testResultText = "Error Regex: ${e.message}"
                                testResultColor = Color.Red
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Simulasikan")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulasikan & Bersuara")
                    }
                }
            }
        }

        if (testResultText.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hasil Analisis Notifikasi:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(testResultText, color = testResultColor, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

fun cleanAmount(rawAmount: String): Double {
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

fun exportToCsv(context: Context, transactions: List<Transaction>) {
    try {
        val csvHeader = "ID,Waktu,Sumber Aplikasi,Nominal,Pengirim,Status Notifikasi,Pesan Asli\n"
        val csvBody = transactions.joinToString("\n") { tx ->
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp))
            val appName = tx.appName.replace(",", " ")
            val payerName = tx.payerName.replace(",", " ")
            val rawText = tx.rawText.replace(",", " ").replace("\n", " ")
            val status = if (tx.isParsedSuccessfully) "SUKSES" else "GAGAL"
            "${tx.id},$formattedDate,$appName,${tx.amount},$payerName,$status,$rawText"
        }
        val csvContent = csvHeader + csvBody
        val filename = "Laporan_QRIS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), filename)
        file.writeText(csvContent)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Laporan Transaksi QRIS")
            putExtra(Intent.EXTRA_TEXT, csvContent)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan CSV"))
        Toast.makeText(context, "Laporan disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal mengekspor laporan: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(viewModel: QrisViewModel, isPermissionGranted: Boolean, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val transactions by viewModel.transactionsFlow.collectAsState()
    
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isBatteryOptimizedExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val prefs = remember { context.getSharedPreferences("qris_prefs", Context.MODE_PRIVATE) }
    var voiceSpeed by remember { mutableStateOf(prefs.getFloat("voice_speed", 1.0f)) }
    var voicePitch by remember { mutableStateOf(prefs.getFloat("voice_pitch", 1.0f)) }
    var isBeepEnabled by remember { mutableStateOf(prefs.getBoolean("is_beep_enabled", true)) }
    var webhookUrl by remember { mutableStateOf(prefs.getString("webhook_url", "") ?: "") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Pengaturan & Sistem",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextWhite
            )
            Text(
                "Sesuaikan preferensi suara, webhook pengiriman data, dan laporan keuangan Anda.",
                fontSize = 13.sp,
                color = TextGray
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pengaturan Suara Soundbox:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Efek Suara Denting (Beep)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Mainkan nada detektor sebelum asisten berbicara.", color = TextGray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isBeepEnabled,
                            onCheckedChange = {
                                isBeepEnabled = it
                                prefs.edit().putBoolean("is_beep_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentPurple, checkedTrackColor = AccentPurple.copy(alpha = 0.5f))
                        )
                    }

                    Divider(color = Color(0xFF2E2856))

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kecepatan Suara: ${String.format(Locale.US, "%.2f", voiceSpeed)}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Default: 1.00x", fontSize = 11.sp, color = TextGray)
                        }
                        Slider(
                            value = voiceSpeed,
                            onValueChange = {
                                voiceSpeed = it
                                prefs.edit().putFloat("voice_speed", it).apply()
                            },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Nada Suara (Pitch): ${String.format(Locale.US, "%.2f", voicePitch)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Default: 1.00", fontSize = 11.sp, color = TextGray)
                        }
                        Slider(
                            value = voicePitch,
                            onValueChange = {
                                voicePitch = it
                                prefs.edit().putFloat("voice_pitch", it).apply()
                            },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentPink, activeTrackColor = AccentPink)
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Integrasi Webhook (API Forwarder):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Kirim payload JSON transaksi secara otomatis ke server lain / bot WhatsApp Anda.", color = TextGray, fontSize = 11.sp)
                    
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = {
                            webhookUrl = it
                            prefs.edit().putString("webhook_url", it).apply()
                        },
                        placeholder = { Text("https://domain.com/api/qris-webhook") },
                        label = { Text("URL Webhook") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Status Izin Sistem:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    StatusRow(
                        title = "Akses Notifikasi",
                        subtitle = "Wajib agar aplikasi bisa membaca notifikasi e-wallet.",
                        status = isPermissionGranted,
                        onClick = {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    )

                    Divider(color = Color(0xFF2E2856))

                    StatusRow(
                        title = "Abaikan Penghemat Baterai",
                        subtitle = "Sangat direkomendasikan agar sistem background tidak dimatikan Android secara paksa.",
                        status = isBatteryOptimizedExempt,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pengelolaan Data & Laporan:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Button(
                        onClick = {
                            exportToCsv(context, transactions)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Ekspor CSV")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekspor Semua Laporan Ke CSV / Excel")
                    }

                    Button(
                        onClick = {
                            viewModel.clearAllTransactions()
                            Toast.makeText(context, "Semua riwayat transaksi berhasil dihapus", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Riwayat")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Semua Riwayat Transaksi")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(title: String, subtitle: String, status: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp)
            Text(subtitle, color = TextGray, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (status) AccentGreen.copy(alpha = 0.2f) else AccentPurple
            ),
            modifier = Modifier.border(
                width = 1.dp,
                color = if (status) AccentGreen else Color.Transparent,
                shape = RoundedCornerShape(100.dp)
            ),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(
                text = if (status) "AKTIF" else "AKTIFKAN",
                color = if (status) AccentGreen else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Preview(showBackground = true, name = "About Screen Preview")
@Composable
fun AboutScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(AccentPurple, AccentPink)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Voice-Notf",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Membantu UMKM berkembang dengan teknologi",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Dengan menggunakan teknologi yang tepat, kita dapat membantu UMKM untuk berkembang dengan lebih efektif dan efisien, sehingga mereka dapat meningkatkan kualitas produk dan layanan mereka, meningkatkan daya saing, dan memperluas pangsa pasar mereka.",
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, contentDescription = "Visi", tint = AccentPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Visi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPurple)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Menjadi platform terdepan dalam pemberdayaan UMKM di Indonesia melalui solusi teknologi yang inovatif dan terpercaya dalam layanan pengisian pulsa dan pembayaran tagihan.",
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, contentDescription = "Misi", tint = AccentPink)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Misi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentPink)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val missions = listOf(
                        "Memberikan pelayanan yang terbaik dengan kualitas dan kecepatan yang tinggi kepada konsumen.",
                        "Meningkatkan aksesibilitas layanan pembayaran tagihan untuk UMKM di seluruh Indonesia.",
                        "Menyediakan solusi teknologi yang inovatif dan mudah digunakan untuk memudahkan UMKM dalam menjalankan bisnis mereka.",
                        "Meningkatkan keterampilan dan pengetahuan UMKM dalam mengelola bisnis mereka melalui program pelatihan dan pendampingan.",
                        "Menjalin kemitraan strategis dengan pihak-pihak terkait untuk memperluas jangkauan layanan Voice-Notf dan mendukung pertumbuhan UMKM di Indonesia."
                    )
                    missions.forEachIndexed { index, misi ->
                        Row(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("${index + 1}.", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(misi, color = TextWhite, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Developer", color = TextGray, fontSize = 12.sp)
                        Text("Ajiputra-tech", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Builder", color = TextGray, fontSize = 12.sp)
                        Text("Agung maulana", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
