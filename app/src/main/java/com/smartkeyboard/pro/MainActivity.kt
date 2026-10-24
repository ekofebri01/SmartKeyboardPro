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
import androidx.compose.ui.draw.shadow
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
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

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
    val tabTitles = listOf("Studio", "Activation", "Cloud & Backup", "Kamus & Auto Text", "Updates", "Privacy")

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
                2 -> CloudBackupTab(viewModel)
                3 -> ClipboardVaultTab(viewModel)
                4 -> GitHubUpdateTab(viewModel)
                5 -> PrivacyDashboardTab()
            }
        }
    }
}

@Composable
fun ThemeStudioTab(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val config = viewModel.themeConfig
    val bgBitmap = viewModel.customImageBitmap
    var previewText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.applyCustomImage(uri)
            Toast.makeText(context, "Custom background applied!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Section: Fixed Live Interactive Keyboard Preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp),
            color = Color(0xFF131C31)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Live Keyboard Preview",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "Fixed",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (previewText.isNotEmpty()) {
                        Text(
                            text = "Clear Text",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { previewText = "" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Interactive test output bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (previewText.isEmpty()) "Ketuk keyboard di bawah untuk mengetik / tahan ⌫..." else previewText,
                            color = if (previewText.isEmpty()) Color(0xFF64748B) else Color.White,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyboard_preview_card")
                ) {
                    SmartKeyboardView(
                        config = config,
                        bgBitmap = bgBitmap,
                        onKeyPress = { previewText += it },
                        onBackspace = {
                            if (previewText.isNotEmpty()) {
                                previewText = previewText.dropLast(1)
                            }
                        },
                        onSpace = { previewText += " " },
                        onEnter = { previewText += "\n" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        // Section: Scrollable Customization Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                                text = if (config.customImagePath != null) "Foto aktif (tetap muncul pada semua preset warna)" else "Personalize your keyboard with any photo",
                                fontSize = 12.sp,
                                color = if (config.customImagePath != null) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                            )
                        }

                        if (config.customImagePath != null) {
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
                            if (config.customImagePath != null) "Choose Different Photo" else "Upload Custom Image"
                        )
                    }

                    if (config.customImagePath != null) {
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

                HorizontalDivider(color = Color(0xFF334155))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart Suggestion Bar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Show word predictions from personal dictionary above keyboard", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Switch(
                        checked = config.showSuggestionBar,
                        onCheckedChange = { viewModel.toggleSuggestionBar(it) },
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
                        Text("Auto-Space After Word", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Automatically insert a space when tapping a suggested word", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Switch(
                        checked = config.autoSpaceAfterWord,
                        onCheckedChange = { viewModel.toggleAutoSpace(it) },
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
    var subTab by remember { mutableIntStateOf(0) } // 0: Kamus Pribadi, 1: Auto Text
    var newSnippetText by remember { mutableStateOf("") }
    var newDictWord by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var autoTextSearchQuery by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshLearnedWords()
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Kamus Pribadi?", color = Color.White) },
            text = {
                Text(
                    "Semua kata yang telah dipelajari secara otomatis akan dihapus dan dikembalikan ke kosakata awal.",
                    color = Color(0xFFCBD5E1)
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLearnedWords()
                        showResetDialog = false
                        Toast.makeText(context, "Kamus pribadi direset", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    var showAutoTextDialog by remember { mutableStateOf(false) }
    var editingAutoTextEntry by remember { mutableStateOf<AutoTextEntry?>(null) }
    var newAutoTextShortcut by remember { mutableStateOf("") }
    var newAutoTextFullText by remember { mutableStateOf("") }

    if (showAutoTextDialog) {
        AlertDialog(
            onDismissRequest = { showAutoTextDialog = false },
            title = {
                Text(
                    text = if (editingAutoTextEntry == null) "Tambah Auto Text Baru" else "Edit Auto Text",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Masukkan keyword/pintasan dan teks lengkap yang akan otomatis muncul di bar saran keyboard saat mengetik.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newAutoTextShortcut,
                        onValueChange = { newAutoTextShortcut = it },
                        label = { Text("Keyword / Pintasan (contoh: thx, alamat, email)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAutoTextFullText,
                        onValueChange = { newAutoTextFullText = it },
                        label = { Text("Isi Teks Lengkap") },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val shortcut = newAutoTextShortcut.trim()
                        val fullText = newAutoTextFullText.trim()
                        if (fullText.isNotBlank()) {
                            if (editingAutoTextEntry != null) {
                                viewModel.updateAutoTextItem(editingAutoTextEntry!!.id, shortcut, fullText)
                                Toast.makeText(context, "Auto Text diperbarui!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addAutoTextItem(shortcut, fullText)
                                Toast.makeText(context, "Auto Text disimpan!", Toast.LENGTH_SHORT).show()
                            }
                            showAutoTextDialog = false
                        } else {
                            Toast.makeText(context, "Isi teks tidak boleh kosong", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoTextDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C2541))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (subTab == 0) Color(0xFF0284C7) else Color.Transparent)
                    .clickable { subTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📖 Kamus Pribadi (${viewModel.learnedWords.size})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (subTab == 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (subTab == 1) Color(0xFF0284C7) else Color.Transparent)
                    .clickable { subTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡ Auto Text (${viewModel.autoTextEntries.size})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (subTab == 1) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        if (subTab == 0) {
            // === KAMUS PRIBADI SUB-TAB ===
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-Learning & Prediksi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        TextButton(
                            onClick = { showResetDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Reset Kamus", color = Color(0xFFF87171), fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = "Smart Keyboard otomatis menyimpan kata baru saat Anda menekan Spasi/Enter. Kata yang sering digunakan akan muncul di bar saran di atas keyboard.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }

            // Input to manually add word
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newDictWord,
                    onValueChange = { newDictWord = it },
                    placeholder = { Text("Tambah kata baru...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
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
                        val word = newDictWord.trim()
                        if (word.isNotBlank()) {
                            viewModel.addLearnedWord(word)
                            newDictWord = ""
                            Toast.makeText(context, "\"$word\" disimpan ke kamus!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Tambah", fontSize = 13.sp)
                }
            }

            // Search filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari kata di kamus...", color = Color(0xFF64748B), fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            val filteredWords = remember(searchQuery, viewModel.learnedWords) {
                if (searchQuery.isBlank()) {
                    viewModel.learnedWords
                } else {
                    viewModel.learnedWords.filter { it.word.contains(searchQuery.trim(), ignoreCase = true) }
                }
            }

            if (filteredWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Belum ada kata tersimpan" else "Tidak ditemukan kata yang cocok",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredWords, key = { it.word }) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = entry.word,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${entry.frequency}x",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.deleteLearnedWord(entry.word)
                                        Toast.makeText(context, "\"${entry.word}\" dihapus", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus kata",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // === AUTO TEXT SUB-TAB ===
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Auto Text & Shortcut Expander",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Button(
                            onClick = {
                                editingAutoTextEntry = null
                                newAutoTextShortcut = ""
                                newAutoTextFullText = ""
                                showAutoTextDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Tambah", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = "Ketik keyword/pintasan (misal: thx, alamat, email) saat mengetik, maka teks lengkap akan otomatis muncul di bar saran keyboard!",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }

            OutlinedTextField(
                value = autoTextSearchQuery,
                onValueChange = { autoTextSearchQuery = it },
                placeholder = { Text("Cari keyword atau isi Auto Text...", color = Color(0xFF64748B), fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            val filteredAutoTexts = remember(autoTextSearchQuery, viewModel.autoTextEntries.toList()) {
                if (autoTextSearchQuery.isBlank()) {
                    viewModel.autoTextEntries
                } else {
                    val q = autoTextSearchQuery.trim()
                    viewModel.autoTextEntries.filter {
                        it.shortcut.contains(q, ignoreCase = true) || it.fullText.contains(q, ignoreCase = true)
                    }
                }
            }

            if (filteredAutoTexts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (autoTextSearchQuery.isBlank()) "Belum ada Auto Text tersimpan" else "Tidak ditemukan Auto Text yang cocok",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredAutoTexts, key = { it.id }) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "⚡ ${entry.shortcut}",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "Pintasan",
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = entry.fullText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingAutoTextEntry = entry
                                            newAutoTextShortcut = entry.shortcut
                                            newAutoTextFullText = entry.fullText
                                            showAutoTextDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("AutoText", entry.fullText))
                                            Toast.makeText(context, "Tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Salin", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.removeAutoTextEntry(entry)
                                            Toast.makeText(context, "Auto Text \"${entry.shortcut}\" dihapus", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                    }
                                }
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

                Text("What's New in v1.3.2:", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("- Auto-switches to numeric keypad on number input fields.", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Text("- Fixed keypress haptic vibration and sound feedback.", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Text("- Enhanced high-contrast secondary symbol hints on keys.", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Text("- Hold '?123' key to quickly open app settings.", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Text("- Cleaned up bottom-left controls and removed duplicate buttons.", fontSize = 12.sp, color = Color(0xFFCBD5E1))


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

@Composable
fun CloudBackupTab(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var jsonInspectorModalItem by remember { mutableStateOf<CloudDocumentInspectorItem?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser) }

    DisposableEffect(Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.refreshCloudBackups()
            viewModel.refreshCloudInspector()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Auth & Firebase Database Connection Info
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
                            text = "Firestore Cloud Database",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (currentUser != null) "Terhubung: ${currentUser?.email ?: currentUser?.uid}" else "Status: Belum Login Google",
                            fontSize = 12.sp,
                            color = if (currentUser != null) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (currentUser != null) "Cloud Active" else "Offline",
                            color = if (currentUser != null) Color(0xFF38BDF8) else Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Database ID: ${context.getString(R.string.firestore_database_id)}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "User ID: ${currentUser?.uid ?: "Belum Terautentikasi"}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                var cloudErrorInfo by remember { mutableStateOf<AuthErrorInfo?>(null) }

                if (currentUser == null) {
                    Button(
                        onClick = {
                            isLoggingIn = true
                            cloudErrorInfo = null
                            signInWithGoogle(
                                context = context,
                                scope = coroutineScope,
                                onSuccess = {
                                    isLoggingIn = false
                                    cloudErrorInfo = null
                                    viewModel.refreshCloudBackups()
                                    viewModel.refreshCloudInspector()
                                },
                                onError = { err, info ->
                                    isLoggingIn = false
                                    cloudErrorInfo = info
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = !isLoggingIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("🔑 Masuk dengan Google", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (cloudErrorInfo != null) {
                        Button(
                            onClick = { /* dialog rendered below */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text("🛠️ Deteksi & Solusi Masalah Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            signOutGoogle(context, coroutineScope) {
                                viewModel.refreshCloudBackups()
                                viewModel.refreshCloudInspector()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Keluar dari Akun Google", color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }

                cloudErrorInfo?.let { info ->
                    AuthTroubleshooterDialog(
                        errorInfo = info,
                        onDismiss = { cloudErrorInfo = null },
                        onRetryLogin = {
                            cloudErrorInfo = null
                            isLoggingIn = true
                            signInWithGoogle(
                                context = context,
                                scope = coroutineScope,
                                onSuccess = {
                                    isLoggingIn = false
                                    viewModel.refreshCloudBackups()
                                    viewModel.refreshCloudInspector()
                                },
                                onError = { err, newInfo ->
                                    isLoggingIn = false
                                    cloudErrorInfo = newInfo
                                }
                            )
                        },
                        onContinueLocal = { cloudErrorInfo = null }
                    )
                }

                if (viewModel.cloudSyncStatus != null) {
                    Surface(
                        color = Color(0xFF0369A1).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = viewModel.cloudSyncStatus ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                var showLogsDialog by remember { mutableStateOf(false) }
                val logEntries by AuthDiagnosticLogger.logEntries.collectAsState()

                TextButton(
                    onClick = { showLogsDialog = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("📋 Lihat Log Diagnostik (In-App Logcat)", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }

                if (showLogsDialog) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val logText = if (logEntries.isEmpty()) {
                        "Belum ada log autentikasi tercatat.\nTekan '🔑 Masuk dengan Google' untuk merekam event logcat."
                    } else {
                        logEntries.joinToString("\n\n")
                    }

                    AlertDialog(
                        onDismissRequest = { showLogsDialog = false },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Logcat Diagnostic Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        },
                        text = {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                            ) {
                                androidx.compose.foundation.rememberScrollState().let { scrollState ->
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        Text(
                                            text = logText,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .verticalScroll(scrollState)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showLogsDialog = false }) {
                                Text("Tutup")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logText))
                                    Toast.makeText(context, "Log diagnosa berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("📄 Salin Log", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        },
                        containerColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.refreshLocalBackupFiles()
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val ok = viewModel.savePersistedFolderUri(uri)
                if (ok) {
                    Toast.makeText(context, "Ijin akses folder disetujui! Hanya folder ini yang dapat diakses.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal menyimpan ijin folder.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Card 1.5: Local Folder Backup & Restore (backups/SmartkeyboardPro)
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
                            text = "📁 Backup & Restore Lokal (Folder)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Folder Target: backups/SmartkeyboardPro",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    IconButton(onClick = { viewModel.refreshLocalBackupFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Local Files", tint = Color(0xFF38BDF8))
                    }
                }

                Text(
                    text = "Aman & Terisolasi: Pilih folder khusus (Storage Access Framework). Aplikasi HANYA diberi ijin membaca & menulis di folder yang kamu izinkan.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                // SAF Permission Selector Status
                val currentFolderUri = viewModel.getPersistedFolderUri()
                Surface(
                    color = if (currentFolderUri != null) Color(0xFF065F46).copy(alpha = 0.3f) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentFolderUri != null) "🔒 Status Ijin: Folder Khusus Aktif" else "🔓 Status Ijin: Belum Ada Folder Khusus",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentFolderUri != null) Color(0xFF34D399) else Color(0xFFF59E0B)
                            )
                            if (currentFolderUri != null) {
                                Text(
                                    text = "Folder: ${currentFolderUri.lastPathSegment ?: "SmartkeyboardPro"}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (currentFolderUri != null) {
                            TextButton(
                                onClick = { viewModel.clearPersistedFolderPermission() }
                            ) {
                                Text("Cabut Ijin", fontSize = 11.sp, color = Color(0xFFF87171))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("🔐 Pilih Folder Ijin", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val status = viewModel.performLocalFolderBackup()
                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💾 Save & Overwrite Local Backup (SmartkeyboardPro)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (viewModel.localBackupStatusMessage != null) {
                    Surface(
                        color = Color(0xFF065F46).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = viewModel.localBackupStatusMessage ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text(
                    text = "Daftar File Backup di Folder 'backups/SmartkeyboardPro':",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (viewModel.localBackupFilesList.isEmpty()) {
                    Text(
                        text = "Belum ada file backup lokal di folder backups/SmartkeyboardPro.\nTekan tombol di atas untuk membuat file backup pertama.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                } else {
                    viewModel.localBackupFilesList.forEach { localFile ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = localFile.fileName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Update: ${localFile.lastModifiedFormatted} • ${localFile.sizeFormatted}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    if (localFile.isSafUri) {
                                        Text(
                                            text = "🔒 Permitted Folder SAF",
                                            fontSize = 10.sp,
                                            color = Color(0xFF34D399)
                                        )
                                    } else {
                                        Text(
                                            text = localFile.file?.parent ?: "",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            val ok = viewModel.restoreFromLocalBackupInfo(localFile)
                                            if (ok) {
                                                Toast.makeText(context, "Restore lokal berhasil!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Gagal restore file lokal.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("🔄 Restore", fontSize = 11.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            try {
                                                if (localFile.file != null && localFile.file.exists()) {
                                                    localFile.file.delete()
                                                } else if (localFile.uri != null) {
                                                    androidx.documentfile.provider.DocumentFile.fromSingleUri(context, localFile.uri)?.delete()
                                                }
                                                viewModel.refreshLocalBackupFiles()
                                                Toast.makeText(context, "File backup dihapus.", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal menghapus file.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = Color(0xFFF87171))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Backup & Sync Action Buttons
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
                    text = "Aksi Backup & Sinkronisasi",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncAllToCloud() },
                        enabled = !viewModel.isSyncingCloud,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (viewModel.isSyncingCloud) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text("⚡ Sinkron Cloud", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.createCloudBackupSnapshot() },
                        enabled = !viewModel.isSyncingCloud,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("☁️ Backup Cloud", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val file = viewModel.saveJsonToDeviceFile()
                            val jsonString = viewModel.exportToJsonString()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SmartKeyboardPro Backup JSON")
                                putExtra(Intent.EXTRA_TEXT, jsonString)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Download / Simpan File Backup JSON"))
                            } catch (e: Exception) {
                                if (file != null) {
                                    Toast.makeText(context, "Tersimpan di ${file.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 Download JSON", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📂 Import Local File", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Card 3: Live Firestore Cloud Inspector (Visual Cloud Database Viewer)
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
                            text = "Inspector Database Firestore",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Lihat dokumen yang benar-benar tersimpan di cloud",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    IconButton(onClick = { viewModel.refreshCloudInspector() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Inspector", tint = Color(0xFF38BDF8))
                    }
                }

                if (viewModel.isFetchingInspectorDocs) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                } else if (viewModel.cloudDocumentsInspectorList.isEmpty()) {
                    Text(
                        text = "Belum ada dokumen tersimpan di Cloud. Klik '⚡ Sinkron Cloud' untuk mengirim data pertama kamu.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    viewModel.cloudDocumentsInspectorList.forEach { doc ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { jsonInspectorModalItem = doc }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF38BDF8).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = doc.collectionName,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = doc.documentId,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Path: ${doc.path}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Lihat JSON",
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 4: Cloud Backups History & Restore List
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
                    Text(
                        text = "Riwayat Snapshot Cloud Backup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    IconButton(onClick = { viewModel.refreshCloudBackups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Backups", tint = Color(0xFF38BDF8))
                    }
                }

                if (viewModel.isFetchingCloudBackups) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                } else if (viewModel.cloudBackupsList.isEmpty()) {
                    Text(
                        text = "Belum ada snapshot backup di cloud. Klik '☁️ Backup Cloud' untuk membuat snapshot.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    viewModel.cloudBackupsList.forEach { backup ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = backup.createdAtFormatted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Perangkat: ${backup.deviceName} • Items: ${backup.itemCount}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                val restored = viewModel.restoreFromCloudBackup(backup)
                                                if (restored) {
                                                    Toast.makeText(context, "Berhasil restore dari cloud!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Restore", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.deleteCloudBackup(backup.backupId) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Hapus", fontSize = 11.sp, color = Color(0xFFF87171))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: JSON Inspector Detail View
    if (jsonInspectorModalItem != null) {
        val doc = jsonInspectorModalItem!!
        AlertDialog(
            onDismissRequest = { jsonInspectorModalItem = null },
            title = {
                Text(
                    text = "📄 ${doc.collectionName} / ${doc.documentId}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Path: ${doc.path}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    Surface(
                        color = Color(0xFF090D16),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = doc.jsonContent,
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { jsonInspectorModalItem = null }) {
                    Text("Tutup", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF1C2541)
        )
    }

    // Modal Dialog: Import Local JSON File
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text("Import Backup dari Local JSON", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tempel isi teks file JSON backup kamu di bawah ini untuk restore secara langsung:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text("{ \"themeConfig\": ... }", fontSize = 11.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.importFromJsonString(importText)
                        if (ok) {
                            Toast.makeText(context, "Restore lokal berhasil!", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                            importText = ""
                        } else {
                            Toast.makeText(context, "Format JSON tidak valid!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Restore Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C2541)
        )
    }
}

