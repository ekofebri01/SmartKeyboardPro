package com.smartkeyboard.pro

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                    onKeyPress = { char ->
                        playFeedback(this)
                        currentInputConnection?.commitText(char, 1)
                    },
                    onBackspace = {
                        playFeedback(this)
                        currentInputConnection?.deleteSurroundingText(1, 0)
                    },
                    onSpace = {
                        playFeedback(this)
                        currentInputConnection?.commitText(" ", 1)
                    },
                    onEnter = {
                        playFeedback(this)
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                )
            }
        }
        composeView = view
        return view
    }

    private fun playFeedback(view: View) {
        if (currentConfig.vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (currentConfig.soundEnabled) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
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

@Composable
fun SmartKeyboardView(
    config: ThemeConfig,
    bgBitmap: ImageBitmap?,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(KeyboardMode.ALPHA) }
    var isShifted by remember { mutableStateOf(false) }

    val row1Alpha = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2Alpha = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3Alpha = listOf("z", "x", "c", "v", "b", "n", "m")

    val row1Num = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row2Num = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
    val row3Num = listOf("*", "\"", "'", ":", ";", "!", "?")

    val emojis = listOf("😊", "😂", "❤️", "👍", "🔥", "🎉", "✨", "🙏", "😎", "🚀", "💡", "💯")

    val effectiveKeyColor = config.keyColor.copy(alpha = config.keyOpacity.coerceIn(0.1f, 1.0f))
    val actionKeyColor = if (config.customImagePath != null) {
        config.backgroundColor.copy(alpha = (config.keyOpacity * 1.1f).coerceIn(0.2f, 1.0f))
    } else {
        config.backgroundColor.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(config.backgroundColor)
    ) {
        // Custom photo background if active
        if (bgBitmap != null && config.themeId == "custom") {
            Image(
                bitmap = bgBitmap,
                contentDescription = "Custom Keyboard Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            // Subtle scrim overlay for text readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (mode) {
                KeyboardMode.ALPHA -> {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row1Alpha.forEach { key ->
                            val text = if (isShifted) key.uppercase() else key
                            ThemedKeyButton(
                                text = text,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
                                }
                            )
                        }
                    }

                    // Row 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row2Alpha.forEach { key ->
                            val text = if (isShifted) key.uppercase() else key
                            ThemedKeyButton(
                                text = text,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
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
                            cornerRadiusDp = config.cornerRadiusDp,
                            modifier = Modifier.weight(1.5f),
                            onClick = { isShifted = !isShifted }
                        )

                        row3Alpha.forEach { key ->
                            val text = if (isShifted) key.uppercase() else key
                            ThemedKeyButton(
                                text = text,
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onKeyPress(text)
                                    if (isShifted) isShifted = false
                                }
                            )
                        }

                        ThemedActionKeyButton(
                            text = "⌫",
                            accentColor = config.accentColor,
                            defaultBg = actionKeyColor,
                            defaultText = config.textColor,
                            cornerRadiusDp = config.cornerRadiusDp,
                            modifier = Modifier.weight(1.5f),
                            onClick = onBackspace
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) }
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) }
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(key) }
                            )
                        }
                        ThemedActionKeyButton(
                            text = "⌫",
                            accentColor = config.accentColor,
                            defaultBg = actionKeyColor,
                            defaultText = config.textColor,
                            cornerRadiusDp = config.cornerRadiusDp,
                            modifier = Modifier.weight(1.5f),
                            onClick = onBackspace
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(emoji) }
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
                                keyColor = effectiveKeyColor,
                                textColor = config.textColor,
                                cornerRadiusDp = config.cornerRadiusDp,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyPress(emoji) }
                            )
                        }
                    }
                }
            }

            // Bottom control row: Mode toggle, Space, Emoji, Enter
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
                    cornerRadiusDp = config.cornerRadiusDp,
                    modifier = Modifier.weight(1.5f),
                    onClick = {
                        mode = if (mode == KeyboardMode.ALPHA) KeyboardMode.NUMBERS else KeyboardMode.ALPHA
                    }
                )

                ThemedActionKeyButton(
                    text = if (mode == KeyboardMode.EMOJI) "ABC" else "😊",
                    accentColor = config.accentColor,
                    defaultBg = actionKeyColor,
                    defaultText = config.textColor,
                    cornerRadiusDp = config.cornerRadiusDp,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        mode = if (mode == KeyboardMode.EMOJI) KeyboardMode.ALPHA else KeyboardMode.EMOJI
                    }
                )

                // Space bar
                Box(
                    modifier = Modifier
                        .weight(4f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
                        .background(effectiveKeyColor)
                        .clickable { onSpace() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Space",
                        color = config.textColor.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }

                ThemedActionKeyButton(
                    text = "↵",
                    accentColor = config.accentColor,
                    defaultBg = config.accentColor,
                    defaultText = Color.White,
                    isHighlighted = true,
                    cornerRadiusDp = config.cornerRadiusDp,
                    modifier = Modifier.weight(1.5f),
                    onClick = onEnter
                )
            }
        }
    }
}

@Composable
fun ThemedKeyButton(
    text: String,
    keyColor: Color,
    textColor: Color,
    cornerRadiusDp: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(keyColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ThemedActionKeyButton(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    accentColor: Color,
    defaultBg: Color,
    defaultText: Color,
    cornerRadiusDp: Int,
    onClick: () -> Unit
) {
    val bg = if (isHighlighted) accentColor else defaultBg
    val fg = if (isHighlighted) Color.White else defaultText

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(bg)
            .clickable { onClick() },
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
