package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BrowserViewModel

@Composable
fun BookmarksPanel(viewModel: BrowserViewModel, onUrlSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val bookmarksList by viewModel.allBookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTab by remember { mutableStateOf(0) } // 0 = Websites, 1 = Novels

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
                text = "Library & Bookmarks",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("bookmarks_close_button")) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Bookmarks")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Premium Accent Dual-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabWeight = Modifier.weight(1f)
            Button(
                onClick = { selectedTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = null,
                modifier = tabWeight
            ) {
                Icon(Icons.Default.Language, "Websites", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Websites", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            
            Button(
                onClick = { selectedTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = null,
                modifier = tabWeight
            ) {
                Icon(Icons.Default.Book, "Novels", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Novels", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter and display bookmarks based on active tab selection
        val filteredTabList = remember(bookmarksList, selectedTab) {
            if (selectedTab == 1) {
                bookmarksList.filter { it.isNovel }
            } else {
                bookmarksList.filter { !it.isNovel }
            }
        }

        var searchQuery by remember { mutableStateOf("") }
        val finalDisplayList = remember(filteredTabList, searchQuery) {
            if (searchQuery.isEmpty()) {
                filteredTabList
            } else {
                filteredTabList.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true) ||
                    (it.novelTitle?.contains(searchQuery, ignoreCase = true) ?: false)
                }
            }
        }

        if (filteredTabList.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (selectedTab == 1) "Search bookmarked novels..." else "Search standard bookmarks...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bookmarks_search_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (finalDisplayList.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedTab == 1) Icons.Default.Book else Icons.Default.StarBorder,
                        contentDescription = "Empty Library",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No results matching search"
                        } else if (selectedTab == 1) {
                            "No novels bookmarked yet.\n(Chapters you bookmark automatically appear here!)"
                        } else {
                            "No bookmarked web pages yet"
                        },
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(finalDisplayList, key = { it.id }) { bookmark ->
                    if (selectedTab == 1) {
                        // Premium visual Novel Card for Bookshelf tab
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    // Deep-link straight into the user's last viewed chapter
                                    onUrlSelected(bookmark.lastViewedChapterUrl ?: bookmark.url)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Book Cover Cover Canvas graphic matching the Dark Cosmic Slate theme
                                val bookTitle = bookmark.novelTitle ?: "Novel"
                                val initials = remember(bookTitle) {
                                    val words = bookTitle.split(" ", "-", "_").filter { it.trim().isNotEmpty() }
                                    if (words.size >= 2) {
                                        (words[0].take(1) + words[1].take(1)).uppercase()
                                    } else if (words.isNotEmpty()) {
                                        words[0].take(minOf(2, words[0].length)).uppercase()
                                    } else {
                                        "NV"
                                    }
                                }

                                val bookGradient = remember(bookTitle) {
                                    val hash = bookTitle.hashCode()
                                    val colorsValue = when (java.lang.Math.abs(hash) % 4) {
                                        0 -> listOf(Color(0xFFE91E63), Color(0xFF9C27B0))
                                        1 -> listOf(Color(0xFF00BCD4), Color(0xFF3F51B5))
                                        2 -> listOf(Color(0xFFFF9800), Color(0xFFE91E63))
                                        else -> listOf(Color(0xFF4CAF50), Color(0xFF009688))
                                    }
                                    Brush.linearGradient(colorsValue)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 72.dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                                        .background(bookGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!bookmark.imageUrl.isNullOrEmpty()) {
                                        coil.compose.AsyncImage(
                                            model = bookmark.imageUrl,
                                            contentDescription = "Cover for $bookTitle",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Spine Crease line
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(2.dp)
                                            .align(Alignment.CenterStart)
                                            .background(Color.White.copy(alpha = 0.22f))
                                    )

                                    if (bookmark.imageUrl.isNullOrEmpty()) {
                                        Text(
                                            text = initials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = bookTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Website domain",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = bookmark.domain ?: "Web Novel Website",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val progressChapter = bookmark.lastViewedChapterTitle ?: bookmark.chapterTitle ?: "Read Chapter"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Book,
                                            contentDescription = "Progress marker",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Last: $progressChapter",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Bookmarked Novel"
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Bookmark Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUrlSelected(bookmark.url) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Bookmark",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bookmark.url,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Bookmark"
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
