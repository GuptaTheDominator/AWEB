package com.aweb.browser.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.browser.BrowserPermissionHandler.PermissionRequest

/**
 * Dialogs shown when a website requests camera, microphone, location,
 * notification, or download permissions.
 *
 * Design rule: always ask the user — never auto-grant.
 */
@Composable
fun PermissionDialog(
    request  : PermissionRequest,
    onAllow  : () -> Unit,
    onDeny   : () -> Unit,
) {
    when (request) {
        is PermissionRequest.MediaRequest   -> MediaPermDialog(request, onAllow, onDeny)
        is PermissionRequest.LocationRequest -> LocationPermDialog(request, onAllow, onDeny)
        is PermissionRequest.NotificationRequest -> NotifPermDialog(request, onAllow, onDeny)
        is PermissionRequest.DownloadRequest -> DownloadConfirmDialog(request, onAllow, onDeny)
    }
}

// ── Media (Camera / Mic) ───────────────────────────────────────────────────

@Composable
private fun MediaPermDialog(
    req     : PermissionRequest.MediaRequest,
    onAllow : () -> Unit,
    onDeny  : () -> Unit,
) {
    val icon = when {
        req.hasVideo && req.hasAudio -> Icons.Filled.Videocam
        req.hasVideo                 -> Icons.Filled.Camera
        else                         -> Icons.Filled.Mic
    }
    val what = when {
        req.hasVideo && req.hasAudio -> "camera and microphone"
        req.hasVideo                 -> "camera"
        else                         -> "microphone"
    }
    PermBaseDialog(
        icon        = icon,
        iconTint    = Color(0xFF4FC3F7),
        title       = "Allow $what access?",
        body        = "${req.origin} is requesting access to your $what.",
        allowLabel  = "Allow",
        denyLabel   = "Deny",
        onAllow     = onAllow,
        onDeny      = onDeny,
    )
}

// ── Location ───────────────────────────────────────────────────────────────

@Composable
private fun LocationPermDialog(
    req     : PermissionRequest.LocationRequest,
    onAllow : () -> Unit,
    onDeny  : () -> Unit,
) {
    PermBaseDialog(
        icon       = Icons.Filled.LocationOn,
        iconTint   = Color(0xFF81C784),
        title      = "Allow location access?",
        body       = "${req.origin} wants to know your location.",
        allowLabel = "Allow",
        denyLabel  = "Deny",
        onAllow    = onAllow,
        onDeny     = onDeny,
    )
}

// ── Notifications ──────────────────────────────────────────────────────────

@Composable
private fun NotifPermDialog(
    req     : PermissionRequest.NotificationRequest,
    onAllow : () -> Unit,
    onDeny  : () -> Unit,
) {
    PermBaseDialog(
        icon       = Icons.Filled.Notifications,
        iconTint   = Color(0xFFFFB74D),
        title      = "Allow notifications?",
        body       = "${req.origin} wants to send you notifications.",
        allowLabel = "Allow",
        denyLabel  = "Block",
        onAllow    = onAllow,
        onDeny     = onDeny,
    )
}

// ── Download confirm ───────────────────────────────────────────────────────

@Composable
fun DownloadConfirmDialog(
    req     : PermissionRequest.DownloadRequest,
    onAllow : () -> Unit,
    onDeny  : () -> Unit,
) {
    val sizeText = if (req.size > 0) {
        val mb = req.size / 1_048_576.0
        if (mb >= 1) "%.1f MB".format(mb) else "${req.size / 1024} KB"
    } else ""

    AlertDialog(
        onDismissRequest  = onDeny,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        icon = {
            Icon(Icons.Filled.Download, null,
                tint = Color(0xFF9C6FFF), modifier = Modifier.size(28.dp))
        },
        title = { Text("Download file?", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(req.filename, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (req.mimeType != null)
                    Text(req.mimeType, color = Color(0xFF888888), fontSize = 11.sp)
                if (sizeText.isNotEmpty())
                    Text(sizeText, color = Color(0xFF888888), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text("File will be saved to Downloads.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text("Download", color = Color(0xFF9C6FFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
    )
}

// ── Base dialog layout ─────────────────────────────────────────────────────

@Composable
private fun PermBaseDialog(
    icon      : ImageVector,
    iconTint  : Color,
    title     : String,
    body      : String,
    allowLabel: String,
    denyLabel : String,
    onAllow   : () -> Unit,
    onDeny    : () -> Unit,
) {
    AlertDialog(
        onDismissRequest  = onDeny,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        icon  = { Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp)) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(body, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(allowLabel, color = iconTint, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(denyLabel, color = Color(0xFF888888))
            }
        },
    )
}
