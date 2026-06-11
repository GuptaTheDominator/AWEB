package com.aweb.browser.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aweb.browser.data.repository.MemoryMode
import com.aweb.browser.data.repository.SearchEngine

/**
 * Full Settings screen — slide-in from the sidebar.
 *
 * Sections:
 *  1. Memory Mode       — Conservative / Balanced / Performance
 *  2. Fine-grain limits — max recent live tabs, max keep-alive tabs
 *  3. Memory Dashboard  — live session count, breakdown by state
 *  4. Browser prefs     — homepage, search engine
 *  5. Display           — keep screen awake while charging
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDashboard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshLiveSessionCount() }

    if (showDashboard) {
        MemoryDashboardScreen(
            viewModel = viewModel,
            onDismiss = { showDashboard = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Settings",
                color      = Color.White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Divider(color = Color(0xFF1E1E1E))

        // ── 1. Memory Mode ────────────────────────────────────────────────
        SectionHeader("Memory Mode")

        MemoryModeSelector(
            selected  = state.memoryMode,
            onSelect  = { viewModel.setMemoryMode(it) },
            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── 2. Fine-grain limits ──────────────────────────────────────────
        SectionHeader("Session Limits")

        StepperRow(
            label       = "Max recent live tabs",
            description = "Sessions kept open after you switch away (0 = unload immediately)",
            value       = state.maxRecentLiveTabs,
            min         = 0,
            max         = 10,
            onDecrement = { viewModel.setMaxRecentLiveTabs(state.maxRecentLiveTabs - 1) },
            onIncrement = { viewModel.setMaxRecentLiveTabs(state.maxRecentLiveTabs + 1) },
        )

        StepperRow(
            label       = "Max Keep Alive tabs",
            description = "Cap on user-marked Keep Alive sessions",
            value       = state.maxKeepAliveTabs,
            min         = 1,
            max         = 10,
            onDecrement = { viewModel.setMaxKeepAliveTabs(state.maxKeepAliveTabs - 1) },
            onIncrement = { viewModel.setMaxKeepAliveTabs(state.maxKeepAliveTabs + 1) },
        )

        // ── 3. Memory Dashboard ───────────────────────────────────────────
        SectionHeader("Memory Dashboard")

        MemoryDashboard(
            liveSessionCount = state.liveSessionCount,
            memoryMode       = state.memoryMode,
            maxRecent        = state.maxRecentLiveTabs,
            maxKeepAlive     = state.maxKeepAliveTabs,
            onExpand         = { showDashboard = true },
            modifier         = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── 4. Browser Preferences ────────────────────────────────────────
        SectionHeader("Browser")

        HomepageRow(
            homepage  = state.defaultHomepage,
            onSave    = { viewModel.setDefaultHomepage(it) },
        )

        SearchEngineRow(
            selected  = state.defaultSearch,
            onSelect  = { viewModel.setDefaultSearchEngine(it) },
        )

        // ── 5. Display ────────────────────────────────────────────────────
        SectionHeader("Display")

        ToggleRow(
            icon        = Icons.Filled.LightMode,
            label       = "Keep screen awake",
            description = "While charging — useful for long-running Keep Alive tabs",
            checked     = state.keepScreenAwake,
            onToggle    = { viewModel.setKeepScreenAwake(it) },
        )

        Spacer(Modifier.height(40.dp))
    }
}

// ── Memory Mode Selector ───────────────────────────────────────────────────

@Composable
private fun MemoryModeSelector(
    selected : MemoryMode,
    onSelect : (MemoryMode) -> Unit,
    modifier : Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryMode.values().forEach { mode ->
            val isSelected = mode == selected
            val borderColor = if (isSelected) Color(0xFF9C6FFF) else Color(0xFF2A2A2A)
            val bg = if (isSelected) Color(0xFF1A1228) else Color(0xFF141414)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable { onSelect(mode) }
                    .padding(14.dp),
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick  = { onSelect(mode) },
                    colors   = RadioButtonDefaults.colors(
                        selectedColor   = Color(0xFF9C6FFF),
                        unselectedColor = Color(0xFF444444),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mode.label,
                            color      = if (isSelected) Color.White else Color(0xFFAAAAAA),
                            fontSize   = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (mode == MemoryMode.BALANCED) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Recommended",
                                color    = Color(0xFF9C6FFF),
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF9C6FFF).copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modeDescription(mode),
                        color    = Color(0xFF666666),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun modeDescription(mode: MemoryMode) = when (mode) {
    MemoryMode.CONSERVATIVE -> "Keep only active tab live. Unload all others immediately. Lowest RAM use."
    MemoryMode.BALANCED     -> "Keep 1–2 recent tabs live. Balanced speed and memory."
    MemoryMode.PERFORMANCE  -> "Keep up to 5 recent tabs live. Faster switching. Higher RAM use."
}

// ── Stepper Row ────────────────────────────────────────────────────────────

@Composable
private fun StepperRow(
    label      : String,
    description: String,
    value      : Int,
    min        : Int,
    max        : Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = Color(0xFF666666), fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick  = onDecrement,
                enabled  = value > min,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.Remove, "-",
                    tint     = if (value > min) Color.White else Color(0xFF3A3A3A),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                "$value",
                color      = Color(0xFF9C6FFF),
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.widthIn(min = 28.dp),
            )
            IconButton(
                onClick  = onIncrement,
                enabled  = value < max,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.Add, "+",
                    tint     = if (value < max) Color.White else Color(0xFF3A3A3A),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Memory Dashboard ───────────────────────────────────────────────────────

@Composable
private fun MemoryDashboard(
    liveSessionCount: Int,
    memoryMode      : MemoryMode,
    maxRecent       : Int,
    maxKeepAlive    : Int,
    onExpand        : () -> Unit = {},
    modifier        : Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Memory, null, tint = Color(0xFF9C6FFF), modifier = Modifier.size(18.dp))
            Text(
                "Live GeckoSessions",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$liveSessionCount",
                color      = Color(0xFF9C6FFF),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.OpenInFull, "Expand dashboard",
                    tint     = Color(0xFF666666),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Divider(color = Color(0xFF1E1E1E))

        // Policy summary
        DashRow("Mode",             memoryMode.label,       Color(0xFF9C6FFF))
        DashRow("Max recent live",  "$maxRecent tabs",      Color(0xFF4FC3F7))
        DashRow("Max Keep Alive",   "$maxKeepAlive tabs",   Color(0xFFFFB74D))

        Divider(color = Color(0xFF1E1E1E))

        // Lifecycle key
        Text(
            "Lifecycle key",
            color    = Color(0xFF555555),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("●", "Active",    Color(0xFF9C6FFF))
            LegendItem("◆", "Keep Alive", Color(0xFFFFB74D))
            LegendItem("◐", "Recent",    Color(0xFF4FC3F7))
            LegendItem("○", "Unloaded",  Color(0xFF444444))
        }
    }
}

@Composable
private fun DashRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendItem(icon: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(icon,  color = color, fontSize = 10.sp)
        Text(label, color = color, fontSize = 10.sp)
    }
}

// ── Homepage Row ───────────────────────────────────────────────────────────

@Composable
private fun HomepageRow(homepage: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft   by remember(homepage) { mutableStateOf(homepage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("Default Homepage", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        if (editing) {
            OutlinedTextField(
                value         = draft,
                onValueChange = { draft = it },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFF9C6FFF),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = Color(0xFF9C6FFF),
                ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { onSave(draft); editing = false }) {
                        Icon(Icons.Filled.Check, "Save", tint = Color(0xFF9C6FFF))
                    }
                }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .clickable { editing = true }
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Home, null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    homepage,
                    color    = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.Edit, "Edit", tint = Color(0xFF555555), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Search Engine Row ──────────────────────────────────────────────────────

@Composable
private fun SearchEngineRow(selected: SearchEngine, onSelect: (SearchEngine) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("Default Search Engine", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchEngine.values().forEach { engine ->
                val isSelected = engine == selected
                FilterChip(
                    selected = isSelected,
                    onClick  = { onSelect(engine) },
                    label    = { Text(engine.label, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor    = Color(0xFF9C6FFF),
                        selectedLabelColor        = Color.Black,
                        containerColor            = Color(0xFF1A1A1A),
                        labelColor                = Color(0xFF888888),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled           = true,
                        selected          = isSelected,
                        borderColor       = Color(0xFF2A2A2A),
                        selectedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

// ── Toggle Row ─────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    icon       : ImageVector,
    label      : String,
    description: String,
    checked    : Boolean,
    onToggle   : (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = Color(0xFF666666), fontSize = 11.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Color(0xFF9C6FFF),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2A2A2A),
            ),
        )
    }
}

// ── Section Header ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        color    = Color(0xFF555555),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
    )
}
