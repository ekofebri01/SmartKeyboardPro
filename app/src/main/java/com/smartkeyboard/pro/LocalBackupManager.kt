package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        // Scoped storage: the previous implementation tried
        // Environment.getExternalStorageDirectory()/DIRECTORY_DOCUMENTS, which silently
        // fails on Android 10+ because the app declares no storage permission. Only
        // app-private locations are used here; arbitrary user folders go through SAF.
        val externalAppDir = context.getExternalFilesDir(null)
        val candidates = listOfNotNull(
            externalAppDir?.let { File(it, BACKUP_FOLDER_NAME) },
            File(context.filesDir, BACKUP_FOLDER_NAME)
        )

        candidates.forEach { dir ->
            try {
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    Log.i(TAG, "Backup dir created ($created): ${dir.absolutePath}")
                }
                if (dir.exists() && dir.canWrite()) {
                    return dir
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create/write backup directory ${dir.absolutePath}: ${e.message}")
            }
        }

        return File(context.filesDir, BACKUP_FOLDER_NAME)
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
     * Parses a stored theme block into a [ThemeConfig].
     *
     * Single source of truth for backup JSON -> ThemeConfig mapping, so local restore,
     * cloud restore and JSON import can no longer drift apart.
     */
    fun themeConfigFromJson(obj: JSONObject, base: ThemeConfig = ThemeConfig()): ThemeConfig {
        return base.copy(
            themeId = obj.optString("themeId", base.themeId),
            cornerRadiusDp = obj.optInt("cornerRadiusDp", base.cornerRadiusDp),
            keyOpacity = obj.optDouble("keyOpacity", base.keyOpacity.toDouble()).toFloat(),
            keyHeightDp = obj.optInt("keyHeightDp", base.keyHeightDp),
            keyShapeStyle = obj.optString("keyShapeStyle", base.keyShapeStyle),
            soundEnabled = obj.optBoolean("soundEnabled", base.soundEnabled),
            vibrationEnabled = obj.optBoolean("vibrationEnabled", base.vibrationEnabled),
            showNumberRow = obj.optBoolean("showNumberRow", base.showNumberRow),
            showKeySymbols = obj.optBoolean("showKeySymbols", base.showKeySymbols),
            enableLongPressSymbols = obj.optBoolean("enableLongPressSymbols", base.enableLongPressSymbols),
            showSuggestionBar = obj.optBoolean("showSuggestionBar", base.showSuggestionBar),
            autoSpaceAfterWord = obj.optBoolean("autoSpaceAfterWord", base.autoSpaceAfterWord)
        )
    }

    /**
     * Reads and parses a backup file. Pure file I/O, safe to call from Dispatchers.IO.
     */
    fun readBackupFile(context: Context, info: LocalBackupFileInfo): LocalRestoreResult {
        val jsonString = try {
            when {
                info.uri != null -> context.contentResolver.openInputStream(info.uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: return LocalRestoreResult.Error("Tidak dapat membuka stream dari URI.")

                info.file != null && info.file.exists() -> info.file.readText()
                else -> return LocalRestoreResult.Error("Source backup tidak ditemukan.")
            }
        } catch (e: Exception) {
            return LocalRestoreResult.Error("Gagal membaca file backup: ${e.localizedMessage ?: e.message}")
        }
        return parseBackupJson(jsonString)
    }

    /**
     * Parses backup JSON. Pure parsing, safe to call from a background thread.
     */
    fun parseBackupJson(jsonContent: String): LocalRestoreResult {
        return try {
            val root = JSONObject(jsonContent)

            val themeConfig = if (root.has("themeConfig")) {
                themeConfigFromJson(root.getJSONObject("themeConfig"))
            } else {
                null
            }

            val learnedWords = mutableListOf<String>()
            if (root.has("learnedWords")) {
                val array = root.getJSONArray("learnedWords")
                for (i in 0 until array.length()) {
                    learnedWords.add(array.getJSONObject(i).getString("word"))
                }
            }

            val snippets = mutableListOf<String>()
            if (root.has("clipboardSnippets")) {
                val array = root.getJSONArray("clipboardSnippets")
                for (i in 0 until array.length()) {
                    snippets.add(array.getString(i))
                }
            }

            LocalRestoreResult.Success(ParsedBackup(themeConfig, learnedWords, snippets))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup json", e)
            LocalRestoreResult.Error("Gagal memulihkan file backup: ${e.localizedMessage ?: e.message}")
        }
    }
}

/** Content of a parsed local backup file. */
data class ParsedBackup(
    val themeConfig: ThemeConfig? = null,
    val learnedWords: List<String> = emptyList(),
    val snippets: List<String> = emptyList()
) {
    val itemCount: Int
        get() = learnedWords.size + snippets.size + if (themeConfig != null) 1 else 0
}

/** Result of reading/parsing a local backup file. */
sealed class LocalRestoreResult {
    data class Success(val backup: ParsedBackup) : LocalRestoreResult()
    data class Error(val message: String) : LocalRestoreResult()
}
