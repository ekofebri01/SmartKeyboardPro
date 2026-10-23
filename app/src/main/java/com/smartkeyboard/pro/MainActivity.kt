package com.smartkeyboard.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: KeyboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Studio", "Activation", "Vault", "Updates", "Privacy")

    Scaffold(
        containerColor = Color(0xFF0B132B),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C2541))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF38BDF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Smart Keyboard Pro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "v$currentVersion • In-App Updater",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1C2541),
                    contentColor = Color(0xFF38BDF8),
                    edgePadding = 12.dp
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ThemeStudioTab(viewModel)
                1 -> ActivationTab()
                2 -> ClipboardVaultTab(viewModel)
                3 -> GitHubUpdateTab(viewModel)
                4 -> PrivacyDashboardTab()
            }
        }
    }
}

@Composable
fun ThemeStudioTab(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val config = viewModel.themeConfig
    val bgBitmap = viewModel.customImageBitmap

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.applyCustomImage(uri)
            Toast.makeText(context, "Custom background applied!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Live Interactive Keyboard Preview
        Text(
            text = "Live Keyboard Preview",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("keyboard_preview_card")
        ) {
            SmartKeyboardView(
                config = config,
                bgBitmap = bgBitmap,
                onKeyPress = {},
                onBackspace = {},
                onSpace = {},
                onEnter = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        // Section: Custom Background Image
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Custom Photo Background",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Personalize your keyboard with any photo",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    if (config.themeId == "custom" && config.customImagePath != null) {
                        TextButton(
                            onClick = { viewModel.removeCustomImage() }
                        ) {
                            Text("Remove", color = Color(0xFFF87171))
                        }
                    }
                }

                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_bg_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        if (config.themeId == "custom") "Choose Different Photo" else "Upload Custom Image"
                    )
                }

                if (config.themeId == "custom") {
                    Text(
                        text = "Key Button Opacity: ${(config.keyOpacity * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = config.keyOpacity,
                        onValueChange = { viewModel.updateKeyOpacity(it) },
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }
        }

        // Section: Predefined Themes
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Preset Color Themes",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    viewModel.predefinedThemes.forEach { theme ->
                        val isSelected = config.themeId == theme.id

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF334155) else Color(0xFF0F172A))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectPredefinedTheme(theme) }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(theme.backgroundColor)
                                    .border(2.dp, theme.accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.keyColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = theme.name,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Section: Keyboard Layout & Symbols
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Layout & Symbol Shortcuts",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dedicated Number Row", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Display row of numbers (1–0) above QWERTY", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Switch(
                        checked = config.showNumberRow,
                        onCheckedChange = { viewModel.toggleNumberRow(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Secondary Symbols", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Display symbol hints (!, @, #, $, etc.) on letter keys", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Switch(
                        checked = config.showKeySymbols,
                        onCheckedChange = { viewModel.toggleKeySymbols(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Long-Press for Symbols", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Hold any letter key to immediately input its symbol", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Switch(
                        checked = config.enableLongPressSymbols,
                        onCheckedChange = { viewModel.toggleLongPressSymbols(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }
        }

        // Section: Key Shapes & Dimensions
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Keyboard Shape & Height",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Text("Keycap Shape Style", color = Color(0xFF94A3B8), fontSize = 13.sp)
                val shapes = listOf(
                    "rounded" to "Rounded",
                    "pill" to "Capsule / Pill",
                    "squircle" to "Squircle",
                    "sharp" to "Sharp"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shapes.forEach { (key, label) ->
                        val isSelected = config.keyShapeStyle == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateKeyShapeStyle(key) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38BDF8),
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            )
                        )
                    }
                }

                Text(
                    text = "Key Height: ${config.keyHeightDp} dp",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Slider(
                    value = config.keyHeightDp.toFloat(),
                    onValueChange = { viewModel.updateKeyHeight(it.toInt()) },
                    valueRange = 36f..54f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8)
                    )
                )

                Text(
                    text = "Corner Radius Fine-Tuning: ${config.cornerRadiusDp} dp",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Slider(
                    value = config.cornerRadiusDp.toFloat(),
                    onValueChange = { viewModel.updateCornerRadius(it.toInt()) },
                    valueRange = 0f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8)
                    )
                )
            }
        }

        // Section: Sound & Haptic Feedback
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Sound & Vibration Feedback",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keypress Sounds", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = config.soundEnabled,
                        onCheckedChange = { viewModel.toggleSound(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback (Vibration)", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = config.vibrationEnabled,
                        onCheckedChange = { viewModel.toggleVibration(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ActivationTab() {
    val context = LocalContext.current
    var testText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Keyboard Activation",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Step 1: Enable in Android Settings")
                }

                OutlinedButton(
                    onClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Step 2: Switch Default Keyboard", color = Color(0xFF38BDF8))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Interactive Typing Test",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Tap below to bring up the Smart Keyboard Pro and test your custom theme live.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    placeholder = { Text("Tap here to type and test...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_typing_field")
                )
            }
        }
    }
}

@Composable
fun ClipboardVaultTab(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    var newSnippetText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Clipboard Vault",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Store fast snippets, canned responses, and templates for one-tap copy & pasting.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSnippetText,
                onValueChange = { newSnippetText = it },
                placeholder = { Text("New quick snippet...", color = Color(0xFF64748B)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newSnippetText.isNotBlank()) {
                        viewModel.addClipboardItem(newSnippetText.trim())
                        newSnippetText = ""
                        Toast.makeText(context, "Snippet added!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(viewModel.clipboardItems) { snippet ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = snippet,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("snippet", snippet))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Copy", tint = Color(0xFF38BDF8))
                            }
                            IconButton(
                                onClick = { viewModel.removeClipboardItem(snippet) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubUpdateTab(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }
    val downloadState = viewModel.downloadState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "In-App Updates",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Version", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Text("v$currentVersion", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("GitHub Releases", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                Text("Repository Source", fontSize = 13.sp, color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.repoOwner,
                        onValueChange = { viewModel.repoOwner = it },
                        label = { Text("Owner") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.repoName,
                        onValueChange = { viewModel.repoName = it },
                        label = { Text("Repo") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = { viewModel.checkForUpdates(currentVersion) },
                    enabled = !viewModel.isCheckingUpdate && downloadState !is DownloadState.Downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_update_button")
                ) {
                    if (viewModel.isCheckingUpdate) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Checking GitHub Releases...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
        }

        // In-App Download Progress & Animation Card
        AnimatedVisibility(visible = downloadState is DownloadState.Downloading) {
            val state = downloadState as? DownloadState.Downloading
            if (state != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Downloading",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Downloading Update In-App...",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                val downloadedMb = state.downloadedBytes / (1024f * 1024f)
                                val totalMb = state.totalBytes / (1024f * 1024f)
                                val text = if (state.totalBytes > 0) {
                                    String.format(Locale.US, "%.1f MB / %.1f MB (%.0f%%)", downloadedMb, totalMb, state.progress * 100)
                                } else {
                                    String.format(Locale.US, "%.1f MB downloaded", downloadedMb)
                                }
                                Text(text, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        if (state.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF334155)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF334155)
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.cancelDownload() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel", color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // In-App Download Complete / Ready to Install Card
        AnimatedVisibility(visible = downloadState is DownloadState.Completed) {
            val state = downloadState as? DownloadState.Completed
            if (state != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Download Complete!",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Text(
                            text = "APK downloaded directly to application cache (${String.format(Locale.US, "%.1f MB", state.file.length() / (1024f * 1024f))}). Once installed, the temporary package file will be automatically deleted.",
                            color = Color(0xFFA7F3D0),
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                val success = viewModel.installUpdate(state.file)
                                if (!success) {
                                    Toast.makeText(context, "Please allow 'Install Unknown Apps' for Smart Keyboard Pro", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Install Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { viewModel.deleteDownloadedFile() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Discard & Delete APK File", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // In-App Download Error
        AnimatedVisibility(visible = downloadState is DownloadState.Error) {
            val state = downloadState as? DownloadState.Error
            if (state != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF87171))
                        Text(state.message, color = Color(0xFFFCA5A5), fontSize = 13.sp)
                    }
                }
            }
        }

        // Update Check Result Card
        when (val result = viewModel.updateResult) {
            is UpdateResult.Success -> {
                val release = result.releaseInfo
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("New Release Found: ${release.tagName}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }

                        Text(release.title, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8), fontSize = 14.sp)
                        Text(release.releaseNotes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)

                        if (downloadState is DownloadState.Idle) {
                            Button(
                                onClick = { viewModel.startInAppDownload(release.downloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Download & Install In-App", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            is UpdateResult.UpToDate -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8))
                        Column {
                            Text("You're on the latest version!", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("v${result.currentVersion} is the latest release.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
            is UpdateResult.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF87171))
                        Text(result.message, color = Color(0xFFFCA5A5), fontSize = 13.sp)
                    }
                }
            }
            null -> {}
        }
    }
}

@Composable
fun PrivacyDashboardTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Privacy & Security Audit",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val audits = listOf(
                    "Zero Keystroke Logging" to "Your typing never leaves your device and is not saved to any remote server.",
                    "100% On-Device Keyboard Engine" to "The IME typing pipeline runs entirely local without network calls.",
                    "In-App Updater Transparency" to "Downloads APK packages directly from GitHub releases and automatically deletes the installer after completion to preserve local storage.",
                    "Zero Trackers & Telemetry" to "No Google Analytics, Firebase Analytics, or third-party SDK trackers."
                )

                audits.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                            Text(desc, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
