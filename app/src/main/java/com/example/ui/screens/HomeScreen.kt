package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoTextEntry
import com.example.model.KeyboardSettings

@Composable
fun HomeScreen(
    settings: KeyboardSettings,
    autoTextCount: Int,
    activeAutoTexts: List<AutoTextEntry> = emptyList(),
    onNavigateToAutoText: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToThemes: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onLearnWord: ((String) -> Unit)? = null,
    getSuggestions: ((String, List<AutoTextEntry>) -> com.example.data.prediction.SuggestionResult)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // System IME status
    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeDefault by remember { mutableStateOf(false) }

    // Check status on resume whenever user returns from Settings
    val activity = context as? androidx.activity.ComponentActivity
    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                if (imm != null) {
                    val enabledList = imm.enabledInputMethodList
                    val myPackage = context.packageName
                    isImeEnabled = enabledList.any { it.packageName == myPackage }

                    val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    isImeDefault = defaultIme != null && defaultIme.contains(myPackage)
                }
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero / Status Header Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().testTag("device_compat_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Keyboard Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ringan, responsif, & hemat baterai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isImeEnabled && isImeDefault)
                        Color(0xFF2E7D32).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isImeEnabled && isImeDefault) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isImeEnabled && isImeDefault)
                                Icons.Default.CheckCircle
                            else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isImeEnabled && isImeDefault)
                                Color(0xFF2E7D32)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when {
                                isImeEnabled && isImeDefault -> "Status: Keyboard Utama Aktif ✓"
                                isImeEnabled -> "Status: Aktif, silakan pilih sebagai keyboard utama"
                                else -> "Status: Belum diaktifkan di sistem"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isImeEnabled && isImeDefault)
                                Color(0xFF2E7D32)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Setup & Activation Section
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Langkah Aktivasi Keyboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Step 1: Enable in Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. Aktifkan di Sistem", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (isImeEnabled) "Sudah aktif di daftar keyboard ✓" else "Aktifkan sakelar Smart Keyboard Pro",
                            fontSize = 12.sp,
                            color = if (isImeEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (e2: Exception) {
                                    // fallback
                                }
                            }
                        },
                        colors = if (isImeEnabled) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("enable_ime_btn")
                    ) {
                        Text(if (isImeEnabled) "Sudah Aktif" else "Aktifkan")
                    }
                }

                HorizontalDivider()

                // Step 2: Choose as Input Method
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("2. Pilih sebagai Keyboard Utama", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (isImeDefault) "Smart Keyboard Pro adalah default ✓" else "Ketuk untuk memilih metode masukan",
                            fontSize = 12.sp,
                            color = if (isImeDefault) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        },
                        colors = if (isImeDefault) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("select_ime_btn")
                    ) {
                        Text(if (isImeDefault) "Ganti" else "Pilih")
                    }
                }
            }
        }

        // Quick Feature Cards (2x2 Grid)
        Text(
            text = "Menu & Fitur Lengkap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickFeatureCard(
                title = "$autoTextCount Shortcut",
                subtitle = "Kelola Auto-Text",
                description = "Singkatan otomatis jadi teks",
                icon = Icons.Default.Bolt,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onNavigateToAutoText,
                modifier = Modifier.weight(1f).testTag("home_autotext_card")
            )

            QuickFeatureCard(
                title = "Backup & Restore",
                subtitle = "Cadangan Data",
                description = "Simpan & pulihkan pengaturan",
                icon = Icons.Default.Restore,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onNavigateToBackup,
                modifier = Modifier.weight(1f).testTag("home_backup_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickFeatureCard(
                title = "Tema Keyboard",
                subtitle = "Tampilan & Ukuran",
                description = "Pilihan tema & tinggi tuts",
                icon = Icons.Default.Palette,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                onClick = onNavigateToThemes,
                modifier = Modifier.weight(1f)
            )

            QuickFeatureCard(
                title = "Simbol & Ketikan",
                subtitle = "Pengaturan Lanjutan",
                description = "Edit simbol subtext, suara & getar",
                icon = Icons.Default.Tune,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onNavigateToSettings?.invoke() ?: onNavigateToThemes() },
                modifier = Modifier.weight(1f)
            )
        }

        // Tips Pintasan Mengetik Cepat
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tips Pintasan Mengetik Cepat",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                QuickTipItem(
                    title = "Tahan Tombol Huruf (Subtext)",
                    desc = "Mengetik simbol sekunder di pojok tombol tanpa perlu berganti ke halaman simbol (?123)."
                )
                QuickTipItem(
                    title = "Tahan Tombol ?123",
                    desc = "Langsung masuk ke halaman Pengaturan aplikasi secara instan."
                )
                QuickTipItem(
                    title = "Tahan Tombol Hapus (Backspace)",
                    desc = "Menghapus karakter berulang secara kontinu, lancar, dan terkontrol."
                )
                QuickTipItem(
                    title = "Kustomisasi Simbol Sekunder",
                    desc = "Buka menu Pengaturan > Kustomisasi Simbol Tombol untuk mengganti karakter tiap tombol."
                )
            }
        }
    }
}

@Composable
private fun QuickFeatureCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun QuickTipItem(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "• $title",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = desc,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
