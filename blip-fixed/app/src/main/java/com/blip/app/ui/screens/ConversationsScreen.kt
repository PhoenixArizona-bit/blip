package com.blip.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.blip.app.ui.components.*
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import com.blip.app.viewmodel.BlipViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationsScreen(
    viewModel: BlipViewModel,
    onOpenChat: (peerId: String, peerName: String, peerColor: Int) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { BlipTopBar("Chats") },
        containerColor = BlipColors.Background
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No chats yet",
                        fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp, color = BlipColors.OnSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a conversation from the\nNearby tab",
                        fontFamily = SoraFamily, fontSize = 14.sp,
                        color = BlipColors.Muted,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenChat(conv.peerId, conv.peerName, conv.peerAvatarColor)
                            },
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BlipAvatar(conv.peerName, conv.peerAvatarColor, size = 52.dp, showOnline = false)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conv.peerName,
                                        fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp, color = BlipColors.OnSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        formatChatTime(conv.lastMessageTime),
                                        fontFamily = SoraFamily, fontSize = 11.sp,
                                        color = BlipColors.Muted
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        conv.lastMessage,
                                        fontFamily = SoraFamily, fontSize = 13.sp,
                                        color = BlipColors.Muted, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (conv.unreadCount > 0) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(BlipColors.Primary, CircleShape)
                                        ) {
                                            Text(
                                                "${conv.unreadCount}",
                                                fontFamily = SoraFamily, fontSize = 10.sp,
                                                color = Color.White, fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = BlipColors.Outline, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
                }
            }
        }
    }
}

fun formatChatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000    -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
        else             -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
