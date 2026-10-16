package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AutoTextEntry
import com.example.model.KeyboardSettings
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class AppNavDestination(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    HOME("Beranda", Icons.Default.Keyboard, "nav_home"),
    AUTO_TEXT("Auto-Text", Icons.Default.Bolt, "nav_autotext"),
    BACKUP("Backup", Icons.Default.Restore, "nav_backup"),
    THEMES("Tema", Icons.Default.Palette, "nav_themes"),
    SETTINGS("Pengaturan", Icons.Default.Tune, "nav_settings")
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as? SmartKeyboardApp)?.repository
            ?: com.example.data.repository.KeyboardRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                val scope = rememberCoroutineScope()
                val settings by repository.settingsFlow.collectAsStateWithLifecycle()
                val autoTexts by repository.allAutoTextsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                val cachedAutoTexts by repository.cachedAutoTexts.collectAsStateWithLifecycle()
                val customWords by repository.customWordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                var currentDestination by remember { mutableStateOf(AppNavDestination.HOME) }

                BackHandler(enabled = currentDestination != AppNavDestination.HOME) {
                    currentDestination = AppNavDestination.HOME
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentDestination) {
                                        AppNavDestination.HOME -> "Smart Keyboard Pro"
                                        AppNavDestination.AUTO_TEXT -> "Kelola Auto-Text"
                                        AppNavDestination.BACKUP -> "Backup & Restore"
                                        AppNavDestination.THEMES -> "Kustomisasi Tema"
                                        AppNavDestination.SETTINGS -> "Pengaturan Ketikan"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            AppNavDestination.entries.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(dest.icon, contentDescription = dest.title)
                                    },
                                    label = { Text(dest.title) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    BackHandler(enabled = currentDestination != AppNavDestination.HOME) {
                        currentDestination = AppNavDestination.HOME
                    }
                    Crossfade(
                        targetState = currentDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) { destination ->
                        when (destination) {
                            AppNavDestination.HOME -> {
                                HomeScreen(
                                    settings = settings,
                                    autoTextCount = autoTexts.size,
                                    activeAutoTexts = cachedAutoTexts,
                                    onNavigateToAutoText = { currentDestination = AppNavDestination.AUTO_TEXT },
                                    onNavigateToBackup = { currentDestination = AppNavDestination.BACKUP },
                                    onNavigateToThemes = { currentDestination = AppNavDestination.THEMES },
                                    onNavigateToSettings = { currentDestination = AppNavDestination.SETTINGS }
                                )
                            }
                            AppNavDestination.AUTO_TEXT -> {
                                AutoTextScreen(
                                    autoTexts = autoTexts,
                                    onAddOrUpdate = { entry ->
                                        scope.launch { repository.addOrUpdateAutoText(entry) }
                                    },
                                    onDelete = { entry ->
                                        scope.launch { repository.deleteAutoText(entry) }
                                    },
                                    onToggleEnabled = { entry ->
                                        scope.launch { repository.toggleAutoTextEnabled(entry) }
                                    },
                                    onSeedDefaults = {
                                        scope.launch { repository.seedDefaultShortcuts() }
                                    }
                                )
                            }
                            AppNavDestination.BACKUP -> {
                                BackupRestoreScreen(
                                    repository = repository,
                                    settings = settings,
                                    onUpdateSettings = { newSettings ->
                                        scope.launch { repository.updateSettings(newSettings) }
                                    }
                                )
                            }
                            AppNavDestination.THEMES -> {
                                ThemeScreen(
                                    settings = settings,
                                    onUpdateSettings = { newSettings ->
                                        scope.launch { repository.updateSettings(newSettings) }
                                    }
                                )
                            }
                            AppNavDestination.SETTINGS -> {
                                SettingsScreen(
                                    repository = repository,
                                    settings = settings,
                                    onUpdateSettings = { newSettings ->
                                        scope.launch { repository.updateSettings(newSettings) }
                                    },
                                    customWords = customWords,
                                    onAddCustomWord = { word ->
                                        scope.launch { repository.addCustomWord(word) }
                                    },
                                    onDeleteCustomWord = { entry ->
                                        scope.launch { repository.deleteCustomWord(entry) }
                                    },
                                    onClearCustomWords = {
                                        scope.launch { repository.clearCustomWords() }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
