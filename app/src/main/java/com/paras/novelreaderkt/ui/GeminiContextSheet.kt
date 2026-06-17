package com.paras.novelreaderkt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paras.novelreaderkt.NovelContextManager
import com.paras.novelreaderkt.data.AppDatabase
import com.paras.novelreaderkt.data.NovelGlossaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive bottom sheet for viewing and editing the Gemini translation
 * context file (glossary) for the current novel.
 *
 * Shows: character names, sect names, cultivation terms, relationships, etc.
 * Allows: editing translations, adding new terms, deleting terms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiContextSheet(
    novelKey: String,
    novelTitle: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var glossaryEntries by remember { mutableStateOf<List<NovelGlossaryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<NovelGlossaryEntry?>(null) }
    var selectedCategory by remember { mutableStateOf("all") }

    val categoryColors = mapOf(
        "character" to Color(0xFF42A5F5),
        "sect" to Color(0xFFAB47BC),
        "system" to Color(0xFFEF5350),
        "technique" to Color(0xFFFFA726),
        "item" to Color(0xFF66BB6A),
        "location" to Color(0xFF26C6DA),
        "relationship" to Color(0xFFEC407A),
        "title" to Color(0xFFFFCA28),
        "other" to Color(0xFF78909C)
    )

    val categoryLabels = mapOf(
        "all" to "All",
        "character" to "Characters",
        "sect" to "Sects",
        "system" to "Systems",
        "technique" to "Techniques",
        "item" to "Items",
        "location" to "Locations",
        "relationship" to "Relations",
        "title" to "Titles",
        "other" to "Other"
    )

    // Load glossary
    LaunchedEffect(novelKey) {
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).browserDao()
                glossaryEntries = dao.getGlossaryForNovel(novelKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoading = false
        }
    }

    fun reloadGlossary() {
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).browserDao()
                glossaryEntries = dao.getGlossaryForNovel(novelKey)
                NovelContextManager.invalidateCache(novelKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val filteredEntries = if (selectedCategory == "all") {
        glossaryEntries
    } else {
        glossaryEntries.filter { it.category == selectedCategory }
    }.sortedWith(compareBy({ it.category }, { -it.frequency }))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Context File",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        novelTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add term")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category filter chips
            val categories = listOf("all", "character", "sect", "system", "technique", "item", "location", "relationship", "title", "other")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val color = categoryColors[cat] ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                categoryLabels[cat] ?: cat,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.15f),
                            selectedLabelColor = color
                        ),
                        border = if (isSelected) null else FilterChipDefaults.filterChipBorder(
                            borderColor = color.copy(alpha = 0.3f),
                            enabled = true,
                            selected = false
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Term count
            Text(
                "${filteredEntries.size} term${if (filteredEntries.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No terms detected yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Terms are auto-detected as you read chapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    var lastCategory = ""
                    items(filteredEntries, key = { it.id }) { entry ->
                        if (entry.category != lastCategory && selectedCategory == "all") {
                            lastCategory = entry.category
                            val catColor = categoryColors[entry.category] ?: MaterialTheme.colorScheme.primary
                            Text(
                                categoryLabels[entry.category]?.uppercase() ?: entry.category.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = catColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        GlossaryTermRow(
                            entry = entry,
                            categoryColor = categoryColors[entry.category] ?: MaterialTheme.colorScheme.primary,
                            onEdit = { editingEntry = it },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val dao = AppDatabase.getDatabase(context).browserDao()
                                        dao.deleteGlossaryEntry(it.id)
                                        NovelContextManager.invalidateCache(novelKey)
                                        withContext(Dispatchers.Main) {
                                            reloadGlossary()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog || editingEntry != null) {
        GlossaryEditDialog(
            novelKey = novelKey,
            existingEntry = editingEntry,
            onDismiss = {
                showAddDialog = false
                editingEntry = null
            },
            onSave = {
                showAddDialog = false
                editingEntry = null
                reloadGlossary()
            }
        )
    }
}

@Composable
private fun GlossaryTermRow(
    entry: NovelGlossaryEntry,
    categoryColor: Color,
    onEdit: (NovelGlossaryEntry) -> Unit,
    onDelete: (NovelGlossaryEntry) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isAutoDetected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "auto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!entry.pinyin.isNullOrBlank() && entry.pinyin != entry.translatedText) {
                        Text(
                            " (${entry.pinyin})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Frequency badge
            if (entry.frequency > 1) {
                Text(
                    "×${entry.frequency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // More menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit(entry)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete(entry)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlossaryEditDialog(
    novelKey: String,
    existingEntry: NovelGlossaryEntry?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isEdit = existingEntry != null
    var sourceText by remember { mutableStateOf(existingEntry?.sourceText ?: "") }
    var translatedText by remember { mutableStateOf(existingEntry?.translatedText ?: "") }
    var pinyin by remember { mutableStateOf(existingEntry?.pinyin ?: "") }
    var category by remember { mutableStateOf(existingEntry?.category ?: "character") }
    var notes by remember { mutableStateOf(existingEntry?.notes ?: "") }

    val categories = listOf("character", "sect", "system", "technique", "item", "location", "relationship", "title", "other")
    val categoryLabels = mapOf(
        "character" to "Character", "sect" to "Sect/Org", "system" to "Power System",
        "technique" to "Technique", "item" to "Item", "location" to "Location",
        "relationship" to "Relationship", "title" to "Title", "other" to "Other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Term" else "Add Term") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text("Chinese (原文)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = translatedText,
                    onValueChange = { translatedText = it },
                    label = { Text("English Translation") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pinyin,
                    onValueChange = { pinyin = it },
                    label = { Text("Pinyin (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = {}
                ) {
                    OutlinedTextField(
                        value = categoryLabels[category] ?: category,
                        onValueChange = {},
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }
                    )
                    ExposedDropdownMenu(
                        expanded = false,
                        onDismissRequest = {}
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(categoryLabels[cat] ?: cat) },
                                onClick = {
                                    category = cat
                                }
                            )
                        }
                    }
                }
                // Simple category selector as clickable row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .clickable {
                            // Cycle through categories
                            val idx = categories.indexOf(category)
                            category = categories[(idx + 1) % categories.size]
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        categoryLabels[category] ?: category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (sourceText.isNotBlank() && translatedText.isNotBlank()) {
                        val ctx = com.paras.novelreaderkt.WtrAudioControlBridge.lastKnownContext ?: return@TextButton
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val dao = com.paras.novelreaderkt.data.AppDatabase.getDatabase(ctx).browserDao()
                                val entry = NovelGlossaryEntry(
                                    id = existingEntry?.id ?: 0,
                                    novelKey = novelKey,
                                    category = category,
                                    sourceText = sourceText.trim(),
                                    translatedText = translatedText.trim(),
                                    pinyin = pinyin.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    isAutoDetected = false
                                )
                                dao.insertGlossaryEntry(entry)
                                NovelContextManager.invalidateCache(novelKey)
                                kotlinx.coroutines.MainScope().launch { onSave() }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                },
                enabled = sourceText.isNotBlank() && translatedText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}