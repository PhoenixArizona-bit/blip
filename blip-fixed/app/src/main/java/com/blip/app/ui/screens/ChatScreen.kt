package com.blip.app.ui.screens

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.blip.app.ui.components.*
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import com.blip.app.viewmodel.BlipViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    peerId: String,
    peerName: String,
    peerColor: Int,
    viewModel: BlipViewModel,
    onBack: () -> Unit
) {
    val chatId = peerId
    val messages by viewModel.getMessages(chatId).collectAsState(initial = emptyList())
    val localUserId by viewModel.localUserId.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = { ChatTopBar(peerName, peerColor, onBack) },
        containerColor = BlipColors.Background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg, isMe = msg.senderId == localUserId)
                }
            }

            AnimatedVisibility(showAttachMenu) {
                AttachMenu(
                    onLocation = {
                        showAttachMenu = false
                        viewModel.sendLocationMessage(chatId, peerId, peerName, 31.5204, 74.3587)
                    },
                    onFile = { showAttachMenu = false },
                    onImage = { showAttachMenu = false }
                )
            }

            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                        Icon(
                            Icons.Default.AttachFile, null,
                            tint = if (showAttachMenu) BlipColors.Primary else BlipColors.Muted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(BlipColors.SurfaceVar, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                "Message...", fontFamily = SoraFamily,
                                fontSize = 15.sp, color = BlipColors.Muted
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = SoraFamily, fontSize = 15.sp,
                                color = BlipColors.OnSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (inputText.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.sendTextMessage(chatId, peerId, peerName, inputText.trim())
                            inputText = ""
                        }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BlipColors.GradientStart, BlipColors.GradientEnd)
                                        ),
                                        CircleShape
                                    )
                            ) {
                                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isRecording) BlipColors.Danger else BlipColors.SurfaceVar,
                                    CircleShape
                                )
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitPointerEvent()
                                        if (down.type == PointerEventType.Press) {
                                            if (audioPermission.status.isGranted) {
                                                isRecording = true
                                                viewModel.startVoiceRecording()
                                            } else {
                                                audioPermission.launchPermissionRequest()
                                            }
                                            // Wait for release
                                            val up = awaitPointerEvent()
                                            if (up.type == PointerEventType.Release && isRecording) {
                                                isRecording = false
                                                viewModel.stopVoiceRecording(chatId, peerId, peerName)
                                            }
                                        }
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Default.Mic, null,
                                tint = if (isRecording) Color.White else BlipColors.Muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatTopBar(peerName: String, peerColor: Int, onBack: () -> Unit) {
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
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            BlipAvatar(peerName, peerColor, size = 36.dp, showOnline = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    peerName, color = Color.White, fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                Text(
                    "via Bluetooth mesh", color = Color.White.copy(alpha = 0.75f),
                    fontFamily = SoraFamily, fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun AttachMenu(onLocation: () -> Unit, onFile: () -> Unit, onImage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AttachButton("Location", Icons.Default.LocationOn, BlipColors.Primary, onLocation)
        AttachButton("Image", Icons.Default.Image, Color(0xFF7C4DFF), onImage)
        AttachButton("File", Icons.Default.AttachFile, Color(0xFF00BCD4), onFile)
    }
}

@Composable
fun AttachButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontFamily = SoraFamily, fontSize = 11.sp, color = BlipColors.Muted)
    }
}
