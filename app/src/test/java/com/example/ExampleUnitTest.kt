package com.example

import com.example.data.prediction.PredictionEngine
import com.example.model.AutoTextEntry
import com.example.model.KeyboardBackupData
import com.example.model.KeyboardSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPredictionEngine_matchesAutoTextShortcut() {
        val engine = PredictionEngine()
        val autoTexts = listOf(
            AutoTextEntry(shortcut = "otw", replacement = "On the way bro! 🛵"),
            AutoTextEntry(shortcut = "thx", replacement = "Terima kasih banyak!")
        )

        val result = engine.getSuggestions("otw", autoTexts)
        assertNotNull(result.matchedAutoText)
        assertEquals("otw", result.matchedAutoText?.shortcut)
        assertTrue(result.suggestions.contains("On the way bro! 🛵"))
    }

    @Test
    fun testPredictionEngine_handlesGeneralPrefix() {
        val engine = PredictionEngine()
        val result = engine.getSuggestions("terim", emptyList())
        assertTrue(result.suggestions.any { it.startsWith("terim", ignoreCase = true) })
    }

    @Test
    fun testBackupData_jsonSerializationAndDeserialization() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(KeyboardBackupData::class.java)

        val sampleBackup = KeyboardBackupData(
            schemaVersion = 1,
            appName = "SmartKeyboardPro",
            backupDate = "2026-10-10",
            deviceTarget = "SM-G610F / crDroid 6.21 / Android 10",
            settings = KeyboardSettings(
                themeId = "amoled_dark",
                keyHeightPercent = 110,
                vibrateOnPress = true
            ),
            autoTexts = listOf(
                AutoTextEntry(id = 1, shortcut = "brb", replacement = "Be right back!")
            )
        )

        val json = adapter.toJson(sampleBackup)
        assertNotNull(json)
        assertTrue(json.contains("SmartKeyboardPro"))
        assertTrue(json.contains("amoled_dark"))
        assertTrue(json.contains("brb"))

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals("amoled_dark", restored?.settings?.themeId)
        assertEquals(1, restored?.autoTexts?.size)
        assertEquals("brb", restored?.autoTexts?.first()?.shortcut)
    }

    @Test
    fun testSkpDefaults_containsExpectedShortcuts() {
        val shortcuts = com.example.data.repository.SkpDefaults.defaultShortcuts
        assertEquals(40, shortcuts.size)
        assertTrue(shortcuts.any { it.shortcut == "ada3kemungkinan" })
        assertTrue(shortcuts.any { it.shortcut == "bca" })
        assertTrue(shortcuts.any { it.shortcut == "ccgojek" })
        assertTrue(shortcuts.any { it.shortcut == "eco" })
    }

    @Test
    fun testPredictionEngine_learnsWordAndSuggestsIt() {
        val engine = PredictionEngine()
        // Record learned word
        engine.recordLearnedWord("crdroid")
        engine.recordLearnedWord("crdroid") // higher frequency

        val result = engine.getSuggestions("cr", emptyList())
        assertTrue("Learned word should appear in suggestions", result.suggestions.any { it.equals("crdroid", ignoreCase = true) })
        // Crdroid should rank higher than random words
        assertEquals("crdroid", result.suggestions.first().lowercase())
    }

    @Test
    fun testKeyboardSettings_defaultAutoLearnWordsIsEnabled() {
        val settings = KeyboardSettings()
        assertTrue("autoLearnWords should be enabled by default", settings.autoLearnWords)
    }
}
