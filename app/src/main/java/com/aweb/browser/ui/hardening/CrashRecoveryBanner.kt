package com.aweb.browser.ui.hardening

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Non-intrusive amber banner shown at the top of BrowserScreen after a crash.
 *
 * Tells the user AWEB recovered from an unexpected exit, with the crash
 * time and a dismiss button. Does not block interaction.
 *
 * After 8 seconds it auto-dismisses.
 */
@Composable
fun CrashRecoveryBanner(
    crashMessage : String,
    crashTime    : Long,
    onDismiss    : () -> Unit,
    modifier     : Modifier = Modifier,
) {
    val timeStr = remember(crashTime) {
        if (crashTime > 0L)
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(crashTime))
        else "unknown time"
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        onDismiss()
    }

    AnimatedVisibility(
        visible  = true,
        enter    = slideInVertically { -it } + fadeIn(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(Color(0xFF3D2A00))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint     = Color(0xFFFFB74D),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "AWEB recovered from an unexpected exit",
                    color      = Color(0xFFFFB74D),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (crashMessage.isNotBlank()) {
                    Text(
                        "$crashMessage  •  $timeStr",
                        color    = Color(0xFF997744),
                        fontSize = 10.sp,
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    "Dismiss",
                    tint     = Color(0xFF997744),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
