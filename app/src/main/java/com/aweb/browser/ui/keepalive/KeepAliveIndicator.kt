package com.aweb.browser.ui.keepalive

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Amber Keep Alive bolt colour — consistent across all UI surfaces. */
val KeepAliveColor = Color(0xFFFFB74D)

/**
 * Pulsing bolt icon shown on Keep Alive tabs.
 *
 * Size variants:
 *  - [KeepAliveIndicatorSize.SMALL]  — tab strip chip (8dp icon)
 *  - [KeepAliveIndicatorSize.MEDIUM] — tab overview card (12dp icon)
 *  - [KeepAliveIndicatorSize.LARGE]  — Keep Alive panel header (20dp icon)
 */
enum class KeepAliveIndicatorSize(val iconDp: Dp, val labelSp: Float) {
    SMALL (8.dp,  0f),     // icon only
    MEDIUM(12.dp, 9f),     // icon + optional label
    LARGE (20.dp, 12f),    // icon + label always shown
}

@Composable
fun KeepAliveBolt(
    size     : KeepAliveIndicatorSize = KeepAliveIndicatorSize.SMALL,
    animated : Boolean                = true,
    label    : String?                = null,
    modifier : Modifier               = Modifier,
) {
    val scale by if (animated) {
        val inf = rememberInfiniteTransition(label = "kaPulse")
        inf.animateFloat(
            initialValue   = 1f,
            targetValue    = 1.25f,
            animationSpec  = infiniteRepeatable(
                animation  = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "kaScale",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier             = modifier,
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = "Keep Alive",
            tint     = KeepAliveColor,
            modifier = Modifier
                .size(size.iconDp)
                .scale(scale),
        )
        if (label != null && size.labelSp > 0f) {
            Text(
                text       = label,
                color      = KeepAliveColor,
                fontSize   = size.labelSp.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Amber badge pill — used in tab overview cards and the Keep Alive panel.
 */
@Composable
fun KeepAliveBadge(
    label    : String   = "Keep Alive",
    modifier : Modifier = Modifier,
) {
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier             = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(KeepAliveColor.copy(alpha = 0.15f))
            .border(0.5.dp, KeepAliveColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(KeepAliveColor),
        )
        Text(
            text       = label,
            color      = KeepAliveColor,
            fontSize   = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
