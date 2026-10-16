package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoTextEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTextScreen(
    autoTexts: List<AutoTextEntry>,
    onAddOrUpdate: (AutoTextEntry) -> Unit,
    onDelete: (AutoTextEntry) -> Unit,
    onToggleEnabled: (AutoTextEntry) -> Unit,
    onSeedDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingItem by remember { mutableStateOf<AutoTextEntry?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<AutoTextEntry?>(null) }

    // Categories
    val categories = remember(autoTexts) {
        listOf("All") + autoTexts.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    // Filtered list
    val filteredList = remember(autoTexts, searchQuery, selectedCategory) {
        autoTexts.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.shortcut.contains(searchQuery, true) ||
                    item.replacement.contains(searchQuery, true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    isAddDialogOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_autotext_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Auto-Text")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("autotext_search_input"),
                placeholder = { Text("Cari shortcut atau teks...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Subtitle & Count info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} Shortcut ditemukan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onSeedDefaults) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Defaults", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pulihkan Preset Bawaan", fontSize = 12.sp)
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShortText,
                            contentDescription = "Empty",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada Auto-Text kustom" else "Tidak ada shortcut yang cocok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tekan tombol + di bawah untuk membuat shortcut baru",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("autotext_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        AutoTextItemCard(
                            item = item,
                            onToggle = { onToggleEnabled(item) },
                            onEdit = {
                                editingItem = item
                                isAddDialogOpen = true
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (isAddDialogOpen) {
        AutoTextEditDialog(
            initialItem = editingItem,
            onDismiss = {
                isAddDialogOpen = false
                editingItem = null
            },
            onSave = { updatedItem ->
                onAddOrUpdate(updatedItem)
                isAddDialogOpen = false
                editingItem = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Auto-Text?") },
            text = { Text("Apakah Anda yakin ingin menghapus shortcut '${itemToDelete?.shortcut}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { onDelete(it) }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun AutoTextItemCard(
    item: AutoTextEntry,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("autotext_item_${item.shortcut}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Shortcut badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.shortcut,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Category tag
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Replacement text
                Text(
                    text = item.replacement,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(end = 4.dp)
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AutoTextEditDialog(
    initialItem: AutoTextEntry?,
    onDismiss: () -> Unit,
    onSave: (AutoTextEntry) -> Unit
) {
    var shortcut by remember { mutableStateOf(initialItem?.shortcut ?: "") }
    var replacement by remember { mutableStateOf(initialItem?.replacement ?: "") }
    var category by remember { mutableStateOf(initialItem?.category ?: "Indonesian") }
    var enabled by remember { mutableStateOf(initialItem?.enabled ?: true) }

    var testInput by remember { mutableStateOf("") }
    val testExpanded = remember(testInput, shortcut, replacement) {
        if (shortcut.isNotEmpty() && testInput.equals(shortcut, true)) replacement else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Tambah Auto-Text Baru" else "Edit Auto-Text") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Shortcut (Kata Singkat)") },
                    placeholder = { Text("Contoh: otw, alamat, thx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_shortcut_input")
                )

                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Teks Pengganti (Ekspansi)") },
                    placeholder = { Text("Contoh: On the way bro! 🛵") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("edit_replacement_input")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori") },
                    placeholder = { Text("Indonesian, Personal, Kaomoji, dll") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview / Tester
                if (shortcut.isNotBlank() && replacement.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Pratinjau:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Ketik '$shortcut' → '$replacement'",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (shortcut.isNotBlank() && replacement.isNotBlank()) {
                        onSave(
                            AutoTextEntry(
                                id = initialItem?.id ?: 0,
                                shortcut = shortcut.trim(),
                                replacement = replacement,
                                category = category.trim().ifEmpty { "General" },
                                enabled = enabled
                            )
                        )
                    }
                },
                enabled = shortcut.isNotBlank() && replacement.isNotBlank(),
                modifier = Modifier.testTag("save_autotext_btn")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
