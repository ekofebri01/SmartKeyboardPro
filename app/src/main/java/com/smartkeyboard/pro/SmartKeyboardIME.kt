package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadCurrentTheme()
    }

    override fun onCreate() {
        super.onCreate()
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

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info != null) {
            val classType = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            if (classType == InputType.TYPE_CLASS_NUMBER ||
                classType == InputType.TYPE_CLASS_PHONE ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                initialMode = KeyboardMode.NUMBERS
            } else {
                initialMode = KeyboardMode.ALPHA
            }
        }
    }

    private fun handleDelete() {
        playFeedback()
        val ic = currentInputConnection ?: return

        // 1. If text is selected (highlighted text, sentence selection, or Select All), commit empty string to delete it
        val selectedText = try {
            ic.getSelectedText(0)
        } catch (e: Exception) {
            null
        }

        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
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
            return
        }

        // 3. Send standard KEYCODE_DEL key event. In Android framework TextView/EditText, KEYCODE_DEL natively
        // handles deleting selections and standard single character deletion.
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
        val sent = try {
            ic.sendKeyEvent(down) && ic.sendKeyEvent(up)
        } catch (e: Exception) {
            false
        }

        // 4. Fallback if the target editor didn't process the key event directly
        if (!sent) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            } catch (e: Exception) {
                ic.deleteSurroundingText(1, 0)
            }
        }
    }

    override fun onCreateInputView(): View {
        setupDecorViewOwners()

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
                    onKeyPress = { char ->
                        playFeedback(this)
                        currentInputConnection?.commitText(char, 1)
                    },
                    onBackspace = {
                        handleDelete()
                    },
                    onSpace = {
                        playFeedback(this)
                        currentInputConnection?.commitText(" ", 1)
                    },
                    onEnter = {
                        playFeedback(this)
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    },
                    onSuggestionSelected = { word, charsToDelete ->
                        playFeedback(this)
                        if (charsToDelete > 0) {
                            currentInputConnection?.deleteSurroundingText(charsToDelete, 0)
                        }
                        val textToCommit = if (currentConfig.autoSpaceAfterWord) "$word " else word
                        currentInputConnection?.commitText(textToCommit, 1)
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(25L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    vibrator?.vibrate(25L)
                }
            } catch (e: Exception) {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (currentConfig.soundEnabled) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.8f)
            } catch (e: Exception) {
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 90)
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 35)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
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
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
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
    var currentComposingWord by remember { mutableStateOf("") }

    val suggestions = remember(currentComposingWord, config.showSuggestionBar) {
        if (config.showSuggestionBar && currentComposingWord.isNotEmpty()) {
            PersonalDictionary.getSuggestions(context, currentComposingWord, limit = 4)
        } else {
            emptyList()
        }
    }

    val autoTextEntries = remember(context) {
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
            list.addAll(
                listOf(
                    AutoTextEntry(shortcut = "thx", fullText = "Terima kasih atas informasinya! 🙏"),
                    AutoTextEntry(shortcut = "alamat", fullText = "Jl. Sudirman No. 123, Jakarta Selatan"),
                    AutoTextEntry(shortcut = "email", fullText = "rafael.aryanto@gmail.com"),
                    AutoTextEntry(shortcut = "halo", fullText = "Halo! Terima kasih sudah menghubungi."),
                    AutoTextEntry(shortcut = "repo", fullText = "https://github.com/ekofebri01/SmartKeyboardPro")
                )
            )
        }
        list
    }

    val matchingAutoTexts = remember(currentComposingWord, autoTextEntries) {
        if (currentComposingWord.isNotEmpty()) {
            val query = currentComposingWord.trim().lowercase()
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

    val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val row1Alpha = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2Alpha = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3Alpha = listOf("z", "x", "c", "v", "b", "n", "m")

    val row1Num = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row2Num = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
    val row3Num = listOf("*", "\"", "'", ":", ";", "!", "?")

    val emojis = listOf("😊", "😂", "❤️", "👍", "🔥", "🎉", "✨", "🙏", "😎", "🚀", "💡", "💯")

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
        "sharp" -> 2
        else -> config.cornerRadiusDp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(config.backgroundColor)
    ) {
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
            // Suggestion Bar (Kamus Pribadi & Auto Text Expander)
            if (config.showSuggestionBar && mode == KeyboardMode.ALPHA) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    color = actionKeyColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (matchingAutoTexts.isNotEmpty()) {
                        // Priority: Keyword matched Auto Text!
                        LazyRow(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(matchingAutoTexts, key = { it.id }) { entry ->
                                val charsToDelete = currentComposingWord.length
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(config.accentColor.copy(alpha = 0.25f))
                                        .clickable {
                                            currentComposingWord = ""
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
                                            val charsToDelete = currentComposingWord.length
                                            PersonalDictionary.learnWord(context, suggestion)
                                            currentComposingWord = ""
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            suggestions.forEachIndexed { idx, suggestion ->
                                val isPrimary = idx == 0 || (suggestions.size > 1 && idx == 1)
                                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(vertical = 3.dp, horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isPrimary) config.accentColor.copy(alpha = 0.22f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                val charsToDelete = currentComposingWord.length
                                                PersonalDictionary.learnWord(context, suggestion)
                                                currentComposingWord = ""
                                                onSuggestionSelected(suggestion, charsToDelete)
                                            },
                                            onLongClick = {
                                                PersonalDictionary.removeWord(context, suggestion)
                                                Toast.makeText(context, "Kata '$suggestion' telah dihapus dari kamus", Toast.LENGTH_SHORT).show()
                                                currentComposingWord = ""
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = suggestion,
                                        color = if (isPrimary) config.accentColor else config.textColor,
                                        fontSize = 14.sp,
                                        fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                                if (idx < suggestions.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(14.dp)
                                            .background(config.textColor.copy(alpha = 0.2f))
                                    )
                                }
                            }
                        }
                    } else if (autoTextEntries.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
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
                                        text = "⚡ Auto Text",
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
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "💡 Ketik kata/pintasan untuk prediksi",
                                color = config.textColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Auto-Spasi: ON",
                                color = config.accentColor.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            when (mode) {
                KeyboardMode.ALPHA -> {
                    // Optional Top Number Row
                    if (config.showNumberRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            numberRow.forEach { num ->
                                ThemedKeyButton(
                                    text = num,
                                    secondarySymbol = null,
                                    showSecondarySymbol = false,
                                    keyColor = effectiveKeyColor.copy(alpha = (config.keyOpacity * 0.85f).coerceIn(0.1f, 1f)),
                                    textColor = config.textColor,
                                    cornerRadiusDp = effectiveRadius,
                                    keyHeightDp = (config.keyHeightDp - 6).coerceAtLeast(34),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (currentComposingWord.isNotBlank()) {
                                            PersonalDictionary.learnWord(context, currentComposingWord)
                                            currentComposingWord = ""
                                        }
                                        onKeyPress(num)
                                    },
                                    onLongClick = { onKeyPress(num) }
                                )
                            }
                        }
                    }

                    // Row 1 (Letters + Secondary Symbols)
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentComposingWord += text
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
                                },
                                onLongClick = {
                                    if (config.enableLongPressSymbols && symbol != null) {
                                        if (currentComposingWord.isNotBlank()) {
                                            PersonalDictionary.learnWord(context, currentComposingWord)
                                            currentComposingWord = ""
                                        }
                                        onKeyPress(symbol)
                                    } else {
                                        currentComposingWord += text
                                        onKeyPress(text)
                                    }
                                }
                            )
                        }
                    }

                    // Row 2 (Letters + Secondary Symbols)
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentComposingWord += text
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
                                },
                                onLongClick = {
                                    if (config.enableLongPressSymbols && symbol != null) {
                                        if (currentComposingWord.isNotBlank()) {
                                            PersonalDictionary.learnWord(context, currentComposingWord)
                                            currentComposingWord = ""
                                        }
                                        onKeyPress(symbol)
                                    } else {
                                        currentComposingWord += text
                                        onKeyPress(text)
                                    }
                                }
                            )
                        }
                    }

                    // Row 3 (Shift, letters, Backspace)
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentComposingWord += text
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
                                },
                                onLongClick = {
                                    if (config.enableLongPressSymbols && symbol != null) {
                                        if (currentComposingWord.isNotBlank()) {
                                            PersonalDictionary.learnWord(context, currentComposingWord)
                                            currentComposingWord = ""
                                        }
                                        onKeyPress(symbol)
                                    } else {
                                        currentComposingWord += text
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
                            onDelete = {
                                if (currentComposingWord.isNotEmpty()) {
                                    currentComposingWord = currentComposingWord.dropLast(1)
                                }
                                onBackspace()
                            }
                        )
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
                            onDelete = onBackspace
                        )
                    }
                }

                KeyboardMode.EMOJI -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        emojis.take(6).forEach { emoji ->
                            ThemedKeyButton(
                                text = emoji,
                                secondarySymbol = null,
                                showSecondarySymbol = false,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(emoji) },
                                onLongClick = { onKeyPress(emoji) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        emojis.drop(6).take(6).forEach { emoji ->
                            ThemedKeyButton(
                                text = emoji,
                                secondarySymbol = null,
                                showSecondarySymbol = false,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = effectiveRadius,
                                keyHeightDp = config.keyHeightDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(emoji) },
                                onLongClick = { onKeyPress(emoji) }
                            )
                        }
                    }
                }
            }

            // Bottom control row: Mode toggle, Emoji, Comma, Space, Period, Enter
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
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        mode = if (mode == KeyboardMode.ALPHA) KeyboardMode.NUMBERS else KeyboardMode.ALPHA
                    },
                    onLongClick = onOpenSettings
                )

                ThemedActionKeyButton(
                    text = if (mode == KeyboardMode.EMOJI) "ABC" else "😊",
                    accentColor = config.accentColor,
                    defaultBg = actionKeyColor,
                    defaultText = config.textColor,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(0.9f),
                    onClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        mode = if (mode == KeyboardMode.EMOJI) KeyboardMode.ALPHA else KeyboardMode.EMOJI
                    }
                )

                // Comma key
                ThemedKeyButton(
                    text = ",",
                    secondarySymbol = null,
                    showSecondarySymbol = false,
                    keyColor = effectiveKeyColor,
                    textColor = config.textColor,
                    symbolColor = config.accentColor,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        onKeyPress(",")
                    },
                    onLongClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        onKeyPress(",")
                    }
                )

                // Space bar
                Box(
                    modifier = Modifier
                        .weight(2.8f)
                        .height(config.keyHeightDp.dp)
                        .clip(RoundedCornerShape(effectiveRadius.dp))
                        .background(effectiveKeyColor)
                        .combinedClickable(
                            onClick = {
                                if (currentComposingWord.isNotBlank()) {
                                    PersonalDictionary.learnWord(context, currentComposingWord)
                                    currentComposingWord = ""
                                }
                                onSpace()
                            },
                            onLongClick = {
                                if (currentComposingWord.isNotBlank()) {
                                    PersonalDictionary.learnWord(context, currentComposingWord)
                                    currentComposingWord = ""
                                }
                                onSpace()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Space",
                        color = config.textColor.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Period key
                ThemedKeyButton(
                    text = ".",
                    secondarySymbol = null,
                    showSecondarySymbol = false,
                    keyColor = effectiveKeyColor,
                    textColor = config.textColor,
                    symbolColor = config.accentColor,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        onKeyPress(".")
                    },
                    onLongClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        onKeyPress(".")
                    }
                )

                ThemedActionKeyButton(
                    text = "↵",
                    accentColor = config.accentColor,
                    defaultBg = config.accentColor,
                    defaultText = Color.White,
                    isHighlighted = true,
                    cornerRadiusDp = effectiveRadius,
                    keyHeightDp = config.keyHeightDp,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        if (currentComposingWord.isNotBlank()) {
                            PersonalDictionary.learnWord(context, currentComposingWord)
                            currentComposingWord = ""
                        }
                        onEnter()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemedKeyButton(
    text: String,
    secondarySymbol: String?,
    showSecondarySymbol: Boolean,
    keyColor: Color,
    textColor: Color,
    symbolColor: Color = textColor,
    cornerRadiusDp: Int,
    keyHeightDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(keyColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemedActionKeyButton(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    cornerRadiusDp: Int,
    keyHeightDp: Int = 44,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick
) {
    val bg = if (isHighlighted) accentColor else defaultBg
    val fg = if (isHighlighted) Color.White else defaultText

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RepeatingDeleteKeyButton(
    text: String = "⌫",
    modifier: Modifier = Modifier,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    cornerRadiusDp: Int,
    keyHeightDp: Int = 44,
    onDelete: () -> Unit
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // First immediate deletion on touch down
            currentOnDelete()
            // Initial delay before continuous repeat starts (400ms)
            delay(400L)
            var deleteCount = 0
            // Continuous deletion while held
            while (isPressed) {
                currentOnDelete()
                deleteCount++
                // Accelerate after deleting multiple characters
                val interval = if (deleteCount > 12) 35L else 55L
                delay(interval)
            }
        }
    }

    val bg = if (isPressed) accentColor.copy(alpha = 0.35f) else defaultBg
    val fg = if (isPressed) accentColor else defaultText

    Box(
        modifier = modifier
            .height(keyHeightDp.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
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

