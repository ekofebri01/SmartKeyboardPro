package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KeyboardBackupData(
    val schemaVersion: Int = 1,
    val appName: String = "SmartKeyboardPro",
    val backupDate: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceTarget: String = "SM-G610F / crDroid 6.21 / Android 10",
    val settings: KeyboardSettings = KeyboardSettings(),
    val autoTexts: List<AutoTextEntry> = emptyList(),
    val customWords: List<CustomWordEntry> = emptyList()
)
