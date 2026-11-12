package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class KeyboardMode {
    ALPHA,
    NUMBERS,
    SYMBOLS,
    EMOJI
}

class SmartKeyboardIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var currentConfig by mutableStateOf(ThemeConfig())
    private var customBgBitmap by mutableStateOf<ImageBitmap?>(null)

    private var composeView: ComposeView? = null
    private var cachedVibrator: Vibrator? = null
    private var cachedAudioManager: AudioManager? = null
    private var isFastRepeatingDelete = false

    /**
     * True while the focused editor is a password / PIN / no-personalized-learning field.
     * In that state nothing typed may be learned, suggested, expanded or synced.
     */
    private var isSensitiveInput by mutableStateOf(false)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadCurrentTheme()
    }

    override fun onCreate() {
        super.onCreate()
        cachedVibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        cachedAudioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            if (!savedStateRegistry.isRestored) {
                savedStateRegistryController.performRestore(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        getSharedPreferences("smart_keyboard_theme_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        loadCurrentTheme()
        PersonalDictionary.load(this)
        KeyboardSoundManager.init(this)
    }

    private fun loadCurrentTheme() {
        val config = ThemeManager.loadConfig(this)
        currentConfig = config
        customBgBitmap = ThemeManager.loadCustomImageBitmap(config.customImagePath)
    }

    private fun setupDecorViewOwners() {
        try {
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(this)
                decorView.setViewTreeViewModelStoreOwner(this)
                decorView.setViewTreeSavedStateRegistryOwner(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        setupDecorViewOwners()
        loadCurrentTheme()
        if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    private var initialMode by mutableStateOf(KeyboardMode.ALPHA)
    private var liveAutoTextEntries by mutableStateOf<List<AutoTextEntry>>(emptyList())
    private var composingWord by mutableStateOf("")

    private fun syncComposingWord() {
        val ic = currentInputConnection
        if (ic == null) {
            if (composingWord.isNotEmpty()) composingWord = ""
            return
        }
        try {
            val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
            if (textBefore.isEmpty()) {
                if (composingWord.isNotEmpty()) composingWord = ""
                return
            }
            val lastChar = textBefore.last()
            // If the character directly before cursor is whitespace or punctuation, no active composing word
            if (lastChar.isWhitespace() || (!lastChar.isLetterOrDigit() && lastChar != '_' && lastChar != '-')) {
                if (composingWord.isNotEmpty()) composingWord = ""
                return
            }
            val match = Regex("""([a-zA-Z0-9_\-]+)$""").find(textBefore)
            val newWord = match?.value ?: ""
            if (composingWord != newWord) {
                composingWord = newWord
            }
        } catch (e: Exception) {
            if (composingWord.isNotEmpty()) composingWord = ""
        }
    }

    private fun reloadAutoTextEntries() {
        val list = mutableListOf<AutoTextEntry>()
        try {
            val prefs = getSharedPreferences("smart_keyboard_autotext_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("autotext_items_json", null)
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val element = jsonArray.get(i)
                    if (element is org.json.JSONObject) {
                        list.add(AutoTextEntry.fromJsonObject(element))
                    } else if (element is String) {
                        list.add(AutoTextEntry.createFromLegacyString(element))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            list.addAll(DefaultAutoTexts)
        }
        liveAutoTextEntries = list
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isSensitiveInput = isSensitiveField(info)
        reloadAutoTextEntries()
        syncComposingWord()
        if (info != null) {
            val classType = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            val numericOnly = classType == InputType.TYPE_CLASS_NUMBER ||
                classType == InputType.TYPE_CLASS_PHONE ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            initialMode = if (numericOnly) KeyboardMode.NUMBERS else KeyboardMode.ALPHA
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Never carry a sensitive-field flag (or a half typed word) into the next field.
        isSensitiveInput = false
        composingWord = ""
    }

    /**
     * Delegates to [InputFieldClassifier] so the sensitive-field rules stay unit-testable.
     */
    private fun isSensitiveField(info: android.view.inputmethod.EditorInfo?): Boolean =
        InputFieldClassifier.isSensitiveField(info)

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (isFastRepeatingDelete) return
        syncComposingWord()
    }

    private fun handleDelete(isRepeat: Boolean = false) {
        val ic = currentInputConnection ?: return

        if (!isRepeat) {
            playDeleteFeedback()

            // 1. If text is selected (highlighted text, sentence selection, or Select All), commit empty string to delete it
            val selectedText = try {
                ic.getSelectedText(0)
            } catch (e: Exception) {
                null
            }

            if (!selectedText.isNullOrEmpty()) {
                ic.commitText("", 1)
                syncComposingWord()
                return
            }

            // 2. Secondary check for selection using ExtractedText if editor didn't return text in getSelectedText
            val extracted = try {
                ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            } catch (e: Exception) {
                null
            }

            if (extracted != null && extracted.selectionStart != extracted.selectionEnd) {
                ic.commitText("", 1)
                syncComposingWord()
                return
            }

            // 3. Direct IME deletion of 1 code point or character before cursor
            var deleted = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    deleted = ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    deleted = ic.deleteSurroundingText(1, 0)
                }
            } catch (e: Exception) {
                deleted = false
            }

            // 4. Fallback if deleteSurroundingText wasn't processed directly
            if (!deleted) {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
                try {
                    ic.sendKeyEvent(down)
                    ic.sendKeyEvent(up)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            syncComposingWord()
        } else {
            // Fast repeat delete: zero audio lag, zero heavy selection IPC calls, smooth cadence
            isFastRepeatingDelete = true
            if (currentConfig.vibrationEnabled) {
                composeView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            var deleted = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    deleted = ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    deleted = ic.deleteSurroundingText(1, 0)
                }
            } catch (e: Exception) {
                deleted = false
            }
            if (!deleted) {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
                try {
                    ic.sendKeyEvent(down)
                    ic.sendKeyEvent(up)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            if (composingWord.isNotEmpty()) {
                composingWord = composingWord.dropLast(1)
            }
        }
    }

    private fun handleDeleteComplete() {
        isFastRepeatingDelete = false
        syncComposingWord()
    }

    override fun onCreateInputView(): View {
        setupDecorViewOwners()
        reloadAutoTextEntries()
        syncComposingWord()

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@SmartKeyboardIME))
            setViewTreeLifecycleOwner(this@SmartKeyboardIME)
            setViewTreeViewModelStoreOwner(this@SmartKeyboardIME)
            setViewTreeSavedStateRegistryOwner(this@SmartKeyboardIME)

            setContent {
                SmartKeyboardView(
                    config = currentConfig,
                    bgBitmap = customBgBitmap,
                    initialMode = initialMode,
                    customAutoTextEntries = liveAutoTextEntries,
                    composingWord = composingWord,
                    isSensitiveInput = isSensitiveInput,
                    onKeyPress = { char ->
                        playFeedback(this)
                        currentInputConnection?.commitText(char, 1)
                        if (char.length == 1 && (char[0].isLetterOrDigit() || char[0] == '_' || char[0] == '-')) {
                            composingWord += char
                        } else {
                            composingWord = ""
                        }
                    },
                    onBackspace = { isRepeat ->
                        handleDelete(isRepeat)
                    },
                    onBackspaceComplete = {
                        handleDeleteComplete()
                    },
                    onSpace = {
                        playFeedback(this)
                        val ic = currentInputConnection
                        var autoTextExpanded = false
                        // Auto-text expansion stays off in password / PIN fields.
                        if (ic != null && !isSensitiveInput) {
                            try {
                                val beforeCursor = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
                                val match = Regex("""([a-zA-Z0-9_\-]+)$""").find(beforeCursor)
                                if (match != null) {
                                    val token = match.value
                                    val matchedEntry = liveAutoTextEntries.firstOrNull { it.shortcut.equals(token, ignoreCase = true) }
                                    if (matchedEntry != null) {
                                        ic.deleteSurroundingText(token.length, 0)
                                        ic.commitText(matchedEntry.fullText + " ", 1)
                                        autoTextExpanded = true
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (!autoTextExpanded) {
                            if (!isSensitiveInput && composingWord.length >= 3 && composingWord.all { it.isLetter() }) {
                                PersonalDictionary.learnWord(this@SmartKeyboardIME, composingWord)
                            }
                            currentInputConnection?.commitText(" ", 1)
                        }
                        syncComposingWord()
                    },
                    onEnter = {
                        playFeedback(this)
                        if (!isSensitiveInput && composingWord.length >= 3 && composingWord.all { it.isLetter() }) {
                            PersonalDictionary.learnWord(this@SmartKeyboardIME, composingWord)
                        }
                        performEnterAction()
                        syncComposingWord()
                    },
                    onSuggestionSelected = { word, charsToDelete ->
                        playFeedback(this)
                        if (charsToDelete > 0) {
                            currentInputConnection?.deleteSurroundingText(charsToDelete, 0)
                        }
                        val textToCommit = if (word.endsWith(" ")) {
                            word
                        } else if (currentConfig.autoSpaceAfterWord) {
                            "$word "
                        } else {
                            word
                        }
                        currentInputConnection?.commitText(textToCommit, 1)
                        syncComposingWord()
                    },
                    onOpenSettings = {
                        playFeedback(this)
                        try {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }
        composeView = view
        return view
    }

    private fun playFeedback(view: View? = composeView) {
        if (currentConfig.vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    cachedVibrator?.vibrate(VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    cachedVibrator?.vibrate(15L)
                }
            } catch (e: Exception) {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (currentConfig.soundEnabled) {
            KeyboardSoundManager.playKeySound(
                profile = currentConfig.soundProfile,
                isDelete = false,
                audioManager = cachedAudioManager
            )
        }
    }

    private fun playDeleteFeedback(view: View? = composeView) {
        if (currentConfig.vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    cachedVibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    cachedVibrator?.vibrate(18L)
                }
            } catch (e: Exception) {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (currentConfig.soundEnabled) {
            KeyboardSoundManager.playKeySound(
                profile = currentConfig.soundProfile,
                isDelete = true,
                audioManager = cachedAudioManager
            )
        }
    }

    /**
     * Honours the editor's imeOptions (Search / Send / Next / Done ...) instead of always
     * sending KEYCODE_ENTER, which did nothing useful in search and URL fields.
     */
    private fun performEnterAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val imeOptions = info?.imeOptions ?: 0
        val action = imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
        val supportsEditorAction =
            imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0

        val handled = supportsEditorAction && when (action) {
            android.view.inputmethod.EditorInfo.IME_ACTION_GO,
            android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,
            android.view.inputmethod.EditorInfo.IME_ACTION_SEND,
            android.view.inputmethod.EditorInfo.IME_ACTION_NEXT,
            android.view.inputmethod.EditorInfo.IME_ACTION_DONE,
            android.view.inputmethod.EditorInfo.IME_ACTION_PREVIOUS -> ic.performEditorAction(action)

            else -> false
        }

        if (!handled) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun onDestroy() {
        try {
            getSharedPreferences("smart_keyboard_theme_prefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartKeyboardView(
    config: ThemeConfig,
    bgBitmap: ImageBitmap?,
    initialMode: KeyboardMode = KeyboardMode.ALPHA,
    customAutoTextEntries: List<AutoTextEntry>? = null,
    composingWord: String = "",
    isSensitiveInput: Boolean = false,
    onKeyPress: (String) -> Unit,
    onBackspace: (Boolean) -> Unit,
    onBackspaceComplete: () -> Unit = {},
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onSuggestionSelected: (String, Int) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(initialMode) }
    androidx.compose.runtime.LaunchedEffect(initialMode) {
        mode = initialMode
    }
    var isShifted by remember { mutableStateOf(false) }
    var dictRevision by remember { mutableStateOf(0) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isSplit = isLandscape && config.splitKeyboardLandscape

    val fallbackAutoTextEntries = remember(context) {
        val list = mutableListOf<AutoTextEntry>()
        try {
            val prefs = context.getSharedPreferences("smart_keyboard_autotext_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("autotext_items_json", null)
            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val element = jsonArray.get(i)
                    if (element is org.json.JSONObject) {
                        list.add(AutoTextEntry.fromJsonObject(element))
                    } else if (element is String) {
                        list.add(AutoTextEntry.createFromLegacyString(element))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            list.addAll(DefaultAutoTexts)
        }
        list
    }

    val autoTextEntries = customAutoTextEntries ?: fallbackAutoTextEntries

    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchingAutoTexts by remember { mutableStateOf<List<AutoTextEntry>>(emptyList()) }

    LaunchedEffect(composingWord, config.showSuggestionBar, dictRevision, autoTextEntries, isSensitiveInput) {
        if (isSensitiveInput || !config.showSuggestionBar || composingWord.isEmpty()) {
            suggestions = emptyList()
            matchingAutoTexts = emptyList()
            return@LaunchedEffect
        }

        // Debounce: If typing fast, wait 35ms so keystrokes remain instantaneous with zero frame-drop
        if (composingWord.length > 1) {
            delay(35L)
        }

        val autoMatches = withContext(Dispatchers.Default) {
            val query = composingWord.trim().lowercase()
            if (query.isNotEmpty() && autoTextEntries.isNotEmpty()) {
                autoTextEntries.filter { entry ->
                    entry.shortcut.lowercase().startsWith(query) ||
                    entry.shortcut.lowercase().contains(query) ||
                    entry.fullText.lowercase().contains(query)
                }.sortedBy { entry ->
                    when {
                        entry.shortcut.lowercase() == query -> 0
                        entry.shortcut.lowercase().startsWith(query) -> 1
                        entry.shortcut.lowercase().contains(query) -> 2
                        else -> 3
                    }
                }
            } else {
                emptyList()
            }
        }

        val dictMatches = withContext(Dispatchers.Default) {
            PersonalDictionary.getSuggestions(context, composingWord, limit = 4)
        }

        matchingAutoTexts = autoMatches
        suggestions = dictMatches
    }

    val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val row1Alpha = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2Alpha = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3Alpha = listOf("z", "x", "c", "v", "b", "n", "m")

    val row1Num = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row2Num = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
    val row3Num = listOf("*", "\"", "'", ":", ";", "!", "?")

    val emojiCategories = remember {
        listOf(
            "😀" to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️",
                "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗",
                "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓",
                "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕",
                "🙁", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳",
                "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭",
                "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😴", "🤤"
            ),
            "👋" to listOf(
                "👍", "👎", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
                "🤳", "💪", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                "👆", "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌",
                "🤏", "🫳", "🫴", "🫵", "👂", "👃", "🧠", "👀", "👁️", "👅"
            ),
            "❤️" to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
                "💟", "💌", "💋", "🫂", "🌹", "🌸", "💐", "🌺", "🌷", "🌻",
                "✨", "⭐", "🌟", "💫", "🔥", "🌈", "☀️", "🌙", "☁️", "⚡"
            ),
            "🎉" to listOf(
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "👑", "💎",
                "🚀", "💡", "💰", "💵", "📱", "💻", "🔔", "🎵", "🎶", "☕",
                "🍕", "🍔", "🍟", "🍻", "🥂", "🎂", "🍰", "🍦", "🍿", "🍩",
                "🍪", "🍫", "🍬", "🍭", "⚽", "🏀", "🎮", "🎲", "🎯", "💯"
            ),
            "🐱" to listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
                "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦆", "🦅",
                "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌",
                "🐞", "🐢", "🐍", "🐙", "🐬", "🐳", "🦈", "🐘", "🦒", "🦩"
            )
        )
    }
    var selectedCategoryIndex by remember { mutableStateOf(0) }

    // Secondary symbols mappings
    val row1Symbols = if (config.showNumberRow) {
        listOf("!", "@", "#", "$", "%", "&", "*", "(", ")", "-")
    } else {
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    }

    val row2Symbols = if (config.showNumberRow) {
        listOf("+", "=", "_", "/", "\\", ":", ";", "\"", "'")
    } else {
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
    }

    val row3Symbols = if (config.showNumberRow) {
        listOf("~", "`", "<", ">", "[", "]", "?")
    } else {
        listOf("*", "\"", "'", ":", ";", "!", "?")
    }

    val effectiveKeyColor = config.keyColor.copy(alpha = config.keyOpacity.coerceIn(0.1f, 1.0f))
    val actionKeyColor = if (config.customImagePath != null) {
        config.backgroundColor.copy(alpha = (config.keyOpacity * 1.1f).coerceIn(0.2f, 1.0f))
    } else {
        config.backgroundColor.copy(alpha = 0.85f)
    }

    val effectiveRadius = when (config.keyShapeStyle) {
        "pill" -> config.keyHeightDp / 2
        "squircle" -> 14
        "sharp" -> 0
        "cut_corner" -> config.cornerRadiusDp.coerceAtLeast(6)
        else -> config.cornerRadiusDp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(config.backgroundColor)
    ) {
        // Preset background pattern/gradient if configured
        if (config.backgroundPattern != "none") {
            KeyboardBackgroundPattern(
                pattern = config.backgroundPattern,
                baseColor = config.backgroundColor,
                modifier = Modifier.matchParentSize()
            )
        }

        // Custom photo background if active
        if (bgBitmap != null && config.customImagePath != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = "Custom Keyboard Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Suggestion Bar (Kamus Pribadi & Auto Text Expander & Persistent Settings)
            if (config.showSuggestionBar && mode == KeyboardMode.ALPHA && !isSensitiveInput) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    color = actionKeyColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (matchingAutoTexts.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(matchingAutoTexts, key = { it.id }) { entry ->
                                        val charsToDelete = composingWord.length
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(config.accentColor.copy(alpha = 0.25f))
                                                .clickable {
                                                    onSuggestionSelected(entry.fullText, charsToDelete)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "⚡ ${entry.shortcut}",
                                                color = config.accentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "→",
                                                color = config.textColor.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = entry.fullText,
                                                color = config.textColor,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    items(suggestions) { suggestion ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(config.keyColor.copy(alpha = 0.5f))
                                                .clickable {
                                                    val charsToDelete = composingWord.length
                                                    PersonalDictionary.learnWord(context, suggestion)
                                                    onSuggestionSelected(suggestion, charsToDelete)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = suggestion,
                                                color = config.textColor,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else if (suggestions.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    suggestions.forEachIndexed { idx, suggestion ->
                                        val isPrimary = idx == 0 || (suggestions.size > 1 && idx == 1)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(vertical = 3.dp, horizontal = 2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isPrimary) config.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                                .combinedClickable(
                                                    onClick = {
                                                        val charsToDelete = composingWord.length
                                                        PersonalDictionary.learnWord(context, suggestion)
                                                        onSuggestionSelected(suggestion, charsToDelete)
                                                    },
                                                    onLongClick = {
                                                        PersonalDictionary.removeWord(context, suggestion)
                                                        dictRevision++
                                                        Toast.makeText(context, "Kata '$suggestion' telah dihapus dari kamus", Toast.LENGTH_SHORT).show()
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = suggestion,
                                                color = if (isPrimary) config.accentColor else config.textColor,
                                                fontSize = 13.sp,
                                                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else if (autoTextEntries.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(config.accentColor.copy(alpha = 0.22f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "⚡ Auto",
                                                color = config.accentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    items(autoTextEntries, key = { it.id }) { entry ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(config.keyColor.copy(alpha = 0.5f))
                                                .clickable {
                                                    onSuggestionSelected(entry.fullText, 0)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "[${entry.shortcut}]",
                                                color = config.accentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = entry.fullText,
                                                color = config.textColor,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "💡 Ketik kata/pintasan untuk prediksi",
                                    color = config.textColor.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Persistent Settings Button in Suggestion Bar
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(config.accentColor.copy(alpha = 0.2f))
                                .clickable(onClick = onOpenSettings),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan",
                                tint = config.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            when (mode) {
                KeyboardMode.ALPHA -> {
                    // Optional Top Number Row
                    if (config.showNumberRow) {
                        if (isSplit) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("1", "2", "3", "4", "5").forEach { num ->
                                        ThemedKeyButton(
                                            text = num,
                                            secondarySymbol = null,
                                            showSecondarySymbol = false,
                                            fontSizeSp = config.keyFontSizeSp,
                                            keyColor = effectiveKeyColor.copy(alpha = (config.keyOpacity * 0.85f).coerceIn(0.1f, 1f)),
                                            textColor = config.textColor,
                                            cornerRadiusDp = effectiveRadius,
                                            keyHeightDp = (config.keyHeightDp - 6).coerceAtLeast(34),
                                            modifier = Modifier.weight(1f),
                                            onClick = { onKeyPress(num) },
                                            onLongClick = { onKeyPress(num) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(24.dp))
                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("6", "7", "8", "9", "0").forEach { num ->
                                        ThemedKeyButton(
                                            text = num,
                                            secondarySymbol = null,
                                            showSecondarySymbol = false,
                                            fontSizeSp = config.keyFontSizeSp,
                                            keyColor = effectiveKeyColor.copy(alpha = (config.keyOpacity * 0.85f).coerceIn(0.1f, 1f)),
                                            textColor = config.textColor,
                                            cornerRadiusDp = effectiveRadius,
                                            keyHeightDp = (config.keyHeightDp - 6).coerceAtLeast(34),
                                            modifier = Modifier.weight(1f),
                                            onClick = { onKeyPress(num) },
                                            onLongClick = { onKeyPress(num) }
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                numberRow.forEach { num ->
                                    ThemedKeyButton(
                                        text = num,
                                        secondarySymbol = null,
                                        showSecondarySymbol = false,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor.copy(alpha = (config.keyOpacity * 0.85f).coerceIn(0.1f, 1f)),
                                        textColor = config.textColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = (config.keyHeightDp - 6).coerceAtLeast(34),
                                        modifier = Modifier.weight(1f),
                                        onClick = { onKeyPress(num) },
                                        onLongClick = { onKeyPress(num) }
                                    )
                                }
                            }
                        }
                    }

                    // Row 1 (Letters + Secondary Symbols)
                    if (isSplit) {
                        val leftKeys = listOf("q", "w", "e", "r", "t")
                        val rightKeys = listOf("y", "u", "i", "o", "p")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                leftKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row1Symbols.getOrNull(index)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rightKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row1Symbols.getOrNull(index + 5)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row1Alpha.forEachIndexed { index, key ->
                                val text = if (isShifted) key.uppercase() else key
                                val symbol = row1Symbols.getOrNull(index)
                                ThemedKeyButton(
                                    text = text,
                                    secondarySymbol = symbol,
                                    showSecondarySymbol = config.showKeySymbols,
                                    fontSizeSp = config.keyFontSizeSp,
                                    keyColor = effectiveKeyColor,
                                    textColor = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = config.keyHeightDp,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onKeyPress(text)
                                        if (isShifted) isShifted = false
                                    },
                                    onLongClick = {
                                        if (config.enableLongPressSymbols && symbol != null) {
                                            onKeyPress(symbol)
                                        } else {
                                            onKeyPress(text)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Row 2 (Letters + Secondary Symbols)
                    if (isSplit) {
                        val leftKeys = listOf("a", "s", "d", "f", "g")
                        val rightKeys = listOf("h", "j", "k", "l")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                leftKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row2Symbols.getOrNull(index)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rightKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row2Symbols.getOrNull(index + 5)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row2Alpha.forEachIndexed { index, key ->
                                val text = if (isShifted) key.uppercase() else key
                                val symbol = row2Symbols.getOrNull(index)
                                ThemedKeyButton(
                                    text = text,
                                    secondarySymbol = symbol,
                                    showSecondarySymbol = config.showKeySymbols,
                                    fontSizeSp = config.keyFontSizeSp,
                                    keyColor = effectiveKeyColor,
                                    textColor = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = config.keyHeightDp,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onKeyPress(text)
                                        if (isShifted) isShifted = false
                                    },
                                    onLongClick = {
                                        if (config.enableLongPressSymbols && symbol != null) {
                                            onKeyPress(symbol)
                                        } else {
                                            onKeyPress(text)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Row 3 (Shift, letters, Backspace)
                    if (isSplit) {
                        val leftKeys = listOf("z", "x", "c", "v")
                        val rightKeys = listOf("b", "n", "m")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ThemedActionKeyButton(
                                    text = if (isShifted) "⇪" else "⇧",
                                    isHighlighted = isShifted,
                                    accentColor = config.accentColor,
                                    defaultBg = actionKeyColor,
                                    defaultText = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = config.keyHeightDp,
                                    modifier = Modifier.weight(1.2f),
                                    onClick = { isShifted = !isShifted }
                                )
                                leftKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row3Symbols.getOrNull(index)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rightKeys.forEachIndexed { index, key ->
                                    val text = if (isShifted) key.uppercase() else key
                                    val symbol = row3Symbols.getOrNull(index + 4)
                                    ThemedKeyButton(
                                        text = text,
                                        secondarySymbol = symbol,
                                        showSecondarySymbol = config.showKeySymbols,
                                        fontSizeSp = config.keyFontSizeSp,
                                        keyColor = effectiveKeyColor,
                                        textColor = config.textColor,
                                        symbolColor = config.accentColor,
                                        cornerRadiusDp = effectiveRadius,
                                        keyHeightDp = config.keyHeightDp,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onKeyPress(text)
                                            if (isShifted) isShifted = false
                                        },
                                        onLongClick = {
                                            if (config.enableLongPressSymbols && symbol != null) {
                                                onKeyPress(symbol)
                                            } else {
                                                onKeyPress(text)
                                            }
                                        }
                                    )
                                }
                                RepeatingDeleteKeyButton(
                                    text = "⌫",
                                    accentColor = config.accentColor,
                                    defaultBg = actionKeyColor,
                                    defaultText = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = config.keyHeightDp,
                                    modifier = Modifier.weight(1.3f),
                                    onDelete = onBackspace,
                                    onDeleteComplete = onBackspaceComplete
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemedActionKeyButton(
                                text = if (isShifted) "⇪" else "⇧",
                                isHighlighted = isShifted,
                                accentColor = config.accentColor,
                                defaultBg = actionKeyColor,
                                defaultText = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1.5f),
                                onClick = { isShifted = !isShifted }
                            )

                            row3Alpha.forEachIndexed { index, key ->
                                val text = if (isShifted) key.uppercase() else key
                                val symbol = row3Symbols.getOrNull(index)
                                ThemedKeyButton(
                                    text = text,
                                    secondarySymbol = symbol,
                                    showSecondarySymbol = config.showKeySymbols,
                                    fontSizeSp = config.keyFontSizeSp,
                                    keyColor = effectiveKeyColor,
                                    textColor = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = config.keyHeightDp,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onKeyPress(text)
                                        if (isShifted) isShifted = false
                                    },
                                    onLongClick = {
                                        if (config.enableLongPressSymbols && symbol != null) {
                                            onKeyPress(symbol)
                                        } else {
                                            onKeyPress(text)
                                        }
                                    }
                                )
                            }

                            RepeatingDeleteKeyButton(
                                text = "⌫",
                                accentColor = config.accentColor,
                                defaultBg = actionKeyColor,
                                defaultText = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1.5f),
                                onDelete = onBackspace,
                                onDeleteComplete = onBackspaceComplete
                            )
                        }
                    }
                }

                KeyboardMode.NUMBERS, KeyboardMode.SYMBOLS -> {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row1Num.forEach { key ->
                            ThemedKeyButton(
                                text = key,
                                secondarySymbol = null,
                                showSecondarySymbol = false,
                                fontSizeSp = config.keyFontSizeSp,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) },
                                onLongClick = { onKeyPress(key) }
                            )
                        }
                    }

                    // Row 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row2Num.forEach { key ->
                            ThemedKeyButton(
                                text = key,
                                secondarySymbol = null,
                                showSecondarySymbol = false,
                                fontSizeSp = config.keyFontSizeSp,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) },
                                onLongClick = { onKeyPress(key) }
                            )
                        }
                    }

                    // Row 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row3Num.forEach { key ->
                            ThemedKeyButton(
                                text = key,
                                secondarySymbol = null,
                                showSecondarySymbol = false,
                                fontSizeSp = config.keyFontSizeSp,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) },
                                onLongClick = { onKeyPress(key) }
                            )
                        }
                        RepeatingDeleteKeyButton(
                            text = "⌫",
                            accentColor = config.accentColor,
                            defaultBg = actionKeyColor,
                            defaultText = config.textColor,
                            cornerRadiusDp = effectiveRadius,
                            keyHeightDp = config.keyHeightDp,
                            modifier = Modifier.weight(1.5f),
                            onDelete = onBackspace,
                            onDeleteComplete = onBackspaceComplete
                        )
                    }
                }

                KeyboardMode.EMOJI -> {
                    // Category tab row + Quick backspace key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((config.keyHeightDp - 6).coerceAtLeast(36).dp)
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(emojiCategories) { index, (catIcon, _) ->
                                val isSelected = selectedCategoryIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) config.accentColor.copy(alpha = 0.3f) else actionKeyColor)
                                        .clickable { selectedCategoryIndex = index }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = catIcon,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        RepeatingDeleteKeyButton(
                            text = "⌫",
                            accentColor = config.accentColor,
                            defaultBg = actionKeyColor,
                            defaultText = config.textColor,
                            cornerRadiusDp = effectiveRadius,
                            keyHeightDp = (config.keyHeightDp - 6).coerceAtLeast(36),
                            modifier = Modifier.width(52.dp),
                            onDelete = onBackspace,
                            onDeleteComplete = onBackspaceComplete
                        )
                    }

                    // Emoji Grid with 7 columns and smooth scrolling
                    val currentCategoryEmojis = emojiCategories.getOrNull(selectedCategoryIndex)?.second ?: emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((config.keyHeightDp * 3) + 8).dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentCategoryEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .height(config.keyHeightDp.dp)
                                    .clip(getKeyShape(config.keyShapeStyle, effectiveRadius))
                                    .background(effectiveKeyColor)
                                    .clickable { onKeyPress(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom control row: Mode toggle, Space, Period/Comma, Enter (Smaller Enter, long-press Enter for Emoji)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemedActionKeyButton(
                    text = if (mode == KeyboardMode.ALPHA) "?123" else "ABC",
                    accentColor = config.accentColor,
                    defaultBg = actionKeyColor,
                    defaultText = config.textColor,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        mode = if (mode == KeyboardMode.ALPHA) KeyboardMode.NUMBERS else KeyboardMode.ALPHA
                    },
                    onLongClick = onOpenSettings
                )

                // Space bar (generous width with instant response)
                val currentOnSpace by rememberUpdatedState(onSpace)
                val currentOnSuggestionSelected by rememberUpdatedState(onSuggestionSelected)
                var isSpacePressed by remember { mutableStateOf(false) }
                val spaceBg = if (isSpacePressed) effectiveKeyColor.copy(alpha = (effectiveKeyColor.alpha + 0.25f).coerceAtMost(1f)) else effectiveKeyColor

                Box(
                    modifier = Modifier
                        .weight(3.6f)
                        .height(config.keyHeightDp.dp)
                        .clip(getKeyShape(config.keyShapeStyle, effectiveRadius))
                        .background(spaceBg)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isSpacePressed = true
                                waitForUpOrCancellation()
                                isSpacePressed = false

                                val trimmedWord = composingWord.trim()
                                val matchedAutoText = if (trimmedWord.isNotEmpty()) {
                                    autoTextEntries.firstOrNull { it.shortcut.equals(trimmedWord, ignoreCase = true) }
                                } else null

                                if (matchedAutoText != null) {
                                    val charsToDelete = composingWord.length
                                    currentOnSuggestionSelected(matchedAutoText.fullText + " ", charsToDelete)
                                } else {
                                    currentOnSpace()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Space",
                        color = config.textColor.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Period key with long-press comma
                ThemedKeyButton(
                    text = ".",
                    secondarySymbol = ",",
                    showSecondarySymbol = true,
                    fontSizeSp = config.keyFontSizeSp,
                    keyColor = effectiveKeyColor,
                    textColor = config.textColor,
                    symbolColor = config.accentColor,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1.0f),
                    onClick = {
                        onKeyPress(".")
                    },
                    onLongClick = {
                        onKeyPress(",")
                    }
                )

                // Enter key (smaller, long-press for Emoji menu)
                ThemedActionKeyButton(
                    accentColor = config.accentColor,
                    defaultBg = config.accentColor,
                    defaultText = Color.White,
                    isHighlighted = true,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1.3f),
                    onClick = onEnter,
                    onLongClick = {
                        mode = KeyboardMode.EMOJI
                    },
                    icon = {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val strokeWidth = 2.8.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val returnPath = Path().apply {
                                moveTo(w * 0.76f, h * 0.28f)
                                lineTo(w * 0.76f, h * 0.65f)
                                lineTo(w * 0.22f, h * 0.65f)
                            }
                            drawPath(
                                path = returnPath,
                                color = Color.White,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            val arrowHead = Path().apply {
                                moveTo(w * 0.44f, h * 0.43f)
                                lineTo(w * 0.22f, h * 0.65f)
                                lineTo(w * 0.44f, h * 0.87f)
                            }
                            drawPath(
                                path = arrowHead,
                                color = Color.White,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ThemedKeyButton(
    text: String,
    secondarySymbol: String?,
    showSecondarySymbol: Boolean,
    fontSizeSp: Int = 17,
    keyColor: Color,
    textColor: Color,
    symbolColor: Color = textColor,
    keyShapeStyle: String = "rounded",
    cornerRadiusDp: Int,
    keyHeightDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val hasLongClick = showSecondarySymbol && secondarySymbol != null
    val shape = getKeyShape(keyShapeStyle, cornerRadiusDp)
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) keyColor.copy(alpha = (keyColor.alpha + 0.25f).coerceAtMost(1f)) else keyColor

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(shape)
            .background(bg)
            .pointerInput(hasLongClick) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    if (hasLongClick) {
                        try {
                            withTimeout(380L) {
                                waitForUpOrCancellation()
                            }
                            currentOnClick()
                        } catch (e: PointerEventTimeoutCancellationException) {
                            currentOnLongClick()
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Small secondary symbol hint in top-right corner with high contrast color
        if (showSecondarySymbol && secondarySymbol != null) {
            Text(
                text = secondarySymbol,
                color = symbolColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }

        // Main key character
        Text(
            text = text,
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun ThemedActionIconButton(
    modifier: Modifier = Modifier,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    keyShapeStyle: String = "rounded",
    cornerRadiusDp: Int,
    keyHeightDp: Int = 44,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val hasDistinctLongClick = onLongClick != onClick
    val shape = getKeyShape(keyShapeStyle, cornerRadiusDp)
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) accentColor.copy(alpha = 0.35f) else defaultBg

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(shape)
            .background(bg)
            .pointerInput(hasDistinctLongClick) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    if (hasDistinctLongClick) {
                        try {
                            withTimeout(380L) {
                                waitForUpOrCancellation()
                            }
                            currentOnClick()
                        } catch (e: PointerEventTimeoutCancellationException) {
                            currentOnLongClick()
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun ThemedActionKeyButton(
    text: String = "",
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    keyShapeStyle: String = "rounded",
    cornerRadiusDp: Int,
    keyHeightDp: Int = 44,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    icon: (@Composable () -> Unit)? = null
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val hasDistinctLongClick = onLongClick != onClick
    val bg = if (isHighlighted) accentColor else defaultBg
    val fg = if (isHighlighted) Color.White else defaultText
    val shape = getKeyShape(keyShapeStyle, cornerRadiusDp)
    var isPressed by remember { mutableStateOf(false) }

    val displayBg = if (isPressed) {
        if (isHighlighted) accentColor.copy(alpha = 0.8f) else accentColor.copy(alpha = 0.35f)
    } else bg

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(shape)
            .background(displayBg)
            .pointerInput(hasDistinctLongClick) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    if (hasDistinctLongClick) {
                        try {
                            withTimeout(380L) {
                                waitForUpOrCancellation()
                            }
                            currentOnClick()
                        } catch (e: PointerEventTimeoutCancellationException) {
                            currentOnLongClick()
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = text,
                color = fg,
                fontSize = if (text.length > 2) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RepeatingDeleteKeyButton(
    text: String = "⌫",
    modifier: Modifier = Modifier,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    keyShapeStyle: String = "rounded",
    cornerRadiusDp: Int,
    keyHeightDp: Int = 44,
    onDelete: (Boolean) -> Unit,
    onDeleteComplete: () -> Unit = {}
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnDeleteComplete by rememberUpdatedState(onDeleteComplete)
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Initial delay before continuous repeat starts (350ms)
            delay(350L)
            var deleteCount = 0
            // Continuous deletion while held
            while (isPressed) {
                currentOnDelete(true)
                deleteCount++
                // Accelerate after deleting multiple characters: 55ms -> 45ms
                val interval = if (deleteCount > 10) 45L else 55L
                delay(interval)
            }
        }
    }

    val bg = if (isPressed) accentColor.copy(alpha = 0.35f) else defaultBg
    val fg = if (isPressed) accentColor else defaultText
    val shape = getKeyShape(keyShapeStyle, cornerRadiusDp)

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(shape)
            .background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnDelete(false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                    currentOnDeleteComplete()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun getKeyShape(keyShapeStyle: String, cornerRadiusDp: Int): Shape {
    return when (keyShapeStyle) {
        "pill" -> CircleShape
        "squircle" -> RoundedCornerShape(14.dp)
        "sharp" -> RectangleShape
        "cut_corner" -> CutCornerShape(cornerRadiusDp.coerceAtLeast(6).dp)
        else -> RoundedCornerShape(cornerRadiusDp.dp)
    }
}

@Composable
fun KeyboardBackgroundPattern(pattern: String, baseColor: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().background(baseColor))

        when (pattern) {
            "cyber_gradient" -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E0533).copy(alpha = 0.8f),
                                    Color(0xFF0F0B24).copy(alpha = 0.85f),
                                    baseColor.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            "sunset_gradient" -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B0764).copy(alpha = 0.8f),
                                    Color(0xFF831843).copy(alpha = 0.8f),
                                    baseColor.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            "emerald_glow" -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF064E3B).copy(alpha = 0.85f),
                                    baseColor.copy(alpha = 0.9f)
                                )
                            )
                        )
                )
            }
            "neon_cyber" -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    baseColor,
                                    Color(0xFF0284C7).copy(alpha = 0.3f),
                                    baseColor
                                )
                            )
                        )
                )
            }
            "carbon_grid" -> {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val step = 16.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }
            }
            "dots_pattern" -> {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val spacing = 20.dp.toPx()
                    val radius = 1.5.dp.toPx()
                    var x = spacing / 2
                    while (x < size.width) {
                        var y = spacing / 2
                        while (y < size.height) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.1f),
                                radius = radius,
                                center = Offset(x, y)
                            )
                            y += spacing
                        }
                        x += spacing
                    }
                }
            }
        }
    }
}

