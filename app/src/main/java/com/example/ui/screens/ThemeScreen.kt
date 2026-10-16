package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemes
import com.example.model.ThemeColorPalette

@Composable
fun ThemeScreen(
    settings: KeyboardSettings,
    onUpdateSettings: (KeyboardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val allThemes = remember { KeyboardThemes.allBuiltIn }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Kustomisasi Tema Keyboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Grid of theme choices
        Text(
            text = "Pilih Tema Visual",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(allThemes) { theme ->
                val isSelected = settings.themeId == theme.id
                ThemeCard(
                    theme = theme,
                    isSelected = isSelected,
                    onClick = {
                        onUpdateSettings(settings.copy(themeId = theme.id))
                    }
                )
            }
        }

        HorizontalDivider()

        // Key Height & Layout Slider
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Dimensi & Tampilan Tombol",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Key Height Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tinggi Tombol (Key Height)")
                        Text(
                            text = "${settings.keyHeightPercent}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.keyHeightPercent.toFloat(),
                        onValueChange = { onUpdateSettings(settings.copy(keyHeightPercent = it.toInt())) },
                        valueRange = 80f..140f,
                        steps = 6,
                        modifier = Modifier.testTag("key_height_slider")
                    )
                }

                // Show Number Row toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Baris Angka Terpisah (0-9)")
                        Text(
                            "Menampilkan baris angka di atas huruf",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showNumberRow,
                        onCheckedChange = { onUpdateSettings(settings.copy(showNumberRow = it)) }
                    )
                }

                // Show Key Preview toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pratinjau Pop-up Tombol")
                        Text(
                            "Menampilkan pop-up saat tombol ditekan",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showKeyPreview,
                        onCheckedChange = { onUpdateSettings(settings.copy(showKeyPreview = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: ThemeColorPalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(theme.backgroundColor)),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(theme.accentColor))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("theme_card_${theme.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = theme.name,
                    color = Color(theme.keyTextColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Key sample visual preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(theme.keyBackgroundColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Q", color = Color(theme.keyTextColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(theme.keyBackgroundColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = Color(theme.keyTextColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(theme.accentColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↵", color = Color(theme.accentTextColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
