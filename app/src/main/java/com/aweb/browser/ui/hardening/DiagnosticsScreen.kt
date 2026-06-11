package com.aweb.browser.ui.hardening

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.AppState

/**
 * Developer diagnostics screen — accessible from Settings → Diagnostics.
 *
 * Shows:
 *  - Current AppState snapshot (workspace, tab count, live sessions)
 *  - Crash info if any
 *  - App version and build type
 *  - Quick self-test results (isolation check, persistence check)
 */
@Composable
fun DiagnosticsScreen(
    onDismiss : () -> Unit,
    viewModel : HardeningViewModel,
) {
    val state   by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val tabs    = AppState.currentTabs
    val ws      = AppState.currentWorkspace

    // Self-test results
    var isolationResult   by remember { mutableStateOf<String?>(null) }
    var persistenceResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Diagnostics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ── App info ──────────────────────────────────────────────────────
        DiagSection("App") {
            val pm      = context.packageManager
            val pkgInfo = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
            DiagRow(Icons.Filled.Info, "Package",     context.packageName,        Color(0xFF9C6FFF))
            DiagRow(Icons.Filled.Tag,  "Version",     pkgInfo?.versionName ?: "?", Color(0xFF9C6FFF))
            DiagRow(Icons.Filled.Code, "Version code","${pkgInfo?.longVersionCode ?: 0}", Color(0xFF9C6FFF))
        }

        // ── Session state ─────────────────────────────────────────────────
        DiagSection("Session State") {
            DiagRow(Icons.Filled.Workspaces, "Active workspace",
                ws?.name ?: "none", Color(0xFF4FC3F7))
            DiagRow(Icons.Filled.Tab, "Total tabs",
                "${tabs.size}", Color(0xFF4FC3F7))
            DiagRow(Icons.Filled.Bolt, "Keep Alive tabs",
                "${tabs.count { it.keepAlive }}", Color(0xFFFFB74D))
            DiagRow(Icons.Filled.Circle, "Active tab",
                tabs.firstOrNull { it.isActive }?.title?.take(30) ?: "none", Color(0xFF81C784))
            DiagRow(Icons.Filled.Memory, "Context IDs",
                "${tabs.map { it.workspaceId }.toSet().size} workspace(s)", Color(0xFF9C6FFF))
        }

        // ── Crash info ────────────────────────────────────────────────────
        DiagSection("Crash Info") {
            val info = state.crashInfo
            if (info != null) {
                DiagRow(Icons.Filled.Warning, "Last crash", info.lastCrashMessage, Color(0xFFCF6679))
                DiagRow(Icons.Filled.Schedule, "Crash time",
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault()).format(java.util.Date(info.lastCrashTime)),
                    Color(0xFFCF6679))
            } else {
                DiagRow(Icons.Filled.CheckCircle, "No crash detected", "Clean exit", Color(0xFF81C784))
            }
        }

        // ── Self-tests ────────────────────────────────────────────────────
        DiagSection("Self-Tests") {
            // Isolation check
            Button(
                onClick = {
                    val wsIds = tabs.map { it.workspaceId }.toSet()
                    val ctxIds = wsIds.size
                    isolationResult = if (ctxIds == AppState.currentTabs
                            .groupBy { it.workspaceId }.keys.size)
                        "✓ Each workspace has a unique contextId (${ctxIds} total)"
                    else "✗ Isolation mismatch detected"
                },
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Icon(Icons.Filled.Security, null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run Isolation Check", color = Color(0xFF81C784), fontSize = 13.sp)
            }
            isolationResult?.let {
                Text(it, color = if (it.startsWith("✓")) Color(0xFF81C784) else Color(0xFFCF6679),
                    fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Persistence check
            Button(
                onClick = {
                    val hasTabs = tabs.isNotEmpty()
                    val hasWs   = ws != null
                    persistenceResult = when {
                        hasTabs && hasWs ->
                            "✓ Workspace + ${tabs.size} tabs persisted in Room"
                        hasWs ->
                            "⚠ Workspace found but no tabs"
                        else ->
                            "✗ No workspace or tabs in Room"
                    }
                },
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2A)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Icon(Icons.Filled.Storage, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run Persistence Check", color = Color(0xFF4FC3F7), fontSize = 13.sp)
            }
            persistenceResult?.let {
                Text(it, color = when {
                    it.startsWith("✓") -> Color(0xFF81C784)
                    it.startsWith("⚠") -> Color(0xFFFFB74D)
                    else               -> Color(0xFFCF6679)
                }, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun DiagSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color         = Color(0xFF555555),
        fontSize      = 10.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier      = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141414))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun DiagRow(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.width(130.dp))
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
    }
}
