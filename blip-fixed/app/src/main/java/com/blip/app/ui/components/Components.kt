package com.blip.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.blip.app.data.model.*
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import java.text.SimpleDateFormat
import java.util.*

// ─── Avatar ───────────────────────────────────────────────────────────────────

@Composable
fun BlipAvatar(
    name: String,
    color: Int,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    showOnline: Boolean = false
) {
    Box(modifier = modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(color))
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Bold
            )
        }
        if (showOnline) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .border(2.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .background(BlipColors.Online)
            )
        }
    }
}

// ─── Message Bubble ───────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: BlipMessage, isMe: Boolean) {
    val bubbleColor = if (isMe) BlipColors.MessageSent else BlipColors.MessageRecv
    val textColor   = if (isMe) Color.White else BlipColors.OnSurface
    val alignment   = if (isMe) Arrangement.End else Arrangement.Start
    val shape = if (isMe)
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.TEXT -> Text(
                        text = message.content,
                        color = textColor,
                        fontFamily = SoraFamily,
                        fontSize = 15.sp
                    )
                    MessageType.VOICE -> VoiceBubble(message, isMe)
                    MessageType.LOCATION -> LocationBubble(message, isMe)
                    MessageType.IMAGE -> ImageBubble(message, isMe)
                    else -> Text(message.content, color = textColor, fontFamily = SoraFamily)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else BlipColors.Muted,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceBubble(message: BlipMessage, isMe: Boolean) {
    val color = if (isMe) Color.White else BlipColors.Primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PlayArrow, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        // Waveform visualization (static bars)
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(12f, 20f, 16f, 28f, 18f, 24f, 14f, 22f, 10f, 18f).forEach { height ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height.dp)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.7f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("0:00", color = color, fontSize = 12.sp, fontFamily = SoraFamily)
    }
}

@Composable
fun LocationBubble(message: BlipMessage, isMe: Boolean) {
    val color = if (isMe) Color.White else BlipColors.Primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Location shared", color = color, fontFamily = SoraFamily, fontSize = 14.sp)
    }
}

@Composable
fun ImageBubble(message: BlipMessage, isMe: Boolean) {
    val color = if (isMe) Color.White else BlipColors.Primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Image, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Image", color = color, fontFamily = SoraFamily, fontSize = 14.sp)
    }
}

@Composable
fun StatusIcon(status: MessageStatus) {
    val (icon, color) = when (status) {
        MessageStatus.SENDING   -> Icons.Default.Schedule to Color.White.copy(alpha = 0.6f)
        MessageStatus.SENT      -> Icons.Default.Check to Color.White.copy(alpha = 0.8f)
        MessageStatus.DELIVERED -> Icons.Default.DoneAll to Color.White
        MessageStatus.FAILED    -> Icons.Default.ErrorOutline to BlipColors.Danger
    }
    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
}

// ─── Nearby User Card ─────────────────────────────────────────────────────────

@Composable
fun UserCard(user: com.blip.app.data.model.BlipUser, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BlipColors.Surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BlipAvatar(user.name, user.avatarColor, size = 48.dp, showOnline = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name, fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = BlipColors.OnSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SignalCellularAlt, null,
                        modifier = Modifier.size(12.dp),
                        tint = signalColor(user.rssi)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        rssiLabel(user.rssi),
                        fontFamily = SoraFamily, fontSize = 12.sp, color = BlipColors.Muted
                    )
                    if (user.meshHops > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "via ${user.meshHops} hop${if (user.meshHops > 1) "s" else ""}",
                            fontFamily = SoraFamily, fontSize = 11.sp,
                            color = BlipColors.Primary
                        )
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = BlipColors.Muted)
        }
    }
}

// ─── Signal helpers ───────────────────────────────────────────────────────────

fun signalColor(rssi: Int) = when {
    rssi >= -60 -> BlipColors.Online
    rssi >= -80 -> Color(0xFFFFAB00)
    else        -> BlipColors.Danger
}

fun rssiLabel(rssi: Int) = when {
    rssi >= -60 -> "Excellent (${rssi}dBm)"
    rssi >= -70 -> "Good (${rssi}dBm)"
    rssi >= -80 -> "Fair (${rssi}dBm)"
    else        -> "Weak (${rssi}dBm)"
}

fun formatTime(ts: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

// ─── Gradient Top Bar ─────────────────────────────────────────────────────────

@Composable
fun BlipTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(BlipColors.GradientStart, BlipColors.GradientEnd)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                title, modifier = Modifier.weight(1f),
                color = Color.White, fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp
            )
            actions()
        }
    }
}

// ─── Gradient FAB ─────────────────────────────────────────────────────────────

@Composable
fun GradientFab(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(BlipColors.GradientStart, BlipColors.GradientEnd),
                    start = Offset(0f, 0f), end = Offset(100f, 100f)
                )
            )
            .clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Color.White)
    }
}
