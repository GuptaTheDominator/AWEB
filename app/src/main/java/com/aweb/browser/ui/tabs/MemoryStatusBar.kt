package com.aweb.browser.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.lifecycle.TabLifecycleState

/**
 * Compact one-line memory status bar shown at the bottom of the WorkspaceSidebar.
 *
 * Displays a quick summary of live vs unloaded sessions:
 *   ● 1 active  ◆ 2 alive  ◐ 2 recent  ○ 5 unloaded
 *
 * This gives the user a clear view of what AWEB is keeping in memory.
 */
@Composable
fun MemoryStatusBar(
    tabs           : List<TabEntity>,
    liveSessionCount: Int,
    modifier       : Modifier = Modifier,
) {
    val active    = tabs.count { it.lastLifecycleState == TabLifecycleState.ACTIVE.dbKey }
    val keepAlive = tabs.count { it.keepAlive }
    val recent    = tabs.count { it.lastLifecycleState == TabLifecycleState.RECENT.dbKey && !it.keepAlive }
    val unloaded  = tabs.count { it.lastLifecycleState == TabLifecycleState.UNLOADED.dbKey }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                tint     = Color(0xFF666666),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Memory",
                color      = Color(0xFF666666),
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemBadge("●", "$active active",    Color(0xFF2F8CFF))
            if (keepAlive > 0) MemBadge("◆", "$keepAlive alive", Color(0xFFFFC857))
            if (recent > 0)    MemBadge("◐", "$recent recent",   Color(0xFF4DD8FF))
            MemBadge("○", "$unloaded off",  Color(0xFF444444))
        }
    }
}

@Composable
private fun MemBadge(icon: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(icon,  color = color, fontSize = 8.sp)
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}
