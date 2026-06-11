package com.aweb.browser.ui.keepalive

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Small bottom-right toast that confirms Keep Alive was enabled or disabled.
 * Auto-dismisses after 2 seconds.
 */
@Composable
fun KeepAliveToast(
    message  : String,
    isEnable : Boolean,
    onDismiss: () -> Unit,
    modifier : Modifier = Modifier,
) {
    LaunchedEffect(message) {
        delay(2_000)
        onDismiss()
    }

    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter   = slideInVertically { it / 2 } + fadeIn(),
        exit    = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            KeepAliveBolt(
                size     = KeepAliveIndicatorSize.MEDIUM,
                animated = isEnable,
            )
            Text(
                text       = message,
                color      = if (isEnable) KeepAliveColor else Color(0xFF888888),
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
