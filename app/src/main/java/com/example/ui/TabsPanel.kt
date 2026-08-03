package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BrowserViewModel
import com.example.data.TabEntry

sealed class TabGroupItem {
    data class Standalone(val tab: TabEntry) : TabGroupItem()
    data class Group(val groupId: Long, val tabs: List<TabEntry>) : TabGroupItem()
}

@Composable
fun TabsPanel(viewModel: BrowserViewModel, onTabSelected: () -> Unit) {
    val tabsList by viewModel.allTabs.collectAsStateWithLifecycle(initialValue = emptyList())
    var joiningTabSelectionFor by remember { mutableStateOf<TabEntry?>(null) }
    var selectedGroupDetail by remember { mutableStateOf<List<TabEntry>?>(null) }

    // Grouping computation helper
    val tabGroupItemsList = remember(tabsList) {
        val list = mutableListOf<TabGroupItem>()
        val tabGroups = tabsList.filter { it.groupId != null }.groupBy { it.groupId }
        val standbyTabs = tabsList.filter { it.groupId == null }
        
        standbyTabs.forEach { list.add(TabGroupItem.Standalone(it)) }
        tabGroups.forEach { (groupId, groupTabs) ->
            if (groupTabs.isNotEmpty()) {
                list.add(TabGroupItem.Group(groupId ?: 0L, groupTabs))
            }
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tabs Manager Grid",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(
                onClick = {
                    viewModel.addNewTab()
                    onTabSelected()
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tab")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Tab", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tabsList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No open tabs", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = tabGroupItemsList
                ) { item ->
                    when (item) {
                        is TabGroupItem.Standalone -> {
                            val tab = item.tab
                            val isActive = tab.isCurrent
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                                                     else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clickable {
                                        viewModel.switchToTab(tab)
                                        onTabSelected()
                                    },
                                border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tab.title.ifEmpty { "Empty Tab" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tab.url,
                                                fontSize = 10.sp,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { viewModel.closeTab(tab) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Tab",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        var showOptionsDropdown by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(
                                                onClick = { showOptionsDropdown = true },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Tab Options",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showOptionsDropdown,
                                                onDismissRequest = { showOptionsDropdown = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Group Tab") },
                                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        joiningTabSelectionFor = tab
                                                        showOptionsDropdown = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Close") },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        viewModel.closeTab(tab)
                                                        showOptionsDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is TabGroupItem.Group -> {
                            val gpTabs = item.tabs
                            val gpId = item.groupId
                            val containsCurrent = gpTabs.any { it.isCurrent }
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (containsCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
                                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedGroupDetail = gpTabs
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Tab Group",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Group",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text(gpTabs.size.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondary)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                        gpTabs.take(3).forEach { t ->
                                            Text(
                                                text = "• " + (t.title.ifEmpty { "Empty Tab" }),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (gpTabs.size > 3) {
                                            Text(text = "and ${gpTabs.size - 3} more...", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "Manage Group",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue helping selection of other standby tabs to group together!
    if (joiningTabSelectionFor != null) {
        val activeTargetTab = joiningTabSelectionFor!!
        val optionsList = tabsList.filter { it.id != activeTargetTab.id && it.groupId == null }

        AlertDialog(
            onDismissRequest = { joiningTabSelectionFor = null },
            title = { Text("Add to Tab Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select another open standalone page to join the group:", fontSize = 13.sp)
                    if (optionsList.isEmpty()) {
                        Text("No other standalone tabs open to group with. Open another tab first!", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                            items(optionsList) { alternativeTab ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            val generatedGroupId = System.currentTimeMillis()
                                            viewModel.groupTabs(
                                                tabIds = listOf(activeTargetTab.id, alternativeTab.id),
                                                targetGroupId = generatedGroupId
                                            )
                                            joiningTabSelectionFor = null
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(alternativeTab.title.ifEmpty { "Empty Page" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(alternativeTab.url, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { joiningTabSelectionFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal bottom drawer listing tabs inside a certain folder, enabling switching, individual closing or ungrouping!
    if (selectedGroupDetail != null) {
        val tabsInGroup = selectedGroupDetail!!
        AlertDialog(
            onDismissRequest = { selectedGroupDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tab Group Tabs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    items(tabsInGroup) { groupTab ->
                        val isHighlighted = groupTab.isCurrent
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isHighlighted) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchToTab(groupTab)
                                    selectedGroupDetail = null
                                    onTabSelected()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = groupTab.title.ifEmpty { "Empty page" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = groupTab.url,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Remove/Ungroup option
                                    IconButton(
                                        onClick = {
                                            viewModel.removeFromGroup(groupTab)
                                            // Refresh remaining inside state
                                            val remaining = tabsInGroup.filter { it.id != groupTab.id }
                                            if (remaining.isEmpty()) {
                                                selectedGroupDetail = null
                                            } else {
                                                selectedGroupDetail = remaining
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOff,
                                            contentDescription = "Ungroup Tab",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Close option
                                    IconButton(
                                        onClick = {
                                            viewModel.closeTab(groupTab)
                                            val remaining = tabsInGroup.filter { it.id != groupTab.id }
                                            if (remaining.isEmpty()) {
                                                selectedGroupDetail = null
                                            } else {
                                                selectedGroupDetail = remaining
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedGroupDetail = null }) {
                    Text("Close Panel")
                }
            }
        )
    }
}
