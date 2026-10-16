package com.example.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ThemeColorPalette(
    val id: String,
    val name: String,
    val backgroundColor: Long,
    val keyBackgroundColor: Long,
    val keyAltBackgroundColor: Long,
    val keyTextColor: Long,
    val keySubTextColor: Long,
    val accentColor: Long,
    val accentTextColor: Long,
    val suggestionBgColor: Long,
    val suggestionTextColor: Long,
    val keyBorderColor: Long = 0x00000000,
    val keyCornerRadiusDp: Int = 8
)

object KeyboardThemes {
    val ClassicSmartPro = ThemeColorPalette(
        id = "classic_smart_pro",
        name = "Smart Pro Classic",
        backgroundColor = 0xFF2B2E33,
        keyBackgroundColor = 0xFF3C4048,
        keyAltBackgroundColor = 0xFF31353B,
        keyTextColor = 0xFFF0F2F5,
        keySubTextColor = 0xFFD1D5DB, // Brighter for high readability
        accentColor = 0xFF2979FF,
        accentTextColor = 0xFFFFFFFF,
        suggestionBgColor = 0xFF232529,
        suggestionTextColor = 0xFFE0E0E0,
        keyBorderColor = 0x22FFFFFF,
        keyCornerRadiusDp = 6
    )

    val AmoledDark = ThemeColorPalette(
        id = "amoled_dark",
        name = "AMOLED Pitch Black",
        backgroundColor = 0xFF000000,
        keyBackgroundColor = 0xFF141414,
        keyAltBackgroundColor = 0xFF0A0A0A,
        keyTextColor = 0xFFFFFFFF,
        keySubTextColor = 0xFFA3A3A3, // Brighter
        accentColor = 0xFF00E676,
        accentTextColor = 0xFF000000,
        suggestionBgColor = 0xFF080808,
        suggestionTextColor = 0xFFE0E0E0,
        keyBorderColor = 0x33333333,
        keyCornerRadiusDp = 8
    )

    val MaterialTeal = ThemeColorPalette(
        id = "material_teal",
        name = "Material Teal / crDroid",
        backgroundColor = 0xFF1C2224,
        keyBackgroundColor = 0xFF283134,
        keyAltBackgroundColor = 0xFF21292B,
        keyTextColor = 0xFFE1F5FE,
        keySubTextColor = 0xFF80CBC4,
        accentColor = 0xFF00BFA5,
        accentTextColor = 0xFF002722,
        suggestionBgColor = 0xFF161B1D,
        suggestionTextColor = 0xFFE0F2F1,
        keyBorderColor = 0x2000BFA5,
        keyCornerRadiusDp = 10
    )

    val CyberNeon = ThemeColorPalette(
        id = "cyber_neon",
        name = "Cyberpunk Neon",
        backgroundColor = 0xFF0D0B18,
        keyBackgroundColor = 0xFF1A162B,
        keyAltBackgroundColor = 0xFF141024,
        keyTextColor = 0xFF00F0FF,
        keySubTextColor = 0xFFFF3399, // Brighter pink
        accentColor = 0xFFFF007F,
        accentTextColor = 0xFFFFFFFF,
        suggestionBgColor = 0xFF080611,
        suggestionTextColor = 0xFF00F0FF,
        keyBorderColor = 0x4400F0FF,
        keyCornerRadiusDp = 6
    )

    val SlateLight = ThemeColorPalette(
        id = "slate_light",
        name = "Clean Light",
        backgroundColor = 0xFFEBEFF5,
        keyBackgroundColor = 0xFFFFFFFF,
        keyAltBackgroundColor = 0xFFD8DFE8,
        keyTextColor = 0xFF1C2530,
        keySubTextColor = 0xFF334155, // Darker slate for high contrast on white keys
        accentColor = 0xFF0284C7,
        accentTextColor = 0xFFFFFFFF,
        suggestionBgColor = 0xFFE2E8F0,
        suggestionTextColor = 0xFF0F172A,
        keyBorderColor = 0x22000000,
        keyCornerRadiusDp = 8
    )

    val RetroTerminal = ThemeColorPalette(
        id = "retro_terminal",
        name = "Retro Green Terminal",
        backgroundColor = 0xFF0A0F0A,
        keyBackgroundColor = 0xFF121A12,
        keyAltBackgroundColor = 0xFF0E140E,
        keyTextColor = 0xFF39FF14,
        keySubTextColor = 0xFF50FF30, // Brighter green
        accentColor = 0xFF39FF14,
        accentTextColor = 0xFF000000,
        suggestionBgColor = 0xFF070B07,
        suggestionTextColor = 0xFF39FF14,
        keyBorderColor = 0x4439FF14,
        keyCornerRadiusDp = 4
    )

    val allBuiltIn = listOf(
        ClassicSmartPro,
        AmoledDark,
        MaterialTeal,
        CyberNeon,
        SlateLight,
        RetroTerminal
    )

    fun getThemeById(id: String): ThemeColorPalette {
        return allBuiltIn.find { it.id == id } ?: ClassicSmartPro
    }
}
