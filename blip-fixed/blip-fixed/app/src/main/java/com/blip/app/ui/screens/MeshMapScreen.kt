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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.blip.app.data.model.MeshNode
import com.blip.app.ui.components.BlipTopBar
import com.blip.app.ui.theme.BlipColors
import com.blip.app.ui.theme.SoraFamily
import com.blip.app.viewmodel.BlipViewModel
import kotlin.math.*

@Composable
fun MeshMapScreen(viewModel: BlipViewModel, onBack: () -> Unit) {
    val meshNodes by viewModel.meshNodes.collectAsState()
    val stats = viewModel.getMeshStats()

    Scaffold(
        topBar = { BlipTopBar("Mesh Network", onBack = onBack) },
        containerColor = BlipColors.Background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Stats row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Nodes", stats.totalNodes.toString(), BlipColors.Primary, Modifier.weight(1f))
                StatCard("Direct", stats.directPeers.toString(), BlipColors.Online, Modifier.weight(1f))
                StatCard("Relayed", stats.relayedPeers.toString(), Color(0xFF7C4DFF), Modifier.weight(1f))
            }

            // ── Network graph canvas ───────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B3E)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                if (meshNodes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No mesh nodes detected",
                                fontFamily = SoraFamily, fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    MeshCanvas(nodes = meshNodes)
                }
            }

            // ── Legend ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LegendItem("You", BlipColors.GradientStart)
                LegendItem("Direct peer", BlipColors.Online)
                LegendItem("Relayed", Color(0xFF7C4DFF))
                LegendItem("Connection", BlipColors.Muted)
            }
        }
    }
}

@Composable
fun MeshCanvas(nodes: List<MeshNode>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius  = minOf(size.width, size.height) * 0.35f

        // Position nodes in a circle around center
        val angleStep = 360f / nodes.size
        val nodePositions = nodes.mapIndexed { i, node ->
            val angle = Math.toRadians((i * angleStep - 90).toDouble())
            val x = (centerX + radius * cos(angle)).toFloat()
            val y = (centerY + radius * sin(angle)).toFloat()
            node to Offset(x, y)
        }.toMap()

        // Draw "self" at center
        drawCircle(
            brush = Brush.radialGradient(
                listOf(BlipColors.GradientStart, BlipColors.GradientEnd),
                center = Offset(centerX, centerY), radius = 28f
            ),
            center = Offset(centerX, centerY),
            radius = 24f
        )
        drawCircle(
            color = BlipColors.GradientStart.copy(alpha = 0.2f),
            center = Offset(centerX, centerY),
            radius = 40f
        )

        // Draw connections
        nodePositions.forEach { (node, pos) ->
            val lineColor = if (node.hopsFromSelf == 0) BlipColors.Online else Color(0xFF7C4DFF)
            drawLine(
                color = lineColor.copy(alpha = 0.4f),
                start = Offset(centerX, centerY),
                end = pos,
                strokeWidth = if (node.hopsFromSelf == 0) 2f else 1f,
                pathEffect = if (node.hopsFromSelf > 0)
                    PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
            )

            // Draw node circle
            val nodeColor = if (node.hopsFromSelf == 0) BlipColors.Online else Color(0xFF7C4DFF)
            drawCircle(
                color = nodeColor.copy(alpha = 0.2f),
                center = pos, radius = 32f
            )
            drawCircle(color = nodeColor, center = pos, radius = 20f)
            // RSSI ring
            val rssiRadius = lerp(28f, 48f, ((node.rssi + 100) / 60f).coerceIn(0f, 1f))
            drawCircle(
                color = nodeColor.copy(alpha = 0.15f),
                center = pos, radius = rssiRadius
            )
        }
    }
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value, fontFamily = SoraFamily, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = color
            )
            Text(
                label, fontFamily = SoraFamily, fontSize = 11.sp, color = BlipColors.Muted
            )
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontFamily = SoraFamily, fontSize = 11.sp, color = BlipColors.Muted)
    }
}
