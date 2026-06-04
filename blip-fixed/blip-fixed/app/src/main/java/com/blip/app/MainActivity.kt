package com.blip.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.blip.app.ui.screens.*
import com.blip.app.ui.theme.*
import com.blip.app.viewmodel.BlipViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlipTheme {
                BlipApp()
            }
        }
    }
}

// ─── Navigation ───────────────────────────────────────────────────────────────

object Route {
    const val ONBOARDING  = "onboarding"
    const val HOME        = "home"
    const val CHATS       = "chats"
    const val PROFILE     = "profile"
    const val CHAT        = "chat/{peerId}/{peerName}/{peerColor}"
    const val MESH_MAP    = "mesh_map"

    fun chat(peerId: String, peerName: String, peerColor: Int) =
        "chat/$peerId/$peerName/$peerColor"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Route.HOME,    "Nearby",    Icons.Default.NearMe),
    BottomNavItem(Route.CHATS,   "Chats",     Icons.Default.Chat),
    BottomNavItem(Route.PROFILE, "Profile",   Icons.Default.Person),
)

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun BlipApp() {
    val navController = rememberNavController()
    val viewModel: BlipViewModel = hiltViewModel()
    val isOnboarded by viewModel.isOnboarded.collectAsState(initial = null)
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDest = currentRoute?.destination?.route

    val showBottomBar = currentDest in listOf(Route.HOME, Route.CHATS, Route.PROFILE)

    // Wait until onboarding state loaded
    if (isOnboarded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BlipColors.Primary)
        }
        return
    }

    val startDest = if (isOnboarded == true) Route.HOME else Route.ONBOARDING

    Scaffold(
        bottomBar = {
            if (showBottomBar) BlipBottomNav(currentDest, navController)
        },
        containerColor = BlipColors.Background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.ONBOARDING) {
                OnboardingScreen(viewModel) {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                }
            }
            composable(Route.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenChat = { peerId, peerName, peerColor ->
                        navController.navigate(Route.chat(peerId, peerName, peerColor))
                    },
                    onOpenMeshMap = { navController.navigate(Route.MESH_MAP) }
                )
            }
            composable(Route.CHATS) {
                ConversationsScreen(viewModel) { peerId, peerName, peerColor ->
                    navController.navigate(Route.chat(peerId, peerName, peerColor))
                }
            }
            composable(Route.PROFILE) {
                ProfileScreen(viewModel)
            }
            composable(Route.MESH_MAP) {
                MeshMapScreen(viewModel) { navController.popBackStack() }
            }
            composable(Route.CHAT) { back ->
                val peerId    = back.arguments?.getString("peerId") ?: ""
                val peerName  = back.arguments?.getString("peerName") ?: ""
                val peerColor = back.arguments?.getString("peerColor")?.toIntOrNull() ?: 0xFF2979FF.toInt()
                ChatScreen(
                    peerId = peerId, peerName = peerName, peerColor = peerColor,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// ─── Bottom Nav ───────────────────────────────────────────────────────────────

@Composable
fun BlipBottomNav(
    currentRoute: String?,
    navController: androidx.navigation.NavHostController
) {
    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) BlipColors.Primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                    ) {
                        if (selected) {
                            Icon(
                                item.icon, null,
                                tint = BlipColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                item.icon, null,
                                tint = BlipColors.Muted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(
                        item.label,
                        fontFamily = SoraFamily,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) BlipColors.Primary else BlipColors.Muted
                    )
                }
            }
        }
    }
}
