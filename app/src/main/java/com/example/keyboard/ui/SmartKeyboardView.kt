package com.example.keyboard.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keyboard.data.EmojiData
import com.example.keyboard.model.KeyboardMode
import com.example.model.AutoTextEntry
import com.example.model.KeyboardSettings
import com.example.model.ThemeColorPalette

@Composable
fun SmartKeyboardView(
    settings: KeyboardSettings,
    suggestions: List<String>,
    matchedAutoText: AutoTextEntry?,
    shiftState: ShiftState,
    imeActionLabel: String,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShiftToggle: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchIme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = remember(settings.themeId, settings.customBgColor) { settings.resolveTheme() }
    var currentMode by remember { mutableStateOf(KeyboardMode.TEXT) }
    var selectedEmojiCategoryIndex by remember { mutableIntStateOf(0) }
    var symbolsPage by remember { mutableIntStateOf(1) }

    // Dynamic height scaling (e.g. 100% -> 44dp base key height)
    val heightScale = settings.keyHeightPercent / 100f
    val baseKeyHeight = (44 * heightScale).dp
    val cornerRadius = theme.keyCornerRadiusDp.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(theme.backgroundColor))
            .testTag("smart_keyboard_container")
    ) {
        // Suggestion & Quick Action Bar
        SuggestionBar(
            theme = theme,
            suggestions = suggestions,
            matchedAutoText = matchedAutoText,
            onSuggestionClick = onSuggestionClick
        )

        when (currentMode) {
            KeyboardMode.TEXT -> {
                // Number row (optional)
                if (settings.showNumberRow) {
                    NumberRow(
                        theme = theme,
                        cornerRadius = cornerRadius,
                        keyHeight = (36 * heightScale).dp,
                        onKeyPress = onKeyPress
                    )
                }

                // Standard QWERTY layout
                QwertyRows(
                    settings = settings,
                    theme = theme,
                    shiftState = shiftState,
                    cornerRadius = cornerRadius,
                    keyHeight = baseKeyHeight,
                    onKeyPress = onKeyPress,
                    onBackspace = onBackspace,
                    onShiftToggle = onShiftToggle
                )

                // Bottom Action Bar
                BottomRow(
                    theme = theme,
                    cornerRadius = cornerRadius,
                    keyHeight = baseKeyHeight,
                    imeActionLabel = imeActionLabel,
                    isSymbolsMode = false,
                    onModeToggle = { currentMode = KeyboardMode.SYMBOLS },
                    onOpenSettings = onOpenSettings,
                    onEmojiToggle = { currentMode = KeyboardMode.EMOJI },
                    onSpace = onSpace,
                    onEnter = onEnter,
                    onKeyPress = onKeyPress
                )
            }

            KeyboardMode.SYMBOLS -> {
                SymbolsRows(
                    theme = theme,
                    page = symbolsPage,
                    cornerRadius = cornerRadius,
                    keyHeight = baseKeyHeight,
                    onKeyPress = onKeyPress,
                    onBackspace = onBackspace,
                    onToggleSymbolsPage = {
                        symbolsPage = if (symbolsPage == 1) 2 else 1
                    }
                )

                BottomRow(
                    theme = theme,
                    cornerRadius = cornerRadius,
                    keyHeight = baseKeyHeight,
                    imeActionLabel = imeActionLabel,
                    isSymbolsMode = true,
                    onModeToggle = { currentMode = KeyboardMode.TEXT },
                    onOpenSettings = onOpenSettings,
                    onEmojiToggle = { currentMode = KeyboardMode.EMOJI },
                    onSpace = onSpace,
                    onEnter = onEnter,
                    onKeyPress = onKeyPress
                )
            }

            KeyboardMode.EMOJI -> {
                EmojiPickerView(
                    theme = theme,
                    selectedCategory = selectedEmojiCategoryIndex,
                    onSelectCategory = { selectedEmojiCategoryIndex = it },
                    onEmojiSelected = onKeyPress,
                    onBackspace = onBackspace,
                    onBackToText = { currentMode = KeyboardMode.TEXT },
                    keyHeight = (210 * heightScale).dp
                )
            }
        }
    }
}

@Composable
fun SuggestionBar(
    theme: ThemeColorPalette,
    suggestions: List<String>,
    matchedAutoText: AutoTextEntry?,
    onSuggestionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(theme.suggestionBgColor))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Suggestion scrollable area
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Auto-text indicator badge if matched
            if (matchedAutoText != null) {
                Surface(
                    color = Color(theme.accentColor).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(theme.accentColor)),
                    modifier = Modifier
                        .clickable { onSuggestionClick(matchedAutoText.replacement) }
                        .padding(vertical = 2.dp)
                        .testTag("autotext_suggestion_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚡ ${matchedAutoText.shortcut} →",
                            color = Color(theme.accentColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = matchedAutoText.replacement,
                            color = Color(theme.keyTextColor),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Standard suggestions
            suggestions.forEach { word ->
                Surface(
                    color = Color(theme.keyBackgroundColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { onSuggestionClick(word) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = word,
                        color = Color(theme.suggestionTextColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NumberRow(
    theme: ThemeColorPalette,
    cornerRadius: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    onKeyPress: (String) -> Unit
) {
    val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        numbers.forEach { num ->
            KeyButton(
                text = num,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyAltBackgroundColor),
                textColor = Color(theme.keyTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(num) }
            )
        }
    }
}

@Composable
fun QwertyRows(
    settings: KeyboardSettings,
    theme: ThemeColorPalette,
    shiftState: ShiftState,
    cornerRadius: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onShiftToggle: () -> Unit
) {
    val isUpper = shiftState != ShiftState.OFF
    val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row1Alts = settings.customRow1Alts

    val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row2Alts = settings.customRow2Alts

    val row3 = listOf("z", "x", "c", "v", "b", "n", "m")
    val row3Alts = settings.customRow3Alts

    // Row 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        row1.forEachIndexed { i, char ->
            val display = if (isUpper) char.uppercase() else char
            val alt = row1Alts.getOrElse(i) { "" }
            KeyButtonWithSubtext(
                primaryText = display,
                subText = alt,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                subTextColor = Color(theme.keySubTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(display) },
                onLongClick = { if (alt.isNotEmpty()) onKeyPress(alt) }
            )
        }
    }

    // Row 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        row2.forEachIndexed { i, char ->
            val display = if (isUpper) char.uppercase() else char
            val alt = row2Alts.getOrElse(i) { "" }
            KeyButtonWithSubtext(
                primaryText = display,
                subText = alt,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                subTextColor = Color(theme.keySubTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(display) },
                onLongClick = { if (alt.isNotEmpty()) onKeyPress(alt) }
            )
        }
    }

    // Row 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Shift Key
        val shiftBg = when (shiftState) {
            ShiftState.CAPS_LOCK -> Color(theme.accentColor)
            ShiftState.ON -> Color(theme.keyAltBackgroundColor)
            ShiftState.OFF -> Color(theme.keyAltBackgroundColor)
        }
        val shiftIconColor = when (shiftState) {
            ShiftState.CAPS_LOCK -> Color(theme.accentTextColor)
            ShiftState.ON -> Color(theme.accentColor)
            ShiftState.OFF -> Color(theme.keyTextColor)
        }

        KeyIconButton(
            modifier = Modifier.weight(1.4f),
            height = keyHeight,
            backgroundColor = shiftBg,
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = onShiftToggle,
            content = {
                Icon(
                    imageVector = if (shiftState == ShiftState.CAPS_LOCK) Icons.Default.VerticalAlignTop else Icons.Default.ArrowUpward,
                    contentDescription = "Shift",
                    tint = shiftIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        row3.forEachIndexed { i, char ->
            val display = if (isUpper) char.uppercase() else char
            val alt = row3Alts.getOrElse(i) { "" }
            KeyButtonWithSubtext(
                primaryText = display,
                subText = alt,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                subTextColor = Color(theme.keySubTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(display) },
                onLongClick = { if (alt.isNotEmpty()) onKeyPress(alt) }
            )
        }

        // Backspace Key with hold-to-repeat continuous delete
        RepeatingBackspaceKeyButton(
            modifier = Modifier.weight(1.4f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onBackspace = onBackspace,
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color(theme.keyTextColor),
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

@Composable
fun SymbolsRows(
    theme: ThemeColorPalette,
    page: Int,
    cornerRadius: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onToggleSymbolsPage: () -> Unit
) {
    val page1Row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val page1Row2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    val page1Row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    val page2Row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
    val page2Row2 = listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}", "\\")
    val page2Row3 = listOf("%", "©", "®", "™", "[", "]", "<", ">")

    val r1 = if (page == 1) page1Row1 else page2Row1
    val r2 = if (page == 1) page1Row2 else page2Row2
    val r3 = if (page == 1) page1Row3 else page2Row3

    // Row 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        r1.forEach { char ->
            KeyButton(
                text = char,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(char) }
            )
        }
    }

    // Row 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        r2.forEach { char ->
            KeyButton(
                text = char,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(char) }
            )
        }
    }

    // Row 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Toggle 1/2 or 2/2
        KeyButton(
            text = if (page == 1) "=\\<" else "?123",
            modifier = Modifier.weight(1.4f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            textColor = Color(theme.accentColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = onToggleSymbolsPage
        )

        r3.forEach { char ->
            KeyButton(
                text = char,
                modifier = Modifier.weight(1f),
                height = keyHeight,
                backgroundColor = Color(theme.keyBackgroundColor),
                textColor = Color(theme.keyTextColor),
                cornerRadius = cornerRadius,
                borderColor = Color(theme.keyBorderColor),
                onClick = { onKeyPress(char) }
            )
        }

        // Backspace with hold-to-repeat continuous delete
        RepeatingBackspaceKeyButton(
            modifier = Modifier.weight(1.4f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onBackspace = onBackspace,
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color(theme.keyTextColor),
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

@Composable
fun BottomRow(
    theme: ThemeColorPalette,
    cornerRadius: androidx.compose.ui.unit.Dp,
    keyHeight: androidx.compose.ui.unit.Dp,
    imeActionLabel: String,
    isSymbolsMode: Boolean,
    onModeToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmojiToggle: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onKeyPress: (String) -> Unit
) {


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mode toggle (?123 / ABC) with direct settings on long press
        KeyButton(
            text = if (isSymbolsMode) "ABC" else "?123",
            modifier = Modifier.width(60.dp),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            textColor = Color(theme.keyTextColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = onModeToggle,
            onLongClick = onOpenSettings
        )

        // Comma
        KeyButton(
            text = ",",
            modifier = Modifier.weight(0.9f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            textColor = Color(theme.keyTextColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = { onKeyPress(",") }
        )

        // Emoji Switcher Key
        KeyButton(
            text = "😀",
            modifier = Modifier.weight(0.9f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            textColor = Color(theme.keyTextColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = onEmojiToggle
        )

        // Space bar
        Box(
            modifier = Modifier
                .weight(4.2f)
                .height(keyHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(theme.keyBackgroundColor))
                .border(1.dp, Color(theme.keyBorderColor), RoundedCornerShape(cornerRadius))
                .clickable { onSpace() }
                .testTag("space_bar_key"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Smart Keyboard Pro",
                    fontSize = 11.sp,
                    color = Color(theme.keySubTextColor),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Period
        KeyButton(
            text = ".",
            modifier = Modifier.weight(0.9f),
            height = keyHeight,
            backgroundColor = Color(theme.keyAltBackgroundColor),
            textColor = Color(theme.keyTextColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = { onKeyPress(".") }
        )

        // Enter / Action
        KeyIconButton(
            modifier = Modifier.weight(1.4f),
            height = keyHeight,
            backgroundColor = Color(theme.accentColor),
            cornerRadius = cornerRadius,
            borderColor = Color(theme.keyBorderColor),
            onClick = onEnter,
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (imeActionLabel.lowercase()) {
                            "search" -> Icons.Default.Search
                            "send" -> Icons.Default.Send
                            "done", "go" -> Icons.Default.Check
                            else -> Icons.Default.KeyboardReturn
                        },
                        contentDescription = imeActionLabel,
                        tint = Color(theme.accentTextColor),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun EmojiPickerView(
    theme: ThemeColorPalette,
    selectedCategory: Int,
    onSelectCategory: (Int) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackToText: () -> Unit,
    keyHeight: androidx.compose.ui.unit.Dp
) {
    val categories = EmojiData.categories
    val currentCategory = categories.getOrElse(selectedCategory) { categories.first() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyHeight)
            .background(Color(theme.backgroundColor))
    ) {
        // Category navigation tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(theme.keyAltBackgroundColor))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, cat ->
                val isSelected = index == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(theme.accentColor).copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { onSelectCategory(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(text = cat.icon, fontSize = 16.sp)
                }
            }
        }

        // Emoji grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 40.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(currentCategory.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = if (currentCategory.title == "Kaomoji") 11.sp else 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Bottom Bar in Emoji View: Return to ABC & Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(theme.suggestionBgColor))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackToText,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(theme.keyAltBackgroundColor),
                    contentColor = Color(theme.keyTextColor)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text("ABC", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Text(
                text = currentCategory.title,
                color = Color(theme.keySubTextColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            RepeatingBackspaceKeyButton(
                modifier = Modifier.size(40.dp),
                height = 36.dp,
                backgroundColor = Color(theme.keyAltBackgroundColor),
                cornerRadius = 6.dp,
                borderColor = Color(theme.keyBorderColor),
                onBackspace = onBackspace,
                content = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = Color(theme.keyTextColor),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    backgroundColor: Color,
    textColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = modifier
            .height(height)
            .shadow(1.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isPressed) backgroundColor.copy(alpha = 0.6f) else backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .pointerInput(text) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        currentOnClick()
                    },
                    onLongPress = {
                        currentOnLongClick?.invoke()
                    }
                )
            }
            .testTag("key_$text"),
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
fun KeyButtonWithSubtext(
    primaryText: String,
    subText: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    backgroundColor: Color,
    textColor: Color,
    subTextColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = modifier
            .height(height)
            .shadow(1.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isPressed) backgroundColor.copy(alpha = 0.6f) else backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .pointerInput(primaryText) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        currentOnClick()
                    },
                    onLongPress = {
                        currentOnLongClick()
                    }
                )
            }
            .testTag("key_$primaryText"),
        contentAlignment = Alignment.Center
    ) {
        // Subtext in top-right corner - high readability
        Text(
            text = subText,
            color = subTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 1.dp, end = 3.dp)
        )

        // Main text centered
        Text(
            text = primaryText,
            color = textColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KeyIconButton(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    backgroundColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .height(height)
            .shadow(1.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isPressed) backgroundColor.copy(alpha = 0.6f) else backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    currentOnClick()
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun RepeatingBackspaceKeyButton(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    backgroundColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    borderColor: Color,
    onBackspace: () -> Unit,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnBackspace by rememberUpdatedState(onBackspace)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Immediate delete on touch down
            currentOnBackspace()
            // Initial wait before repeating
            delay(380L)
            var count = 0
            while (isPressed && isActive) {
                currentOnBackspace()
                count++
                // Accelerate deletion as user holds:
                // Fast deleting for sentences (60ms down to 35ms after 10 deletions)
                val interval = if (count > 10) 35L else 60L
                delay(interval)
            }
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .shadow(1.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isPressed) backgroundColor.copy(alpha = 0.6f) else backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    isPressed = false
                }
            }
            .testTag("key_backspace"),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

enum class ShiftState {
    OFF,
    ON,
    CAPS_LOCK
}
