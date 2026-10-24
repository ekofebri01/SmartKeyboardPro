package com.smartkeyboard.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

data class KeyboardTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val keyColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val isCustomImage: Boolean = false
)

data class ThemeConfig(
    val themeId: String = "midnight",
    val backgroundColor: Color = Color(0xFF0F172A),
    val keyColor: Color = Color(0xFF1E293B),
    val textColor: Color = Color.White,
    val accentColor: Color = Color(0xFF38BDF8),
    val cornerRadiusDp: Int = 8,
    val keyOpacity: Float = 0.9f,
    val customImagePath: String? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showNumberRow: Boolean = true,
    val showKeySymbols: Boolean = true,
    val enableLongPressSymbols: Boolean = true,
    val keyHeightDp: Int = 44,
    val keyShapeStyle: String = "rounded", // "rounded", "pill", "squircle", "sharp"
    val showSuggestionBar: Boolean = true,
    val autoSpaceAfterWord: Boolean = true
)

object ThemeManager {
    private const val PREFS_NAME = "smart_keyboard_theme_prefs"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_KEY_COLOR = "key_color"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_CORNER_RADIUS = "corner_radius"
    private const val KEY_KEY_OPACITY = "key_opacity"
    private const val KEY_CUSTOM_IMAGE_PATH = "custom_image_path"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATION = "vibration_enabled"
    private const val KEY_SHOW_NUMBER_ROW = "show_number_row"
    private const val KEY_SHOW_KEY_SYMBOLS = "show_key_symbols"
    private const val KEY_ENABLE_LONG_PRESS = "enable_long_press"
    private const val KEY_KEY_HEIGHT = "key_height"
    private const val KEY_KEY_SHAPE_STYLE = "key_shape_style"
    private const val KEY_SHOW_SUGGESTION_BAR = "show_suggestion_bar"
    private const val KEY_AUTO_SPACE = "auto_space"

    val predefinedThemes = listOf(
        KeyboardTheme(
            id = "midnight",
            name = "Midnight Slate",
            backgroundColor = Color(0xFF0F172A),
            keyColor = Color(0xFF1E293B),
            textColor = Color.White,
            accentColor = Color(0xFF38BDF8)
        ),
        KeyboardTheme(
            id = "cyberpunk",
            name = "Cyberpunk Neon",
            backgroundColor = Color(0xFF18092B),
            keyColor = Color(0xFF2D144E),
            textColor = Color(0xFFF472B6),
            accentColor = Color(0xFF06B6D4)
        ),
        KeyboardTheme(
            id = "emerald",
            name = "Emerald Forest",
            backgroundColor = Color(0xFF062319),
            keyColor = Color(0xFF0D3E2F),
            textColor = Color(0xFFA7F3D0),
            accentColor = Color(0xFF34D399)
        ),
        KeyboardTheme(
            id = "sunset",
            name = "Sunset Glow",
            backgroundColor = Color(0xFF2B0F1A),
            keyColor = Color(0xFF481B2D),
            textColor = Color(0xFFFDE047),
            accentColor = Color(0xFFF97316)
        ),
        KeyboardTheme(
            id = "nord",
            name = "Nord Frost",
            backgroundColor = Color(0xFF2E3440),
            keyColor = Color(0xFF3B4252),
            textColor = Color(0xFFECEFF4),
            accentColor = Color(0xFF88C0D0)
        ),
        KeyboardTheme(
            id = "light",
            name = "Clean Light",
            backgroundColor = Color(0xFFF8FAFC),
            keyColor = Color(0xFFE2E8F0),
            textColor = Color(0xFF0F172A),
            accentColor = Color(0xFF0284C7)
        ),
        KeyboardTheme(
            id = "amoled",
            name = "AMOLED Black",
            backgroundColor = Color(0xFF000000),
            keyColor = Color(0xFF18181B),
            textColor = Color.White,
            accentColor = Color(0xFFA1A1AA)
        )
    )

    fun loadConfig(context: Context): ThemeConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME_ID, "midnight") ?: "midnight"
        val preset = predefinedThemes.find { it.id == themeId }

        val bgColor = if (preset != null && themeId != "custom") {
            preset.backgroundColor
        } else {
            Color(prefs.getInt(KEY_BG_COLOR, Color(0xFF0F172A).toArgb()))
        }

        val keyColor = if (preset != null && themeId != "custom") {
            preset.keyColor
        } else {
            Color(prefs.getInt(KEY_KEY_COLOR, Color(0xFF1E293B).toArgb()))
        }

        val textColor = if (preset != null && themeId != "custom") {
            preset.textColor
        } else {
            Color(prefs.getInt(KEY_TEXT_COLOR, Color.White.toArgb()))
        }

        val accentColor = if (preset != null && themeId != "custom") {
            preset.accentColor
        } else {
            Color(prefs.getInt(KEY_ACCENT_COLOR, Color(0xFF38BDF8).toArgb()))
        }

        val customImagePath = prefs.getString(KEY_CUSTOM_IMAGE_PATH, null)
        val validImagePath = if (customImagePath != null && File(customImagePath).exists()) customImagePath else null

        return ThemeConfig(
            themeId = themeId,
            backgroundColor = bgColor,
            keyColor = keyColor,
            textColor = textColor,
            accentColor = accentColor,
            cornerRadiusDp = prefs.getInt(KEY_CORNER_RADIUS, 8),
            keyOpacity = prefs.getFloat(KEY_KEY_OPACITY, 0.9f),
            customImagePath = validImagePath,
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            showNumberRow = prefs.getBoolean(KEY_SHOW_NUMBER_ROW, true),
            showKeySymbols = prefs.getBoolean(KEY_SHOW_KEY_SYMBOLS, true),
            enableLongPressSymbols = prefs.getBoolean(KEY_ENABLE_LONG_PRESS, true),
            showSuggestionBar = prefs.getBoolean(KEY_SHOW_SUGGESTION_BAR, true),
            autoSpaceAfterWord = prefs.getBoolean(KEY_AUTO_SPACE, true),
            keyHeightDp = prefs.getInt(KEY_KEY_HEIGHT, 44),
            keyShapeStyle = prefs.getString(KEY_KEY_SHAPE_STYLE, "rounded") ?: "rounded"
        )
    }

    fun saveConfig(context: Context, config: ThemeConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_THEME_ID, config.themeId)
            putInt(KEY_BG_COLOR, config.backgroundColor.toArgb())
            putInt(KEY_KEY_COLOR, config.keyColor.toArgb())
            putInt(KEY_TEXT_COLOR, config.textColor.toArgb())
            putInt(KEY_ACCENT_COLOR, config.accentColor.toArgb())
            putInt(KEY_CORNER_RADIUS, config.cornerRadiusDp)
            putFloat(KEY_KEY_OPACITY, config.keyOpacity)
            putString(KEY_CUSTOM_IMAGE_PATH, config.customImagePath)
            putBoolean(KEY_SOUND, config.soundEnabled)
            putBoolean(KEY_VIBRATION, config.vibrationEnabled)
            putBoolean(KEY_SHOW_NUMBER_ROW, config.showNumberRow)
            putBoolean(KEY_SHOW_KEY_SYMBOLS, config.showKeySymbols)
            putBoolean(KEY_ENABLE_LONG_PRESS, config.enableLongPressSymbols)
            putBoolean(KEY_SHOW_SUGGESTION_BAR, config.showSuggestionBar)
            putBoolean(KEY_AUTO_SPACE, config.autoSpaceAfterWord)
            putInt(KEY_KEY_HEIGHT, config.keyHeightDp)
            putString(KEY_KEY_SHAPE_STYLE, config.keyShapeStyle)
            apply()
        }
    }

    fun saveCustomImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                var inSampleSize = 1
                val reqWidth = 1280
                val reqHeight = 720
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2
                    }
                }

                contentResolver.openInputStream(uri)?.use { realStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(realStream, null, decodeOptions) ?: return null

                    val targetFile = File(context.filesDir, "custom_keyboard_bg.jpg")
                    FileOutputStream(targetFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    targetFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadCustomImageBitmap(path: String?): ImageBitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteCustomImage(context: Context) {
        try {
            val file = File(context.filesDir, "custom_keyboard_bg.jpg")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
