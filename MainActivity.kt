package com.blackberry.loader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        sharedPrefs = getSharedPreferences("BlackBerryPrefs", Context.MODE_PRIVATE)

        Shizuku.addBinderReceivedListenerSticky { }
        // Uygulama her açıldığında giriş ekranı gelsin
        sharedPrefs.edit().putBoolean("isLoggedIn", false).apply()

        setContent {
            MaterialTheme {
                var currentTheme by remember { mutableStateOf(sharedPrefs.getString("theme", "Gold") ?: "Gold") }
                var currentAnimation by remember { mutableStateOf(sharedPrefs.getString("animation", "Yıldızlar") ?: "Yıldızlar") }
                var soundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("sound", true)) }
                var vibrationEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("vibration", true)) }

                var isLoggedIn by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }

                var kalanSure by remember { mutableStateOf("") }
                var kullanilanKey by remember { mutableStateOf("") }

                // GLOBAL BAKIM MODU
                var isAppInMaintenance by remember { mutableStateOf(false) }
                var isCheckingMaintenance by remember { mutableStateOf(true) }

                globalTheme = currentTheme
                globalAnimation = currentAnimation
                globalSound = soundEnabled
                globalVibration = vibrationEnabled

                // EN DIŞARIDA BAKIM KONTROLÜ (IO thread'de)
                LaunchedEffect(Unit) {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val versionTxtUrl = "http://37.148.214.37/version.txt"
                            val url = URL(versionTxtUrl)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            val text = conn.inputStream.bufferedReader().use { it.readText().trim() }
                            conn.disconnect()
                            text
                        }
                        Log.d("BAKIM_DEBUG", "response = '$result'")
                        if (result.uppercase().contains("BAKIM")) {
                            isAppInMaintenance = true
                            mediaPlayer?.pause()
                            playSound(this@MainActivity, R.raw.maintenance_sound)
                        }
                    } catch (e: Exception) {
                        Log.e("BAKIM_DEBUG", "hata: ${e.message}", e)
                    } finally {
                        isCheckingMaintenance = false
                    }
                }

                when {
                    isCheckingMaintenance -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF050505)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFFD700))
                        }
                    }
                    isAppInMaintenance -> {
                        MaintenanceScreen()
                    }
                    else -> {
                        fun saveSettings() {
                            sharedPrefs.edit()
                                .putString("theme", currentTheme)
                                .putString("animation", currentAnimation)
                                .putBoolean("sound", soundEnabled)
                                .putBoolean("vibration", vibrationEnabled)
                                .apply()
                        }

                        LaunchedEffect(soundEnabled) {
                            if (soundEnabled && mediaPlayer == null) {
                                mediaPlayer = MediaPlayer.create(this@MainActivity, R.raw.intro)
                                mediaPlayer?.start()
                            } else if (!soundEnabled && mediaPlayer != null) {
                                mediaPlayer?.release()
                                mediaPlayer = null
                            }
                        }

                        key(currentTheme, currentAnimation) {
                            AnimatedContent(
                                targetState = isLoggedIn to showSettings,
                                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                                label = "screen_transition"
                            ) { (loggedIn, settingsOpen) ->

                                if (settingsOpen) {
                                    SettingsScreen(
                                        onBack = { showSettings = false },
                                        selectedTheme = currentTheme,
                                        onThemeChange = { currentTheme = it; saveSettings() },
                                        selectedAnimation = currentAnimation,
                                        onAnimationChange = { currentAnimation = it; saveSettings() },
                                        soundEnabled = soundEnabled,
                                        onSoundToggle = { soundEnabled = it; saveSettings() },
                                        vibrationEnabled = vibrationEnabled,
                                        onVibrationToggle = { vibrationEnabled = it; saveSettings() }
                                    )
                                } else if (loggedIn) {
                                    DashboardScreen(
                                        onLogout = { isLoggedIn = false },
                                        onOpenSettings = { showSettings = true },
                                        kalanSure = kalanSure,
                                        kullanilanKey = kullanilanKey
                                    )
                                } else {
                                    LoginScreen(
                                        onLoginSuccess = { sure, key ->
                                            kalanSure = sure
                                            kullanilanKey = key
                                            isLoggedIn = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

// BAKIM EKRANI FONKSİYONU (BÜYÜK KIRMIZI KUTU)
@Composable
fun MaintenanceScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF1A1500)))),
        contentAlignment = Alignment.Center
    ) {
        AnimatedBackground()

        val maintenanceTransition = rememberInfiniteTransition(label = "maintenance")
        val maintenanceAlpha by maintenanceTransition.animateFloat(
            initialValue = 0.6f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
            label = "maintenanceAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000).copy(alpha = maintenanceAlpha),
                            Color(0xFF8B0000).copy(alpha = maintenanceAlpha)
                        )
                    )
                )
                .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Uyarı",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "SİSTEM BAKIMDA",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Şu anda sistem güncellemesi yapıyoruz.\nLütfen daha sonra tekrar deneyin.\nAnlayışınız için teşekkürler.",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

var globalTheme = "Gold"
var globalAnimation = "Yıldızlar"
var globalSound = true
var globalVibration = true

fun getThemeColor(): Color {
    return when (globalTheme) {
        "Gold" -> Color(0xFFFFD700)
        "Blue" -> Color(0xFF2196F3)
        "Red" -> Color(0xFFF44336)
        "White" -> Color(0xFFFFFFFF)
        "Purple" -> Color(0xFF9C27B0)
        else -> Color(0xFFFFD700)
    }
}

fun getThemeDarkColor(): Color {
    return when (globalTheme) {
        "Gold" -> Color(0xFFB8860B)
        "Blue" -> Color(0xFF1976D2)
        "Red" -> Color(0xFFD32F2F)
        "White" -> Color(0xFFBDBDBD)
        "Purple" -> Color(0xFF7B1FA2)
        else -> Color(0xFFB8860B)
    }
}

var currentSoundPlayer: MediaPlayer? = null

fun playSound(context: Context, soundResId: Int) {
    if (!globalSound) return
    try {
        currentSoundPlayer?.release()
        val mp = MediaPlayer.create(context, soundResId)
        currentSoundPlayer = mp
        mp?.setOnCompletionListener {
            it.release()
            if (currentSoundPlayer == it) currentSoundPlayer = null
        }
        mp?.start()
    } catch (e: Exception) {}
}

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    var keyText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isShizukuRunning by remember { mutableStateOf(false) }
    var hasShizukuPermission by remember { mutableStateOf(false) }

    var showLicenseInfo by remember { mutableStateOf(false) }
    var licenseTime by remember { mutableStateOf("") }
    var licenseKey by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val goldColor = getThemeColor()
    val darkGoldColor = getThemeDarkColor()

    LaunchedEffect(Unit) {
        while(true) {
            try {
                if (Shizuku.pingBinder()) {
                    isShizukuRunning = true
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        hasShizukuPermission = true
                    } else {
                        Shizuku.requestPermission(1000)
                    }
                } else {
                    isShizukuRunning = false
                    hasShizukuPermission = false
                }
            } catch (e: Exception) {
                isShizukuRunning = false
            }
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "animations")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF1A1500))))
    ) {
        AnimatedBackground()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(visible = !isShizukuRunning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 20.dp)) {
                    Text("⚠️ SİSTEME GİRİŞ YAPILAMAZ", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Lütfen önce Shizuku'yu aktif edin.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/CdU0OlLds2k"))) } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Shizuku Kurulum Videosu", color = Color.White)
                    }
                }
            }

            AnimatedVisibility(visible = isShizukuRunning && !hasShizukuPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 20.dp)) {
                    Text("⚠️ SHIZUKU İZNİ GEREKLİ", color = Color.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Lütfen Shizuku iznini verin.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try { Shizuku.requestPermission(1000) } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = "İzin", tint = Color.Black)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("İzin İste", color = Color.Black)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 40.dp, shape = RoundedCornerShape(28.dp), ambientColor = goldColor, spotColor = goldColor)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0A0A0A).copy(alpha = 0.9f))
                    .border(1.5.dp, Brush.linearGradient(listOf(goldColor.copy(alpha = glowAlpha), Color.Transparent, darkGoldColor.copy(alpha = glowAlpha))), RoundedCornerShape(28.dp))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BLACKBERRY", color = goldColor, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("P R E M I U M   G O L D", color = darkGoldColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(45.dp))

                if (showLicenseInfo) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF121212), RoundedCornerShape(16.dp)).border(1.dp, goldColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("VIP LİSANS SÜRESİ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("● VIP AKTİF", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(licenseTime, color = goldColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Lisans Anahtarı: $licenseKey", color = Color.Gray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onLoginSuccess(licenseTime, licenseKey) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = goldColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SİSTEME GİRİŞ YAP", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                } else {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; isSuccess = null; statusText = "" },
                        label = { Text("Lisans Anahtarınız", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isShizukuRunning && hasShizukuPermission,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = goldColor, unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = goldColor, unfocusedBorderColor = darkGoldColor,
                            focusedContainerColor = Color(0xFF000000), unfocusedContainerColor = Color(0xFF000000),
                            disabledTextColor = Color.Gray, disabledBorderColor = Color.DarkGray, disabledContainerColor = Color(0xFF000000)
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            if (!isShizukuRunning) { statusText = "Shizuku Aktif Değil!"; isSuccess = false; return@Button }
                            if (!hasShizukuPermission) { statusText = "Shizuku İzni Verilmedi!"; isSuccess = false; return@Button }
                            if (keyText.isBlank()) { statusText = "Lütfen bir anahtar girin!"; isSuccess = false; playSound(context, R.raw.empty_sound); return@Button }

                            isLoading = true; isSuccess = null; statusText = ""

                            scope.launch {
                                val hwid = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                                val result = verifyKey(keyText.trim(), hwid)
                                isLoading = false

                                if (result.startsWith("GİRİŞ BAŞARILI|")) {
                                    val parts = result.split("|")
                                    licenseTime = if (parts.size > 1) parts[1] else "Bilinmiyor"
                                    licenseKey = if (parts.size > 2) parts[2] else keyText.trim()
                                    isSuccess = true
                                    playSound(context, R.raw.success_sound)
                                    showLicenseInfo = true
                                } else {
                                    isSuccess = false
                                    statusText = result
                                    if (result.contains("KULLANILMIŞ") || result.contains("LİMİT")) playSound(context, R.raw.used_sound)
                                    else playSound(context, R.raw.error_sound)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(62.dp).shadow(elevation = if (isLoading || !isShizukuRunning || !hasShizukuPermission) 0.dp else 25.dp, shape = RoundedCornerShape(14.dp), ambientColor = goldColor, spotColor = goldColor),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading && isShizukuRunning && hasShizukuPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
                        val shimmerPosition by shimmerTransition.animateFloat(
                            initialValue = -500f, targetValue = 1500f,
                            animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
                            label = "shimmerPos"
                        )

                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    colors = if (isLoading || !isShizukuRunning || !hasShizukuPermission) listOf(Color(0xFF332B00), Color(0xFF1A1500)) else listOf(goldColor, darkGoldColor)
                                )
                            )
                        ) {
                            if (!isLoading && isShizukuRunning && hasShizukuPermission) {
                                Box(modifier = Modifier.fillMaxSize().background(
                                    Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent),
                                        start = Offset(shimmerPosition, 0f), end = Offset(shimmerPosition + 200f, 200f)
                                    )
                                ))
                            }
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (isLoading) CircularProgressIndicator(color = goldColor, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                else Text(
                                    text = when {
                                        !isShizukuRunning -> "SHIZUKU BEKLENİYOR..."
                                        !hasShizukuPermission -> "İZİN BEKLENİYOR..."
                                        else -> "SİSTEME GİRİŞ YAP"
                                    },
                                    color = if (isShizukuRunning && hasShizukuPermission) Color.Black else Color.Gray,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    AnimatedVisibility(visible = statusText.isNotEmpty(), enter = fadeIn() + slideInVertically()) {
                        Text(text = statusText, color = if (isSuccess == true) goldColor else Color(0xFFFF5252), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(onLogout: () -> Unit, onOpenSettings: () -> Unit, kalanSure: String, kullanilanKey: String) {
    val goldColor = getThemeColor()
    val darkGoldColor = getThemeDarkColor()
    val context = LocalContext.current

    var isShizukuRunning by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isVpnConnected by remember { mutableStateOf(false) }
    var isUpdatingPak by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var dialogIsSuccess by remember { mutableStateOf(true) }

    var showUpdateBanner by remember { mutableStateOf(false) }

    val versionTxtUrl = "http://37.148.214.37/version.txt"
    val pakFileUrl = "http://37.148.214.37/res_pufferpatch_4.6.1.21589.pak"
    val pakFileName = "res_pufferpatch_4.6.1.21589.pak"

    LaunchedEffect(Unit) {
        isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }

        withContext(Dispatchers.IO) {
            try {
                val versionUrl = URL(versionTxtUrl)
                val versionConn = versionUrl.openConnection() as HttpURLConnection
                versionConn.requestMethod = "GET"
                val serverVersion = versionConn.inputStream.bufferedReader().use { it.readText().trim() }
                versionConn.disconnect()

                val sharedPrefs = context.getSharedPreferences("BlackBerryPrefs", Context.MODE_PRIVATE)
                val localVersion = sharedPrefs.getString("pak_version", "0") ?: "0"

                if (!serverVersion.trim().uppercase().contains("BAKIM") && serverVersion != localVersion) {
                    withContext(Dispatchers.Main) {
                        showUpdateBanner = true
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun downloadAndInstallPak() {
        if (isUpdatingPak) return
        isUpdatingPak = true

        Thread {
            try {
                val versionUrl = URL(versionTxtUrl)
                val versionConn = versionUrl.openConnection() as HttpURLConnection
                versionConn.requestMethod = "GET"
                val serverVersion = versionConn.inputStream.bufferedReader().use { it.readText().trim() }
                versionConn.disconnect()

                val sharedPrefs = context.getSharedPreferences("BlackBerryPrefs", Context.MODE_PRIVATE)
                val localVersion = sharedPrefs.getString("pak_version", "0") ?: "0"

                if (serverVersion.trim().uppercase().contains("BAKIM")) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialogMessage = "Sistem şu anda bakımda!\nLütfen daha sonra tekrar deneyin."
                        dialogIsSuccess = false
                        showDialog = true
                        isUpdatingPak = false
                    }
                    return@Thread
                }

                if (serverVersion == localVersion) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialogMessage = "Sistem Zaten Güncel!\nMevcut Sürüm: $localVersion"
                        dialogIsSuccess = true
                        showDialog = true
                        isUpdatingPak = false
                    }
                    return@Thread
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    dialogMessage = "Yeni Sürüm Bulundu ($serverVersion)!\nİndiriliyor..."
                    dialogIsSuccess = true
                    showDialog = true
                }

                if (!Shizuku.pingBinder()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialogMessage = "Shizuku çalışmıyor!\nLütfen Shizuku'yu başlatın."
                        dialogIsSuccess = false
                        showDialog = true
                        isUpdatingPak = false
                    }
                    return@Thread
                }

                val targetDir = "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Paks/puffer_temp/"
                val targetFile = targetDir + pakFileName

                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                newProcessMethod.isAccessible = true

                var command = "mkdir -p $targetDir && rm -rf $targetDir*"
                var process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess
                process.waitFor()

                command = "curl -L -o $targetFile $pakFileUrl"
                process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess

                val errorStream = process.errorStream
                val errorOutput = errorStream.bufferedReader().readText().trim()
                process.waitFor()

                if (errorOutput.contains("not found") || errorOutput.contains("inaccessible")) {
                    command = "wget -O $targetFile $pakFileUrl"
                    process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as rikka.shizuku.ShizukuRemoteProcess
                    process.waitFor()
                }

                val checkCommand = "ls -l $targetDir"
                val checkProcess = newProcessMethod.invoke(null, arrayOf("sh", "-c", checkCommand), null, null) as rikka.shizuku.ShizukuRemoteProcess
                val lsOutput = checkProcess.inputStream.bufferedReader().readText().trim()
                checkProcess.waitFor()

                if (lsOutput.isEmpty() || lsOutput.contains("total 0") || !lsOutput.contains(pakFileName)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialogMessage = "İndirme Başarısız!\nİndirme komutu çalışmadı. Hata: $errorOutput"
                        dialogIsSuccess = false
                        showDialog = true
                        isUpdatingPak = false
                    }
                } else {
                    sharedPrefs.edit().putString("pak_version", serverVersion).apply()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialogMessage = "Güncelleme Başarılı!\nSürüm $serverVersion yüklendi."
                        dialogIsSuccess = true
                        showDialog = true
                        showUpdateBanner = false
                        isUpdatingPak = false
                    }
                }

            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    dialogMessage = "Hata: ${e.message}"
                    dialogIsSuccess = false
                    showDialog = true
                    isUpdatingPak = false
                }
            }
        }.start()
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val intent = Intent(context, MyVpnService::class.java)
            context.startService(intent)
            isVpnConnected = true
            dialogMessage = "VPN Başlatıldı!\nTrafik filtreleniyor. Panel ve Telegram serbest."
            dialogIsSuccess = true
            showDialog = true
        } else {
            dialogMessage = "VPN izni reddedildi. Lütfen tekrar deneyin."
            dialogIsSuccess = false
            showDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF1A1500))))) {
        AnimatedBackground()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = showUpdateBanner,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                val shimmerTransition = rememberInfiniteTransition(label = "banner_shimmer")
                val shimmerPos by shimmerTransition.animateFloat(
                    initialValue = -500f, targetValue = 1000f,
                    animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
                    label = "shimmerPos"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(2.dp, goldColor, RoundedCornerShape(16.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent),
                                start = Offset(shimmerPos, 0f), end = Offset(shimmerPos + 200f, 200f)
                            )
                        )
                    )

                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Update, contentDescription = "Update", tint = goldColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("YENİ GÜNCELLEME MEVCUT!", color = goldColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Lütfen aşağıdaki 'PAK DOSYASINI GÜNCELLE' butonuna basarak en son sürüme geçin.", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).background(Color(0xFF121212).copy(alpha=0.8f), RoundedCornerShape(16.dp)).border(1.dp, goldColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("VIP LİSANS DURUMU", color = Color.Gray, fontSize = 12.sp)
                Text(kalanSure, color = goldColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Key: $kullanilanKey", color = Color.DarkGray, fontSize = 10.sp)
            }

            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape).background(Color(0xFF121212)).border(3.dp, Brush.linearGradient(listOf(goldColor, darkGoldColor)), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Person, contentDescription = "Profil", tint = goldColor, modifier = Modifier.size(50.dp)) }

            Spacer(modifier = Modifier.height(10.dp))
            Text("VIP KULLANICI", color = goldColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(text = if (isShizukuRunning) "🟢 Shizuku Aktif" else "🔴 Shizuku Aktif Değil", color = if (isShizukuRunning) Color.Green else Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 20.dp, top = 5.dp))

            MenuButton(icon = Icons.Default.PlayArrow, title = "OYUNU BAŞLAT", subtitle = "PUBG Mobile'ı aç", goldColor = goldColor) {
                try {
                    val packages = listOf("com.tencent.ig", "com.pubg.krmobile", "com.rekoo.pubgm", "com.vng.pubgmobile", "com.pubg.imobile")
                    var found = false
                    for (pkg in packages) {
                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) { context.startActivity(intent); found = true; break }
                    }
                    if (!found) { dialogMessage = "PUBG Mobile yüklü değil!"; dialogIsSuccess = false; showDialog = true }
                } catch (e: Exception) { dialogMessage = "Oyun başlatılamadı: ${e.message}"; dialogIsSuccess = false; showDialog = true }
            }

            MenuButton(icon = Icons.Default.Security, title = "VPN BAĞLANTISI", subtitle = if (isVpnConnected) "Bağlı (Trafik Engelli)" else "Bağlanmak için tıkla", goldColor = goldColor) {
                if (isVpnConnected) {
                    val intent = Intent(context, MyVpnService::class.java); intent.action = "STOP"; context.startService(intent)
                    isVpnConnected = false; dialogMessage = "VPN Bağlantısı Kesildi."; dialogIsSuccess = true; showDialog = true
                } else {
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) { vpnPermissionLauncher.launch(vpnIntent) }
                    else {
                        val intent = Intent(context, MyVpnService::class.java); context.startService(intent)
                        isVpnConnected = true; dialogMessage = "VPN Başlatıldı!\nTrafik filtreleniyor. Panel ve Telegram serbest."; dialogIsSuccess = true; showDialog = true
                    }
                }
            }

            MenuButton(icon = Icons.Default.CloudDownload, title = "PAK DOSYASINI GÜNCELLE", subtitle = if (isUpdatingPak) "İndiriliyor..." else "Otomatik indir ve kur", goldColor = goldColor) {
                if (!Shizuku.pingBinder()) {
                    dialogMessage = "Shizuku çalışmıyor!\nLütfen Shizuku'yu başlatın."
                    dialogIsSuccess = false
                    showDialog = true
                    return@MenuButton
                }
                downloadAndInstallPak()
            }

            MenuButton(icon = Icons.Default.Delete, title = "DOSYALARI TEMİZLE", subtitle = if(isDeleting) "Temizleniyor..." else "Önbellek ve Logları Sil", goldColor = goldColor) {
                if (isDeleting) return@MenuButton
                if (!Shizuku.pingBinder()) { dialogMessage = "Shizuku çalışmıyor!\nLütfen Shizuku'yu başlatın."; dialogIsSuccess = false; showDialog = true; return@MenuButton }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { Shizuku.requestPermission(1000); return@MenuButton }

                isDeleting = true
                try {
                    val path1 = "/storage/emulated/0/Android/data/com.tencent.ig/cache"
                    val path2 = "/storage/emulated/0/Android/data/com.tencent.ig/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Logs"
                    val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                    val newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    newProcessMethod.isAccessible = true
                    val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", "rm -rf $path1/* $path2/*"), null, null) as rikka.shizuku.ShizukuRemoteProcess
                    process.waitFor()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val isPath1Empty = java.io.File(path1).listFiles()?.isEmpty() ?: true
                            val isPath2Empty = java.io.File(path2).listFiles()?.isEmpty() ?: true
                            if (isPath1Empty && isPath2Empty) { dialogMessage = "Temizlik Başarılı!\nÖnbellek ve loglar silindi."; dialogIsSuccess = true }
                            else { dialogMessage = "Hata: Bazı dosyalar silinemedi.\nLütfen Shizuku izinlerini kontrol edin."; dialogIsSuccess = false }
                            showDialog = true
                        } catch (e: Exception) { dialogMessage = "Kontrol hatası: ${e.message}"; dialogIsSuccess = false; showDialog = true }
                        finally { isDeleting = false }
                    }, 1000)
                } catch (e: Exception) { isDeleting = false; dialogMessage = "Silme hatası: ${e.message}"; dialogIsSuccess = false; showDialog = true }
            }

            MenuButton(icon = Icons.Default.Settings, title = "AYARLAR", subtitle = "Tema, animasyon ve ses ayarları", goldColor = goldColor) {
                onOpenSettings()
            }

            Spacer(modifier = Modifier.height(30.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SİSTEMDEN ÇIKIŞ YAP", color = Color.Red, fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(60.dp))
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = Color(0xFF121212),
                titleContentColor = if (dialogIsSuccess) goldColor else Color.Red,
                textContentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, if (dialogIsSuccess) goldColor else Color.Red, RoundedCornerShape(20.dp)),
                title = { Text(text = if (dialogIsSuccess) "✅ BAŞARILI" else "❌ HATA", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                text = { Text(text = dialogMessage, fontSize = 16.sp) },
                confirmButton = { TextButton(onClick = { showDialog = false }) { Text("TAMAM", color = goldColor, fontWeight = FontWeight.Bold) } }
            )
        }
    }
}

@Composable
fun MenuButton(icon: ImageVector, title: String, subtitle: String, goldColor: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF121212))
            .border(1.dp, goldColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = icon, contentDescription = title, tint = goldColor, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color.Gray, fontSize = 11.sp)
            }
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Git", tint = goldColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "background_anim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "time"
    )
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "ringPulse"
    )

    val themeColor = getThemeColor()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = themeColor.copy(alpha = 0.05f), radius = (size.width * 0.4f) * ringPulse, center = center, style = Stroke(width = 2f))
        drawCircle(color = themeColor.copy(alpha = 0.08f), radius = (size.width * 0.3f) * (2f - ringPulse), center = center, style = Stroke(width = 3f))
        drawCircle(color = themeColor.copy(alpha = 0.1f), radius = (size.width * 0.2f) * ringPulse, center = center, style = Stroke(width = 4f))

        if (globalAnimation == "Yıldızlar" || globalAnimation == "Matrix") {
            val starCount = if (globalAnimation == "Matrix") 30 else 15
            val random = Random(42)
            for (i in 0 until starCount) {
                val startX = random.nextFloat() * size.width
                val startY = random.nextFloat() * size.height
                val speed = random.nextFloat() * 0.5f + 0.5f
                val currentX = startX + (time * size.width * speed) % (size.width * 1.5f)
                val currentY = startY + (time * size.height * speed) % (size.height * 1.5f)

                if (currentX < size.width * 1.2f && currentY < size.height * 1.2f) {
                    val tailLength = 100f * speed
                    drawLine(
                        brush = Brush.linearGradient(colors = listOf(themeColor, Color.Transparent), start = Offset(currentX, currentY), end = Offset(currentX - tailLength, currentY - tailLength)),
                        start = Offset(currentX, currentY), end = Offset(currentX - tailLength, currentY - tailLength), strokeWidth = 2f * speed
                    )
                    drawCircle(color = Color.White, radius = 1.5f * speed, center = Offset(currentX, currentY))
                }
            }
        } else if (globalAnimation == "Ay") {
            val moonRadius = size.width * 0.15f
            val moonCenter = Offset(size.width * 0.8f, size.height * 0.2f)
            drawCircle(color = themeColor.copy(alpha = 0.8f), radius = moonRadius, center = moonCenter)
            drawCircle(color = Color(0xFF050505), radius = moonRadius * 0.9f, center = Offset(moonCenter.x + 20f, moonCenter.y - 10f))
        } else if (globalAnimation == "Güneş") {
            val sunRadius = size.width * 0.2f * ringPulse
            drawCircle(color = themeColor.copy(alpha = 0.3f), radius = sunRadius, center = center)
            drawCircle(color = themeColor.copy(alpha = 0.5f), radius = sunRadius * 0.8f, center = center)
        }
    }
}

suspend fun verifyKey(key: String, hwid: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val apiUrl = "http://panel.blackberrykey.com/api.php"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val postData = "user_key=$key&hwid=$hwid&timestamp=${System.currentTimeMillis() / 1000}"
            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(postData)
            writer.flush()
            writer.close()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText().trim()
                if (response.startsWith("valid|")) {
                    val parts = response.split("|")
                    if (parts.size >= 3) {
                        "GİRİŞ BAŞARILI|${parts[1]}|${parts[2]}"
                    } else {
                        "GİRİŞ BAŞARILI|Süre Bilgisi Yok|$key"
                    }
                } else {
                    when (response) {
                        "expired" -> "SÜRENİZ DOLMUŞ"
                        "invalid" -> "GEÇERSİZ ANAHTAR"
                        "device_limit" -> "CİHAZ LİMİTİ DOLU"
                        "hwid_mismatch" -> "BAŞKA CİHAZDA KULLANILMIŞ"
                        else -> "BİLİNMEYEN CEVAP: $response"
                    }
                }
            } else {
                "SUNUCU HATASI"
            }
        } catch (e: Exception) {
            "BAĞLANTI HATASI"
        }
    }
}