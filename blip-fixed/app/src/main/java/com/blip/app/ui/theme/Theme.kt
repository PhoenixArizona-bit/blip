package com.blip.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blip.app.R

// ─── Sora Font ────────────────────────────────────────────────────────────────
val SoraFamily = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold)
)

// ─── Blip Color Palette ───────────────────────────────────────────────────────
object BlipColors {
    val Primary       = Color(0xFF2979FF)  // Vivid blue
    val PrimaryLight  = Color(0xFF5393FF)
    val PrimaryDark   = Color(0xFF0046CB)
    val Secondary     = Color(0xFF00E5FF)  // Cyan accent
    val Background    = Color(0xFFF5F7FF)  // Soft blue-white
    val Surface       = Color(0xFFFFFFFF)
    val SurfaceVar    = Color(0xFFEEF2FF)
    val OnPrimary     = Color(0xFFFFFFFF)
    val OnBackground  = Color(0xFF0D1B3E)
    val OnSurface     = Color(0xFF1A2340)
    val Outline       = Color(0xFFDDE3F5)
    val Muted         = Color(0xFF8A96B8)
    val GradientStart = Color(0xFF2979FF)
    val GradientEnd   = Color(0xFF00E5FF)
    val MessageSent   = Color(0xFF2979FF)
    val MessageRecv   = Color(0xFFEEF2FF)
    val Online        = Color(0xFF00C853)
    val Danger        = Color(0xFFFF1744)
    // Avatar palette
    val AvatarColors  = listOf(
        0xFF2979FF.toInt(), 0xFF00BCD4.toInt(), 0xFF7C4DFF.toInt(),
        0xFFFF6D00.toInt(), 0xFF00C853.toInt(), 0xFFD500F9.toInt(),
        0xFFFF1744.toInt(), 0xFFFFAB00.toInt(), 0xFF00B0FF.toInt(),
        0xFF76FF03.toInt()
    )
}

// ─── Typography ───────────────────────────────────────────────────────────────
val BlipTypography = Typography(
    displayLarge  = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold,    fontSize = 36.sp),
    displayMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold,    fontSize = 28.sp),
    titleLarge    = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,fontSize = 22.sp),
    titleMedium   = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,fontSize = 18.sp),
    titleSmall    = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium,  fontSize = 15.sp),
    bodyLarge     = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal,  fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal,  fontSize = 14.sp),
    bodySmall     = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal,  fontSize = 12.sp),
    labelLarge    = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium,  fontSize = 14.sp),
    labelSmall    = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal,  fontSize = 11.sp),
)

// ─── Material Theme ───────────────────────────────────────────────────────────
private val BlipColorScheme = lightColorScheme(
    primary          = BlipColors.Primary,
    onPrimary        = BlipColors.OnPrimary,
    primaryContainer = BlipColors.PrimaryLight,
    secondary        = BlipColors.Secondary,
    background       = BlipColors.Background,
    surface          = BlipColors.Surface,
    surfaceVariant   = BlipColors.SurfaceVar,
    onBackground     = BlipColors.OnBackground,
    onSurface        = BlipColors.OnSurface,
    outline          = BlipColors.Outline,
)

@Composable
fun BlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlipColorScheme,
        typography  = BlipTypography,
        content     = content
    )
}
