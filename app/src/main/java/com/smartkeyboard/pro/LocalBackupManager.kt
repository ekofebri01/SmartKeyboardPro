package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalBackupStatus(
    val success: Boolean,
    val filePath: String = "",
    val folderPath: String = "",
    val formattedDate: String = "",
    val fileSizeBytes: Long = 0,
    val message: String = ""
)

data class LocalBackupFileInfo(
    val fileName: String,
    val file: File? = null,
    val uri: Uri? = null,
    val lastModifiedFormatted: String,
    val sizeFormatted: String,
    val isSafUri: Boolean = false
)

/**
 * Local file backup & restore utility with Scoped Storage Access Framework (SAF) folder permissions.
 * Allows granting permission strictly to `backups/SmartkeyboardPro` folder or any chosen directory.
 */
object LocalBackupManager {
    private const val TAG = "LocalBackupManager"
    private const val PREFS_NAME = "smart_keyboard_local_backup_prefs"
    private const val KEY_FOLDER_URI = "permitted_folder_uri"

    const val BACKUP_FOLDER_NAME = "backups/SmartkeyboardPro"
    const val DEFAULT_BACKUP_FILENAME = "smartkeyboardpro_backup.json"

    /**
     * Persists the user-granted folder URI permission across app restarts.
     */
    fun savePersistableUri(context: Context, uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
            Log.i(TAG, "Successfully persisted folder URI permission: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist URI permission", e)
            false
        }
    }

    /**
     * Clears saved folder permission.
     */
    fun clearPersistedUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FOLDER_URI).apply()
    }

    /**
     * Retrieves the persisted folder URI if permission is still valid.
     */
    fun getPersistedFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        val uri = Uri.parse(uriStr)

        return try {
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedPermissions.any { it.uri == uri && it.isWritePermission }
            if (hasPermission) uri else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves or creates the target backup directory on device.
     */
    fun getOrCreateBackupDirectory(context: Context): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        val primaryDir = File(externalStorage, BACKUP_FOLDER_NAME)

        try {
            if (!primaryDir.exists()) {
                val created = primaryDir.mkdirs()
                Log.i(TAG, "Primary dir created ($created): ${primaryDir.absolutePath}")
            }
            if (primaryDir.exists() && primaryDir.canWrite()) {
                return primaryDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create/write to primary directory: ${e.message}")
        }

        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val secondaryDir = File(docsDir, BACKUP_FOLDER_NAME)
            if (!secondaryDir.exists()) {
                secondaryDir.mkdirs()
            }
            if (secondaryDir.exists()) {
                return secondaryDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed secondary directory creation: ${e.message}")
        }

        val fallbackDir = File(context.getExternalFilesDir(null), BACKUP_FOLDER_NAME)
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    /**
     * Performs auto-backup using permitted SAF Folder URI if available, falling back to local files.
     */
    fun performAutoBackup(
        context: Context,
        jsonPayload: String
    ): LocalBackupStatus {
        val safUri = getPersistedFolderUri(context)

        if (safUri != null) {
            try {
                val folder = DocumentFile.fromTreeUri(context, safUri)
                if (folder != null && folder.exists()) {
                    var file = folder.findFile(DEFAULT_BACKUP_FILENAME)
                    if (file == null || !file.exists()) {
                        file = folder.createFile("application/json", DEFAULT_BACKUP_FILENAME)
                    }

                    if (file != null) {
                        context.contentResolver.openOutputStream(file.uri, "rwt")?.use { stream ->
                            stream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                        }

                        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
                        val formattedDate = dateFormat.format(Date())

                        return LocalBackupStatus(
                            success = true,
                            filePath = file.uri.toString(),
                            folderPath = folder.name ?: "Permitted Folder",
                            formattedDate = formattedDate,
                            fileSizeBytes = file.length(),
                            message = "Backup disalin & di-overwrite ke folder ijin khusus (${folder.name}/$DEFAULT_BACKUP_FILENAME)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to SAF Uri: $safUri", e)
            }
        }

        // File-based fallback
        return try {
            val dir = getOrCreateBackupDirectory(context)
            val backupFile = File(dir, DEFAULT_BACKUP_FILENAME)

            backupFile.writeText(jsonPayload)

            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(backupFile.lastModified()))

            LocalBackupStatus(
                success = true,
                filePath = backupFile.absolutePath,
                folderPath = dir.absolutePath,
                formattedDate = formattedDate,
                fileSizeBytes = backupFile.length(),
                message = "Backup lokal disimpan & di-overwrite di ${backupFile.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing local auto backup", e)
            LocalBackupStatus(
                success = false,
                message = "Gagal membuat backup lokal: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * Searches for available `.json` local backup files in SAF directory and file system.
     */
    fun getAvailableBackupFiles(context: Context): List<LocalBackupFileInfo> {
        val resultList = mutableListOf<LocalBackupFileInfo>()
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

        // 1. SAF Permitted Folder
        val safUri = getPersistedFolderUri(context)
        if (safUri != null) {
            try {
                val folder = DocumentFile.fromTreeUri(context, safUri)
                if (folder != null && folder.exists()) {
                    folder.listFiles()
                        .filter { it.name?.endsWith(".json", ignoreCase = true) == true }
                        .forEach { docFile ->
                            val sizeKb = String.format("%.1f KB", docFile.length() / 1024.0)
                            resultList.add(
                                LocalBackupFileInfo(
                                    fileName = docFile.name ?: DEFAULT_BACKUP_FILENAME,
                                    uri = docFile.uri,
                                    lastModifiedFormatted = dateFormat.format(Date(docFile.lastModified())),
                                    sizeFormatted = sizeKb,
                                    isSafUri = true
                                )
                            )
                        }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning SAF folder", e)
            }
        }

        // 2. Local File System
        val backupDir = getOrCreateBackupDirectory(context)
        if (backupDir.exists() && backupDir.isDirectory) {
            backupDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }?.forEach { file ->
                val sizeKb = String.format("%.1f KB", file.length() / 1024.0)
                resultList.add(
                    LocalBackupFileInfo(
                        fileName = file.name,
                        file = file,
                        lastModifiedFormatted = dateFormat.format(Date(file.lastModified())),
                        sizeFormatted = sizeKb,
                        isSafUri = false
                    )
                )
            }
        }

        return resultList.distinctBy { it.fileName }
    }

    /**
     * Restores configuration, dictionary, and snippets from a local JSON string or Uri/File.
     */
    fun restoreFromJsonContent(
        context: Context,
        jsonContent: String,
        sourceName: String,
        onRestoreTheme: (ThemeConfig) -> Unit,
        onRestoreDictionaryWord: (String) -> Unit,
        onRestoreSnippet: (String) -> Unit
    ): Pair<Boolean, String> {
        return try {
            val root = JSONObject(jsonContent)
            var itemCount = 0

            // Restore theme
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

                val restoredConfig = ThemeConfig(
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
                onRestoreTheme(restoredConfig)
                itemCount++
            }

            // Restore dictionary
            if (root.has("learnedWords")) {
                val array = root.getJSONArray("learnedWords")
                for (i in 0 until array.length()) {
                    val wObj = array.getJSONObject(i)
                    val w = wObj.getString("word")
                    onRestoreDictionaryWord(w)
                    itemCount++
                }
            }

            // Restore snippets
            if (root.has("clipboardSnippets")) {
                val array = root.getJSONArray("clipboardSnippets")
                for (i in 0 until array.length()) {
                    val snippet = array.getString(i)
                    onRestoreSnippet(snippet)
                    itemCount++
                }
            }

            Pair(true, "Restore lokal berhasil! ($itemCount item & setting dipulihkan dari $sourceName)")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing json content from $sourceName", e)
            Pair(false, "Gagal memulihkan file backup: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Reads JSON from LocalBackupFileInfo (either File or Uri) and restores.
     */
    fun restoreFromBackupInfo(
        context: Context,
        info: LocalBackupFileInfo,
        onRestoreTheme: (ThemeConfig) -> Unit,
        onRestoreDictionaryWord: (String) -> Unit,
        onRestoreSnippet: (String) -> Unit
    ): Pair<Boolean, String> {
        val jsonString = if (info.uri != null) {
            try {
                context.contentResolver.openInputStream(info.uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: return Pair(false, "Tidak dapat membuka stream dari URI.")
            } catch (e: Exception) {
                return Pair(false, "Gagal membaca file dari URI: ${e.message}")
            }
        } else if (info.file != null && info.file.exists()) {
            info.file.readText()
        } else {
            return Pair(false, "Source backup tidak ditemukan.")
        }

        return restoreFromJsonContent(
            context = context,
            jsonContent = jsonString,
            sourceName = info.fileName,
            onRestoreTheme = onRestoreTheme,
            onRestoreDictionaryWord = onRestoreDictionaryWord,
            onRestoreSnippet = onRestoreSnippet
        )
    }
}
