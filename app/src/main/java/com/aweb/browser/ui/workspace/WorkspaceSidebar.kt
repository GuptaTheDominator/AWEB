@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aweb.browser.ui.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.WorkspaceRepository
import com.aweb.browser.ui.tabs.MemoryStatusBar

/**
 * Persistent left-rail workspace sidebar for tablet layout.
 *
 * Shows:
 *  - AWEB logo / title
 *  - List of workspaces (tap to switch, long-press for options)
 *  - "New Workspace" button at bottom
 *
 * Width: 220dp — comfortable for a tablet, leaves plenty of room for the browser.
 */
@Composable
fun WorkspaceSidebar(
    workspaces       : List<WorkspaceEntity>,
    activeWorkspace  : WorkspaceEntity?,
    activeTabs       : List<TabEntity> = emptyList(),
    liveSessionCount : Int = 0,
    onSwitch         : (WorkspaceEntity) -> Unit,
    onNew            : () -> Unit,
    onRename         : (WorkspaceEntity) -> Unit,
    onDelete         : (WorkspaceEntity) -> Unit,
    onClearData      : (WorkspaceEntity) -> Unit,
    onOpenSettings   : () -> Unit = {},
    modifier         : Modifier = Modifier,
) {
    Surface(
        color         = Color(0xFF161616),
        tonalElevation = 0.dp,
        modifier      = modifier.fillMaxHeight().width(220.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── App title ─────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    text       = "AWEB",
                    color      = Color(0xFF9C6FFF),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint   = Color(0xFF9C6FFF),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF2A2A2A))
            Spacer(Modifier.height(8.dp))

            Text(
                text     = "WORKSPACES",
                color    = Color(0xFF666666),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // ── Workspace list ────────────────────────────────────────────
            LazyColumn(
                modifier      = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(workspaces, key = { it.id }) { workspace ->
                    WorkspaceItem(
                        workspace       = workspace,
                        isActive        = workspace.id == activeWorkspace?.id,
                        onSwitch        = { onSwitch(workspace) },
                        onRename        = { onRename(workspace) },
                        onDelete        = { onDelete(workspace) },
                        onClearData     = { onClearData(workspace) },
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // ── Memory status ─────────────────────────────────────────────
            if (activeTabs.isNotEmpty()) {
                MemoryStatusBar(
                    tabs             = activeTabs,
                    liveSessionCount = liveSessionCount,
                    modifier         = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // ── New workspace button ───────────────────────────────────────
            TextButton(
                onClick  = onNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Workspace",
                    tint = Color(0xFF9C6FFF),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("New Workspace", color = Color(0xFF9C6FFF), fontSize = 13.sp)
            }

            // ── Settings button ────────────────────────────────────────────
            TextButton(
                onClick  = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF555555),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Settings", color = Color(0xFF555555), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Single workspace row ───────────────────────────────────────────────────

@Composable
private fun WorkspaceItem(
    workspace   : WorkspaceEntity,
    isActive    : Boolean,
    onSwitch    : () -> Unit,
    onRename    : () -> Unit,
    onDelete    : () -> Unit,
    onClearData : () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val wsColor = runCatching {
        Color(android.graphics.Color.parseColor(workspace.colorHex ?: "#9C6FFF"))
    }.getOrDefault(Color(0xFF9C6FFF))

    val targetBackground = if (isActive) Color(0xFF2D2D2D) else Color.Transparent
    val targetTextColor  = if (isActive) Color.White else Color(0xFF888888)
    val animatedBackground by animateColorAsState(targetBackground, label = "bg")
    val animatedTextColor  by animateColorAsState(targetTextColor, label = "text")

    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(animatedBackground)
                .combinedClickable(
                    onClick      = onSwitch,
                    onLongClick  = { showMenu = true },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Animated active pill
            AnimatedVisibility(
                visible = isActive,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .width(4.dp)
                        .height(20.dp)
                        .clip(CircleShape)
                        .background(wsColor),
                )
            }

            // Workspace color dot
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(wsColor.copy(alpha = 0.6f)),
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text       = workspace.name,
                color      = animatedTextColor,
                fontSize   = 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f),
            )

            if (isActive) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint     = wsColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded        = showMenu,
            onDismissRequest = { showMenu = false },
            modifier         = Modifier.background(Color(0xFF1E1E1E)),
        ) {
            DropdownMenuItem(
                text    = { Text("Rename", color = Color.White) },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White) },
                onClick = { showMenu = false; onRename() },
            )
            DropdownMenuItem(
                text    = { Text("Clear data", color = Color(0xFFFFB74D)) },
                leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, tint = Color(0xFFFFB74D)) },
                onClick = { showMenu = false; onClearData() },
            )
            HorizontalDivider(color = Color(0xFF333333))
            DropdownMenuItem(
                text    = { Text("Delete workspace", color = Color(0xFFCF6679)) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFCF6679)) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }
}
