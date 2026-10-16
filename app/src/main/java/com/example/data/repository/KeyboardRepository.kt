package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.AppDatabase
import com.example.data.prediction.PredictionEngine
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class KeyboardRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val autoTextDao = db.autoTextDao()
    private val customWordDao = db.customWordDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_keyboard_pro_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val predictionEngine = PredictionEngine()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val backupAdapter = moshi.adapter(KeyboardBackupData::class.java).indent("  ")
    private val settingsAdapter = moshi.adapter(KeyboardSettings::class.java)

    // Cache of active auto-texts for zero-latency lookup during typing
    private val _cachedAutoTexts = MutableStateFlow<List<AutoTextEntry>>(emptyList())
    val cachedAutoTexts: StateFlow<List<AutoTextEntry>> = _cachedAutoTexts.asStateFlow()

    // Settings StateFlow
    private val _settingsFlow = MutableStateFlow(loadSettingsFromPrefs())
    val settingsFlow: StateFlow<KeyboardSettings> = _settingsFlow.asStateFlow()

    // AutoTexts Flow directly from Room
    val allAutoTextsFlow: Flow<List<AutoTextEntry>> = autoTextDao.getAllFlow()

    // Learned custom words Flow from Room
    val customWordsFlow: Flow<List<CustomWordEntry>> = customWordDao.getAllFlow()

    val dictionaryManager = com.example.data.dictionary.DictionaryManager(context)

    init {
        // Collect auto-texts to maintain hot in-memory cache for keyboard IME
        scope.launch {
            allAutoTextsFlow.collect { list ->
                _cachedAutoTexts.value = list.filter { it.enabled }
                if (_settingsFlow.value.autoBackupEnabled) {
                    saveAutoBackupInternal()
                }
            }
        }

        // Collect custom words to feed prediction engine
        scope.launch {
            customWordDao.getAllFlow().collect { words ->
                val map = words.associate { it.word.lowercase(Locale.ROOT) to it.frequency }
                predictionEngine.updateCustomWords(map)
            }
        }
        // Clean up any historical custom words containing sentences or spaces
        scope.launch {
            try {
                val all = customWordDao.getAll()
                val invalid = all.filter { it.word.contains(" ") || it.word.length > 30 }
                if (invalid.isNotEmpty()) {
                    invalid.forEach { customWordDao.delete(it) }
                    invalid.forEach { entry ->
                        val words = entry.word.split(Regex("[\\s\\p{Punct}]+"))
                        for (w in words) {
                            val clean = w.trim().lowercase(Locale.ROOT)
                            if (clean.length in 2..30 && clean.any { it.isLetter() }) {
                                if (customWordDao.incrementFrequency(clean) == 0) {
                                    customWordDao.insert(CustomWordEntry(word = clean, frequency = entry.frequency))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load installed dictionaries into PredictionEngine
        scope.launch {
            predictionEngine.setActiveLanguage(_settingsFlow.value.activeDictionaryLanguage)
            reloadInstalledDictionaries()
        }

        // Observe settings changes for activeDictionaryLanguage
        scope.launch {
            _settingsFlow.collect { s ->
                predictionEngine.setActiveLanguage(s.activeDictionaryLanguage)
            }
        }

        // Initialize default shortcuts with user's Smart Keyboard Pro backup if empty or missing
        scope.launch {
            val currentList = autoTextDao.getAll()
            if (currentList.isEmpty() || currentList.none { it.shortcut == "ada3kemungkinan" }) {
                seedDefaultShortcuts()
            }
        }
    }

    suspend fun reloadInstalledDictionaries() {
        if (dictionaryManager.isDictionaryInstalled(com.example.data.dictionary.DictionaryLanguage.INDONESIAN)) {
            val idWords = dictionaryManager.loadDictionaryWords(com.example.data.dictionary.DictionaryLanguage.INDONESIAN)
            predictionEngine.setDownloadedIndonesianWords(idWords)
        } else {
            predictionEngine.setDownloadedIndonesianWords(emptySet())
        }

        if (dictionaryManager.isDictionaryInstalled(com.example.data.dictionary.DictionaryLanguage.ENGLISH)) {
            val enWords = dictionaryManager.loadDictionaryWords(com.example.data.dictionary.DictionaryLanguage.ENGLISH)
            predictionEngine.setDownloadedEnglishWords(enWords)
        } else {
            predictionEngine.setDownloadedEnglishWords(emptySet())
        }
    }

    suspend fun downloadDictionary(
        language: com.example.data.dictionary.DictionaryLanguage,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Boolean {
        val success = dictionaryManager.downloadDictionary(language, onProgress)
        if (success) {
            reloadInstalledDictionaries()
            val current = _settingsFlow.value
            val updated = when (language) {
                com.example.data.dictionary.DictionaryLanguage.INDONESIAN -> current.copy(isIndonesianDownloaded = true)
                com.example.data.dictionary.DictionaryLanguage.ENGLISH -> current.copy(isEnglishDownloaded = true)
            }
            updateSettings(updated)
        }
        return success
    }

    suspend fun deleteDictionary(language: com.example.data.dictionary.DictionaryLanguage): Boolean {
        val deleted = dictionaryManager.deleteDictionary(language)
        if (deleted) {
            reloadInstalledDictionaries()
            val current = _settingsFlow.value
            val updated = when (language) {
                com.example.data.dictionary.DictionaryLanguage.INDONESIAN -> current.copy(isIndonesianDownloaded = false)
                com.example.data.dictionary.DictionaryLanguage.ENGLISH -> current.copy(isEnglishDownloaded = false)
            }
            updateSettings(updated)
        }
        return deleted
    }

    private fun loadSettingsFromPrefs(): KeyboardSettings {
        val json = prefs.getString("settings_json", null)
        if (json != null) {
            try {
                return settingsAdapter.fromJson(json) ?: KeyboardSettings()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return KeyboardSettings()
    }

    suspend fun updateSettings(newSettings: KeyboardSettings) {
        val json = settingsAdapter.toJson(newSettings)
        prefs.edit().putString("settings_json", json).apply()
        _settingsFlow.value = newSettings

        if (newSettings.autoBackupEnabled) {
            saveAutoBackupInternal()
        }
    }

    suspend fun addOrUpdateAutoText(entry: AutoTextEntry): Long {
        val id = autoTextDao.insert(entry)
        return id
    }

    suspend fun deleteAutoText(entry: AutoTextEntry) {
        autoTextDao.delete(entry)
    }

    suspend fun deleteAutoTextById(id: Long) {
        autoTextDao.deleteById(id)
    }

    suspend fun toggleAutoTextEnabled(entry: AutoTextEntry) {
        autoTextDao.update(entry.copy(enabled = !entry.enabled))
    }

    suspend fun learnWord(text: String) {
        if (!_settingsFlow.value.autoLearnWords) return
        // Split by whitespace and punctuation to ensure ONLY individual words are learned, NEVER whole sentences!
        val words = text.split(Regex("[\\s\\p{Punct}]+"))
        for (w in words) {
            val clean = w.trim().lowercase(Locale.ROOT)
            // A word must have between 2 and 30 characters, contain no spaces, not be purely numbers, and have at least one letter
            if (clean.length in 2..30 && clean.any { it.isLetter() } && !clean.contains(" ")) {
                predictionEngine.recordLearnedWord(clean)
                val updated = customWordDao.incrementFrequency(clean)
                if (updated == 0) {
                    customWordDao.insert(CustomWordEntry(word = clean, frequency = 1))
                }
            }
        }
    }

    suspend fun addCustomWord(text: String) {
        val words = text.split(Regex("[\\s\\p{Punct}]+"))
        for (w in words) {
            val clean = w.trim().lowercase(Locale.ROOT)
            if (clean.length in 2..30 && clean.any { it.isLetter() } && !clean.contains(" ")) {
                predictionEngine.recordLearnedWord(clean)
                val updated = customWordDao.incrementFrequency(clean)
                if (updated == 0) {
                    customWordDao.insert(CustomWordEntry(word = clean, frequency = 1))
                }
            }
        }
    }

    suspend fun deleteCustomWord(entry: CustomWordEntry) {
        customWordDao.delete(entry)
    }

    suspend fun clearCustomWords() {
        customWordDao.clearAll()
        predictionEngine.updateCustomWords(emptyMap())
    }

    // Auto-backup to internal storage file
    private suspend fun saveAutoBackupInternal() {
        try {
            val backupFile = File(context.filesDir, "autobackup_smartkeyboardpro.json")
            val json = exportBackupJson()
            backupFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAutoBackupFile(): File {
        return File(context.filesDir, "autobackup_smartkeyboardpro.json")
    }

    fun hasAutoBackup(): Boolean {
        val f = getAutoBackupFile()
        return f.exists() && f.length() > 0
    }

    fun getAutoBackupDate(): String? {
        val f = getAutoBackupFile()
        if (!f.exists()) return null
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(f.lastModified()))
    }

    suspend fun exportBackupJson(): String {
        val currentSettings = _settingsFlow.value
        val autoTexts = autoTextDao.getAll()
        val customWords = customWordDao.getAll()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val backupData = KeyboardBackupData(
            schemaVersion = 1,
            appName = "SmartKeyboardPro",
            backupDate = dateFormat.format(Date()),
            timestamp = System.currentTimeMillis(),
            deviceTarget = "SM-G610F / crDroid 6.21 / Android 10",
            settings = currentSettings,
            autoTexts = autoTexts,
            customWords = customWords
        )

        return try {
            backupAdapter.toJson(backupData)
        } catch (e: Exception) {
            fallbackSerializeBackup(backupData)
        }
    }

    /**
     * Restore from user-provided JSON string.
     * Robust handling: can parse standard Moshi format or raw lenient JSON.
     */
    suspend fun restoreFromJson(jsonString: String): RestoreResult {
        if (jsonString.isBlank()) {
            return RestoreResult.Error("Backup file is empty")
        }

        try {
            // First attempt: typed Moshi adapter
            val data = try {
                backupAdapter.fromJson(jsonString)
            } catch (e: Exception) {
                null
            }

            if (data != null && (data.autoTexts.isNotEmpty() || data.appName.isNotBlank())) {
                return applyBackupData(data)
            }

            // Second attempt: Manual lenient JSON parsing (for custom exports / external tools)
            return parseLenientJson(jsonString)
        } catch (e: Exception) {
            return RestoreResult.Error("Failed to parse backup: ${e.localizedMessage ?: "Invalid JSON format"}")
        }
    }

    private suspend fun applyBackupData(data: KeyboardBackupData): RestoreResult {
        // 1. Restore settings
        updateSettings(data.settings)

        // 2. Restore auto-texts (merge or replace)
        val incomingAutoTexts = data.autoTexts.map {
            // reset ID to 0 so Room autogenerates clean primary keys
            it.copy(id = 0)
        }
        if (incomingAutoTexts.isNotEmpty()) {
            autoTextDao.clearAll()
            autoTextDao.insertAll(incomingAutoTexts)
        }

        // 3. Restore custom words (Kamus Pribadi)
        val incomingWords = data.customWords.map { it.copy(id = 0) }
        if (incomingWords.isNotEmpty()) {
            customWordDao.clearAll()
            customWordDao.insertAll(incomingWords)
            val wordMap = incomingWords.associate { it.word to it.frequency }
            predictionEngine.updateCustomWords(wordMap)
        }

        return RestoreResult.Success(
            autoTextCount = incomingAutoTexts.size,
            wordCount = incomingWords.size,
            themeName = data.settings.themeId,
            backupDate = data.backupDate.ifEmpty { "Recently restored" }
        )
    }

    private suspend fun parseLenientJson(raw: String): RestoreResult {
        val root = JSONObject(raw)
        val autoTextsList = mutableListOf<AutoTextEntry>()
        val customWordsList = mutableListOf<CustomWordEntry>()

        if (root.has("autoTexts")) {
            val arr = root.getJSONArray("autoTexts")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val shortcut = obj.optString("shortcut", "")
                val replacement = obj.optString("replacement", "")
                val category = obj.optString("category", "General")
                val enabled = obj.optBoolean("enabled", true)
                if (shortcut.isNotEmpty() && replacement.isNotEmpty()) {
                    autoTextsList.add(
                        AutoTextEntry(
                            id = 0,
                            shortcut = shortcut,
                            replacement = replacement,
                            category = category,
                            enabled = enabled
                        )
                    )
                }
            }
        }

        if (root.has("customWords")) {
            val arr = root.getJSONArray("customWords")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val word = obj.optString("word", "")
                val freq = obj.optInt("frequency", 1)
                if (word.isNotEmpty()) {
                    customWordsList.add(CustomWordEntry(id = 0, word = word, frequency = freq))
                }
            }
        }

        var restoredSettings = _settingsFlow.value
        if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")
            restoredSettings = restoredSettings.copy(
                themeId = sObj.optString("themeId", restoredSettings.themeId),
                keyHeightPercent = sObj.optInt("keyHeightPercent", restoredSettings.keyHeightPercent),
                vibrateOnPress = sObj.optBoolean("vibrateOnPress", restoredSettings.vibrateOnPress),
                vibrationDurationMs = sObj.optInt("vibrationDurationMs", restoredSettings.vibrationDurationMs),
                soundOnPress = sObj.optBoolean("soundOnPress", restoredSettings.soundOnPress),
                soundVolume = sObj.optDouble("soundVolume", restoredSettings.soundVolume.toDouble()).toFloat(),
                showNumberRow = sObj.optBoolean("showNumberRow", restoredSettings.showNumberRow),
                autoCapitalize = sObj.optBoolean("autoCapitalize", restoredSettings.autoCapitalize),
                predictionEnabled = sObj.optBoolean("predictionEnabled", restoredSettings.predictionEnabled),
                autoSpaceAfterPunctuation = sObj.optBoolean("autoSpaceAfterPunctuation", restoredSettings.autoSpaceAfterPunctuation),
                autoSpaceAfterShortcut = sObj.optBoolean("autoSpaceAfterShortcut", restoredSettings.autoSpaceAfterShortcut),
                autoLearnWords = sObj.optBoolean("autoLearnWords", restoredSettings.autoLearnWords),
                customRow1Alts = if (sObj.has("customRow1Alts")) {
                    val arr = sObj.getJSONArray("customRow1Alts")
                    (0 until arr.length()).map { arr.getString(it) }
                } else restoredSettings.customRow1Alts,
                customRow2Alts = if (sObj.has("customRow2Alts")) {
                    val arr = sObj.getJSONArray("customRow2Alts")
                    (0 until arr.length()).map { arr.getString(it) }
                } else restoredSettings.customRow2Alts,
                customRow3Alts = if (sObj.has("customRow3Alts")) {
                    val arr = sObj.getJSONArray("customRow3Alts")
                    (0 until arr.length()).map { arr.getString(it) }
                } else restoredSettings.customRow3Alts
            )
        }

        val backupData = KeyboardBackupData(
            schemaVersion = root.optInt("schemaVersion", 1),
            appName = root.optString("appName", "SmartKeyboardPro"),
            backupDate = root.optString("backupDate", ""),
            settings = restoredSettings,
            autoTexts = autoTextsList,
            customWords = customWordsList
        )

        return applyBackupData(backupData)
    }

    private fun fallbackSerializeBackup(data: KeyboardBackupData): String {
        val root = JSONObject()
        root.put("schemaVersion", data.schemaVersion)
        root.put("appName", data.appName)
        root.put("backupDate", data.backupDate)
        root.put("timestamp", data.timestamp)
        root.put("deviceTarget", data.deviceTarget)

        val settingsObj = JSONObject()
        settingsObj.put("themeId", data.settings.themeId)
        settingsObj.put("keyHeightPercent", data.settings.keyHeightPercent)
        settingsObj.put("vibrateOnPress", data.settings.vibrateOnPress)
        settingsObj.put("vibrationDurationMs", data.settings.vibrationDurationMs)
        settingsObj.put("soundOnPress", data.settings.soundOnPress)
        settingsObj.put("soundVolume", data.settings.soundVolume)
        settingsObj.put("showNumberRow", data.settings.showNumberRow)
        settingsObj.put("autoCapitalize", data.settings.autoCapitalize)
        settingsObj.put("autoSpaceAfterPunctuation", data.settings.autoSpaceAfterPunctuation)
        settingsObj.put("autoSpaceAfterShortcut", data.settings.autoSpaceAfterShortcut)
        settingsObj.put("predictionEnabled", data.settings.predictionEnabled)
        settingsObj.put("autoLearnWords", data.settings.autoLearnWords)
        settingsObj.put("customRow1Alts", JSONArray(data.settings.customRow1Alts))
        settingsObj.put("customRow2Alts", JSONArray(data.settings.customRow2Alts))
        settingsObj.put("customRow3Alts", JSONArray(data.settings.customRow3Alts))
        root.put("settings", settingsObj)

        val autoTextArr = JSONArray()
        data.autoTexts.forEach {
            val item = JSONObject()
            item.put("shortcut", it.shortcut)
            item.put("replacement", it.replacement)
            item.put("category", it.category)
            item.put("enabled", it.enabled)
            autoTextArr.put(item)
        }
        root.put("autoTexts", autoTextArr)

        val wordsArr = JSONArray()
        data.customWords.forEach {
            val item = JSONObject()
            item.put("word", it.word)
            item.put("frequency", it.frequency)
            wordsArr.put(item)
        }
        root.put("customWords", wordsArr)

        return root.toString(2)
    }

    suspend fun restoreFromUniversalStream(inputStream: java.io.InputStream, fileName: String): RestoreResult {
        return try {
            val bytes = inputStream.use { it.readBytes() }
            val text = try {
                String(bytes, Charsets.UTF_8).replace("\uFEFF", "").trim()
            } catch (e: Exception) {
                ""
            }

            // 1. If it's JSON content or ends with .json, parse JSON directly!
            if (text.startsWith("{") || text.startsWith("[") || fileName.lowercase().endsWith(".json")) {
                return restoreFromJson(text)
            }

            // 2. Check if ZIP (magic number PK\x03\x04 or ends with .zip)
            val isZip = fileName.lowercase().endsWith(".zip") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
            if (isZip) {
                val parsed = SmartKeyboardProBackupImporter.parseZipBackup(context, java.io.ByteArrayInputStream(bytes))
                if (parsed.autoTexts.isNotEmpty() || parsed.settings != null) {
                    if (parsed.autoTexts.isNotEmpty()) {
                        autoTextDao.clearAll()
                        autoTextDao.insertAll(parsed.autoTexts)
                    }
                    if (parsed.settings != null) {
                        updateSettings(parsed.settings)
                    }
                    return RestoreResult.Success(
                        autoTextCount = parsed.autoTexts.size,
                        wordCount = 0,
                        themeName = parsed.settings?.themeId ?: _settingsFlow.value.themeId,
                        backupDate = "Dipulihkan dari Smart Keyboard Pro ZIP"
                    )
                }
            }

            // 3. Check if SQLite DB (.db or SQLite magic header)
            if (fileName.lowercase().endsWith(".db") || (bytes.size >= 15 && String(bytes.sliceArray(0..14)).startsWith("SQLite format"))) {
                val tempDb = File(context.cacheDir, "temp_import_direct.db")
                tempDb.writeBytes(bytes)
                val list = SmartKeyboardProBackupImporter.readSqliteAutoTexts(tempDb)
                tempDb.delete()
                if (list.isNotEmpty()) {
                    autoTextDao.clearAll()
                    autoTextDao.insertAll(list)
                    return RestoreResult.Success(
                        autoTextCount = list.size,
                        wordCount = 0,
                        themeName = _settingsFlow.value.themeId,
                        backupDate = "Dipulihkan dari autotext.db"
                    )
                }
            }

            // 4. Fallback: try parsing as JSON if there's text
            if (text.isNotEmpty()) {
                restoreFromJson(text)
            } else {
                RestoreResult.Error("Berkas cadangan kosong atau format tidak didukung")
            }
        } catch (e: Exception) {
            RestoreResult.Error("Gagal membaca berkas cadangan: ${e.localizedMessage}")
        }
    }

    suspend fun seedDefaultShortcuts() {
        autoTextDao.clearAll()
        autoTextDao.insertAll(SkpDefaults.defaultShortcuts)
    }
}

sealed class RestoreResult {
    data class Success(
        val autoTextCount: Int,
        val wordCount: Int,
        val themeName: String,
        val backupDate: String
    ) : RestoreResult()

    data class Error(val message: String) : RestoreResult()
}
