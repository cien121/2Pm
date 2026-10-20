package com.chan.weave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Weave design system — "Obsidian Gold".
 *
 * Near-black canvas, gold accents. Premium, calm, distinctive.
 * Dark-first.
 */

// ── Palette ──────────────────────────────────────────────────────────
val NightCanvas = Color(0xFF0B0B0D)      // app background (obsidian)
val NightSurface = Color(0xFF141417)    // cards
val NightSurfaceHigh = Color(0xFF1D1D21)
val NightOutline = Color(0x26FFFFFF)    // 15% white hairlines

val AuroraCyan = Color(0xFFD4A853)      // primary: gold
val AuroraViolet = Color(0xFFB8860B)    // secondary: dark gold
val AuroraMagenta = Color(0xFFF0D090)   // tertiary: light gold

val InkPrimary = Color(0xFFF5F0E6)
val InkSecondary = Color(0xFFA89F8D)
val InkTertiary = Color(0xFF6B655A)

val SignalUp = Color(0xFF4ADE80)
val SignalDown = Color(0xFFD4A853)
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
