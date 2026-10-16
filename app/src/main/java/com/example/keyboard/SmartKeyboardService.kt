package com.example.keyboard

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import com.example.SmartKeyboardApp
import com.example.data.prediction.SuggestionResult
import com.example.data.repository.KeyboardRepository
import com.example.keyboard.ui.ShiftState
import com.example.keyboard.ui.SmartKeyboardView
import com.example.model.AutoTextEntry
import com.example.model.KeyboardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class SmartKeyboardService : LifecycleInputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: KeyboardRepository

    private var currentWord = StringBuilder()
    private val currentSuggestions = mutableStateListOf<String>()
    private var matchedAutoTextState = mutableStateOf<AutoTextEntry?>(null)
    private var shiftState = mutableStateOf(ShiftState.OFF)
    private var imeActionLabel = mutableStateOf("Enter")

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        repository = (applicationContext as? SmartKeyboardApp)?.repository
            ?: KeyboardRepository(applicationContext)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        setupViewTree(composeView)

        composeView.setContent {
            val settings by repository.settingsFlow.collectAsState()
            val cachedAutoTexts by repository.cachedAutoTexts.collectAsState()

            // Observe suggestions
            val suggestions = remember { currentSuggestions }
            val matchedAutoText by remember { matchedAutoTextState }
            val currentShift by remember { shiftState }
            val actionLabel by remember { imeActionLabel }

            SmartKeyboardView(
                settings = settings,
                suggestions = suggestions.toList(),
                matchedAutoText = matchedAutoText,
                shiftState = currentShift,
                imeActionLabel = actionLabel,
                onKeyPress = { text ->
                    handleKeyPress(text, settings, cachedAutoTexts)
                },
                onBackspace = {
                    handleBackspace(settings, cachedAutoTexts)
                },
                onSpace = {
                    handleSpace(settings, cachedAutoTexts)
                },
                onEnter = {
                    handleEnter(settings)
                },
                onShiftToggle = {
                    toggleShift()
                },
                onSuggestionClick = { word ->
                    applySuggestion(word, settings)
                },
                onOpenSettings = {
                    openSettingsActivity()
                },
                onSwitchIme = {
                    // Switch to next IME if supported
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        switchToNextInputMethod(false)
                    }
                }
            )
        }

        return composeView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentWord.clear()
        currentSuggestions.clear()
        matchedAutoTextState.value = null

        // Determine action label from EditorInfo
        imeActionLabel.value = when (attribute?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_GO -> "Go"
            else -> "Enter"
        }

        // Auto-capitalize first word if configured
        val settings = repository.settingsFlow.value
        if (settings.autoCapitalize) {
            shiftState.value = ShiftState.ON
        }
    }

    private fun handleKeyPress(text: String, settings: KeyboardSettings, activeAutoTexts: List<AutoTextEntry>) {
        playFeedback(settings)
        val ic: InputConnection = currentInputConnection ?: return

        // If it's punctuation, check if preceding word was an Auto-Text shortcut
        if (text in listOf(".", ",", "!", "?", ":", ";", "\n")) {
            val wordToTest = currentWord.toString().trim()
            val autoTextMatch = activeAutoTexts.firstOrNull {
                it.shortcut.equals(wordToTest, ignoreCase = true)
            }

            if (autoTextMatch != null) {
                // Delete shortcut and replace
                ic.deleteSurroundingText(wordToTest.length, 0)
                ic.commitText(autoTextMatch.replacement, 1)
                if (settings.autoSpaceAfterShortcut) {
                    ic.commitText(" ", 1)
                }
            } else if (settings.autoLearnWords && wordToTest.isNotEmpty()) {
                val singleWord = wordToTest.substringAfterLast(' ')
                if (singleWord.isNotEmpty()) {
                    serviceScope.launch {
                        repository.learnWord(singleWord)
                    }
                }
            }

            ic.commitText(text, 1)
            currentWord.clear()
            currentSuggestions.clear()
            matchedAutoTextState.value = null

            // If sentence ender, shift next char to uppercase
            if (settings.autoCapitalize && (text == "." || text == "!" || text == "?")) {
                shiftState.value = ShiftState.ON
            }
            return
        }

        // Normal character
        ic.commitText(text, 1)
        if (text.contains(" ")) {
            currentWord.clear()
        } else {
            currentWord.append(text)
        }

        // Reset single-use shift
        if (shiftState.value == ShiftState.ON) {
            shiftState.value = ShiftState.OFF
        }

        // Update word prediction
        updatePredictions(settings, activeAutoTexts)
    }

    private fun handleSpace(settings: KeyboardSettings, activeAutoTexts: List<AutoTextEntry>) {
        playFeedback(settings)
        val ic = currentInputConnection ?: return

        val wordToTest = currentWord.toString().trim()
        val autoTextMatch = activeAutoTexts.firstOrNull {
            it.shortcut.equals(wordToTest, ignoreCase = true)
        }

        if (autoTextMatch != null) {
            // Instant Auto-Text Expansion!
            ic.deleteSurroundingText(wordToTest.length, 0)
            ic.commitText(autoTextMatch.replacement, 1)
            if (settings.autoSpaceAfterShortcut) {
                ic.commitText(" ", 1)
            }
        } else {
            // Commit word to learned dictionary (ONLY single word, NEVER sentence)
            val singleWord = wordToTest.substringAfterLast(' ')
            if (singleWord.isNotEmpty()) {
                serviceScope.launch {
                    repository.learnWord(singleWord)
                }
            }
            ic.commitText(" ", 1)
        }

        currentWord.clear()
        matchedAutoTextState.value = null
        updatePredictions(settings, activeAutoTexts)
    }

    private fun handleBackspace(settings: KeyboardSettings, activeAutoTexts: List<AutoTextEntry>) {
        playFeedback(settings)
        val ic = currentInputConnection ?: return

        // 1. Commit empty text to clear any selected text (e.g. Select All)
        ic.commitText("", 1)

        val selectedText = try {
            ic.getSelectedText(0)
        } catch (e: Exception) {
            null
        }

        if (!selectedText.isNullOrEmpty()) {
            currentWord.clear()
            currentSuggestions.clear()
            matchedAutoTextState.value = null
            return
        }

        // 2. Normal backspace
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }

        val sent = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) &&
                   ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        if (!sent) {
            ic.deleteSurroundingText(1, 0)
        }

        updatePredictions(settings, activeAutoTexts)
    }

    private fun handleEnter(settings: KeyboardSettings) {
        playFeedback(settings)
        val ic = currentInputConnection ?: return

        val wordToTest = currentWord.toString().trim()
        val singleWord = wordToTest.substringAfterLast(' ')
        if (singleWord.isNotEmpty()) {
            serviceScope.launch { repository.learnWord(singleWord) }
        }

        currentWord.clear()
        currentSuggestions.clear()
        matchedAutoTextState.value = null

        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO) {
            val handled = ic.performEditorAction(action)
            if (!handled) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        } else {
            // Always insert newline (\n) so cursor moves down to new line in WhatsApp and other chat apps
            ic.commitText("\n", 1)
        }
    }

    private fun toggleShift() {
        shiftState.value = when (shiftState.value) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    private fun applySuggestion(replacement: String, settings: KeyboardSettings) {
        playFeedback(settings)
        val ic = currentInputConnection ?: return

        val isPunctuationSymbol = replacement in listOf("?", "!", ":", "&", ",", ".")

        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText(replacement, 1)
        if (settings.autoSpaceAfterShortcut && !isPunctuationSymbol) {
            ic.commitText(" ", 1)
        }

        if (settings.autoLearnWords && replacement.isNotEmpty() && !isPunctuationSymbol) {
            serviceScope.launch {
                repository.learnWord(replacement)
            }
        }

        currentWord.clear()
        matchedAutoTextState.value = null
        val cachedAutoTexts = repository.cachedAutoTexts.value
        updatePredictions(settings, cachedAutoTexts)
    }

    private fun updatePredictions(settings: KeyboardSettings, activeAutoTexts: List<AutoTextEntry>) {
        if (!settings.predictionEnabled) {
            currentSuggestions.clear()
            matchedAutoTextState.value = null
            return
        }

        if (currentWord.isEmpty()) {
            // Symbol predictions after space
            currentSuggestions.clear()
            currentSuggestions.addAll(listOf("?", "!", ":", "&", ",", "."))
            matchedAutoTextState.value = null
            return
        }

        val prefix = currentWord.toString()
        val result: SuggestionResult = repository.predictionEngine.getSuggestions(prefix, activeAutoTexts)

        currentSuggestions.clear()
        currentSuggestions.addAll(result.suggestions)
        matchedAutoTextState.value = result.matchedAutoText
    }

    private fun playFeedback(settings: KeyboardSettings) {
        // Haptic Feedback
        if (settings.vibrateOnPress && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(
                            settings.vibrationDurationMs.toLong().coerceAtLeast(5L),
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(settings.vibrationDurationMs.toLong().coerceAtLeast(5L))
                }
            } catch (e: Exception) {
                // Ignore vibration failure
            }
        }

        // Sound Feedback
        if (settings.soundOnPress && audioManager != null) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, settings.soundVolume)
            } catch (e: Exception) {
                // Ignore audio failure
            }
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
