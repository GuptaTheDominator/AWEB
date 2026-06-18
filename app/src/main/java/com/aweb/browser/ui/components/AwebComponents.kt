package com.aweb.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.ui.theme.AwebColors

@Composable
fun AwebPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AwebColors.InkDeep, AwebColors.Ink, AwebColors.Navy)
                )
            ),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = AwebColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = AwebColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    accent: Color = AwebColors.PrimaryBlue,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AwebColors.Surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(label, color = AwebColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun AwebPill(
    text: String,
    color: Color = AwebColors.Cyan,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
fun WorkspaceAvatar(
    name: String,
    colorHex: String?,
    modifier: Modifier = Modifier,
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex ?: "#2F8CFF")) }
        .getOrDefault(AwebColors.PrimaryBlue)
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "A" },
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SecurityLine(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Security,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AwebColors.Teal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = AwebColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AwebActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = AwebColors.Surface.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AwebColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = AwebColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
