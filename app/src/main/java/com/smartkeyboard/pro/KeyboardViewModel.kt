package com.smartkeyboard.pro

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class KeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    var themeConfig by mutableStateOf(ThemeManager.loadConfig(context))
        private set

    var customImageBitmap by mutableStateOf<ImageBitmap?>(
        ThemeManager.loadCustomImageBitmap(themeConfig.customImagePath)
    )
        private set

    val predefinedThemes = ThemeManager.predefinedThemes

    // Clipboard snippets
    val clipboardItems = mutableStateListOf(
        "Halo! Terima kasih sudah menghubungi.",
        "Jl. Sudirman No. 123, Jakarta Selatan",
        "rafael.aryanto@gmail.com",
        "Terima kasih atas informasinya! 🙏",
        "https://github.com/ekofebri01/SmartKeyboardPro"
    )

    // Update Checker State
    var repoOwner by mutableStateOf("ekofebri01")
    var repoName by mutableStateOf("SmartKeyboardPro")
    var isCheckingUpdate by mutableStateOf(false)
    var updateResult by mutableStateOf<UpdateResult?>(null)

    // In-App Download & Install State
    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    private var downloadJob: Job? = null

    init {
        // Clean up any stale update files on launch
        UpdateChecker.cleanupUpdateFiles(context)
    }

    fun selectPredefinedTheme(theme: KeyboardTheme) {
        val newConfig = themeConfig.copy(
            themeId = theme.id,
            backgroundColor = theme.backgroundColor,
            keyColor = theme.keyColor,
            textColor = theme.textColor,
            accentColor = theme.accentColor
        )
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun applyCustomImage(uri: Uri) {
        viewModelScope.launch {
            val savedPath = ThemeManager.saveCustomImageFromUri(context, uri)
            if (savedPath != null) {
                val newConfig = themeConfig.copy(
                    themeId = "custom",
                    customImagePath = savedPath
                )
                themeConfig = newConfig
                customImageBitmap = ThemeManager.loadCustomImageBitmap(savedPath)
                ThemeManager.saveConfig(context, newConfig)
            }
        }
    }

    fun removeCustomImage() {
        ThemeManager.deleteCustomImage(context)
        val defaultTheme = predefinedThemes.first()
        val newConfig = themeConfig.copy(
            themeId = defaultTheme.id,
            customImagePath = null,
            backgroundColor = defaultTheme.backgroundColor,
            keyColor = defaultTheme.keyColor,
            textColor = defaultTheme.textColor,
            accentColor = defaultTheme.accentColor
        )
        themeConfig = newConfig
        customImageBitmap = null
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateCornerRadius(radiusDp: Int) {
        val newConfig = themeConfig.copy(cornerRadiusDp = radiusDp)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyOpacity(opacity: Float) {
        val newConfig = themeConfig.copy(keyOpacity = opacity)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleSound(enabled: Boolean) {
        val newConfig = themeConfig.copy(soundEnabled = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleVibration(enabled: Boolean) {
        val newConfig = themeConfig.copy(vibrationEnabled = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleNumberRow(enabled: Boolean) {
        val newConfig = themeConfig.copy(showNumberRow = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleKeySymbols(enabled: Boolean) {
        val newConfig = themeConfig.copy(showKeySymbols = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun toggleLongPressSymbols(enabled: Boolean) {
        val newConfig = themeConfig.copy(enableLongPressSymbols = enabled)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyHeight(heightDp: Int) {
        val newConfig = themeConfig.copy(keyHeightDp = heightDp)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun updateKeyShapeStyle(style: String) {
        val radius = when (style) {
            "pill" -> 22
            "squircle" -> 14
            "sharp" -> 2
            else -> 8
        }
        val newConfig = themeConfig.copy(keyShapeStyle = style, cornerRadiusDp = radius)
        themeConfig = newConfig
        ThemeManager.saveConfig(context, newConfig)
    }

    fun addClipboardItem(text: String) {
        if (text.isNotBlank() && !clipboardItems.contains(text)) {
            clipboardItems.add(0, text)
            if (clipboardItems.size > 20) {
                clipboardItems.removeAt(clipboardItems.size - 1)
            }
        }
    }

    fun removeClipboardItem(text: String) {
        clipboardItems.remove(text)
    }

    fun checkForUpdates(currentVersion: String) {
        isCheckingUpdate = true
        updateResult = null
        downloadState = DownloadState.Idle
        viewModelScope.launch {
            val result = UpdateChecker.checkLatestRelease(
                currentVersion = currentVersion,
                repoOwner = repoOwner.trim(),
                repoName = repoName.trim()
            )
            updateResult = result
            isCheckingUpdate = false
        }
    }

    fun startInAppDownload(downloadUrl: String) {
        downloadJob?.cancel()
        downloadState = DownloadState.Downloading(0f, 0L, 0L)
        downloadJob = viewModelScope.launch {
            UpdateChecker.downloadApk(context, downloadUrl) { state ->
                downloadState = state
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadState = DownloadState.Idle
        UpdateChecker.cleanupUpdateFiles(context)
    }

    fun installUpdate(apkFile: File): Boolean {
        val launched = UpdateChecker.installApk(context, apkFile)
        if (launched) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                UpdateChecker.cleanupUpdateFiles(context)
                downloadState = DownloadState.Idle
            }
        }
        return launched
    }

    fun deleteDownloadedFile() {
        UpdateChecker.cleanupUpdateFiles(context)
        downloadState = DownloadState.Idle
    }
}
