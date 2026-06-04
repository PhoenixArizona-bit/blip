package com.blip.app.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.*
import com.blip.app.ui.components.*
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import com.blip.app.viewmodel.BlipViewModel

@Composable
fun HomeScreen(
    viewModel: BlipViewModel,
    onOpenChat: (peerId: String, peerName: String, peerColor: Int) -> Unit,
    onOpenMeshMap: () -> Unit
) {
    val nearbyUsers by viewModel.nearbyUsers.collectAsState()
    val isScanning  by viewModel.isScanning.collectAsState()
    val userName    by viewModel.userName.collectAsState(initial = null)
    val avatarColor by viewModel.avatarColor.collectAsState(initial = BlipColors.AvatarColors[0])

    LaunchedEffect(Unit) { viewModel.startMesh() }

    Scaffold(
        topBar = {
            BlipTopBar(
                title = "Blip",
                actions = {
                    IconButton(onClick = onOpenMeshMap) {
                        Icon(Icons.Default.Hub, null, tint = Color.White)
                    }
                }
            )
        },
        containerColor = BlipColors.Background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            // ── Self Card ──────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlipAvatar(
                        name = userName ?: "?",
                        color = avatarColor,
                        size = 52.dp,
                        showOnline = isScanning
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            userName ?: "Unknown",
                            fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = BlipColors.OnSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isScanning) BlipColors.Online else BlipColors.Muted,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isScanning) "Mesh active" else "Offline",
                                fontFamily = SoraFamily, fontSize = 13.sp, color = BlipColors.Muted
                            )
                        }
                    }
                    // Mesh stats badge
                    val stats = viewModel.getMeshStats()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${stats.totalNodes}",
                            fontFamily = SoraFamily, fontWeight = FontWeight.Bold,
                            fontSize = 22.sp, color = BlipColors.Primary
                        )
                        Text(
                            "nearby", fontFamily = SoraFamily, fontSize = 11.sp,
                            color = BlipColors.Muted
                        )
                    }
                }
            }

            // ── Section header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby", fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = BlipColors.Muted,
                    modifier = Modifier.weight(1f)
                )
                if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = BlipColors.Primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Scanning...", fontFamily = SoraFamily,
                            fontSize = 12.sp, color = BlipColors.Primary
                        )
                    }
                }
            }

            // ── Users list ─────────────────────────────────────────────────
            if (nearbyUsers.isEmpty()) {
                EmptyNearbyState()
            } else {
                LazyColumn {
                    items(nearbyUsers, key = { it.id }) { user ->
                        UserCard(user) {
                            onOpenChat(user.id, user.name, user.avatarColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNearbyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📡", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "No one nearby yet",
            fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, color = BlipColors.OnSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Blip automatically discovers\nnearby users via Bluetooth.",
            fontFamily = SoraFamily, fontSize = 14.sp,
            color = BlipColors.Muted,
            lineHeight = 22.sp
        )
    }
}
