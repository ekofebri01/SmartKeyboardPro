package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.KeyboardRepository
import com.example.data.repository.RestoreResult
import com.example.model.KeyboardSettings
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreScreen(
    repository: KeyboardRepository,
    settings: KeyboardSettings,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Paste JSON dialog
    var isPasteDialogOpen by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }

    // Auto-backup details
    var autoBackupDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings) {
        autoBackupDate = repository.getAutoBackupDate()
    }

    // SAF File Open Launcher (Restore from user file: ZIP, DB, or JSON)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val fileName = try {
                        var name = uri.lastPathSegment ?: "backup.json"
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                name = cursor.getString(nameIndex) ?: name
                            }
                        }
                        name
                    } catch (e: Exception) {
                        uri.lastPathSegment ?: "backup.json"
                    }
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val result = repository.restoreFromUniversalStream(inputStream, fileName)
                        when (result) {
                            is RestoreResult.Success -> {
                                isError = false
                                statusMessage = "Berhasil memulihkan ${result.autoTextCount} shortcut, ${result.wordCount} kata kamus pribadi, dan seluruh pengaturan!"
                                Toast.makeText(context, "Restore Berhasil! (${result.autoTextCount} Shortcut, ${result.wordCount} Kata Kamus)", Toast.LENGTH_LONG).show()
                            }
                            is RestoreResult.Error -> {
                                isError = true
                                statusMessage = "Gagal memulihkan: ${result.message}"
                                Toast.makeText(context, "Gagal memulihkan file", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    isError = true
                    statusMessage = "Error membaca file: ${e.localizedMessage}"
                }
            }
        }
    }

    // SAF File Create Launcher (Export to file)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = repository.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    isError = false
                    statusMessage = "Cadangan berhasil disimpan ke file!"
                    Toast.makeText(context, "File cadangan tersimpan!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    isError = true
                    statusMessage = "Gagal menyimpan file: ${e.localizedMessage}"
                }
            }
        }
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Cadangkan, 1: Pulihkan

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Banner if any
        if (statusMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("backup_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = "Status",
                        tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { statusMessage = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Clean Modern Segmented Tab Bar
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Cadangkan (Backup)",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Pulihkan (Restore)",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }

        // TAB 0: CADANGKAN (BACKUP)
        if (selectedTab == 0) {
            // Main Backup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().testTag("export_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Simpan File Cadangan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mencakup semua pengaturan, simbol tombol, kamus pribadi, dan shortcut",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    // Primary Action: Save to File
                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                            val fileName = "smartkeyboardpro_backup_${sdf.format(Date())}.json"
                            createDocumentLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_backup_file_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan")
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan File Cadangan (.json)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Secondary Actions: Share & Copy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = repository.exportBackupJson()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        putExtra(Intent.EXTRA_SUBJECT, "SmartKeyboardPro Backup")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Bagikan Cadangan Smart Keyboard Pro")
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("share_backup_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Bagikan", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bagikan")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = repository.exportBackupJson()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SmartKeyboardPro Backup", json)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Data cadangan disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salin JSON")
                        }
                    }
                }
            }

            // Automatic Local Backup Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cadangan Otomatis (Auto-Backup)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Menyimpan salinan otomatis ke penyimpanan lokal saat ada perubahan data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoBackupEnabled = it)) }
                        )
                    }

                    if (autoBackupDate != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Terakhir dicadangkan: $autoBackupDate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // TAB 1: PULIHKAN (RESTORE)
        if (selectedTab == 1) {
            // Main File Restore Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().testTag("restore_section_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Pulihkan dari File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pilih file .json, .zip (Smart Keyboard Pro), atau .db",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    // Primary Action: Pick file
                    Button(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "application/zip", "application/x-sqlite3", "application/octet-stream", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_backup_file_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Pilih File")
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih File Cadangan (.json / .zip / .db)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Quick Restore from Local Auto-Backup if present
                    if (autoBackupDate != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val file = repository.getAutoBackupFile()
                                    if (file.exists()) {
                                        val json = file.readText()
                                        val result = repository.restoreFromJson(json)
                                        when (result) {
                                            is RestoreResult.Success -> {
                                                isError = false
                                                statusMessage = "Berhasil memulihkan ${result.autoTextCount} shortcut dan ${result.wordCount} kata kamus dari Auto-Backup lokal!"
                                                Toast.makeText(context, "Auto-Backup berhasil dipulihkan!", Toast.LENGTH_SHORT).show()
                                            }
                                            is RestoreResult.Error -> {
                                                isError = true
                                                statusMessage = "Gagal: ${result.message}"
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pulihkan dari Auto-Backup Lokal Terakhir", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Alternative Restore Options Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Opsi Pemulihan Lainnya",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Option A: 1-Tap Restore Smart Keyboard Pro Backup (40 Shortcut)
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                repository.seedDefaultShortcuts()
                                isError = false
                                statusMessage = "Berhasil memulihkan 40 shortcut bawaan Smart Keyboard Pro!"
                                Toast.makeText(context, "40 Shortcut Smart Keyboard Pro Dipulihkan!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("restore_skp_defaults_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Preset SKP", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gunakan 40 Auto-Text Bawaan Smart Keyboard Pro", fontSize = 13.sp)
                    }

                    // Option B: Paste JSON
                    OutlinedButton(
                        onClick = {
                            isPasteDialogOpen = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("paste_backup_json_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Tempel Teks", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tempel Teks Cadangan Manual (JSON)", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Direct JSON Paste Dialog
    if (isPasteDialogOpen) {
        AlertDialog(
            onDismissRequest = { isPasteDialogOpen = false },
            title = { Text("Tempel Teks Backup JSON") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tempel isi file backup (.json) yang Anda miliki di bawah ini:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = pastedJsonText,
                        onValueChange = { pastedJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("paste_json_textarea"),
                        placeholder = { Text("{\n  \"schemaVersion\": 1,\n  \"autoTexts\": [...]\n}") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        maxLines = 12
                    )

                    // Quick Paste from Clipboard button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val item = clipboard.primaryClip?.getItemAt(0)
                            val text = item?.text?.toString()
                            if (!text.isNullOrBlank()) {
                                pastedJsonText = text
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tempel dari Clipboard", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = repository.restoreFromJson(pastedJsonText)
                            isPasteDialogOpen = false
                            when (result) {
                                is RestoreResult.Success -> {
                                    isError = false
                                    statusMessage = "Berhasil memulihkan ${result.autoTextCount} shortcut dan preferensi dari teks JSON!"
                                    Toast.makeText(context, "Restore Berhasil!", Toast.LENGTH_LONG).show()
                                }
                                is RestoreResult.Error -> {
                                    isError = true
                                    statusMessage = "Gagal memulihkan: ${result.message}"
                                    Toast.makeText(context, "Gagal memulihkan JSON", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = pastedJsonText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_restore_json_btn")
                ) {
                    Text("Pulihkan Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPasteDialogOpen = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
