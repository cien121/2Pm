package com.chan.ciancore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * CianCore design system — "Aurora Night".
 *
 * Deep navy-black canvas, aurora cyan→violet accents, frosted-glass
 * surfaces. Dark-first; light theme is not a priority for v1.
 */

// ── Palette ──────────────────────────────────────────────────────────
val NightCanvas = Color(0xFF0B0E14)      // app background
val NightSurface = Color(0xFF151B26)    // cards
val NightSurfaceHigh = Color(0xFF1C2431)
val NightOutline = Color(0x26FFFFFF)    // 15% white hairlines

val AuroraCyan = Color(0xFF00E5CC)
val AuroraViolet = Color(0xFF7B61FF)
val AuroraMagenta = Color(0xFFFF5CA8)

val InkPrimary = Color(0xFFE8ECF4)
val InkSecondary = Color(0xFF8B94A7)
val InkTertiary = Color(0xFF5A6376)

val SignalUp = Color(0xFF00E5A0)
val SignalDown = Color(0xFF4CC3FF)
val SignalWarn = Color(0xFFFFB545)
val SignalError = Color(0xFFFF6B6B)

// ── Gradients ────────────────────────────────────────────────────────
val AuroraGradient = Brush.linearGradient(
    listOf(AuroraCyan, AuroraViolet, AuroraMagenta)
)
val AuroraGradientVertical = Brush.verticalGradient(
    listOf(AuroraCyan.copy(alpha = 0.9f), AuroraViolet.copy(alpha = 0.9f))
)
val HeroGlow = Brush.radialGradient(
    listOf(
        AuroraViolet.copy(alpha = 0.35f),
        AuroraCyan.copy(alpha = 0.12f),
        Color.Transparent
    )
)

// ── Latency color ramp (shared) ──────────────────────────────────────
fun latencyColor(ms: Long): Color = when {
    ms < 0 -> InkTertiary
    ms < 300 -> SignalUp
    ms < 800 -> SignalWarn
    else -> SignalError
}

// ── Material3 scheme ─────────────────────────────────────────────────
private val CianCoreDarkScheme = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = Color(0xFF06231F),
    primaryContainer = AuroraViolet,
    onPrimaryContainer = Color.White,
    secondary = AuroraViolet,
    tertiary = AuroraMagenta,
    background = NightCanvas,
    onBackground = InkPrimary,
    surface = NightSurface,
    onSurface = InkPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = InkSecondary,
    outline = NightOutline,
    outlineVariant = Color(0x14FFFFFF),
    error = SignalError
)

@Composable
fun CianCoreTheme(content: @Composable () -> Unit) {
    // Force dark — the design system is dark-first.
    MaterialTheme(
        colorScheme = CianCoreDarkScheme,
        content = content
    )
    // Suppress unused-parameter warning for future light-theme support.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
}
