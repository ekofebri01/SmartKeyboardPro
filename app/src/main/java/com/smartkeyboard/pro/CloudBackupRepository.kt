package com.smartkeyboard.pro

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudBackupItem(
    val backupId: String = "",
    val deviceName: String = "",
    val appVersion: String = "1.4.0",
    val createdAtFormatted: String = "",
    val timestamp: Long = 0L,
    val themeConfigJson: String = "",
    val learnedWordsJson: String = "",
    val clipboardSnippetsJson: String = "",
    val itemCount: Int = 0
)

data class CloudDocumentInspectorItem(
    val collectionName: String,
    val documentId: String,
    val path: String,
    val updatedAtFormatted: String,
    val jsonContent: String,
    val fieldCount: Int
)

class CloudBackupRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val dbId: String = context.getString(R.string.firestore_database_id)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(dbId)

    private fun getUid(): String? = auth.currentUser?.uid

    /**
     * Synchronizes current local settings, dictionary, and clipboard directly to live Firestore document collections.
     */
    suspend fun syncAllToCloud(
        config: ThemeConfig,
        learnedWords: List<DictionaryEntry>,
        snippets: List<String>
    ): Boolean {
        val uid = getUid() ?: return false
        return try {
            val userDocRef = firestore.collection("users").document(uid)

            // 1. Sync Theme Settings to /users/{uid}/settings/current
            val settingsMap = mapOf(
                "themeId" to config.themeId,
                "backgroundColorHex" to String.format("#%08X", config.backgroundColor.value.toLong()),
                "keyColorHex" to String.format("#%08X", config.keyColor.value.toLong()),
                "textColorHex" to String.format("#%08X", config.textColor.value.toLong()),
                "accentColorHex" to String.format("#%08X", config.accentColor.value.toLong()),
                "cornerRadiusDp" to config.cornerRadiusDp,
                "keyOpacity" to config.keyOpacity,
                "keyHeightDp" to config.keyHeightDp,
                "keyShapeStyle" to config.keyShapeStyle,
                "soundEnabled" to config.soundEnabled,
                "vibrationEnabled" to config.vibrationEnabled,
                "showNumberRow" to config.showNumberRow,
                "showKeySymbols" to config.showKeySymbols,
                "enableLongPressSymbols" to config.enableLongPressSymbols,
                "showSuggestionBar" to config.showSuggestionBar,
                "autoSpaceAfterWord" to config.autoSpaceAfterWord,
                "hasCustomPhoto" to (config.customImagePath != null),
                "updatedAt" to FieldValue.serverTimestamp(),
                "deviceName" to Build.MODEL
            )
            userDocRef.collection("settings").document("current").set(settingsMap).await()

            // 2. Sync Personal Dictionary words to /users/{uid}/learned_words/{word}
            learnedWords.forEach { entry ->
                userDocRef.collection("learned_words").document(entry.word).set(
                    mapOf(
                        "word" to entry.word,
                        "frequency" to entry.frequency,
                        "lastUsed" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 3. Sync Clipboard Snippets to /users/{uid}/snippets/{snippetId}
            snippets.forEachIndexed { idx, snippet ->
                val snippetId = "snippet_$idx"
                userDocRef.collection("snippets").document(snippetId).set(
                    mapOf(
                        "content" to snippet,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a full timestamped Cloud Backup document in /users/{uid}/backups/{backupId}
     */
    suspend fun createCloudBackup(
        config: ThemeConfig,
        learnedWords: List<DictionaryEntry>,
        snippets: List<String>
    ): String? {
        val uid = getUid() ?: return null
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()
        val backupId = "backup_" + System.currentTimeMillis()
        val dateString = timeFormat.format(now)

        try {
            // Theme config JSON
            val configObj = JSONObject().apply {
                put("themeId", config.themeId)
                put("cornerRadiusDp", config.cornerRadiusDp)
                put("keyOpacity", config.keyOpacity.toDouble())
                put("keyHeightDp", config.keyHeightDp)
                put("keyShapeStyle", config.keyShapeStyle)
                put("soundEnabled", config.soundEnabled)
                put("vibrationEnabled", config.vibrationEnabled)
                put("showNumberRow", config.showNumberRow)
                put("showKeySymbols", config.showKeySymbols)
                put("enableLongPressSymbols", config.enableLongPressSymbols)
                put("showSuggestionBar", config.showSuggestionBar)
                put("autoSpaceAfterWord", config.autoSpaceAfterWord)
            }

            // Learned words JSON
            val wordsArray = JSONArray()
            learnedWords.forEach { entry ->
                wordsArray.put(JSONObject().apply {
                    put("word", entry.word)
                    put("frequency", entry.frequency)
                })
            }

            // Clipboard snippets JSON
            val snippetsArray = JSONArray()
            snippets.forEach { snippetsArray.put(it) }

            val totalItems = learnedWords.size + snippets.size

            val backupMap = mapOf(
                "backupId" to backupId,
                "deviceName" to (Build.MODEL ?: "Android Device"),
                "appVersion" to "1.4.0",
                "createdAtFormatted" to dateString,
                "timestamp" to now.time,
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "themeConfigJson" to configObj.toString(),
                "learnedWordsJson" to wordsArray.toString(),
                "clipboardSnippetsJson" to snippetsArray.toString(),
                "itemCount" to totalItems
            )

            firestore.collection("users").document(uid)
                .collection("backups").document(backupId)
                .set(backupMap).await()

            return backupId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Fetches all cloud backup documents stored under /users/{uid}/backups
     */
    suspend fun fetchCloudBackups(): List<CloudBackupItem> {
        val uid = getUid() ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("backups")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().await()

            snapshot.documents.mapNotNull { doc ->
                val backupId = doc.getString("backupId") ?: doc.id
                val deviceName = doc.getString("deviceName") ?: "Unknown Device"
                val appVersion = doc.getString("appVersion") ?: "1.4.0"
                val createdAtFormatted = doc.getString("createdAtFormatted") ?: "Unknown"
                val timestamp = doc.getLong("timestamp") ?: 0L
                val themeConfigJson = doc.getString("themeConfigJson") ?: ""
                val learnedWordsJson = doc.getString("learnedWordsJson") ?: ""
                val clipboardSnippetsJson = doc.getString("clipboardSnippetsJson") ?: ""
                val itemCount = doc.getLong("itemCount")?.toInt() ?: 0

                CloudBackupItem(
                    backupId = backupId,
                    deviceName = deviceName,
                    appVersion = appVersion,
                    createdAtFormatted = createdAtFormatted,
                    timestamp = timestamp,
                    themeConfigJson = themeConfigJson,
                    learnedWordsJson = learnedWordsJson,
                    clipboardSnippetsJson = clipboardSnippetsJson,
                    itemCount = itemCount
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Deletes a cloud backup document from Firestore.
     */
    suspend fun deleteCloudBackup(backupId: String): Boolean {
        val uid = getUid() ?: return false
        return try {
            firestore.collection("users").document(uid)
                .collection("backups").document(backupId)
                .delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads all live Firestore documents across collections to display in the Cloud Database Inspector UI.
     */
    suspend fun fetchLiveCloudDocuments(): List<CloudDocumentInspectorItem> {
        val uid = getUid() ?: return emptyList()
        val list = mutableListOf<CloudDocumentInspectorItem>()
        val timeFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

        try {
            val userRef = firestore.collection("users").document(uid)

            // 1. Settings Collection
            val settingsSnap = userRef.collection("settings").get().await()
            settingsSnap.documents.forEach { doc ->
                val dataMap = doc.data ?: emptyMap<String, Any>()
                val json = JSONObject(dataMap).toString(2)
                list.add(
                    CloudDocumentInspectorItem(
                        collectionName = "settings",
                        documentId = doc.id,
                        path = "users/$uid/settings/${doc.id}",
                        updatedAtFormatted = timeFormat.format(Date()),
                        jsonContent = json,
                        fieldCount = dataMap.size
                    )
                )
            }

            // 2. Learned Words Collection
            val wordsSnap = userRef.collection("learned_words").get().await()
            if (!wordsSnap.isEmpty) {
                val wordsSummary = JSONObject().apply {
                    put("total_words", wordsSnap.size())
                    val sampleList = JSONArray()
                    wordsSnap.documents.take(5).forEach { d ->
                        sampleList.put(JSONObject().apply {
                            put("word", d.getString("word"))
                            put("frequency", d.getLong("frequency"))
                        })
                    }
                    put("sample_words", sampleList)
                }
                list.add(
                    CloudDocumentInspectorItem(
                        collectionName = "learned_words",
                        documentId = "all_words (${wordsSnap.size()} items)",
                        path = "users/$uid/learned_words/*",
                        updatedAtFormatted = timeFormat.format(Date()),
                        jsonContent = wordsSummary.toString(2),
                        fieldCount = wordsSnap.size()
                    )
                )
            }

            // 3. Clipboard Snippets Collection
            val snippetsSnap = userRef.collection("snippets").get().await()
            if (!snippetsSnap.isEmpty) {
                val snippetsSummary = JSONObject().apply {
                    put("total_snippets", snippetsSnap.size())
                    val sampleList = JSONArray()
                    snippetsSnap.documents.forEach { d ->
                        sampleList.put(d.getString("content") ?: "")
                    }
                    put("snippets", sampleList)
                }
                list.add(
                    CloudDocumentInspectorItem(
                        collectionName = "snippets",
                        documentId = "all_snippets (${snippetsSnap.size()} items)",
                        path = "users/$uid/snippets/*",
                        updatedAtFormatted = timeFormat.format(Date()),
                        jsonContent = snippetsSummary.toString(2),
                        fieldCount = snippetsSnap.size()
                    )
                )
            }

            // 4. Backups Collection
            val backupsSnap = userRef.collection("backups").get().await()
            backupsSnap.documents.forEach { doc ->
                val backupId = doc.getString("backupId") ?: doc.id
                val dev = doc.getString("deviceName") ?: "Android"
                val date = doc.getString("createdAtFormatted") ?: "N/A"
                val items = doc.getLong("itemCount") ?: 0
                val summaryObj = JSONObject().apply {
                    put("backupId", backupId)
                    put("deviceName", dev)
                    put("createdAt", date)
                    put("totalItemsCount", items)
                    put("themeConfigJson", doc.getString("themeConfigJson"))
                }
                list.add(
                    CloudDocumentInspectorItem(
                        collectionName = "backups",
                        documentId = backupId,
                        path = "users/$uid/backups/$backupId",
                        updatedAtFormatted = date,
                        jsonContent = summaryObj.toString(2),
                        fieldCount = doc.data?.size ?: 0
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    /**
     * Exports all local data to a JSON string payload for offline device download.
     */
    fun exportAllDataToJson(
        config: ThemeConfig,
        learnedWords: List<DictionaryEntry>,
        snippets: List<String>
    ): String {
        val root = JSONObject()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        root.put("app", "SmartKeyboardPro")
        root.put("exportDate", timeFormat.format(Date()))
        root.put("device", Build.MODEL ?: "Android")

        // Theme config
        val configObj = JSONObject().apply {
            put("themeId", config.themeId)
            put("cornerRadiusDp", config.cornerRadiusDp)
            put("keyOpacity", config.keyOpacity.toDouble())
            put("keyHeightDp", config.keyHeightDp)
            put("keyShapeStyle", config.keyShapeStyle)
            put("soundEnabled", config.soundEnabled)
            put("vibrationEnabled", config.vibrationEnabled)
            put("showNumberRow", config.showNumberRow)
            put("showKeySymbols", config.showKeySymbols)
            put("enableLongPressSymbols", config.enableLongPressSymbols)
            put("showSuggestionBar", config.showSuggestionBar)
            put("autoSpaceAfterWord", config.autoSpaceAfterWord)
        }
        root.put("themeConfig", configObj)

        // Dictionary
        val dictionaryArray = JSONArray()
        learnedWords.forEach { entry ->
            dictionaryArray.put(JSONObject().apply {
                put("word", entry.word)
                put("frequency", entry.frequency)
            })
        }
        root.put("learnedWords", dictionaryArray)

        // Snippets
        val snippetsArray = JSONArray()
        snippets.forEach { snippetsArray.put(it) }
        root.put("clipboardSnippets", snippetsArray)

        return root.toString(2)
    }

    /**
     * Saves exported JSON to a local backup file on the device.
     */
    fun saveJsonFileToDevice(context: Context, jsonPayload: String): File? {
        return try {
            val fileName = "SmartKeyboardPro_Backup_${System.currentTimeMillis()}.json"
            val file = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
            file.writeText(jsonPayload)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
