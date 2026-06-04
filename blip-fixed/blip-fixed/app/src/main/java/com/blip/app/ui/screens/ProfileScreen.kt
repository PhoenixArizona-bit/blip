package com.blip.app.ui.screens

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
import androidx.compose.ui.unit.*
import com.blip.app.ui.components.BlipAvatar
import com.blip.app.ui.components.BlipTopBar
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import com.blip.app.viewmodel.BlipViewModel

@Composable
fun ProfileScreen(viewModel: BlipViewModel) {
    val userName    by viewModel.userName.collectAsState(initial = null)
    val avatarColor by viewModel.avatarColor.collectAsState(initial = BlipColors.AvatarColors[0])
    val stealthMode by viewModel.stealthMode.collectAsState(initial = false)
    var editingName by remember { mutableStateOf(false) }
    var nameInput   by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(userName) { nameInput = userName ?: "" }

    Scaffold(
        topBar = { BlipTopBar("Profile") },
        containerColor = BlipColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Avatar header ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(BlipColors.GradientStart, BlipColors.Background)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        BlipAvatar(userName ?: "?", avatarColor, size = 80.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color.White, CircleShape)
                                .clickable { showColorPicker = !showColorPicker }
                                .shadow(2.dp, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Edit, null,
                                tint = BlipColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        userName ?: "Unknown",
                        color = Color.White,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            // ── Color picker ───────────────────────────────────────────────
            if (showColorPicker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Choose color", fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = BlipColors.OnSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        BlipColors.AvatarColors.chunked(5).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(color))
                                            .clickable {
                                                viewModel.updateAvatarColor(color)
                                                showColorPicker = false
                                            }
                                            .then(
                                                if (color == avatarColor)
                                                    Modifier.border(3.dp, BlipColors.Primary, CircleShape)
                                                else Modifier
                                            )
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // ── Settings list ──────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                // Name edit
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Display Name", fontFamily = SoraFamily,
                        fontSize = 12.sp, color = BlipColors.Muted
                    )
                    Spacer(Modifier.height(4.dp))
                    if (editingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.updateName(nameInput.trim())
                                    editingName = false
                                }) {
                                    Icon(Icons.Default.Check, null, tint = BlipColors.Primary)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = SoraFamily, fontSize = 15.sp
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingName = true }
                        ) {
                            Text(
                                userName ?: "Tap to set name",
                                fontFamily = SoraFamily, fontSize = 16.sp,
                                color = BlipColors.OnSurface, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.Edit, null, tint = BlipColors.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = BlipColors.Outline)

                // Stealth mode
                SettingToggleRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Stealth Mode",
                    subtitle = "Hide from nearby discovery",
                    checked = stealthMode,
                    onToggle = { viewModel.toggleStealthMode(it) }
                )
            }

            // ── Security info card ─────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Lock, null,
                        tint = BlipColors.Online, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "End-to-End Encrypted",
                            fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = Color(0xFF1B5E20)
                        )
                        Text(
                            "All messages are encrypted with ECDH + AES-GCM. No servers. No logs.",
                            fontFamily = SoraFamily, fontSize = 12.sp,
                            color = Color(0xFF388E3C), lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = BlipColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = SoraFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = BlipColors.OnSurface)
            Text(subtitle, fontFamily = SoraFamily, fontSize = 12.sp, color = BlipColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BlipColors.Primary)
        )
    }
}
