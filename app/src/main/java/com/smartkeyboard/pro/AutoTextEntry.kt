package com.smartkeyboard.pro

import org.json.JSONObject
import java.util.UUID

data class AutoTextEntry(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val fullText: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("shortcut", shortcut)
            put("fullText", fullText)
        }
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
            if (shortcut.isBlank() && fullText.isNotBlank()) {
                return createFromLegacyString(fullText)
            }
            return AutoTextEntry(id = id, shortcut = shortcut, fullText = fullText)
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
