package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CustomWordEntry
import com.example.model.KeyboardSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: com.example.data.repository.KeyboardRepository,
    settings: KeyboardSettings,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    customWords: List<CustomWordEntry> = emptyList(),
    onAddCustomWord: ((String) -> Unit)? = null,
    onDeleteCustomWord: ((CustomWordEntry) -> Unit)? = null,
    onClearCustomWords: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDictionaryDialog by remember { mutableStateOf(false) }
    var showSubtextDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    fun testVibrate(ms: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(ms.toLong())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Typing & Prediction Settings
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Prediksi Teks & Auto-Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Prediction Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Saran & Prediksi Kata", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tampilkan bilah saran kata & pintasan Auto-Text di atas keyboard",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.predictionEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(predictionEnabled = it)) }
                    )
                }

                HorizontalDivider()

                // Auto-Capitalize
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kapitalisasi Otomatis", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Otomatis huruf besar di awal kalimat dan setelah tanda titik",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoCapitalize,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoCapitalize = it)) }
                    )
                }

                HorizontalDivider()

                // Auto-space after shortcut expansion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spasi Otomatis Setelah Auto-Text", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Menambahkan spasi otomatis setelah shortcut diganti dengan teks panjang",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoSpaceAfterShortcut,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoSpaceAfterShortcut = it)) }
                    )
                }

                HorizontalDivider()

                // Auto-Learn Words
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pelajari Kata Baru Otomatis", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Otomatis menyimpan kata yang Anda ketik ke kamus pribadi agar muncul di saran preview berikutnya",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoLearnWords,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoLearnWords = it)) },
                        modifier = Modifier.testTag("auto_learn_words_toggle")
                    )
                }

                OutlinedButton(
                    onClick = { showDictionaryDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("manage_dictionary_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = "Kamus", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kelola Kamus")
                }

                OutlinedButton(
                    onClick = { showSubtextDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("manage_subtext_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Simbol", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kustomisasi Simbol Tombol (Subtext)")
                }
            }
        }

        if (showDictionaryDialog) {
            DictionarySettingsDialog(
                repository = repository,
                onDismiss = { showDictionaryDialog = false }
            )
        }

        if (showSubtextDialog) {
            SubtextCustomizationDialog(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onDismiss = { showSubtextDialog = false }
            )
        }


        // Haptic & Vibration Settings
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Umpan Balik Haptik & Getaran",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Vibrate toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Getar Saat Tekan Tombol", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Respons haptik nyaman untuk mengetik responsif",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.vibrateOnPress,
                        onCheckedChange = { onUpdateSettings(settings.copy(vibrateOnPress = it)) },
                        modifier = Modifier.testTag("vibrate_toggle")
                    )
                }

                if (settings.vibrateOnPress) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Durasi Getaran Haptik: ${settings.vibrationDurationMs} ms")
                            TextButton(onClick = { testVibrate(settings.vibrationDurationMs) }) {
                                Icon(Icons.Default.Vibration, contentDescription = "Test", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tes Getar")
                            }
                        }
                        Slider(
                            value = settings.vibrationDurationMs.toFloat(),
                            onValueChange = { onUpdateSettings(settings.copy(vibrationDurationMs = it.toInt())) },
                            valueRange = 5f..80f,
                            steps = 14,
                            modifier = Modifier.testTag("vibration_duration_slider")
                        )
                    }
                }

                HorizontalDivider()

                // Sound feedback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bunyi Saat Tekan Tombol", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Bunyi klik mekanik standar Android",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.soundOnPress,
                        onCheckedChange = { onUpdateSettings(settings.copy(soundOnPress = it)) }
                    )
                }
            }
        }

        // crDroid / J7 Prime Optimization Note
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = "Performance", tint = MaterialTheme.colorScheme.primary)
                    Text("Optimasi Samsung Galaxy J7 Prime", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Aplikasi ini dirancang khusus tanpa proses background berat. Sangat hemat daya baterai dan lancar dijalankan di custom ROM crDroid 6.21 (Android 10) dengan arsitektur Exynos 7870.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDictionaryDialog) {
        LearnedDictionaryDialog(
            customWords = customWords,
            onDismiss = { showDictionaryDialog = false },
            onAddWord = { word -> onAddCustomWord?.invoke(word) },
            onDeleteWord = { entry -> onDeleteCustomWord?.invoke(entry) },
            onClearAll = { onClearCustomWords?.invoke() }
        )
    }
}

@Composable
fun LearnedDictionaryDialog(
    customWords: List<CustomWordEntry>,
    onDismiss: () -> Unit,
    onAddWord: (String) -> Unit,
    onDeleteWord: (CustomWordEntry) -> Unit,
    onClearAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newWordInput by remember { mutableStateOf("") }

    val filteredWords = remember(customWords, searchQuery) {
        if (searchQuery.isBlank()) customWords
        else customWords.filter { it.word.contains(searchQuery.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = "Kamus", tint = MaterialTheme.colorScheme.primary)
                Text("Kamus Kata Pribadi (${customWords.size})", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Kata-kata di bawah ini dipelajari otomatis saat Anda mengetik atau dapat ditambahkan secara manual:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Add Word input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newWordInput,
                        onValueChange = { newWordInput = it },
                        placeholder = { Text("Tambah kata manual...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).testTag("add_custom_word_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            if (newWordInput.isNotBlank()) {
                                onAddWord(newWordInput.trim())
                                newWordInput = ""
                            }
                        },
                        enabled = newWordInput.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("add_custom_word_btn")
                    ) {
                        Text("Tambah", fontSize = 12.sp)
                    }
                }

                // Search Bar if list is large
                if (customWords.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari kata dalam kamus...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // List of words
                if (filteredWords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (customWords.isEmpty()) "Belum ada kata yang dipelajari.\nKetik kalimat di keyboard untuk mulai belajar!" else "Tidak ada kata yang cocok.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredWords, key = { it.id }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.word,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Dipakai ${entry.frequency} kali",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteWord(entry) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        dismissButton = {
            if (customWords.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            }
        }
    )
}

@Composable
fun DictionarySettingsDialog(
    repository: com.example.data.repository.KeyboardRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dictionaryManager = repository.dictionaryManager
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<com.example.data.dictionary.DownloadStatus>(com.example.data.dictionary.DownloadStatus.Idle) }

    LaunchedEffect(Unit) {
        dictionaryManager.downloadStatus.collect { status = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Kamus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.data.dictionary.DictionaryLanguage.values().forEach { lang ->
                    val isInstalled = dictionaryManager.isDictionaryInstalled(lang)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.displayName)
                        if (isInstalled) {
                            TextButton(onClick = { 
                                dictionaryManager.deleteDictionary(lang)
                                // Trigger refresh if needed
                            }) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    dictionaryManager.downloadDictionary(lang) { _, _ -> }
                                }
                            }) {
                                Text("Download")
                            }
                        }
                    }
                }
                
                if (status is com.example.data.dictionary.DownloadStatus.Downloading) {
                    val s = status as com.example.data.dictionary.DownloadStatus.Downloading
                    Text(s.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun SubtextCustomizationDialog(
    settings: KeyboardSettings,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var row1List by remember { mutableStateOf(settings.customRow1Alts) }
    var row2List by remember { mutableStateOf(settings.customRow2Alts) }
    var row3List by remember { mutableStateOf(settings.customRow3Alts) }

    val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3Keys = listOf("z", "x", "c", "v", "b", "n", "m")

    // Active selected key: (row 1, 2, or 3, index in row)
    var selectedRow by remember { mutableIntStateOf(1) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Keyboard Visual, 1: Daftar Lengkap

    val popularSymbols = listOf(
        "~", "`", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
        "-", "_", "=", "+", "[", "]", "{", "}", "\\", "|", ";", ":",
        "'", "\"", ",", ".", "<", ">", "/", "?", "€", "£", "¥", "°", "§", "¶", "©", "®",
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    )

    fun getCurrentKey(): String {
        return when (selectedRow) {
            1 -> row1Keys.getOrElse(selectedIndex) { "Q" }.uppercase()
            2 -> row2Keys.getOrElse(selectedIndex) { "A" }.uppercase()
            else -> row3Keys.getOrElse(selectedIndex) { "Z" }.uppercase()
        }
    }

    fun getCurrentSubtext(): String {
        return when (selectedRow) {
            1 -> row1List.getOrElse(selectedIndex) { "" }
            2 -> row2List.getOrElse(selectedIndex) { "" }
            else -> row3List.getOrElse(selectedIndex) { "" }
        }
    }

    fun setSubtext(newVal: String) {
        val cleanVal = newVal.take(2)
        when (selectedRow) {
            1 -> {
                val list = row1List.toMutableList()
                while (list.size <= selectedIndex) list.add("")
                list[selectedIndex] = cleanVal
                row1List = list
            }
            2 -> {
                val list = row2List.toMutableList()
                while (list.size <= selectedIndex) list.add("")
                list[selectedIndex] = cleanVal
                row2List = list
            }
            3 -> {
                val list = row3List.toMutableList()
                while (list.size <= selectedIndex) list.add("")
                list[selectedIndex] = cleanVal
                row3List = list
            }
        }
    }

    fun nextKey() {
        when (selectedRow) {
            1 -> {
                if (selectedIndex < row1Keys.size - 1) selectedIndex++
                else { selectedRow = 2; selectedIndex = 0 }
            }
            2 -> {
                if (selectedIndex < row2Keys.size - 1) selectedIndex++
                else { selectedRow = 3; selectedIndex = 0 }
            }
            3 -> {
                if (selectedIndex < row3Keys.size - 1) selectedIndex++
                else { selectedRow = 1; selectedIndex = 0 }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drag pill indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(44.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )

                    // Header with Title & Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Simbol Tombol (Subtext)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Muncul saat tombol keyboard ditekan lama",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                            Button(
                                onClick = {
                                    onUpdateSettings(
                                        settings.copy(
                                            customRow1Alts = row1List,
                                            customRow2Alts = row2List,
                                            customRow3Alts = row3List
                                        )
                                    )
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Simpan")
                            }
                        }
                    }

                    // Presets quick row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {
                                row1List = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                            },
                            label = { Text("🔢 Angka di Baris 1", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                row1List = listOf("~", "`", "€", "£", "¥", "°", "§", "¶", "©", "®")
                                row2List = listOf("!", "@", "#", "$", "%", "^", "&", "*", "+")
                                row3List = listOf("-", "_", "=", "[", "]", "{", "}")
                            },
                            label = { Text("🔣 Simbol Standar", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                row1List = KeyboardSettings().customRow1Alts
                                row2List = KeyboardSettings().customRow2Alts
                                row3List = KeyboardSettings().customRow3Alts
                            },
                            label = { Text("🔄 Reset Default", fontSize = 12.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                row1List = List(10) { "" }
                                row2List = List(9) { "" }
                                row3List = List(7) { "" }
                            },
                            label = { Text("🧹 Kosongkan", fontSize = 12.sp) }
                        )
                    }

                    // View Mode Tabs
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("⌨️ Keyboard Visual", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("📋 Daftar Huruf (A-Z)", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    if (activeTab == 0) {
                        // TAB 0: INTERACTIVE VISUAL KEYBOARD
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Ketuk tombol mana saja di keyboard untuk memilih dan mengganti simbolnya:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // MINI VISUAL KEYBOARD CONTAINER (Fits 100% on screen width!)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Row 1: 10 keys
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        row1Keys.forEachIndexed { i, key ->
                                            val isSelected = selectedRow == 1 && selectedIndex == i
                                            VisualKeyButton(
                                                letter = key.uppercase(),
                                                subtext = row1List.getOrElse(i) { "" },
                                                isSelected = isSelected,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    selectedRow = 1
                                                    selectedIndex = i
                                                }
                                            )
                                        }
                                    }

                                    // Row 2: 9 keys (centered with padding)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        row2Keys.forEachIndexed { i, key ->
                                            val isSelected = selectedRow == 2 && selectedIndex == i
                                            VisualKeyButton(
                                                letter = key.uppercase(),
                                                subtext = row2List.getOrElse(i) { "" },
                                                isSelected = isSelected,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    selectedRow = 2
                                                    selectedIndex = i
                                                }
                                            )
                                        }
                                    }

                                    // Row 3: 7 keys (centered with padding)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 28.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        row3Keys.forEachIndexed { i, key ->
                                            val isSelected = selectedRow == 3 && selectedIndex == i
                                            VisualKeyButton(
                                                letter = key.uppercase(),
                                                subtext = row3List.getOrElse(i) { "" },
                                                isSelected = isSelected,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    selectedRow = 3
                                                    selectedIndex = i
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ACTIVE KEY EDITOR CARD
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ),
                                border = borderStroke(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Big preview tile
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = getCurrentKey(),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 22.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    if (getCurrentSubtext().isNotEmpty()) {
                                                        Text(
                                                            text = getCurrentSubtext(),
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(top = 2.dp, end = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = "Tombol ${getCurrentKey()}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = if (getCurrentSubtext().isNotEmpty())
                                                        "Simbol saat ini: '${getCurrentSubtext()}'"
                                                    else "Belum ada simbol",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (getCurrentSubtext().isNotEmpty()) {
                                                OutlinedButton(
                                                    onClick = { setSubtext("") },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Hapus", fontSize = 12.sp)
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = { nextKey() },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Lanjut ➡️", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    // Manual input field
                                    OutlinedTextField(
                                        value = getCurrentSubtext(),
                                        onValueChange = { setSubtext(it) },
                                        label = { Text("Ketik simbol manual untuk tombol ${getCurrentKey()}") },
                                        placeholder = { Text("Ketik 1 atau 2 karakter...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        trailingIcon = {
                                            if (getCurrentSubtext().isNotEmpty()) {
                                                IconButton(onClick = { setSubtext("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                                }
                                            }
                                        }
                                    )

                                    // Quick Pick Popular Symbols Chip Bar
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Atau ketuk cepat simbol populer di bawah:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            popularSymbols.forEach { sym ->
                                                val isCurrent = getCurrentSubtext() == sym
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface,
                                                    border = borderStroke(
                                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                    ),
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clickable {
                                                            setSubtext(sym)
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = sym,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB 1: VERTICAL LIST OF ALL KEYS (A-Z) (Smooth vertical scroll, no horizontal locks!)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Daftar lengkap semua tombol. Ubah karakter simbol langsung di kolom masing-masing:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Group 1: Row 1
                            RowGroupCard(
                                title = "Baris 1 (Q - P)",
                                keys = row1Keys,
                                values = row1List,
                                onUpdate = { idx, value ->
                                    val list = row1List.toMutableList()
                                    while (list.size <= idx) list.add("")
                                    list[idx] = value.take(2)
                                    row1List = list
                                }
                            )

                            // Group 2: Row 2
                            RowGroupCard(
                                title = "Baris 2 (A - L)",
                                keys = row2Keys,
                                values = row2List,
                                onUpdate = { idx, value ->
                                    val list = row2List.toMutableList()
                                    while (list.size <= idx) list.add("")
                                    list[idx] = value.take(2)
                                    row2List = list
                                }
                            )

                            // Group 3: Row 3
                            RowGroupCard(
                                title = "Baris 3 (Z - M)",
                                keys = row3Keys,
                                values = row3List,
                                onUpdate = { idx, value ->
                                    val list = row3List.toMutableList()
                                    while (list.size <= idx) list.add("")
                                    list[idx] = value.take(2)
                                    row3List = list
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualKeyButton(
    letter: String,
    subtext: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = borderStroke(
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            if (isSelected) 2.dp else 1.dp
        ),
        tonalElevation = if (isSelected) 6.dp else 1.dp,
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            // Secondary subtext in top-right
            if (subtext.isNotEmpty()) {
                Text(
                    text = subtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp)
                )
            }

            // Main letter in center
            Text(
                text = letter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun RowGroupCard(
    title: String,
    keys: List<String>,
    values: List<String>,
    onUpdate: (Int, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )

            keys.forEachIndexed { idx, key ->
                val currentVal = values.getOrElse(idx) { "" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Key badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = key.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // TextField
                    OutlinedTextField(
                        value = currentVal,
                        onValueChange = { onUpdate(idx, it) },
                        placeholder = { Text("Ketik simbol...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (currentVal.isNotEmpty()) {
                                IconButton(onClick = { onUpdate(idx, "") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun borderStroke(color: Color, width: androidx.compose.ui.unit.Dp = 1.dp) =
    androidx.compose.foundation.BorderStroke(width, color)

