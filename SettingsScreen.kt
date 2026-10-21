package com.blackberry.loader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    selectedTheme: String,
    onThemeChange: (String) -> Unit,
    selectedAnimation: String,
    onAnimationChange: (String) -> Unit,
    soundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationToggle: (Boolean) -> Unit
) {
    val goldColor = Color(0xFFFFD700)
    val darkGoldColor = Color(0xFFB8860B)
    val bgBlack = Color(0xFF050505)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgBlack, Color(0xFF1A1500))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // GERİ BUTONU VE BAŞLIK
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = goldColor)
                }
                Text(
                    text = "AYARLAR",
                    color = goldColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            // --- TEMA SEÇİMİ ---
            Text("Tema Rengi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val themes = listOf(
                    "Gold" to Color(0xFFFFD700),
                    "Blue" to Color(0xFF2196F3),
                    "Red" to Color(0xFFF44336),
                    "White" to Color(0xFFFFFFFF),
                    "Purple" to Color(0xFF9C27B0)
                )
                themes.forEach { (name, color) ->
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedTheme == name) 3.dp else 1.dp,
                                color = if (selectedTheme == name) Color.White else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onThemeChange(name) }
                    )
                }
            }

            // --- ANİMASYON SEÇİMİ ---
            Text("Arka Plan Animasyonu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                val anims = listOf("Yıldızlar", "Ay", "Güneş", "Matrix")
                anims.forEach { anim ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAnimationChange(anim) }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = selectedAnimation == anim,
                            onClick = { onAnimationChange(anim) },
                            colors = RadioButtonDefaults.colors(selectedColor = goldColor, unselectedColor = Color.Gray)
                        )
                        Text(text = anim, color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            // --- SES AÇ/KAPAT ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Ses", tint = goldColor)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Sesler", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = goldColor, checkedTrackColor = darkGoldColor)
                )
            }

            // --- TİTREŞİM AÇ/KAPAT ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Icon(Icons.Default.Vibration, contentDescription = "Titreşim", tint = goldColor)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Titreşim", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = goldColor, checkedTrackColor = darkGoldColor)
                )
            }

            // --- HAKKINDA ---
            Divider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 24.dp))
            Text("Hakkında", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Text("BlackBerry Loader v1.0", color = Color.Gray, fontSize = 14.sp)
            Text("CAPTCHA ONAYLIDIR", color = Color.Gray, fontSize = 14.sp)
            Text("LOADER ONAYLIDIR", color = Color.Gray, fontSize = 14.sp)
        }
    }
}