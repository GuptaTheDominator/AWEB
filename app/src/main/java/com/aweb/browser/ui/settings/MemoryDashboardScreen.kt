@file:Suppress("DEPRECATION")
package com.aweb.browser.ui.settings


import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.AppState
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.lifecycle.TabLifecycleState

/**
 * Dedicated full-screen memory dashboard.
 *
 * Accessible from Settings → "Memory Dashboard" card → expand arrow.
 *
 * Shows:
 *  - Animated ring chart: ratio of live vs unloaded sessions
 *  - Per-state breakdown with animated count bars
 *  - Pressure simulation buttons for developer testing
 *  - Realtime tab-state list (from AppState snapshot)
 */
@Composable
fun MemoryDashboardScreen(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Pull live snapshot from AppState
    val tabs = AppState.currentTabs

    val totalTabs  = tabs.size
    val active     = tabs.count { it.lastLifecycleState == TabLifecycleState.ACTIVE.dbKey }
    val keepAlive  = tabs.count { it.keepAlive }
    val recent     = tabs.count { it.lastLifecycleState == TabLifecycleState.RECENT.dbKey && !it.keepAlive }
    val unloaded   = tabs.count { it.lastLifecycleState == TabLifecycleState.UNLOADED.dbKey }
    val liveSessions = state.liveSessionCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column {
                Text(
                    "Memory Dashboard",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    "$totalTabs tabs  •  $liveSessions live sessions",
                    color = Color(0xFF666666), fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            // Refresh button
            IconButton(onClick = { viewModel.refreshLiveSessionCount() }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Color(0xFF9C6FFF))
            }
        }

        HorizontalDivider(color = Color(0xFF1E1E1E))
        Spacer(Modifier.height(16.dp))

        // ── Animated ring chart ───────────────────────────────────────────
        SessionRingChart(
            liveSessions = liveSessions,
            totalTabs    = totalTabs,
            modifier     = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(24.dp))

        // ── Per-state bars ────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StateBar("● Active",    active,    totalTabs, Color(0xFF9C6FFF))
            StateBar("◆ Keep Alive", keepAlive, totalTabs, Color(0xFFFFB74D))
            StateBar("◐ Recent",    recent,    totalTabs, Color(0xFF4FC3F7))
            StateBar("○ Unloaded",  unloaded,  totalTabs, Color(0xFF444444))
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ── Policy summary ────────────────────────────────────────────────
        SectionLabel("CURRENT POLICY")
        PolicyGrid(
            mode        = state.memoryMode.label,
            maxRecent   = state.maxRecentLiveTabs,
            maxKa       = state.maxKeepAliveTabs,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ── Pressure simulation ───────────────────────────────────────────
        SectionLabel("PRESSURE SIMULATION  (Dev)")
        PressureSimPanel(viewModel = viewModel)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ── Tab-state list ────────────────────────────────────────────────
        SectionLabel("TAB STATES")
        TabStateList(tabs = tabs)

        Spacer(Modifier.height(40.dp))
    }
}

// ── Ring chart ─────────────────────────────────────────────────────────────

@Composable
private fun SessionRingChart(liveSessions: Int, totalTabs: Int, modifier: Modifier = Modifier) {
    val fraction = if (totalTabs > 0) liveSessions.toFloat() / totalTabs else 0f
    val animFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "ring",
    )

    val ringColor = when {
        fraction > 0.7f -> Color(0xFFCF6679)
        fraction > 0.4f -> Color(0xFFFFB74D)
        else            -> Color(0xFF9C6FFF)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress      = { animFraction },
            modifier      = Modifier.size(140.dp),
            strokeWidth   = 12.dp,
            color         = ringColor,
            trackColor    = Color(0xFF1E1E1E),
            strokeCap     = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$liveSessions",
                color      = Color.White,
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "live sessions",
                color    = Color(0xFF666666),
                fontSize = 11.sp,
            )
            Text(
                "of $totalTabs tabs",
                color    = ringColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── State bar ──────────────────────────────────────────────────────────────

@Composable
private fun StateBar(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val animFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "bar_$label",
    )

    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$count", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress      = { animFraction },
            modifier      = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color         = color,
            trackColor    = Color(0xFF1E1E1E),
            strokeCap     = StrokeCap.Round,
        )
    }
}

// ── Policy grid ────────────────────────────────────────────────────────────

@Composable
private fun PolicyGrid(mode: String, maxRecent: Int, maxKa: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PolicyCard("Mode",       mode,         Color(0xFF9C6FFF), Modifier.weight(1f))
        PolicyCard("Max Recent", "$maxRecent", Color(0xFF4FC3F7), Modifier.weight(1f))
        PolicyCard("Max KA",     "$maxKa",     Color(0xFFFFB74D), Modifier.weight(1f))
    }
}

@Composable
private fun PolicyCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141414))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp),
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF666666), fontSize = 10.sp)
    }
}

// ── Pressure simulation panel ─────────────────────────────────────────────

@Composable
private fun PressureSimPanel(viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Simulate Android memory pressure to see how AWEB responds.\nSessions will be unloaded according to the current policy.",
            color    = Color(0xFF666666),
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressureButton("Mild",     Color(0xFF4FC3F7)) { viewModel.simulatePressure(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) }
            PressureButton("Low",      Color(0xFFFFB74D)) { viewModel.simulatePressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) }
            PressureButton("Critical", Color(0xFFCF6679)) { viewModel.simulatePressure(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }
            PressureButton("Severe",   Color(0xFFFF5252)) { viewModel.simulatePressure(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) }
        }
    }
}

@Composable
private fun PressureButton(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        border   = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Tab state list ─────────────────────────────────────────────────────────

@Composable
private fun TabStateList(tabs: List<TabEntity>) {
    if (tabs.isEmpty()) {
        Text(
            "No tabs in current workspace",
            color    = Color(0xFF555555),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        tabs.sortedWith(
            compareByDescending<TabEntity> { it.isActive }
                .thenByDescending { it.keepAlive }
                .thenByDescending { it.lastAccessed }
        ).forEach { tab ->
            val (icon, color) = when {
                tab.isActive   -> "●" to Color(0xFF9C6FFF)
                tab.keepAlive  -> "◆" to Color(0xFFFFB74D)
                tab.lastLifecycleState == "recent"   -> "◐" to Color(0xFF4FC3F7)
                else           -> "○" to Color(0xFF444444)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            ) {
                Text(icon, color = color, fontSize = 12.sp, modifier = Modifier.width(20.dp))
                Text(
                    tab.title.ifBlank { tab.url },
                    color    = if (tab.isActive) Color.White else Color(0xFF888888),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (tab.isPinned) {
                    Text("📌", fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        color         = Color(0xFF555555),
        fontSize      = 10.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier      = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
