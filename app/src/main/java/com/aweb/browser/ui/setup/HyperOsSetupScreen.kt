package com.aweb.browser.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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

/**
 * HyperOS Setup Guide — shown on first launch (or from Settings).
 *
 * Walks the user through the 6 steps needed for best 24/7 survival on
 * Redmi Pad SE 4G / HyperOS:
 *
 *  Step 1 — Enable Autostart
 *  Step 2 — Disable Battery Optimization
 *  Step 3 — Battery Saver → No Restrictions
 *  Step 4 — Lock in Recent Apps
 *  Step 5 — Allow Notifications
 *  Step 6 — (Optional) Keep Screen Awake while charging
 *
 * Each step has:
 *  - Status indicator (done / pending / optional)
 *  - Description
 *  - "Open Settings" deep-link button where Android allows it
 *  - Manual instruction where deep-link is not possible
 */
@Composable
fun HyperOsSetupScreen(
    onDismiss   : () -> Unit,
    onAllDone   : () -> Unit = {},
    completedSteps: Set<String> = emptySet(),
    onStepDoneChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val steps   = rememberSetupSteps(context)

    val allRequired = steps.filter { !it.isOptional }
    val doneCount   = allRequired.count { it.id in completedSteps }
    val allComplete = doneCount == allRequired.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column {
                Text("HyperOS Setup", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$doneCount / ${allRequired.size} required steps done",
                    color = if (allComplete) Color(0xFF81C784) else Color(0xFF666666),
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (allComplete) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(28.dp))
            }
        }

        HorizontalDivider(color = Color(0xFF1E1E1E))

        // ── Intro ─────────────────────────────────────────────────────────
        Text(
            "AWEB needs these settings to survive 24/7 on HyperOS.\n" +
            "Without them, HyperOS may kill the app or freeze Keep Alive tabs.",
            color    = Color(0xFF888888),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        // ── Steps ─────────────────────────────────────────────────────────
        steps.forEachIndexed { index, step ->
            val isDone = step.id in completedSteps
            SetupStepCard(
                stepNumber = index + 1,
                step       = step,
                isDone     = isDone,
                onDoneChange = { done -> onStepDoneChange(step.id, done) },
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Done button ───────────────────────────────────────────────────
        Button(
            onClick = { onAllDone(); onDismiss() },
            enabled = allComplete,
            colors  = ButtonDefaults.buttonColors(
                containerColor        = Color(0xFF9C6FFF),
                contentColor          = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor  = Color(0xFF555555),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp),
        ) {
            Text(
                if (allComplete) "All done — AWEB is ready!" else "Complete all required steps",
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
            )
        }

        // Skip / remind later
        TextButton(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            Text("Remind me later", color = Color(0xFF555555), fontSize = 12.sp)
        }
    }
}

// ── Step card ──────────────────────────────────────────────────────────────

@Composable
private fun SetupStepCard(
    stepNumber : Int,
    step       : SetupStep,
    isDone     : Boolean,
    onDoneChange: (Boolean) -> Unit,
    modifier   : Modifier = Modifier,
) {
    val expanded  = remember { mutableStateOf(false) }
    val context   = LocalContext.current

    val borderColor = when {
        isDone           -> Color(0xFF81C784).copy(alpha = 0.4f)
        step.isOptional  -> Color(0xFF2A2A2A)
        else             -> Color(0xFF9C6FFF).copy(alpha = 0.3f)
    }
    val bg = when {
        isDone -> Color(0xFF0D1A0D)
        else   -> Color(0xFF141414)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { expanded.value = !expanded.value }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Step number circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone          -> Color(0xFF81C784)
                            step.isOptional -> Color(0xFF2A2A2A)
                            else            -> Color(0xFF9C6FFF)
                        }
                    ),
            ) {
                if (isDone) {
                    Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        "$stepNumber",
                        color      = if (step.isOptional) Color(0xFF888888) else Color.Black,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        step.title,
                        color      = if (isDone) Color(0xFF81C784) else Color.White,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (step.isOptional) {
                        Text(
                            "Optional",
                            color    = Color(0xFF666666),
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    step.subtitle,
                    color    = Color(0xFF666666),
                    fontSize = 11.sp,
                )
            }

            Icon(
                if (expanded.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                tint     = Color(0xFF555555),
                modifier = Modifier.size(20.dp),
            )
        }

        // Expanded details
        AnimatedVisibility(visible = expanded.value) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = Color(0xFF1E1E1E))
                Spacer(Modifier.height(10.dp))

                // Manual path
                Text(
                    step.manualPath,
                    color    = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Deep link button
                    if (step.deepLinkAction != null) {
                        Button(
                            onClick = {
                                try { step.deepLinkAction.invoke(context) } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9C6FFF),
                                contentColor   = Color.Black,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Mark done toggle
                    OutlinedButton(
                        onClick = { onDoneChange(!isDone) },
                        border  = BorderStroke(
                            1.dp,
                            if (isDone) Color(0xFF81C784) else Color(0xFF444444),
                        ),
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDone) Color(0xFF81C784) else Color(0xFF888888),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            if (isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isDone) "Done" else "Mark done", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Setup step model ───────────────────────────────────────────────────────

data class SetupStep(
    val id             : String,
    val title          : String,
    val subtitle       : String,
    val manualPath     : String,
    val isOptional     : Boolean              = false,
    val deepLinkAction : ((Context) -> Unit)? = null,
)

@Composable
private fun rememberSetupSteps(context: Context): List<SetupStep> = remember {
    buildList {

        // Step 1 — Autostart
        add(SetupStep(
            id         = HyperOsSetupStepIds.AUTOSTART,
            title      = "Enable Autostart",
            subtitle   = "Lets AWEB start itself after reboot",
            manualPath = "Settings → Apps → AWEB → Autostart → ON",
            deepLinkAction = {
                it.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data  = Uri.fromParts("package", it.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
        ))

        // Step 2 — Battery Optimization
        add(SetupStep(
            id         = HyperOsSetupStepIds.BATTERY_OPTIMIZATION,
            title      = "Disable Battery Optimization",
            subtitle   = "Prevents HyperOS from freezing AWEB in background",
            manualPath = "Settings → Apps → AWEB → Battery → No Restrictions",
            deepLinkAction = {
                val pm = it.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(it.packageName)) {
                    it.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data  = Uri.fromParts("package", it.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            },
        ))

        // Step 3 — HyperOS Battery Saver
        add(SetupStep(
            id         = HyperOsSetupStepIds.HYPEROS_BATTERY_SAVER,
            title      = "Set Battery Saver → No Restrictions",
            subtitle   = "HyperOS-specific setting beyond standard Android",
            manualPath = "Settings → Apps → Manage Apps → AWEB\n→ Battery Saver → No Restrictions",
            deepLinkAction = {
                it.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data  = Uri.fromParts("package", it.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
        ))

        // Step 4 — Lock in recents (manual only — no deep link possible)
        add(SetupStep(
            id         = HyperOsSetupStepIds.LOCK_RECENTS,
            title      = "Lock AWEB in Recent Apps",
            subtitle   = "Prevents HyperOS from clearing it when RAM is needed",
            manualPath = "Open Recents → Long-press AWEB card → Lock",
            deepLinkAction = null,
        ))

        // Step 5 — Notifications
        add(SetupStep(
            id         = HyperOsSetupStepIds.NOTIFICATIONS,
            title      = "Allow Notifications",
            subtitle   = "Required for the persistent foreground service notification",
            manualPath = "Settings → Apps → AWEB → Notifications → Allow",
            deepLinkAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } else {
                    it.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data  = Uri.fromParts("package", it.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            },
        ))

        // Step 6 — Keep screen awake (optional)
        add(SetupStep(
            id         = HyperOsSetupStepIds.KEEP_SCREEN_AWAKE,
            title      = "Keep Screen Awake While Charging",
            subtitle   = "Useful for always-on Keep Alive sessions (e.g. trading dashboards)",
            manualPath = "AWEB Settings → Display → Keep screen awake (toggle)\n" +
                         "or: Android Developer Options → Stay awake",
            isOptional = true,
        ))
    }
}
