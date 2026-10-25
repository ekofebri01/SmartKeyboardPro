package com.smartkeyboard.pro

import com.google.firebase.Timestamp
import org.json.JSONObject
import java.util.UUID

data class AutoTextEntry(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val fullText: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("shortcut", shortcut)
            put("fullText", fullText)
            put("updatedAt", updatedAt)
        }
    }

    fun toFirestoreMap(userId: String): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "shortcut" to shortcut.trim().lowercase(),
            "fullText" to fullText.trim(),
            "userId" to userId,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromJson(jsonStr: String): AutoTextEntry? {
            return try {
                val obj = JSONObject(jsonStr)
                fromJsonObject(obj)
            } catch (e: Exception) {
                // Legacy plain string fallback
                if (jsonStr.isNotBlank()) {
                    createFromLegacyString(jsonStr)
                } else null
            }
        }

        fun fromJsonObject(obj: JSONObject): AutoTextEntry {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val shortcut = obj.optString("shortcut", "")
            val fullText = obj.optString("fullText", "")
            val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            if (shortcut.isBlank() && fullText.isNotBlank()) {
                return createFromLegacyString(fullText)
            }
            return AutoTextEntry(id = id, shortcut = shortcut, fullText = fullText, updatedAt = updatedAt)
        }

        fun fromFirestoreDoc(data: Map<String, Any?>, docId: String = ""): AutoTextEntry {
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val shortcut = data["shortcut"] as? String ?: ""
            val fullText = data["fullText"] as? String ?: ""
            val updatedAt = when (val t = data["updatedAt"]) {
                is Timestamp -> t.toDate().time
                is Number -> t.toLong()
                else -> System.currentTimeMillis()
            }
            return AutoTextEntry(id = id, shortcut = shortcut, fullText = fullText, updatedAt = updatedAt)
        }

        fun createFromLegacyString(text: String): AutoTextEntry {
            val trimmed = text.trim()
            return if (trimmed.contains(": ")) {
                val parts = trimmed.split(": ", limit = 2)
                AutoTextEntry(
                    shortcut = parts[0].trim().lowercase(),
                    fullText = parts[1].trim()
                )
            } else {
                val words = trimmed.split("\\s+".toRegex())
                val shortcutCandidate = if (words.isNotEmpty()) words[0].take(8).lowercase().filter { it.isLetterOrDigit() } else "text"
                AutoTextEntry(
                    shortcut = if (shortcutCandidate.isNotBlank()) shortcutCandidate else "text",
                    fullText = trimmed
                )
            }
        }
    }
}
