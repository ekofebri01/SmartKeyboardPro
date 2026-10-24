package com.smartkeyboard.pro

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class KeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    var themeConfig by mutableStateOf(ThemeManager.loadConfig(context))
        private set

    var customImageBitmap by mutableStateOf<ImageBitmap?>(
        ThemeManager.loadCustomImageBitmap(themeConfig.customImagePath)
    )
        private set

    val predefinedThemes = ThemeManager.predefinedThemes

    // Auto Text items
    val autoTextEntries = mutableStateListOf<AutoTextEntry>()
    val clipboardItems = mutableStateListOf<String>()

    // Update Checker State
    var repoOwner by mutableStateOf("ekofebri01")
    var repoName by mutableStateOf("SmartKeyboardPro")
    var isCheckingUpdate by mutableStateOf(false)
    var updateResult by mutableStateOf<UpdateResult?>(null)

    // In-App Download & Install State
    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    private var downloadJob: Job? = null

    // Personal Dictionary State
    var learnedWords by mutableStateOf<List<DictionaryEntry>>(emptyList())
        private set

    private val repo = PersonalDictionaryRepository(context, com.google.firebase.auth.FirebaseAuth.getInstance(), com.google.firebase.firestore.FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)))

    init {
        // Clean up any stale update files on launch
        UpdateChecker.cleanupUpdateFiles(context)
        PersonalDictionary.load(context)
        refreshLearnedWords()
        loadAutoTextItems()
        
        viewModelScope.launch {
            val cloudWords = repo.fetchLearnedWords()
            // In a real app, we would merge cloud and local here.
        }
    }

    private fun saveAutoTextItems() {
        try {
            val jsonArray = org.json.JSONArray()
            autoTextEntries.forEach { jsonArray.put(it.toJsonObject()) }
            context.getSharedPreferences("smart_keyboard_autotext_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("autotext_items_json", jsonArray.toString())
                .apply()

            clipboardItems.clear()
            clipboardItems.addAll(autoTextEntries.map { it.fullText })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAutoTextItems() {
        try {
            val prefs = context.getSharedPreferences("smart_keyboard_autotext_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("autotext_items_json", null)
            autoTextEntries.clear()
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val element = jsonArray.get(i)
                    if (element is org.json.JSONObject) {
                        autoTextEntries.add(AutoTextEntry.fromJsonObject(element))
                    } else if (element is String) {
                        autoTextEntries.add(AutoTextEntry.createFromLegacyString(element))
                    }
                }
            }
            if (autoTextEntries.isEmpty()) {
                autoTextEntries.addAll(
                    listOf(
                        AutoTextEntry(shortcut = "thx", fullText = "Terima kasih atas informasinya! 🙏"),
                        AutoTextEntry(shortcut = "alamat", fullText = "Jl. Sudirman No. 123, Jakarta Selatan"),
                        AutoTextEntry(shortcut = "email", fullText = "rafael.aryanto@gmail.com"),
                        AutoTextEntry(shortcut = "halo", fullText = "Halo! Terima kasih sudah menghubungi."),
                        AutoTextEntry(shortcut = "repo", fullText = "https://github.com/ekofebri01/SmartKeyboardPro")
                    )
                )
                saveAutoTextItems()
            } else {
                clipboardItems.clear()
                clipboardItems.addAll(autoTextEntries.map { it.fullText })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshLearnedWords() {
        learnedWords = PersonalDictionary.getAllLearnedWords(context)
    }

    fun addLearnedWord(word: String) {
        PersonalDictionary.addWordManually(context, word)
        refreshLearnedWords()
        viewModelScope.launch {
            val entry = PersonalDictionary.getAllLearnedWords(context).find { it.word == word.trim().lowercase(java.util.Locale.ROOT) }
            if (entry != null) repo.syncLearnedWord(entry)
        }
    }

    fun deleteLearnedWord(word: String) {
        PersonalDictionary.removeWord(context, word)
        refreshLearnedWords()
        viewModelScope.launch {
            repo.removeLearnedWord(word.trim().lowercase(java.util.Locale.ROOT))
        }
    }

    fun clearLearnedWords() {
        PersonalDictionary.clearUserDictionary(context)
        refreshLearnedWords()
    }

    fun selectPredefinedTheme(theme: KeyboardTheme) {
        val newConfig = themeConfig.copy(
            themeId = theme.id,
            backgroundColor = theme.backgroundColor,
            keyColor = theme.keyColor,
            textColor = theme.textColor,
            accentColor = theme.accentColor
        )
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun applyCustomImage(uri: Uri) {
        viewModelScope.launch {
            val savedPath = ThemeManager.saveCustomImageFromUri(context, uri)
            if (savedPath != null) {
                val newConfig = themeConfig.copy(
                    customImagePath = savedPath
                )
                themeConfig = newConfig
                customImageBitmap = ThemeManager.loadCustomImageBitmap(savedPath)
                ThemeManager.saveConfig(context, newConfig)
            }
        }
    }

    fun removeCustomImage() {
        ThemeManager.deleteCustomImage(context)
        val defaultTheme = predefinedThemes.first()
        val newConfig = themeConfig.copy(
            themeId = defaultTheme.id,
            customImagePath = null,
            backgroundColor = defaultTheme.backgroundColor,
            keyColor = defaultTheme.keyColor,
            textColor = defaultTheme.textColor,
            accentColor = defaultTheme.accentColor
        )
        themeConfig = newConfig
        customImageBitmap = null
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateCornerRadius(radiusDp: Int) {
        val newConfig = themeConfig.copy(cornerRadiusDp = radiusDp)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyOpacity(opacity: Float) {
        val newConfig = themeConfig.copy(keyOpacity = opacity)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleSound(enabled: Boolean) {
        val newConfig = themeConfig.copy(soundEnabled = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleVibration(enabled: Boolean) {
        val newConfig = themeConfig.copy(vibrationEnabled = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleNumberRow(enabled: Boolean) {
        val newConfig = themeConfig.copy(showNumberRow = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleKeySymbols(enabled: Boolean) {
        val newConfig = themeConfig.copy(showKeySymbols = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleLongPressSymbols(enabled: Boolean) {
        val newConfig = themeConfig.copy(enableLongPressSymbols = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleSuggestionBar(enabled: Boolean) {
        val newConfig = themeConfig.copy(showSuggestionBar = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleAutoSpace(enabled: Boolean) {
        val newConfig = themeConfig.copy(autoSpaceAfterWord = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyHeight(heightDp: Int) {
        val newConfig = themeConfig.copy(keyHeightDp = heightDp)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyShapeStyle(style: String) {
        val radius = when (style) {
            "pill" -> 22
            "squircle" -> 14
            "sharp" -> 2
            else -> 8
        }
        val newConfig = themeConfig.copy(keyShapeStyle = style, cornerRadiusDp = radius)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun addAutoTextItem(shortcut: String, fullText: String) {
        val cleanFullText = fullText.trim()
        if (cleanFullText.isNotBlank()) {
            val cleanShortcut = shortcut.trim().lowercase()
            val finalShortcut = if (cleanShortcut.isNotBlank()) {
                cleanShortcut
            } else {
                AutoTextEntry.createFromLegacyString(cleanFullText).shortcut
            }
            val newEntry = AutoTextEntry(shortcut = finalShortcut, fullText = cleanFullText)
            autoTextEntries.add(0, newEntry)
            if (autoTextEntries.size > 100) {
                autoTextEntries.removeAt(autoTextEntries.size - 1)
            }
            saveAutoTextItems()
        }
    }

    fun updateAutoTextItem(id: String, shortcut: String, fullText: String) {
        val index = autoTextEntries.indexOfFirst { it.id == id }
        val cleanFullText = fullText.trim()
        if (index >= 0 && cleanFullText.isNotBlank()) {
            val cleanShortcut = shortcut.trim().lowercase()
            val finalShortcut = if (cleanShortcut.isNotBlank()) {
                cleanShortcut
            } else {
                AutoTextEntry.createFromLegacyString(cleanFullText).shortcut
            }
            autoTextEntries[index] = autoTextEntries[index].copy(
                shortcut = finalShortcut,
                fullText = cleanFullText
            )
            saveAutoTextItems()
        }
    }

    fun removeAutoTextEntry(entry: AutoTextEntry) {
        if (autoTextEntries.remove(entry) || autoTextEntries.removeAll { it.id == entry.id }) {
            saveAutoTextItems()
        }
    }

    fun addClipboardItem(text: String) {
        addAutoTextItem("", text)
    }

    fun removeClipboardItem(text: String) {
        val entry = autoTextEntries.find { it.fullText == text }
        if (entry != null) {
            removeAutoTextEntry(entry)
        } else if (clipboardItems.remove(text)) {
            saveAutoTextItems()
        }
    }

    fun checkForUpdates(currentVersion: String) {
        isCheckingUpdate = true
        updateResult = null
        downloadState = DownloadState.Idle
        viewModelScope.launch {
            val result = UpdateChecker.checkLatestRelease(
                currentVersion = currentVersion,
                repoOwner = repoOwner.trim(),
                repoName = repoName.trim()
            )
            updateResult = result
            isCheckingUpdate = false
        }
    }

    fun startInAppDownload(downloadUrl: String) {
        downloadJob?.cancel()
        downloadState = DownloadState.Downloading(0f, 0L, 0L)
        downloadJob = viewModelScope.launch {
            UpdateChecker.downloadApk(context, downloadUrl) { state ->
                downloadState = state
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadState = DownloadState.Idle
        UpdateChecker.cleanupUpdateFiles(context)
    }

    fun installUpdate(apkFile: File): Boolean {
        val launched = UpdateChecker.installApk(context, apkFile)
        if (launched) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                UpdateChecker.cleanupUpdateFiles(context)
                downloadState = DownloadState.Idle
            }
        }
        return launched
    }

    fun deleteDownloadedFile() {
        UpdateChecker.cleanupUpdateFiles(context)
        downloadState = DownloadState.Idle
    }

    // --- Cloud Backup & Firestore Inspector State & Operations ---
    private val cloudBackupRepo = CloudBackupRepository(context)

    var isSyncingCloud by mutableStateOf(false)
        private set
    var cloudSyncStatus by mutableStateOf<String?>(null)
        private set

    var cloudBackupsList by mutableStateOf<List<CloudBackupItem>>(emptyList())
        private set
    var isFetchingCloudBackups by mutableStateOf(false)
        private set

    var cloudDocumentsInspectorList by mutableStateOf<List<CloudDocumentInspectorItem>>(emptyList())
        private set
    var isFetchingInspectorDocs by mutableStateOf(false)
        private set

    fun syncAllToCloud() {
        isSyncingCloud = true
        cloudSyncStatus = "Proses sinkronisasi ke cloud..."
        viewModelScope.launch {
            val ok = cloudBackupRepo.syncAllToCloud(themeConfig, learnedWords, clipboardItems)
            if (ok) {
                cloudSyncStatus = "Sukses! Semua data & setting berhasil disinkron ke Cloud."
                refreshCloudInspector()
            } else {
                cloudSyncStatus = "Gagal sinkron. Pastikan terhubung internet & sudah login Google."
            }
            isSyncingCloud = false
        }
    }

    fun createCloudBackupSnapshot() {
        isSyncingCloud = true
        cloudSyncStatus = "Membuat snapshot backup di cloud..."
        viewModelScope.launch {
            val backupId = cloudBackupRepo.createCloudBackup(themeConfig, learnedWords, clipboardItems)
            if (backupId != null) {
                cloudSyncStatus = "Snapshot backup berhasil dibuat di Cloud (ID: $backupId)"
                refreshCloudBackups()
                refreshCloudInspector()
            } else {
                cloudSyncStatus = "Gagal membuat backup cloud."
            }
            isSyncingCloud = false
        }
    }

    fun refreshCloudBackups() {
        isFetchingCloudBackups = true
        viewModelScope.launch {
            cloudBackupsList = cloudBackupRepo.fetchCloudBackups()
            isFetchingCloudBackups = false
        }
    }

    fun refreshCloudInspector() {
        isFetchingInspectorDocs = true
        viewModelScope.launch {
            cloudDocumentsInspectorList = cloudBackupRepo.fetchLiveCloudDocuments()
            isFetchingInspectorDocs = false
        }
    }

    fun deleteCloudBackup(backupId: String) {
        viewModelScope.launch {
            val ok = cloudBackupRepo.deleteCloudBackup(backupId)
            if (ok) {
                refreshCloudBackups()
                refreshCloudInspector()
            }
        }
    }

    fun restoreFromCloudBackup(item: CloudBackupItem): Boolean {
        return try {
            if (item.themeConfigJson.isNotBlank()) {
                val obj = org.json.JSONObject(item.themeConfigJson)
                val themeId = obj.optString("themeId", "midnight")
                val radius = obj.optInt("cornerRadiusDp", 8)
                val opacity = obj.optDouble("keyOpacity", 0.9).toFloat()
                val height = obj.optInt("keyHeightDp", 44)
                val shape = obj.optString("keyShapeStyle", "rounded")
                val sound = obj.optBoolean("soundEnabled", true)
                val vib = obj.optBoolean("vibrationEnabled", true)
                val numRow = obj.optBoolean("showNumberRow", true)
                val keySym = obj.optBoolean("showKeySymbols", true)
                val longSym = obj.optBoolean("enableLongPressSymbols", true)
                val sug = obj.optBoolean("showSuggestionBar", true)
                val space = obj.optBoolean("autoSpaceAfterWord", true)

                val newConfig = themeConfig.copy(
                    themeId = themeId,
                    cornerRadiusDp = radius,
                    keyOpacity = opacity,
                    keyHeightDp = height,
                    keyShapeStyle = shape,
                    soundEnabled = sound,
                    vibrationEnabled = vib,
                    showNumberRow = numRow,
                    showKeySymbols = keySym,
                    enableLongPressSymbols = longSym,
                    showSuggestionBar = sug,
                    autoSpaceAfterWord = space
                )
                themeConfig = newConfig
                ThemeManager.saveConfig(context, newConfig)
            }

            if (item.learnedWordsJson.isNotBlank()) {
                val array = org.json.JSONArray(item.learnedWordsJson)
                for (i in 0 until array.length()) {
                    val wObj = array.getJSONObject(i)
                    val w = wObj.getString("word")
                    PersonalDictionary.learnWord(context, w)
                }
                refreshLearnedWords()
            }

            if (item.clipboardSnippetsJson.isNotBlank()) {
                val array = org.json.JSONArray(item.clipboardSnippetsJson)
                for (i in 0 until array.length()) {
                    val snippet = array.getString(i)
                    addClipboardItem(snippet)
                }
            }

            cloudSyncStatus = "Restore berhasil! Data & setting telah dipulihkan dari Cloud."
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cloudSyncStatus = "Gagal restore data: ${e.message}"
            false
        }
    }

    fun exportToJsonString(): String {
        return cloudBackupRepo.exportAllDataToJson(themeConfig, learnedWords, clipboardItems)
    }

    fun saveJsonToDeviceFile(): File? {
        val json = exportToJsonString()
        return cloudBackupRepo.saveJsonFileToDevice(context, json)
    }

    fun importFromJsonString(jsonString: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonString)
            if (root.has("themeConfig")) {
                val obj = root.getJSONObject("themeConfig")
                val themeId = obj.optString("themeId", "midnight")
                val radius = obj.optInt("cornerRadiusDp", 8)
                val opacity = obj.optDouble("keyOpacity", 0.9).toFloat()
                val height = obj.optInt("keyHeightDp", 44)
                val shape = obj.optString("keyShapeStyle", "rounded")
                val sound = obj.optBoolean("soundEnabled", true)
                val vib = obj.optBoolean("vibrationEnabled", true)
                val numRow = obj.optBoolean("showNumberRow", true)
                val keySym = obj.optBoolean("showKeySymbols", true)
                val longSym = obj.optBoolean("enableLongPressSymbols", true)
                val sug = obj.optBoolean("showSuggestionBar", true)
                val space = obj.optBoolean("autoSpaceAfterWord", true)

                val newConfig = themeConfig.copy(
                    themeId = themeId,
                    cornerRadiusDp = radius,
                    keyOpacity = opacity,
                    keyHeightDp = height,
                    keyShapeStyle = shape,
                    soundEnabled = sound,
                    vibrationEnabled = vib,
                    showNumberRow = numRow,
                    showKeySymbols = keySym,
                    enableLongPressSymbols = longSym,
                    showSuggestionBar = sug,
                    autoSpaceAfterWord = space
                )
                themeConfig = newConfig
                ThemeManager.saveConfig(context, newConfig)
            }

            if (root.has("learnedWords")) {
                val array = root.getJSONArray("learnedWords")
                for (i in 0 until array.length()) {
                    val wObj = array.getJSONObject(i)
                    val w = wObj.getString("word")
                    PersonalDictionary.learnWord(context, w)
                }
                refreshLearnedWords()
            }

            if (root.has("clipboardSnippets")) {
                val array = root.getJSONArray("clipboardSnippets")
                for (i in 0 until array.length()) {
                    val snippet = array.getString(i)
                    addClipboardItem(snippet)
                }
            }

            cloudSyncStatus = "Restore dari file local JSON berhasil!"
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cloudSyncStatus = "File JSON tidak valid: ${e.message}"
            false
        }
    }

    var localBackupStatusMessage by mutableStateOf<String?>(null)
        private set
    var localBackupFilesList by mutableStateOf<List<LocalBackupFileInfo>>(emptyList())
        private set

    fun performLocalFolderBackup(): LocalBackupStatus {
        val json = exportToJsonString()
        val status = LocalBackupManager.performAutoBackup(context, json)
        localBackupStatusMessage = status.message
        refreshLocalBackupFiles()
        return status
    }

    fun refreshLocalBackupFiles() {
        localBackupFilesList = LocalBackupManager.getAvailableBackupFiles(context)
    }

    fun savePersistedFolderUri(uri: android.net.Uri): Boolean {
        val ok = LocalBackupManager.savePersistableUri(context, uri)
        if (ok) {
            localBackupStatusMessage = "Ijin akses folder khusus berhasil diberikan!"
            refreshLocalBackupFiles()
        }
        return ok
    }

    fun getPersistedFolderUri(): android.net.Uri? {
        return LocalBackupManager.getPersistedFolderUri(context)
    }

    fun clearPersistedFolderPermission() {
        LocalBackupManager.clearPersistedUri(context)
        localBackupStatusMessage = "Ijin folder khusus dicabut."
        refreshLocalBackupFiles()
    }

    fun restoreFromLocalBackupInfo(info: LocalBackupFileInfo): Boolean {
        val (ok, msg) = LocalBackupManager.restoreFromBackupInfo(
            context = context,
            info = info,
            onRestoreTheme = { restored ->
                themeConfig = restored
                ThemeManager.saveConfig(context, restored)
            },
            onRestoreDictionaryWord = { word ->
                PersonalDictionary.learnWord(context, word)
            },
            onRestoreSnippet = { snippet ->
                addClipboardItem(snippet)
            }
        )
        if (ok) {
            refreshLearnedWords()
        }
        localBackupStatusMessage = msg
        return ok
    }
}
