package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KeyboardSettings(
    val themeId: String = "classic_smart_pro",
    val keyHeightPercent: Int = 100, // 80 - 130
    val vibrateOnPress: Boolean = false,
    val vibrationDurationMs: Int = 15, // 5 - 100
    val soundOnPress: Boolean = true,
    val soundVolume: Float = 1.0f,
    val showNumberRow: Boolean = true,
    val autoCapitalize: Boolean = true,
    val autoSpaceAfterPunctuation: Boolean = true,
    val autoSpaceAfterShortcut: Boolean = true,
    val predictionEnabled: Boolean = true,
    val autoCorrectEnabled: Boolean = true,
    val longPressDelayMs: Int = 280,
    val showKeyPreview: Boolean = true,
    val smartPunctuation: Boolean = true,
    val autoBackupEnabled: Boolean = true,
    val autoLearnWords: Boolean = true,
    val activeDictionaryLanguage: String = "ALL", // "ALL", "ID", "EN"
    val isIndonesianDownloaded: Boolean = false,
    val isEnglishDownloaded: Boolean = false,
    // Custom theme parameters
    val customBgColor: Long = 0xFF22262B,
    val customKeyColor: Long = 0xFF353A40,
    val customTextColor: Long = 0xFFFFFFFF,
    val customAccentColor: Long = 0xFF3D82F6,
    val customKeyRadiusDp: Int = 8,
    // Custom subtext / secondary symbols for QWERTY rows
    val customRow1Alts: List<String> = listOf("~", "`", "€", "£", "¥", "°", "§", "¶", "©", "®"),
    val customRow2Alts: List<String> = listOf("!", "@", "#", "$", "%", "^", "&", "*", "+"),
    val customRow3Alts: List<String> = listOf("-", "_", "=", "[", "]", "{", "}")
) {
    fun resolveTheme(): ThemeColorPalette {
        if (themeId == "custom") {
            return ThemeColorPalette(
                id = "custom",
                name = "Custom User Theme",
                backgroundColor = customBgColor,
                keyBackgroundColor = customKeyColor,
                keyAltBackgroundColor = (customKeyColor and 0x00FFFFFFL) or 0xCC000000L,
                keyTextColor = customTextColor,
                keySubTextColor = (customTextColor and 0x00FFFFFFL) or 0x88000000L,
                accentColor = customAccentColor,
                accentTextColor = 0xFFFFFFFF,
                suggestionBgColor = (customBgColor and 0x00FFFFFFL) or 0xEE000000L,
                suggestionTextColor = customTextColor,
                keyBorderColor = 0x22FFFFFF,
                keyCornerRadiusDp = customKeyRadiusDp
            )
        }
        return KeyboardThemes.getThemeById(themeId)
    }
}
