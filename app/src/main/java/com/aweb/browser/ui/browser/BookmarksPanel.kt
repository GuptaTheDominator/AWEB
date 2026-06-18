@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aweb.browser.ui.browser

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.BookmarkEntity

/**
 * Side-panel showing the user's bookmarks.
 *
 * Features:
 *  - List of all bookmarks (newest first)
 *  - Tap to open in current tab
 *  - Swipe-to-delete / long-press delete option
 *  - Empty state with hint
 */
@Composable
fun BookmarksPanel(
    bookmarks   : List<BookmarkEntity>,
    onOpen      : (BookmarkEntity) -> Unit,
    onDelete    : (BookmarkEntity) -> Unit,
    onDismiss   : () -> Unit,
    modifier    : Modifier = Modifier,
) {
    Surface(
        color  = Color(0xFF111111),
        shape  = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // Handle
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF444444))
                )
            }

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF2F8CFF), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bookmarks", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = Color(0xFF888888))
                }
            }

            HorizontalDivider(color = Color(0xFF222222))

            if (bookmarks.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.BookmarkBorder, null,
                            tint     = Color(0xFF333333),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            "No bookmarks yet",
                            color = Color(0xFF666666), fontSize = 14.sp,
                        )
                        Text(
                            "Tap ☆ in the toolbar to bookmark a page.",
                            color = Color(0xFF444444), fontSize = 11.sp,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        BookmarkRow(
                            bookmark = bm,
                            onOpen   = { onOpen(bm); onDismiss() },
                            onDelete = { onDelete(bm) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark : BookmarkEntity,
    onOpen   : () -> Unit,
    onDelete : () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Filled.Bookmark, null,
                tint     = Color(0xFF2F8CFF).copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.title,
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    bookmark.url.removePrefix("https://").removePrefix("http://"),
                    color    = Color(0xFF666666),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete, "Delete bookmark",
                    tint     = Color(0xFF444444),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = showMenu, onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1E1E1E)),
        ) {
            DropdownMenuItem(
                text    = { Text("Open", color = Color.White) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null, tint = Color.White) },
                onClick = { showMenu = false; onOpen() },
            )
            DropdownMenuItem(
                text    = { Text("Delete bookmark", color = Color(0xFFFF5C7A)) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5C7A)) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }
}
